package com.emf.runtime.formula;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Built-in formula functions registered as Spring beans.
 */
public final class BuiltInFunctions {

    private BuiltInFunctions() {}

    @Component
    public static class Today implements FormulaFunction {
        @Override public String name() { return "TODAY"; }
        @Override public Object execute(List<Object> args, FormulaContext ctx) {
            return LocalDate.now();
        }
    }

    @Component
    public static class Now implements FormulaFunction {
        @Override public String name() { return "NOW"; }
        @Override public Object execute(List<Object> args, FormulaContext ctx) {
            return LocalDateTime.now();
        }
    }

    @Component
    public static class IsBlank implements FormulaFunction {
        @Override public String name() { return "ISBLANK"; }
        @Override public Object execute(List<Object> args, FormulaContext ctx) {
            if (args.isEmpty()) return true;
            Object val = args.get(0);
            return val == null || (val instanceof String s && s.isBlank());
        }
    }

    @Component
    public static class BlankValue implements FormulaFunction {
        @Override public String name() { return "BLANKVALUE"; }
        @Override public Object execute(List<Object> args, FormulaContext ctx) {
            if (args.size() < 2) throw new FormulaException("BLANKVALUE requires 2 arguments");
            Object val = args.get(0);
            if (val == null || (val instanceof String s && s.isBlank())) return args.get(1);
            return val;
        }
    }

    @Component
    public static class If implements FormulaFunction {
        @Override public String name() { return "IF"; }
        @Override public Object execute(List<Object> args, FormulaContext ctx) {
            if (args.size() < 3) throw new FormulaException("IF requires 3 arguments");
            boolean condition = FormulaAst.toBoolean(args.get(0));
            return condition ? args.get(1) : args.get(2);
        }
    }

    @Component
    public static class And implements FormulaFunction {
        @Override public String name() { return "AND"; }
        @Override public Object execute(List<Object> args, FormulaContext ctx) {
            return args.stream().allMatch(FormulaAst::toBoolean);
        }
    }

    @Component
    public static class Or implements FormulaFunction {
        @Override public String name() { return "OR"; }
        @Override public Object execute(List<Object> args, FormulaContext ctx) {
            return args.stream().anyMatch(FormulaAst::toBoolean);
        }
    }

    @Component
    public static class Not implements FormulaFunction {
        @Override public String name() { return "NOT"; }
        @Override public Object execute(List<Object> args, FormulaContext ctx) {
            if (args.isEmpty()) throw new FormulaException("NOT requires 1 argument");
            return !FormulaAst.toBoolean(args.get(0));
        }
    }

    @Component
    public static class Len implements FormulaFunction {
        @Override public String name() { return "LEN"; }
        @Override public Object execute(List<Object> args, FormulaContext ctx) {
            if (args.isEmpty() || args.get(0) == null) return 0;
            return args.get(0).toString().length();
        }
    }

    @Component
    public static class Contains implements FormulaFunction {
        @Override public String name() { return "CONTAINS"; }
        @Override public Object execute(List<Object> args, FormulaContext ctx) {
            if (args.size() < 2) throw new FormulaException("CONTAINS requires 2 arguments");
            String text = args.get(0) != null ? args.get(0).toString() : "";
            String search = args.get(1) != null ? args.get(1).toString() : "";
            return text.contains(search);
        }
    }

    @Component
    public static class Upper implements FormulaFunction {
        @Override public String name() { return "UPPER"; }
        @Override public Object execute(List<Object> args, FormulaContext ctx) {
            if (args.isEmpty() || args.get(0) == null) return null;
            return args.get(0).toString().toUpperCase();
        }
    }

    @Component
    public static class Lower implements FormulaFunction {
        @Override public String name() { return "LOWER"; }
        @Override public Object execute(List<Object> args, FormulaContext ctx) {
            if (args.isEmpty() || args.get(0) == null) return null;
            return args.get(0).toString().toLowerCase();
        }
    }

    @Component
    public static class Trim implements FormulaFunction {
        @Override public String name() { return "TRIM"; }
        @Override public Object execute(List<Object> args, FormulaContext ctx) {
            if (args.isEmpty() || args.get(0) == null) return null;
            return args.get(0).toString().trim();
        }
    }

    @Component
    public static class Text implements FormulaFunction {
        @Override public String name() { return "TEXT"; }
        @Override public Object execute(List<Object> args, FormulaContext ctx) {
            if (args.isEmpty() || args.get(0) == null) return "";
            return args.get(0).toString();
        }
    }

    @Component
    public static class Value implements FormulaFunction {
        @Override public String name() { return "VALUE"; }
        @Override public Object execute(List<Object> args, FormulaContext ctx) {
            if (args.isEmpty() || args.get(0) == null) return 0.0;
            return FormulaAst.toDouble(args.get(0));
        }
    }

    @Component
    public static class Round implements FormulaFunction {
        @Override public String name() { return "ROUND"; }
        @Override public Object execute(List<Object> args, FormulaContext ctx) {
            if (args.size() < 2) throw new FormulaException("ROUND requires 2 arguments");
            double num = FormulaAst.toDouble(args.get(0));
            int places = (int) FormulaAst.toDouble(args.get(1));
            double factor = Math.pow(10, places);
            return Math.round(num * factor) / factor;
        }
    }

    @Component
    public static class Abs implements FormulaFunction {
        @Override public String name() { return "ABS"; }
        @Override public Object execute(List<Object> args, FormulaContext ctx) {
            if (args.isEmpty()) throw new FormulaException("ABS requires 1 argument");
            return Math.abs(FormulaAst.toDouble(args.get(0)));
        }
    }

    @Component
    public static class Max implements FormulaFunction {
        @Override public String name() { return "MAX"; }
        @Override public Object execute(List<Object> args, FormulaContext ctx) {
            if (args.size() < 2) throw new FormulaException("MAX requires 2 arguments");
            return Math.max(FormulaAst.toDouble(args.get(0)), FormulaAst.toDouble(args.get(1)));
        }
    }

    @Component
    public static class Min implements FormulaFunction {
        @Override public String name() { return "MIN"; }
        @Override public Object execute(List<Object> args, FormulaContext ctx) {
            if (args.size() < 2) throw new FormulaException("MIN requires 2 arguments");
            return Math.min(FormulaAst.toDouble(args.get(0)), FormulaAst.toDouble(args.get(1)));
        }
    }

    @Component
    public static class Regex implements FormulaFunction {
        @Override public String name() { return "REGEX"; }
        @Override public Object execute(List<Object> args, FormulaContext ctx) {
            if (args.size() < 2) throw new FormulaException("REGEX requires 2 arguments");
            String text = args.get(0) != null ? args.get(0).toString() : "";
            String pattern = args.get(1) != null ? args.get(1).toString() : "";
            return text.matches(pattern);
        }
    }

    @Component
    public static class DateDiff implements FormulaFunction {
        @Override public String name() { return "DATEDIFF"; }
        @Override public Object execute(List<Object> args, FormulaContext ctx) {
            if (args.size() < 2) throw new FormulaException("DATEDIFF requires 2 arguments");
            LocalDate d1 = toLocalDate(args.get(0));
            LocalDate d2 = toLocalDate(args.get(1));
            return ChronoUnit.DAYS.between(d2, d1);
        }

        private LocalDate toLocalDate(Object val) {
            if (val instanceof LocalDate ld) return ld;
            if (val instanceof LocalDateTime ldt) return ldt.toLocalDate();
            if (val instanceof String s) return LocalDate.parse(s);
            throw new FormulaException("Cannot convert to date: " + val);
        }
    }
}
