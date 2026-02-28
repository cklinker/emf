package com.emf.worker.config;

import com.emf.runtime.flow.*;
import com.emf.runtime.formula.FormulaEvaluator;
import com.emf.runtime.workflow.ActionHandlerRegistry;
import com.emf.worker.flow.JdbcFlowStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Spring configuration for the flow execution engine.
 * <p>
 * Wires the {@link FlowEngine} with its dependencies.
 * The legacy WorkflowEngine has been removed; flows are the sole automation engine.
 * <p>
 * Enabled by default. Disable with {@code emf.flow.enabled=false}.
 *
 * @since 1.0.0
 */
@Configuration
@ConditionalOnProperty(name = "emf.flow.enabled", havingValue = "true", matchIfMissing = true)
public class FlowConfig {

    private static final Logger log = LoggerFactory.getLogger(FlowConfig.class);

    @Bean
    public FlowStore flowStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        return new JdbcFlowStore(jdbcTemplate, objectMapper);
    }

    @Bean
    public FlowEngine flowEngine(FlowStore flowStore,
                                  ActionHandlerRegistry actionHandlerRegistry,
                                  ObjectMapper objectMapper,
                                  @Value("${emf.flow.executor.pool-size:10}") int poolSize) {
        log.info("Creating FlowEngine with thread pool size {}", poolSize);
        return new FlowEngine(flowStore, actionHandlerRegistry, objectMapper, poolSize);
    }

    @Bean
    public FlowTriggerEvaluator flowTriggerEvaluator(FormulaEvaluator formulaEvaluator) {
        return new FlowTriggerEvaluator(formulaEvaluator);
    }

    @Bean
    public InitialStateBuilder initialStateBuilder() {
        return new InitialStateBuilder();
    }

    @Bean
    public WorkflowRuleToFlowMigrator workflowRuleToFlowMigrator() {
        return new WorkflowRuleToFlowMigrator();
    }
}
