package com.emf.runtime.flow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses a flow definition JSON string into a {@link FlowDefinition} object.
 * <p>
 * The JSON format follows AWS Step Functions conventions adapted for EMF:
 * <pre>
 * {
 *   "Comment": "...",
 *   "StartAt": "FirstState",
 *   "States": {
 *     "FirstState": { "Type": "Task", "Resource": "HTTP_CALLOUT", ... },
 *     ...
 *   },
 *   "_metadata": { "nodePositions": { ... } }
 * }
 * </pre>
 *
 * @since 1.0.0
 */
public class FlowDefinitionParser {

    private final ObjectMapper objectMapper;

    public FlowDefinitionParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Parses a JSON string into a FlowDefinition.
     *
     * @param json the flow definition JSON
     * @return the parsed flow definition
     * @throws FlowDefinitionException if the JSON is malformed or missing required fields
     */
    public FlowDefinition parse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);

            String comment = textOrNull(root, "Comment");
            String startAt = requiredText(root, "StartAt");
            JsonNode statesNode = root.get("States");
            if (statesNode == null || !statesNode.isObject()) {
                throw new FlowDefinitionException("Flow definition must contain a 'States' object");
            }

            Map<String, StateDefinition> states = new LinkedHashMap<>();
            var fields = statesNode.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                states.put(entry.getKey(), parseState(entry.getKey(), entry.getValue()));
            }

            Map<String, Object> metadata = null;
            JsonNode metaNode = root.get("_metadata");
            if (metaNode != null && metaNode.isObject()) {
                metadata = objectMapper.convertValue(metaNode, new TypeReference<>() {});
            }

            return new FlowDefinition(comment, startAt, states, metadata);
        } catch (FlowDefinitionException e) {
            throw e;
        } catch (Exception e) {
            throw new FlowDefinitionException("Failed to parse flow definition: " + e.getMessage(), e);
        }
    }

    private StateDefinition parseState(String stateId, JsonNode node) {
        String type = requiredText(node, "Type");
        String name = textOrDefault(node, "Name", stateId);
        String comment = textOrNull(node, "Comment");

        return switch (type) {
            case "Task" -> parseTaskState(name, comment, node);
            case "Choice" -> parseChoiceState(name, comment, node);
            case "Parallel" -> parseParallelState(name, comment, node);
            case "Map" -> parseMapState(name, comment, node);
            case "Wait" -> parseWaitState(name, comment, node);
            case "Pass" -> parsePassState(name, comment, node);
            case "Succeed" -> new StateDefinition.SucceedState(name, comment);
            case "Fail" -> new StateDefinition.FailState(
                name, comment,
                textOrNull(node, "Error"),
                textOrNull(node, "Cause")
            );
            default -> throw new FlowDefinitionException("Unknown state type: " + type);
        };
    }

    private StateDefinition.TaskState parseTaskState(String name, String comment, JsonNode node) {
        return new StateDefinition.TaskState(
            name, comment,
            requiredText(node, "Resource"),
            textOrNull(node, "InputPath"),
            textOrNull(node, "OutputPath"),
            textOrNull(node, "ResultPath"),
            intOrNull(node, "TimeoutSeconds"),
            textOrNull(node, "Next"),
            boolOrFalse(node, "End"),
            parseRetryPolicies(node.get("Retry")),
            parseCatchPolicies(node.get("Catch"))
        );
    }

    private StateDefinition.ChoiceState parseChoiceState(String name, String comment, JsonNode node) {
        JsonNode choicesNode = node.get("Choices");
        if (choicesNode == null || !choicesNode.isArray()) {
            throw new FlowDefinitionException("Choice state must contain a 'Choices' array");
        }

        List<ChoiceRule> choices = new ArrayList<>();
        for (JsonNode ruleNode : choicesNode) {
            choices.add(parseChoiceRule(ruleNode));
        }

        return new StateDefinition.ChoiceState(
            name, comment, choices,
            textOrNull(node, "Default")
        );
    }

    private StateDefinition.ParallelState parseParallelState(String name, String comment, JsonNode node) {
        JsonNode branchesNode = node.get("Branches");
        if (branchesNode == null || !branchesNode.isArray()) {
            throw new FlowDefinitionException("Parallel state must contain a 'Branches' array");
        }

        List<FlowDefinition> branches = new ArrayList<>();
        for (JsonNode branchNode : branchesNode) {
            branches.add(parse(branchNode.toString()));
        }

        return new StateDefinition.ParallelState(
            name, comment, branches,
            textOrNull(node, "InputPath"),
            textOrNull(node, "OutputPath"),
            textOrNull(node, "ResultPath"),
            textOrNull(node, "Next"),
            boolOrFalse(node, "End"),
            parseRetryPolicies(node.get("Retry")),
            parseCatchPolicies(node.get("Catch"))
        );
    }

    private StateDefinition.MapState parseMapState(String name, String comment, JsonNode node) {
        JsonNode iteratorNode = node.get("Iterator");
        FlowDefinition iterator = null;
        if (iteratorNode != null && iteratorNode.isObject()) {
            iterator = parse(iteratorNode.toString());
        }

        return new StateDefinition.MapState(
            name, comment,
            textOrNull(node, "ItemsPath"),
            iterator,
            intOrNull(node, "MaxConcurrency"),
            textOrNull(node, "InputPath"),
            textOrNull(node, "OutputPath"),
            textOrNull(node, "ResultPath"),
            textOrNull(node, "Next"),
            boolOrFalse(node, "End")
        );
    }

    private StateDefinition.WaitState parseWaitState(String name, String comment, JsonNode node) {
        return new StateDefinition.WaitState(
            name, comment,
            intOrNull(node, "Seconds"),
            textOrNull(node, "Timestamp"),
            textOrNull(node, "TimestampPath"),
            textOrNull(node, "EventName"),
            textOrNull(node, "Next"),
            boolOrFalse(node, "End")
        );
    }

    private StateDefinition.PassState parsePassState(String name, String comment, JsonNode node) {
        Map<String, Object> result = null;
        JsonNode resultNode = node.get("Result");
        if (resultNode != null) {
            result = objectMapper.convertValue(resultNode, new TypeReference<>() {});
        }

        return new StateDefinition.PassState(
            name, comment, result,
            textOrNull(node, "InputPath"),
            textOrNull(node, "OutputPath"),
            textOrNull(node, "ResultPath"),
            textOrNull(node, "Next"),
            boolOrFalse(node, "End")
        );
    }

    // -------------------------------------------------------------------------
    // Choice Rule Parsing
    // -------------------------------------------------------------------------

    private ChoiceRule parseChoiceRule(JsonNode node) {
        String next = textOrNull(node, "Next");

        // Compound rules
        if (node.has("And")) {
            return new ChoiceRule.And(parseRuleList(node.get("And")), next);
        }
        if (node.has("Or")) {
            return new ChoiceRule.Or(parseRuleList(node.get("Or")), next);
        }
        if (node.has("Not")) {
            return new ChoiceRule.Not(parseChoiceRule(node.get("Not")), next);
        }

        // Simple comparison rules
        String variable = textOrNull(node, "Variable");

        if (node.has("StringEquals")) {
            return new ChoiceRule.StringEquals(variable, node.get("StringEquals").asText(), next);
        }
        if (node.has("StringNotEquals")) {
            return new ChoiceRule.StringNotEquals(variable, node.get("StringNotEquals").asText(), next);
        }
        if (node.has("StringGreaterThan")) {
            return new ChoiceRule.StringGreaterThan(variable, node.get("StringGreaterThan").asText(), next);
        }
        if (node.has("StringLessThan")) {
            return new ChoiceRule.StringLessThan(variable, node.get("StringLessThan").asText(), next);
        }
        if (node.has("StringGreaterThanEquals")) {
            return new ChoiceRule.StringGreaterThanEquals(variable, node.get("StringGreaterThanEquals").asText(), next);
        }
        if (node.has("StringLessThanEquals")) {
            return new ChoiceRule.StringLessThanEquals(variable, node.get("StringLessThanEquals").asText(), next);
        }
        if (node.has("StringMatches")) {
            return new ChoiceRule.StringMatches(variable, node.get("StringMatches").asText(), next);
        }
        if (node.has("NumericEquals")) {
            return new ChoiceRule.NumericEquals(variable, node.get("NumericEquals").asDouble(), next);
        }
        if (node.has("NumericNotEquals")) {
            return new ChoiceRule.NumericNotEquals(variable, node.get("NumericNotEquals").asDouble(), next);
        }
        if (node.has("NumericGreaterThan")) {
            return new ChoiceRule.NumericGreaterThan(variable, node.get("NumericGreaterThan").asDouble(), next);
        }
        if (node.has("NumericLessThan")) {
            return new ChoiceRule.NumericLessThan(variable, node.get("NumericLessThan").asDouble(), next);
        }
        if (node.has("NumericGreaterThanEquals")) {
            return new ChoiceRule.NumericGreaterThanEquals(variable, node.get("NumericGreaterThanEquals").asDouble(), next);
        }
        if (node.has("NumericLessThanEquals")) {
            return new ChoiceRule.NumericLessThanEquals(variable, node.get("NumericLessThanEquals").asDouble(), next);
        }
        if (node.has("BooleanEquals")) {
            return new ChoiceRule.BooleanEquals(variable, node.get("BooleanEquals").asBoolean(), next);
        }
        if (node.has("IsPresent")) {
            return new ChoiceRule.IsPresent(variable, node.get("IsPresent").asBoolean(), next);
        }
        if (node.has("IsNull")) {
            return new ChoiceRule.IsNull(variable, node.get("IsNull").asBoolean(), next);
        }

        throw new FlowDefinitionException("Unrecognized choice rule: " + node);
    }

    private List<ChoiceRule> parseRuleList(JsonNode arrayNode) {
        List<ChoiceRule> rules = new ArrayList<>();
        if (arrayNode != null && arrayNode.isArray()) {
            for (JsonNode child : arrayNode) {
                rules.add(parseChoiceRule(child));
            }
        }
        return rules;
    }

    // -------------------------------------------------------------------------
    // Retry & Catch Parsing
    // -------------------------------------------------------------------------

    private List<RetryPolicy> parseRetryPolicies(JsonNode retryNode) {
        if (retryNode == null || !retryNode.isArray()) {
            return List.of();
        }
        List<RetryPolicy> policies = new ArrayList<>();
        for (JsonNode r : retryNode) {
            List<String> errorEquals = parseStringList(r.get("ErrorEquals"));
            int interval = r.has("IntervalSeconds") ? r.get("IntervalSeconds").asInt() : RetryPolicy.DEFAULT_INTERVAL_SECONDS;
            int maxAttempts = r.has("MaxAttempts") ? r.get("MaxAttempts").asInt() : RetryPolicy.DEFAULT_MAX_ATTEMPTS;
            double backoff = r.has("BackoffRate") ? r.get("BackoffRate").asDouble() : RetryPolicy.DEFAULT_BACKOFF_RATE;
            policies.add(new RetryPolicy(errorEquals, interval, maxAttempts, backoff));
        }
        return policies;
    }

    private List<CatchPolicy> parseCatchPolicies(JsonNode catchNode) {
        if (catchNode == null || !catchNode.isArray()) {
            return List.of();
        }
        List<CatchPolicy> policies = new ArrayList<>();
        for (JsonNode c : catchNode) {
            List<String> errorEquals = parseStringList(c.get("ErrorEquals"));
            String resultPath = textOrNull(c, "ResultPath");
            String next = requiredText(c, "Next");
            policies.add(new CatchPolicy(errorEquals, resultPath, next));
        }
        return policies;
    }

    // -------------------------------------------------------------------------
    // JSON Utilities
    // -------------------------------------------------------------------------

    private String requiredText(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull() || !child.isTextual()) {
            throw new FlowDefinitionException("Required field '" + field + "' is missing or not a string");
        }
        return child.asText();
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        return (child != null && child.isTextual()) ? child.asText() : null;
    }

    private String textOrDefault(JsonNode node, String field, String defaultValue) {
        String value = textOrNull(node, field);
        return value != null ? value : defaultValue;
    }

    private Integer intOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        return (child != null && child.isNumber()) ? child.asInt() : null;
    }

    private boolean boolOrFalse(JsonNode node, String field) {
        JsonNode child = node.get(field);
        return child != null && child.asBoolean(false);
    }

    private List<String> parseStringList(JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (JsonNode item : arrayNode) {
            result.add(item.asText());
        }
        return result;
    }
}
