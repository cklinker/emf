package com.emf.runtime.flow.executor;

import com.emf.runtime.flow.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

/**
 * Executes Map states by iterating over an array in the state data
 * and executing a sub-flow for each item.
 *
 * @since 1.0.0
 */
public class MapStateExecutor implements StateExecutor {

    private static final Logger log = LoggerFactory.getLogger(MapStateExecutor.class);

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
        List<Future<Map<String, Object>>> futures = new ArrayList<>();
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

        // Collect results
        List<Map<String, Object>> results = new ArrayList<>();
        for (int i = 0; i < futures.size(); i++) {
            try {
                results.add(futures.get(i).get());
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

        // Apply ResultPath
        Map<String, Object> afterResult = dataResolver.applyResultPath(
            context.stateData(), results, map.resultPath());
        Map<String, Object> output = dataResolver.applyOutputPath(afterResult, map.outputPath());

        if (map.end()) {
            return StateExecutionResult.terminalSuccess(output);
        }
        return StateExecutionResult.success(map.next(), output);
    }

    private StateExecutionResult handleNoItems(StateDefinition.MapState map, FlowExecutionContext context) {
        Map<String, Object> afterResult = dataResolver.applyResultPath(
            context.stateData(), List.of(), map.resultPath());
        Map<String, Object> output = dataResolver.applyOutputPath(afterResult, map.outputPath());
        if (map.end()) {
            return StateExecutionResult.terminalSuccess(output);
        }
        return StateExecutionResult.success(map.next(), output);
    }
}
