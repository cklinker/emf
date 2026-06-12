package io.kelta.runtime.flow.executor;

import io.kelta.runtime.flow.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

/**
 * Executes Map states by iterating over an array in the state data
 * and executing a sub-flow for each item.
 * <p>
 * Iterations commonly use {@code Catch: [{ ErrorEquals: [States.ALL], Next: Done }]}
 * so one bad item does not abort the whole batch. Historically those catches made
 * per-iteration failures disappear: the Map state returned a list of "successful"
 * iteration results indistinguishable from a clean run. This executor now records
 * every iteration whose sub-flow caught an error and surfaces aggregate counts
 * plus a capped sample of errors in the Map state's result, so callers — and the
 * top-level execution summary — see exactly how many items failed.
 *
 * @since 1.0.0
 */
public class MapStateExecutor implements StateExecutor {

    private static final Logger log = LoggerFactory.getLogger(MapStateExecutor.class);

    /** Maximum number of per-iteration errors retained on the Map result. */
    public static final int MAX_ERROR_SAMPLES = 10;

    private final StateDataResolver dataResolver;
    private final ExecutorService executorService;
    private final FlowEngine engine;

    public MapStateExecutor(StateDataResolver dataResolver,
                            ExecutorService executorService,
                            FlowEngine engine) {
        this.dataResolver = dataResolver;
        this.executorService = executorService;
        this.engine = engine;
    }

    @Override
    public String stateType() {
        return "Map";
    }

    @Override
    @SuppressWarnings("unchecked")
    public StateExecutionResult execute(StateDefinition state, FlowExecutionContext context) {
        StateDefinition.MapState map = (StateDefinition.MapState) state;

        Map<String, Object> input = dataResolver.applyInputPath(context.stateData(), map.inputPath());

        // Read the items array
        Object itemsObj = dataResolver.readPath(input, map.itemsPath());
        if (!(itemsObj instanceof List)) {
            return StateExecutionResult.failure("States.ItemsNotArray",
                "ItemsPath '" + map.itemsPath() + "' did not resolve to an array",
                context.stateData());
        }

        List<Object> items = (List<Object>) itemsObj;
        if (items.isEmpty() || map.iterator() == null) {
            return handleNoItems(map, context);
        }

        // Determine concurrency
        int maxConcurrency = map.maxConcurrency() != null ? map.maxConcurrency() : items.size();
        Semaphore semaphore = new Semaphore(maxConcurrency);

        // Execute sub-flow for each item
        List<Future<SubFlowResult>> futures = new ArrayList<>();
        for (Object item : items) {
            Map<String, Object> itemInput;
            if (item instanceof Map) {
                itemInput = new LinkedHashMap<>((Map<String, Object>) item);
            } else {
                itemInput = new LinkedHashMap<>();
                itemInput.put("item", item);
            }

            futures.add(executorService.submit(() -> {
                semaphore.acquire();
                try {
                    return engine.executeSubFlow(map.iterator(), itemInput, context);
                } finally {
                    semaphore.release();
                }
            }));
        }

        // Collect per-iteration results, separating clean iterations from those
        // whose sub-flow caught an error.
        List<Map<String, Object>> itemResults = new ArrayList<>();
        List<Map<String, Object>> errorSamples = new ArrayList<>();
        int succeeded = 0;
        int failed = 0;

        for (int i = 0; i < futures.size(); i++) {
            try {
                SubFlowResult itemResult = futures.get(i).get();
                itemResults.add(itemResult.stateData());

                if (itemResult.hadCaughtError()) {
                    failed++;
                    if (errorSamples.size() < MAX_ERROR_SAMPLES) {
                        errorSamples.add(buildErrorSample(i, itemResult.caughtErrors()));
                    }
                    log.warn("Map '{}' iteration {} caught {} error(s) — first: {}",
                        map.name(), i, itemResult.caughtErrors().size(),
                        itemResult.caughtErrors().get(0).errorCode());
                } else {
                    succeeded++;
                }
            } catch (ExecutionException e) {
                log.error("Map item {} failed", i, e.getCause());
                return StateExecutionResult.failure("MapItemFailed",
                    "Item " + i + " failed: " + e.getCause().getMessage(),
                    context.stateData());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return StateExecutionResult.failure("MapInterrupted",
                    "Map execution interrupted", context.stateData());
            }
        }

        // Aggregate iteration outcomes into a single result payload. The items
        // list is preserved (so downstream states can still read the per-item
        // data) and joined by succeeded/failed/errors so callers can detect
        // partial failure without having to introspect each item.
        Map<String, Object> aggregated = new LinkedHashMap<>();
        aggregated.put("succeeded", succeeded);
        aggregated.put("failed", failed);
        aggregated.put("errors", errorSamples);
        aggregated.put("items", itemResults);

        // Propagate failedCount up to the parent execution so the top-level
        // execution summary reflects it even when the Map state passes.
        if (failed > 0) {
            context.addFailedCount(failed);
        }

        // failOnPartial: convert any caught iteration into a state failure so
        // the run cannot silently report success.
        if (map.failOnPartial() && failed > 0) {
            String message = failed + " of " + items.size()
                + " iteration(s) caught errors; failOnPartial=true";
            log.warn("Map '{}' failing: {}", map.name(), message);
            // Preserve the aggregate at the configured ResultPath so the
            // failure record still carries the per-iteration sample.
            Map<String, Object> failureData = dataResolver.applyResultPath(
                context.stateData(), aggregated, map.resultPath());
            return StateExecutionResult.failure("MapPartialFailure", message, failureData);
        }

        // Apply ResultPath
        Map<String, Object> afterResult = dataResolver.applyResultPath(
            context.stateData(), aggregated, map.resultPath());
        Map<String, Object> output = dataResolver.applyOutputPath(afterResult, map.outputPath());

        if (map.end()) {
            return StateExecutionResult.terminalSuccess(output);
        }
        return StateExecutionResult.success(map.next(), output);
    }

    private Map<String, Object> buildErrorSample(int index,
                                                 List<FlowExecutionContext.CaughtError> caughtErrors) {
        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("index", index);
        FlowExecutionContext.CaughtError first = caughtErrors.get(0);
        sample.put("stateId", first.stateId());
        sample.put("error", first.errorCode());
        sample.put("cause", first.errorMessage());
        if (caughtErrors.size() > 1) {
            sample.put("additionalCatches", caughtErrors.size() - 1);
        }
        return sample;
    }

    private StateExecutionResult handleNoItems(StateDefinition.MapState map, FlowExecutionContext context) {
        Map<String, Object> aggregated = new LinkedHashMap<>();
        aggregated.put("succeeded", 0);
        aggregated.put("failed", 0);
        aggregated.put("errors", List.of());
        aggregated.put("items", List.of());

        Map<String, Object> afterResult = dataResolver.applyResultPath(
            context.stateData(), aggregated, map.resultPath());
        Map<String, Object> output = dataResolver.applyOutputPath(afterResult, map.outputPath());
        if (map.end()) {
            return StateExecutionResult.terminalSuccess(output);
        }
        return StateExecutionResult.success(map.next(), output);
    }
}
