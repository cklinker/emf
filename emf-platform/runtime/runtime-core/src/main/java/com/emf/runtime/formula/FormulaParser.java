package com.emf.runtime.formula;

import java.util.ArrayList;
import java.util.List;

/**
 * Recursive descent parser for formula expressions.
 * Supports: field references, literals, operators, function calls, parentheses.
 */
public class FormulaParser {

    private String input;
    private int pos;

    public FormulaAst parse(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new FormulaException("Formula expression cannot be empty");
        }
        this.input = expression.trim();
        this.pos = 0;
        FormulaAst result = parseOr();
        skipWhitespace();
        if (pos < input.length()) {
            throw new FormulaException("Unexpected character at position " + pos + ": '" + input.charAt(pos) + "'");
        }
        return result;
    }

    private FormulaAst parseOr() {
        FormulaAst left = parseAnd();
        skipWhitespace();
        while (pos < input.length() && match("||")) {
            FormulaAst right = parseAnd();
            left = new FormulaAst.BinaryOp("||", left, right);
            skipWhitespace();
        }
        return left;
    }

    private FormulaAst parseAnd() {
        FormulaAst left = parseComparison();
        skipWhitespace();
        while (pos < input.length() && match("&&")) {
            FormulaAst right = parseComparison();
            left = new FormulaAst.BinaryOp("&&", left, right);
            skipWhitespace();
        }
        return left;
    }

    private FormulaAst parseComparison() {
        FormulaAst left = parseAddSub();
        skipWhitespace();
        if (pos < input.length()) {
            for (String op : new String[]{">=", "<=", "!=", "<>", "==", ">", "<", "="}) {
                if (match(op)) {
                    FormulaAst right = parseAddSub();
                    return new FormulaAst.BinaryOp(op, left, right);
                }
            }
        }
        return left;
    }

    private FormulaAst parseAddSub() {
        FormulaAst left = parseMulDiv();
        skipWhitespace();
        while (pos < input.length()) {
            if (match("+")) {
                left = new FormulaAst.BinaryOp("+", left, parseMulDiv());
            } else if (matchChar('-') && !isStartOfNumber()) {
                left = new FormulaAst.BinaryOp("-", left, parseMulDiv());
            } else {
                break;
            }
            skipWhitespace();
        }
        return left;
    }

    private boolean isStartOfNumber() {
        // After operator -, check if this is a unary minus before a digit
        return false;
    }

    private boolean matchChar(char c) {
        skipWhitespace();
        if (pos < input.length() && input.charAt(pos) == c) {
            pos++;
            return true;
        }
        return false;
    }

    private FormulaAst parseMulDiv() {
        FormulaAst left = parseUnary();
        skipWhitespace();
        while (pos < input.length()) {
            if (match("*")) {
                left = new FormulaAst.BinaryOp("*", left, parseUnary());
            } else if (match("/")) {
                left = new FormulaAst.BinaryOp("/", left, parseUnary());
            } else {
                break;
            }
            skipWhitespace();
        }
        return left;
    }

    private FormulaAst parseUnary() {
        skipWhitespace();
        if (pos < input.length()) {
            if (input.charAt(pos) == '-') {
                pos++;
                return new FormulaAst.UnaryOp("-", parsePrimary());
            }
            if (input.charAt(pos) == '!') {
                pos++;
                return new FormulaAst.UnaryOp("!", parsePrimary());
            }
        }
        return parsePrimary();
    }

    private FormulaAst parsePrimary() {
        skipWhitespace();
        if (pos >= input.length()) {
            throw new FormulaException("Unexpected end of expression");
        }

        char c = input.charAt(pos);

        // Parenthesized expression
        if (c == '(') {
            pos++;
            FormulaAst expr = parseOr();
            skipWhitespace();
            expect(')');
            return expr;
        }

        // String literal
        if (c == '\'' || c == '"') {
            return parseStringLiteral(c);
        }

        // Number literal
        if (Character.isDigit(c) || (c == '.' && pos + 1 < input.length() && Character.isDigit(input.charAt(pos + 1)))) {
            return parseNumberLiteral();
        }

        // Boolean literal or identifier/function
        if (Character.isLetter(c) || c == '_') {
            return parseIdentifierOrFunction();
        }

        throw new FormulaException("Unexpected character at position " + pos + ": '" + c + "'");
    }

    private FormulaAst parseStringLiteral(char quote) {
        pos++; // skip opening quote
        StringBuilder sb = new StringBuilder();
        while (pos < input.length() && input.charAt(pos) != quote) {
            if (input.charAt(pos) == '\\' && pos + 1 < input.length()) {
                pos++;
            }
            sb.append(input.charAt(pos));
            pos++;
        }
        if (pos >= input.length()) {
            throw new FormulaException("Unterminated string literal");
        }
        pos++; // skip closing quote
        return new FormulaAst.Literal(sb.toString());
    }

    private FormulaAst parseNumberLiteral() {
        int start = pos;
        while (pos < input.length() && (Character.isDigit(input.charAt(pos)) || input.charAt(pos) == '.')) {
            pos++;
        }
        String numStr = input.substring(start, pos);
        try {
            if (numStr.contains(".")) {
                return new FormulaAst.Literal(Double.parseDouble(numStr));
            } else {
                long val = Long.parseLong(numStr);
                if (val >= Integer.MIN_VALUE && val <= Integer.MAX_VALUE) {
                    return new FormulaAst.Literal((int) val);
                }
                return new FormulaAst.Literal(val);
            }
        } catch (NumberFormatException e) {
            throw new FormulaException("Invalid number: " + numStr);
        }
    }

    private FormulaAst parseIdentifierOrFunction() {
        int start = pos;
        while (pos < input.length() && (Character.isLetterOrDigit(input.charAt(pos)) || input.charAt(pos) == '_')) {
            pos++;
        }
        String name = input.substring(start, pos);
        skipWhitespace();

        // Boolean literals
        if ("true".equalsIgnoreCase(name)) return new FormulaAst.Literal(true);
        if ("false".equalsIgnoreCase(name)) return new FormulaAst.Literal(false);
        if ("null".equalsIgnoreCase(name)) return new FormulaAst.Literal(null);

        // Function call
        if (pos < input.length() && input.charAt(pos) == '(') {
            pos++; // skip (
            List<FormulaAst> args = new ArrayList<>();
            skipWhitespace();
            if (pos < input.length() && input.charAt(pos) != ')') {
                args.add(parseOr());
                skipWhitespace();
                while (pos < input.length() && input.charAt(pos) == ',') {
                    pos++; // skip ,
                    args.add(parseOr());
                    skipWhitespace();
                }
            }
            expect(')');
            return new FormulaAst.FunctionCall(name, args);
        }

        // Field reference
        return new FormulaAst.FieldRef(name);
    }

    private boolean match(String s) {
        skipWhitespace();
        if (pos + s.length() <= input.length() && input.substring(pos, pos + s.length()).equals(s)) {
            pos += s.length();
            return true;
        }
        return false;
    }

    private void expect(char c) {
        skipWhitespace();
        if (pos >= input.length() || input.charAt(pos) != c) {
            throw new FormulaException("Expected '" + c + "' at position " + pos);
        }
        pos++;
    }

    private void skipWhitespace() {
        while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
            pos++;
        }
    }
}
