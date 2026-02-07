package com.emf.runtime.formula;

import java.util.List;

/**
 * Abstract syntax tree nodes for parsed formula expressions.
 */
public sealed interface FormulaAst {

    Object evaluate(FormulaContext context);

    record Literal(Object value) implements FormulaAst {
        @Override
        public Object evaluate(FormulaContext context) {
            return value;
        }
    }

    record FieldRef(String fieldName) implements FormulaAst {
        @Override
        public Object evaluate(FormulaContext context) {
            return context.getFieldValue(fieldName);
        }
    }

    record BinaryOp(String operator, FormulaAst left, FormulaAst right) implements FormulaAst {
        @Override
        public Object evaluate(FormulaContext context) {
            Object leftVal = left.evaluate(context);
            Object rightVal = right.evaluate(context);

            return switch (operator) {
                case "+" -> add(leftVal, rightVal);
                case "-" -> subtract(leftVal, rightVal);
                case "*" -> multiply(leftVal, rightVal);
                case "/" -> divide(leftVal, rightVal);
                case ">" -> compare(leftVal, rightVal) > 0;
                case "<" -> compare(leftVal, rightVal) < 0;
                case ">=" -> compare(leftVal, rightVal) >= 0;
                case "<=" -> compare(leftVal, rightVal) <= 0;
                case "=" , "==" -> objectEquals(leftVal, rightVal);
                case "!=" , "<>" -> !objectEquals(leftVal, rightVal);
                case "&&" -> toBoolean(leftVal) && toBoolean(rightVal);
                case "||" -> toBoolean(leftVal) || toBoolean(rightVal);
                default -> throw new FormulaException("Unknown operator: " + operator);
            };
        }

        private static Object add(Object a, Object b) {
            if (a instanceof String || b instanceof String) {
                return String.valueOf(a) + String.valueOf(b);
            }
            return toDouble(a) + toDouble(b);
        }

        private static Object subtract(Object a, Object b) {
            return toDouble(a) - toDouble(b);
        }

        private static Object multiply(Object a, Object b) {
            return toDouble(a) * toDouble(b);
        }

        private static Object divide(Object a, Object b) {
            double divisor = toDouble(b);
            if (divisor == 0) throw new FormulaException("Division by zero");
            return toDouble(a) / divisor;
        }

        @SuppressWarnings("unchecked")
        private static int compare(Object a, Object b) {
            if (a instanceof Comparable c && b != null) {
                if (a instanceof Number && b instanceof Number) {
                    return Double.compare(toDouble(a), toDouble(b));
                }
                return c.compareTo(b);
            }
            return 0;
        }

        private static boolean objectEquals(Object a, Object b) {
            if (a == null && b == null) return true;
            if (a == null || b == null) return false;
            if (a instanceof Number && b instanceof Number) {
                return Double.compare(toDouble(a), toDouble(b)) == 0;
            }
            return a.equals(b);
        }
    }

    record UnaryOp(String operator, FormulaAst operand) implements FormulaAst {
        @Override
        public Object evaluate(FormulaContext context) {
            Object val = operand.evaluate(context);
            return switch (operator) {
                case "-" -> -toDouble(val);
                case "!" -> !toBoolean(val);
                default -> throw new FormulaException("Unknown unary operator: " + operator);
            };
        }
    }

    record FunctionCall(String functionName, List<FormulaAst> arguments) implements FormulaAst {
        @Override
        public Object evaluate(FormulaContext context) {
            FormulaFunction fn = context.getFunction(functionName.toUpperCase());
            if (fn == null) {
                throw new FormulaException("Unknown function: " + functionName);
            }
            List<Object> args = arguments.stream()
                    .map(a -> a.evaluate(context))
                    .toList();
            return fn.execute(args, context);
        }
    }

    // Utility methods
    static double toDouble(Object value) {
        if (value == null) return 0.0;
        if (value instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            throw new FormulaException("Cannot convert to number: " + value);
        }
    }

    static boolean toBoolean(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.doubleValue() != 0;
        return Boolean.parseBoolean(value.toString());
    }
}
