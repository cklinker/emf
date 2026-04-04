package io.kelta.runtime.module.integration.spi.graalvm;

import io.kelta.runtime.module.integration.spi.ScriptExecutor;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.SandboxPolicy;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * GraalVM-based implementation of {@link ScriptExecutor} that executes JavaScript
 * scripts in a sandboxed environment.
 *
 * <p>Security features:
 * <ul>
 *   <li>No filesystem access</li>
 *   <li>No network access</li>
 *   <li>No host class access (only explicitly allowed types)</li>
 *   <li>Configurable execution timeout (default 30 seconds)</li>
 *   <li>Constrained sandbox policy</li>
 * </ul>
 *
 * <p>Scripts receive bindings as global variables. The script's last expression
 * value is captured as the return value and converted to a Java Map.
 *
 * @since 1.0.0
 */
public class GraalVmScriptExecutor implements ScriptExecutor {

    private static final Logger log = LoggerFactory.getLogger(GraalVmScriptExecutor.class);
    private static final String JS = "js";
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    private final Engine engine;
    private final int defaultTimeoutSeconds;
    private final ExecutorService timeoutExecutor;
    private final ConcurrentHashMap<String, ScriptInfo> scriptRegistry = new ConcurrentHashMap<>();

    public GraalVmScriptExecutor() {
        this(DEFAULT_TIMEOUT_SECONDS);
    }

    public GraalVmScriptExecutor(int defaultTimeoutSeconds) {
        this.defaultTimeoutSeconds = defaultTimeoutSeconds;
        this.engine = Engine.newBuilder(JS)
            .sandbox(SandboxPolicy.CONSTRAINED)
            .out(OutputStream.nullOutputStream())
            .err(OutputStream.nullOutputStream())
            .option("engine.WarnInterpreterOnly", "false")
            .build();
        this.timeoutExecutor = Executors.newVirtualThreadPerTaskExecutor();
        log.info("GraalVM ScriptExecutor initialized with {}s default timeout", defaultTimeoutSeconds);
    }

    /**
     * Registers a script in the in-memory registry. In production, this would
     * be backed by a database lookup.
     */
    public void registerScript(ScriptInfo script) {
        scriptRegistry.put(script.id(), script);
    }

    @Override
    public Optional<ScriptInfo> getScript(String scriptId) {
        return Optional.ofNullable(scriptRegistry.get(scriptId));
    }

    @Override
    public String queueExecution(String tenantId, String scriptId, String triggerType, String recordId) {
        String executionId = UUID.randomUUID().toString();
        log.info("Script execution queued: id={}, tenant={}, script={}, trigger={}, record={}",
            executionId, tenantId, scriptId, triggerType, recordId);
        return executionId;
    }

    @Override
    public ScriptExecutionResult execute(ScriptExecutionRequest request) {
        long startTime = System.currentTimeMillis();

        if (request.scriptSource() == null || request.scriptSource().isBlank()) {
            return ScriptExecutionResult.failure("Script source is required",
                System.currentTimeMillis() - startTime);
        }

        int timeout = request.timeoutSeconds() > 0 ? request.timeoutSeconds() : defaultTimeoutSeconds;

        Context context = createSandboxedContext();
        try {
            Value bindings = context.getBindings(JS);
            if (request.bindings() != null) {
                for (Map.Entry<String, Object> entry : request.bindings().entrySet()) {
                    bindings.putMember(entry.getKey(), toGuestValue(entry.getValue()));
                }
            }

            Source source = Source.newBuilder(JS, request.scriptSource(), "script.js")
                .cached(false)
                .build();

            Future<Value> future = timeoutExecutor.submit(() -> context.eval(source));
            Value result;
            try {
                result = future.get(timeout, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                context.close(true);
                long elapsed = System.currentTimeMillis() - startTime;
                log.warn("Script execution timed out after {}ms (limit={}s)", elapsed, timeout);
                return ScriptExecutionResult.failure(
                    "Script execution timed out after " + timeout + " seconds", elapsed);
            } catch (ExecutionException e) {
                throw e.getCause();
            }

            Map<String, Object> output = convertResult(result);
            long elapsed = System.currentTimeMillis() - startTime;

            log.debug("Script executed successfully in {}ms", elapsed);
            context.close();
            return ScriptExecutionResult.success(output, elapsed);

        } catch (PolyglotException e) {
            long elapsed = System.currentTimeMillis() - startTime;
            if (e.isCancelled()) {
                log.warn("Script execution cancelled after {}ms", elapsed);
                return ScriptExecutionResult.failure(
                    "Script execution timed out after " + timeout + " seconds", elapsed);
            }
            log.warn("Script execution failed after {}ms: {}", elapsed, e.getMessage());
            try { context.close(); } catch (Exception ignored) {}
            return ScriptExecutionResult.failure(
                "Script error: " + e.getMessage(), elapsed);
        } catch (Throwable e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("Unexpected error during script execution: {}", e.getMessage(), e);
            try { context.close(); } catch (Exception ignored) {}
            return ScriptExecutionResult.failure(
                "Execution error: " + e.getMessage(), elapsed);
        }
    }

    private Context createSandboxedContext() {
        return Context.newBuilder(JS)
            .engine(engine)
            .sandbox(SandboxPolicy.CONSTRAINED)
            .allowHostAccess(HostAccess.CONSTRAINED)
            .allowCreateProcess(false)
            .allowCreateThread(false)
            .allowNativeAccess(false)
            .build();
    }

    @SuppressWarnings("unchecked")
    private Object toGuestValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> typedMap = (Map<String, Object>) map;
            return ProxyObject.fromMap(
                typedMap.entrySet().stream()
                    .collect(HashMap::new,
                        (m, e) -> m.put(e.getKey(), toGuestValue(e.getValue())),
                        HashMap::putAll));
        }
        if (value instanceof List<?> list) {
            Object[] arr = list.stream().map(this::toGuestValue).toArray();
            return ProxyArray.fromArray(arr);
        }
        return value;
    }

    private Map<String, Object> convertResult(Value value) {
        Map<String, Object> output = new HashMap<>();
        if (value == null || value.isNull()) {
            output.put("result", null);
        } else {
            output.put("result", convertValue(value));
        }
        return output;
    }

    private Object convertObject(Value value) {
        Map<String, Object> map = new HashMap<>();
        for (String key : value.getMemberKeys()) {
            Value member = value.getMember(key);
            map.put(key, convertValue(member));
        }
        return map;
    }

    private Object convertArray(Value value) {
        List<Object> list = new ArrayList<>();
        for (long i = 0; i < value.getArraySize(); i++) {
            list.add(convertValue(value.getArrayElement(i)));
        }
        return list;
    }

    private Object convertValue(Value value) {
        if (value == null || value.isNull()) return null;
        if (value.isBoolean()) return value.asBoolean();
        if (value.isNumber()) {
            if (value.fitsInInt()) return value.asInt();
            if (value.fitsInLong()) return value.asLong();
            return value.asDouble();
        }
        if (value.isString()) return value.asString();
        if (value.hasArrayElements()) return convertArray(value);
        if (value.hasMembers()) return convertObject(value);
        return value.toString();
    }

    /**
     * Closes the shared engine and timeout executor. Should be called during application shutdown.
     */
    public void close() {
        timeoutExecutor.shutdown();
        engine.close();
    }
}
