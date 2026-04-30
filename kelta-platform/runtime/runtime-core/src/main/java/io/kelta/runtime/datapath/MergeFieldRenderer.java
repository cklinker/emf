package io.kelta.runtime.datapath;

import io.kelta.runtime.formula.FormulaEvaluator;
import io.kelta.runtime.model.CollectionDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders merge tags in text templates using DataPath resolution and, when a
 * {@link FormulaEvaluator} is supplied, formula evaluation.
 *
 * <p>Merge tag syntax uses double curly braces: {@code {{expression}}}. The
 * expression is interpreted as either:
 * <ul>
 *   <li>a field path against the source collection (e.g. {@code customer_id.name}),
 *       resolved via {@link DataPathResolver}; or</li>
 *   <li>a formula call (e.g. {@code TEXT(amount)} or {@code IF(active, "Yes", "No")}),
 *       evaluated via the configured {@link FormulaEvaluator}.</li>
 * </ul>
 * The two cases are distinguished by whether the expression contains an opening
 * parenthesis. If a {@link FormulaEvaluator} is not configured, expressions
 * containing parentheses fall back to data-path resolution and will produce an
 * empty string when parsing fails.
 *
 * <p>Used by workflow action handlers for rendering email subjects/bodies,
 * notification messages, webhook body templates, and any text that needs
 * dynamic field substitution.
 *
 * <p>Missing values (null resolved values) are replaced with an empty string.
 *
 * @since 1.0.0
 */
public class MergeFieldRenderer {

    private static final Logger logger = LoggerFactory.getLogger(MergeFieldRenderer.class);

    /**
     * Pattern matching merge tags: {{expression}}
     * Captures the content between double curly braces.
     */
    private static final Pattern MERGE_TAG_PATTERN = Pattern.compile("\\{\\{\\s*([^}]+?)\\s*}}");

    private final DataPathResolver resolver;
    private final FormulaEvaluator formulaEvaluator;

    /**
     * Creates a renderer that supports field-path merge tags only.
     */
    public MergeFieldRenderer(DataPathResolver resolver) {
        this(resolver, null);
    }

    /**
     * Creates a renderer that supports both field-path merge tags and formula
     * function calls.
     *
     * @param resolver         the data path resolver for resolving field-path tags
     * @param formulaEvaluator the formula evaluator for resolving function-call
     *                         tags; pass {@code null} to disable function support
     */
    public MergeFieldRenderer(DataPathResolver resolver, FormulaEvaluator formulaEvaluator) {
        this.resolver = Objects.requireNonNull(resolver, "resolver cannot be null");
        this.formulaEvaluator = formulaEvaluator;
    }

    /**
     * Renders all merge tags in the template string.
     *
     * @param template         the template containing {@code {{path}}} merge tags
     * @param sourceRecord     the starting record data
     * @param sourceCollection the starting collection definition
     * @return the rendered string with merge tags replaced by resolved values
     */
    public String render(String template, Map<String, Object> sourceRecord,
                         CollectionDefinition sourceCollection) {
        if (template == null || template.isEmpty()) {
            return template;
        }
        Objects.requireNonNull(sourceRecord, "sourceRecord cannot be null");
        Objects.requireNonNull(sourceCollection, "sourceCollection cannot be null");

        Matcher matcher = MERGE_TAG_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String expression = matcher.group(1).trim();
            String replacement = renderExpression(expression, sourceRecord, sourceCollection);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    private String renderExpression(String expression,
                                    Map<String, Object> sourceRecord,
                                    CollectionDefinition sourceCollection) {
        boolean looksLikeFunctionCall = expression.indexOf('(') >= 0;

        if (looksLikeFunctionCall && formulaEvaluator != null) {
            try {
                Object value = formulaEvaluator.evaluate(expression, sourceRecord);
                return value != null ? value.toString() : "";
            } catch (Exception e) {
                logger.warn("Failed to evaluate merge tag formula '{{{}}}': {}",
                    expression, e.getMessage());
                return "";
            }
        }

        try {
            DataPath path = DataPath.parse(expression, sourceCollection.name());
            Object value = resolver.resolve(path, sourceRecord, sourceCollection);
            return value != null ? value.toString() : "";
        } catch (Exception e) {
            logger.warn("Failed to resolve merge tag '{{{}}}': {}",
                expression, e.getMessage());
            return "";
        }
    }
}
