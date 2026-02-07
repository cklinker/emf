package com.emf.runtime.formula;

import org.springframework.stereotype.Component;

/**
 * Attempts to translate a formula expression to a SQL expression.
 * Returns null if the formula uses functions not translatable to SQL.
 */
@Component
public class FormulaToSqlTranslator {

    private final FormulaParser parser = new FormulaParser();

    /**
     * Translates a formula expression to SQL.
     * Returns null if the formula is too complex for SQL translation.
     */
    public String translate(String formulaExpression) {
        try {
            FormulaAst ast = parser.parse(formulaExpression);
            return toSql(ast);
        } catch (Exception e) {
            return null;
        }
    }

    private String toSql(FormulaAst ast) {
        if (ast instanceof FormulaAst.Literal lit) {
            Object val = lit.value();
            if (val == null) return "NULL";
            if (val instanceof String s) return "'" + s.replace("'", "''") + "'";
            if (val instanceof Boolean b) return b ? "TRUE" : "FALSE";
            return val.toString();
        }

        if (ast instanceof FormulaAst.FieldRef ref) {
            String name = ref.fieldName();
            if (!name.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) return null;
            return name;
        }

        if (ast instanceof FormulaAst.BinaryOp op) {
            String left = toSql(op.left());
            String right = toSql(op.right());
            if (left == null || right == null) return null;
            String sqlOp = switch (op.operator()) {
                case "=", "==" -> "=";
                case "!=" , "<>" -> "!=";
                case "&&" -> "AND";
                case "||" -> "OR";
                default -> op.operator();
            };
            return "(" + left + " " + sqlOp + " " + right + ")";
        }

        if (ast instanceof FormulaAst.UnaryOp op) {
            String operand = toSql(op.operand());
            if (operand == null) return null;
            String sqlOp = switch (op.operator()) {
                case "!" -> "NOT ";
                default -> op.operator();
            };
            return "(" + sqlOp + operand + ")";
        }

        // Function calls generally not translatable to SQL
        if (ast instanceof FormulaAst.FunctionCall fn) {
            return switch (fn.functionName().toUpperCase()) {
                case "COALESCE" -> {
                    StringBuilder sb = new StringBuilder("COALESCE(");
                    for (int i = 0; i < fn.arguments().size(); i++) {
                        if (i > 0) sb.append(", ");
                        String arg = toSql(fn.arguments().get(i));
                        if (arg == null) yield null;
                        sb.append(arg);
                    }
                    sb.append(")");
                    yield sb.toString();
                }
                default -> null;
            };
        }

        return null;
    }
}
