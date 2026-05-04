package assets;

/*
 * CalculatorEngine.java
 *
 * Small arithmetic helper for Goddess Matrix numpad/calculator mode.
 *
 * Supports:
 * - +, -, *, /
 * - decimals
 * - unary + and -
 * - comma-separated expressions through solveSegment()
 */

import java.util.Arrays;
import java.util.stream.Collectors;

public final class CalculatorEngine {

    private CalculatorEngine() {
    }

    public static String solveSegment(String input) {
        if (input == null || input.trim().isEmpty()) {
            return "";
        }

        int lastSemi = input.lastIndexOf(';');
        String segment = lastSemi == -1 ? input : input.substring(lastSemi + 1);

        if (segment.trim().isEmpty()) {
            return "";
        }

        return Arrays.stream(segment.trim().split(","))
                .map(String::trim)
                .map(CalculatorEngine::solve)
                .collect(Collectors.joining(", "));
    }

    public static String solve(String expression) {
        try {
            String clean = expression
                    .replaceAll("[^0-9.\\+\\-\\*/]", "")
                    .trim();

            if (clean.isEmpty()) {
                return "?";
            }

            double result = new Parser(clean).parse();

            if (Double.isNaN(result) || Double.isInfinite(result)) {
                return "NAN";
            }

            if (result == Math.rint(result)) {
                return String.valueOf((long) result);
            }

            return String.valueOf(result);

        } catch (Exception e) {
            return "NAN";
        }
    }

    private static final class Parser {
        private final String text;
        private int pos = -1;
        private int ch;

        private Parser(String text) {
            this.text = text;
        }

        private void nextChar() {
            ch = (++pos < text.length()) ? text.charAt(pos) : -1;
        }

        private boolean eat(int c) {
            while (ch == ' ') {
                nextChar();
            }

            if (ch == c) {
                nextChar();
                return true;
            }

            return false;
        }

        private double parse() {
            nextChar();
            double x = parseExpression();

            if (pos < text.length()) {
                throw new IllegalArgumentException("Unexpected character: " + (char) ch);
            }

            return x;
        }

        private double parseExpression() {
            double x = parseTerm();

            for (;;) {
                if (eat('+')) {
                    x += parseTerm();
                } else if (eat('-')) {
                    x -= parseTerm();
                } else {
                    return x;
                }
            }
        }

        private double parseTerm() {
            double x = parseFactor();

            for (;;) {
                if (eat('*')) {
                    x *= parseFactor();
                } else if (eat('/')) {
                    x /= parseFactor();
                } else {
                    return x;
                }
            }
        }

        private double parseFactor() {
            if (eat('+')) {
                return parseFactor();
            }

            if (eat('-')) {
                return -parseFactor();
            }

            double x;
            int start = this.pos;

            if ((ch >= '0' && ch <= '9') || ch == '.') {
                while ((ch >= '0' && ch <= '9') || ch == '.') {
                    nextChar();
                }

                x = Double.parseDouble(text.substring(start, this.pos));
            } else {
                throw new IllegalArgumentException("Number expected");
            }

            return x;
        }
    }
}
