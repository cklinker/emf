package com.emf.runtime.datapath;

/**
 * Exception thrown when a data path expression is invalid.
 *
 * <p>This can occur when:
 * <ul>
 *   <li>A field name in the path does not exist in the collection</li>
 *   <li>A non-relationship field is used as an intermediate traversal hop</li>
 *   <li>The path expression is empty or malformed</li>
 *   <li>The maximum traversal depth is exceeded</li>
 * </ul>
 *
 * <p>This exception is typically thrown at save time during validation
 * (configuration error), not at runtime during resolution where null
 * is returned gracefully for missing data.
 *
 * @since 1.0.0
 */
public class InvalidDataPathException extends RuntimeException {

    private final String expression;
    private final String segmentName;

    /**
     * Creates an InvalidDataPathException with the expression and message.
     *
     * @param expression the full data path expression
     * @param message    the error description
     */
    public InvalidDataPathException(String expression, String message) {
        super(message);
        this.expression = expression;
        this.segmentName = null;
    }

    /**
     * Creates an InvalidDataPathException with the expression, segment, and message.
     *
     * @param expression  the full data path expression
     * @param segmentName the specific segment that caused the error
     * @param message     the error description
     */
    public InvalidDataPathException(String expression, String segmentName, String message) {
        super(message);
        this.expression = expression;
        this.segmentName = segmentName;
    }

    /**
     * Returns the full data path expression that caused the error.
     */
    public String getExpression() {
        return expression;
    }

    /**
     * Returns the specific segment name that caused the error, or null.
     */
    public String getSegmentName() {
        return segmentName;
    }
}
