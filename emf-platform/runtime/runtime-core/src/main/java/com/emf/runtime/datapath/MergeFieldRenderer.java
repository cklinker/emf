package com.emf.runtime.datapath;

import com.emf.runtime.model.CollectionDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders merge tags in text templates using DataPath resolution.
 *
 * <p>Merge tag syntax uses double curly braces: {@code {{path.expression}}}.
 * For example:
 * <pre>
 *   "Hello {{customer_id.name}}, your order {{id}} is ready"
 *   → "Hello John, your order ORD-001 is ready"
 * </pre>
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

    /**
     * Creates a new MergeFieldRenderer.
     *
     * @param resolver the data path resolver for resolving merge tag expressions
     */
    public MergeFieldRenderer(DataPathResolver resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver cannot be null");
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
            String replacement;

            try {
                DataPath path = DataPath.parse(expression, sourceCollection.name());
                Object value = resolver.resolve(path, sourceRecord, sourceCollection);
                replacement = value != null ? value.toString() : "";
            } catch (Exception e) {
                logger.warn("Failed to resolve merge tag '{{{}}}': {}",
                    expression, e.getMessage());
                replacement = "";
            }

            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }
}
