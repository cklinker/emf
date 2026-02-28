package com.emf.runtime.module.core;

import com.emf.runtime.module.core.handlers.*;
import com.emf.runtime.workflow.ActionHandler;
import com.emf.runtime.workflow.module.EmfModule;
import com.emf.runtime.workflow.module.ModuleContext;

import java.util.ArrayList;
import java.util.List;

/**
 * EMF module that provides core action handlers for workflow execution.
 *
 * <p>This module registers the following action handlers:
 * <ul>
 *   <li><b>FIELD_UPDATE</b> — Updates fields on the triggering record</li>
 *   <li><b>CREATE_RECORD</b> — Creates a new record in a target collection</li>
 *   <li><b>UPDATE_RECORD</b> — Updates a record in any collection (cross-collection)</li>
 *   <li><b>DELETE_RECORD</b> — Deletes a record from a collection</li>
 *   <li><b>CREATE_TASK</b> — Creates a task record for follow-up</li>
 *   <li><b>LOG_MESSAGE</b> — Writes a message to the execution log</li>
 *   <li><b>DECISION</b> — Implements conditional branching (if/else)</li>
 *   <li><b>TRIGGER_FLOW</b> — Invokes another flow as a sub-invocation</li>
 * </ul>
 *
 * <p>Handlers requiring runtime services (CollectionRegistry, FormulaEvaluator,
 * ActionHandlerRegistry) are constructed lazily in {@link #onStartup(ModuleContext)}.
 *
 * @since 1.0.0
 */
public class CoreActionsModule implements EmfModule {

    private final List<ActionHandler> handlers = new ArrayList<>();

    @Override
    public String getId() {
        return "emf-core-actions";
    }

    @Override
    public String getName() {
        return "Core Actions Module";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public void onStartup(ModuleContext context) {
        var objectMapper = context.objectMapper();
        var collectionRegistry = context.collectionRegistry();

        // Simple handlers (ObjectMapper only)
        handlers.add(new FieldUpdateActionHandler(objectMapper));
        handlers.add(new CreateTaskActionHandler(objectMapper));
        handlers.add(new LogMessageActionHandler(objectMapper));

        // TriggerFlow handler — triggers another flow as a sub-invocation
        handlers.add(new TriggerFlowActionHandler(objectMapper));

        // Handlers needing CollectionRegistry
        handlers.add(new CreateRecordActionHandler(objectMapper, collectionRegistry));
        handlers.add(new UpdateRecordActionHandler(objectMapper, collectionRegistry));
        handlers.add(new DeleteRecordActionHandler(objectMapper, collectionRegistry));

        // Handler needing FormulaEvaluator + ActionHandlerRegistry
        handlers.add(new DecisionActionHandler(
            objectMapper,
            context.formulaEvaluator(),
            context.actionHandlerRegistry()
        ));
    }

    @Override
    public List<ActionHandler> getActionHandlers() {
        return handlers;
    }
}
