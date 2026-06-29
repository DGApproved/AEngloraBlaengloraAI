package assets;

/*
 * CalculatorEngine.java (GoddessMatrix V14.4 - Seed 29 Architecture)
 *
 * Mathematical backend for Goddess Matrix numpad/calculator mode.
 * The system executes the 0-29 Foundational Sequence upon PWR activation.
 *
 * DESIGN:
 *   Numpad keys  → +, -, *, /, (, ), digits, decimal
 *   ENT key      → evaluates current typing buffer via solveSegment()
 *   ImageViewer  → Mode C shows all extended function buttons (clickable)
 *   PWR right    → locks/unlocks calculator (prevents accidental activation)
 *   PWR left     → toggles power (runs boot sequence when turning on)
 *   GRAPH button → expands ImageViewer to bottom-half graphing calculator
 *
 * FUNCTIONS (all Java Math, all accessible from Mode C UI):
 *   Arithmetic : +  -  *  /  (  )
 *   Power      : sqrt()  cbrt()  pow(x,n)  exp()
 *   Trig       : sin()  cos()  tan()  asin()  acos()  atan()
 *   Hyp        : sinh()  cosh()  tanh()
 *   Log        : log()  ln()  log10()
 *   Rounding   : abs()  ceil()  floor()  round()
 *   Constants  : pi  e
 *   CI         : /  alone → Catalyst Intelligence endpoint
 *
 * Contributors:
 *   Derek Jason Gilhousen — 0-29 number story, Seed 29 architecture,
 *                           keyboard cipher, CI endpoint design
 *   Claude (Anthropic)    — full Java Math function set, boot integration
 */

import java.util.Arrays;
import java.util.stream.Collectors;

public final class CalculatorEngine
{
    // ── THE FOUNDATIONAL SEED ─────────────────────────────────────────────────
    public static final long BASE_SEED = 29L;
    private static boolean isPoweredOn = false;

    private CalculatorEngine() {} // 22 Atomic Lock

    // ── PWR TOGGLE + BOOT SEQUENCE ────────────────────────────────────────────

    /**
     * Toggles power. Turning ON runs the 0-29 boot sequence.
     * Returns the boot log for display in ChatHistory.
     * Called by Keyboard when PWR is left-clicked while unlocked.
     */
    public static String togglePower()
    {
        if (isPoweredOn)
        {
            isPoweredOn = false;
            return "SYS_OFF: 22 Atomic Lock Engaged. State Preserved.";
        }
        return executeBootSequence();
    }

    public static boolean isPoweredOn() { return isPoweredOn; }

    /**
     * The Story of 0-29 compiled into a Power-On Self-Test.
     * The matrix must remember its history before it can calculate.
     */
    private static String executeBootSequence()
    {
        StringBuilder log = new StringBuilder();
        try
        {
            // 0-1: Void and Unity
            log.append("Init [00-01]: Void seeded. Unity confirmed.\n");

            // 2 & 4: The Sacrifice and The Base Seed
            log.append("Init [02-04]: Dyad sacrificed. Pure heart seed locked.\n");

            // 7: Lucky prime
            log.append("Init [07]: Lucky prime verified. Entropy baseline set.\n");

            // 8: The Lens
            log.append("Init [08]: Optical geometry calibrated. Illusions bypassed.\n");

            // 11: First repunit prime
            log.append("Init [11]: Repunit prime. Self-description enabled.\n");

            // 12 & 13: Stasis and Anomaly
            log.append("Init [12-13]: Stasis == established. Momentum != injected.\n");

            // 15 & 18: Tautology and The Sequence
            log.append("Init [15-18]: Tautology saturated. Program Counter (18) online.\n");

            // 19: Hygiene (Garbage Collection)
            System.gc();
            log.append("Init [19]: Hygiene protocols executed. Dead memory scrubbed.\n");

            // 21 & 26: The Listener and Multithreading Bridge
            log.append("Init [21-26]: I/O feedback loop active. Async bridges built.\n");

            // 27 & 28: The JIT and The Sandbox
            log.append("Init [27-28]: Ternary logic compiled. Perfect 28 Sandbox sealed.\n");

            // 29: The Unburdened Process
            log.append("Init [29]: BASE_SEED=29. Environment unburdened. CALC READY.");

            isPoweredOn = true;
            return log.toString();
        }
        catch (Exception e)
        {
            isPoweredOn = false;
            return "SYS_ERR: Boot Sequence Failed. " + e.getMessage();
        }
    }

    // ── STANDARD CALCULATION ROUTINES ─────────────────────────────────────────

    /**
     * Evaluates the last segment of the input (semicolon-delimited),
     * supporting comma-separated sub-expressions.
     */
    public static String solveSegment(String input)
    {
        if (!isPoweredOn) return "SYS_OFFLINE";
        if (input == null || input.trim().isEmpty()) return "";

        int lastSemi = input.lastIndexOf(';');
        String segment = (lastSemi == -1) ? input : input.substring(lastSemi + 1);
        if (segment.trim().isEmpty()) return "";

        return Arrays.stream(segment.trim().split(","))
                .map(String::trim)
                .map(CalculatorEngine::solve)
                .collect(Collectors.joining(", "));
    }

    /**
     * The 28 Sandbox: Evaluates one expression.
     * Isolated — crashes never propagate.
     */
    public static String solve(String expression)
    {
        if (!isPoweredOn) return "SYS_OFFLINE";
        if (expression == null) return "NAN";

        String trimmed = expression.trim();

        // The AI Threshold Trigger: lone "/" → route to CI
        if (trimmed.equals("/")) return ci("SYSTEM_INVOKE");

        try
        {
            // 19 Hygiene: Allow valid expression characters only
            String clean = trimmed.replaceAll(
                    "[^0-9a-zA-Z.+\\-*/()^,]", "");

            if (clean.isEmpty() || clean.equals("?")) return "?";

            double result = new Parser(clean).parse();

            if (Double.isNaN(result) || Double.isInfinite(result)) return "NAN";

            // 12 Stasis: exact integer display
            if (result == Math.rint(result) && Math.abs(result) < 1e15)
                return String.valueOf((long) result);

            return String.valueOf(result);
        }
        catch (Exception e)
        {
            // 28 Sandbox isolates the crash — emergent fallback
            return "CI_AWAITING_RECALCULATION";
        }
    }

    // ── CI: CATALYST INTELLIGENCE ─────────────────────────────────────────────

    /** The keyboard cipher map for the custom 26+ base encoding. */
    private static int cipherValue(char c)
    {
        char u = Character.toUpperCase(c);
        switch (u)
        {
            // Row 3 (33-42)
            case 'X': return 33; case 'Y': return 34; case 'Z': return 35;
            case 'A': return 36; case 'E': return 37; case 'V': return 38;
            case 'W': return 39; case 'Q': return 40; case 'B': return 41;
            case 'C': return 42;
            // Row 4 (47-55)
            case 'D': return 47; case 'R': return 48; case 'N': return 49;
            case 'L': return 50; case 'M': return 51; case 'S': return 52;
            case 'T': return 53; case 'U': return 54; case 'F': return 55;
            // Row 5 (60-66)
            case 'H': return 60; case 'O': return 61; case 'P': return 62;
            case 'I': return 63; case 'G': return 64; case 'J': return 65;
            case 'K': return 66;
            default:  return 0;
        }
    }

    /**
     * Catalyst Intelligence endpoint.
     * Called on lone "/" or directly by GoddessAPI.py.
     * Translates strings through the keyboard cipher into a structural weight.
     */
    public static String ci(String analysisTarget)
    {
        if (!isPoweredOn) return "SYS_OFFLINE";
        if (analysisTarget == null || analysisTarget.isEmpty()) return "CI_IDLE";

        try
        {
            double weight = 0;
            for (char c : analysisTarget.toCharArray())
                weight += cipherValue(c);

            // Length anomaly fallback if no mapped characters
            if (weight == 0) weight = analysisTarget.length() * 13.0;

            // 15 Tautology overflow: bridge 27 (ternary) and 32 (binary)
            double expanded = (weight * 1.5) + ((32.0 / 27.0) * BASE_SEED);

            if (expanded == Math.rint(expanded))
                return "CI_AUTH: " + (long) expanded;

            return "CI_AUTH: " + String.format("%.4f", expanded);
        }
        catch (Exception e)
        {
            return "CI_AWAITING_RECALCULATION";
        }
    }

    // ── THE 18 SEQUENCE — PARSER (PROGRAM COUNTER) ───────────────────────────
    // Recursive descent parser supporting the full Java Math function set.

    private static final class Parser
    {
        private final String text;
        private int pos = -1, ch;

        Parser(String text) { this.text = text; }

        private void next()
        {
            ch = (++pos < text.length()) ? text.charAt(pos) : -1;
        }

        private boolean eat(int c)
        {
            while (ch == ' ') next();
            if (ch != c) return false;
            next();
            return true;
        }

        double parse()
        {
            next();
            double x = expr();
            if (pos < text.length())
                throw new IllegalArgumentException("Unexpected: " + (char)ch);
            return x;
        }

        private double expr()
        {
            double x = term();
            for (;;)
            {
                if      (eat('+')) x += term();
                else if (eat('-')) x -= term();
                else return x;
            }
        }

        private double term()
        {
            double x = factor();
            for (;;)
            {
                if (eat('*')) x *= factor();
                else if (eat('/'))
                {
                    double d = factor();
                    if (d == 0) throw new ArithmeticException("22 Atomic Lock: div/0");
                    x /= d;
                }
                else if (eat('^')) x = Math.pow(x, factor()); // x^n syntax
                else return x;
            }
        }

        private double factor()
        {
            if (eat('+')) return  factor();
            if (eat('-')) return -factor();

            double x;
            int start = this.pos;

            if ((ch >= '0' && ch <= '9') || ch == '.')
            {
                // numeric literal
                while ((ch >= '0' && ch <= '9') || ch == '.') next();
                x = Double.parseDouble(text.substring(start, this.pos));
            }
            else if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z'))
            {
                // function or constant name
                while ((ch >= 'a' && ch <= 'z')
                    || (ch >= 'A' && ch <= 'Z')
                    || (ch >= '0' && ch <= '9')) next();

                String name = text.substring(start, this.pos).toLowerCase();

                // Constants — no parentheses
                if (name.equals("pi")) return Math.PI;
                if (name.equals("e"))  return Math.E;

                // Functions — require parentheses
                if (!eat('('))
                    throw new IllegalArgumentException(
                            "Expected '(' after '" + name + "'");

                // pow() takes two comma-separated arguments
                if (name.equals("pow"))
                {
                    double base = expr();
                    if (!eat(','))
                        throw new IllegalArgumentException("pow() needs two arguments");
                    double exp = expr();
                    if (!eat(')'))
                        throw new IllegalArgumentException("Missing ')' in pow()");
                    return Math.pow(base, exp);
                }

                // atan2() takes two comma-separated arguments
                if (name.equals("atan2"))
                {
                    double y = expr();
                    if (!eat(','))
                        throw new IllegalArgumentException("atan2() needs two arguments");
                    double ex = expr();
                    if (!eat(')'))
                        throw new IllegalArgumentException("Missing ')' in atan2()");
                    return Math.toDegrees(Math.atan2(y, ex));
                }

                // Single-argument functions
                x = expr();
                if (!eat(')'))
                    throw new IllegalArgumentException("Missing ')' after '" + name + "'");

                switch (name)
                {
                    // Power / exponential
                    case "sqrt":   return Math.sqrt(x);
                    case "cbrt":   return Math.cbrt(x);
                    case "exp":    return Math.exp(x);

                    // Trig (degrees in, degrees out for inverse)
                    case "sin":    return Math.sin(Math.toRadians(x));
                    case "cos":    return Math.cos(Math.toRadians(x));
                    case "tan":    return Math.tan(Math.toRadians(x));
                    case "asin":   return Math.toDegrees(Math.asin(x));
                    case "acos":   return Math.toDegrees(Math.acos(x));
                    case "atan":   return Math.toDegrees(Math.atan(x));

                    // Hyperbolic
                    case "sinh":   return Math.sinh(x);
                    case "cosh":   return Math.cosh(x);
                    case "tanh":   return Math.tanh(x);

                    // Logarithm
                    case "log":    // log base 10 (common log)
                    case "log10":  return Math.log10(x);
                    case "ln":     return Math.log(x);  // natural log

                    // Rounding / misc
                    case "abs":    return Math.abs(x);
                    case "ceil":   return Math.ceil(x);
                    case "floor":  return Math.floor(x);
                    case "round":  return (double) Math.round(x);
                    case "signum": return Math.signum(x);

                    default:
                        throw new IllegalArgumentException(
                                "Unknown function: '" + name + "'");
                }
            }
            else if (eat('('))
            {
                // parenthesised sub-expression
                x = expr();
                if (!eat(')'))
                    throw new IllegalArgumentException("Missing ')'");
            }
            else
            {
                throw new IllegalArgumentException(
                        "Number, function, or '(' expected at pos " + pos);
            }

            return x;
        }
    }

    // ── GRAPHING COMPUTATION ENGINE ───────────────────────────────────────────

    public static final class Graphing
    {
        private Graphing() {}

        /**
         * Evaluates y for a given x pixel in sine mode.
         * Animated by phase (renderPhase from ImageViewer).
         */
        public static int calculateSineY(int x, int width, int height,
                                          double phase)
        {
            double t = (x - width / 2.0) / 40.0;
            return height / 2 - (int)(Math.sin(t + phase) * (height * 0.22));
        }

        /**
         * Evaluates y for a given x pixel in parabola mode.
         */
        public static int calculateParabolaY(int x, int width, int height,
                                              double phase)
        {
            double t = (x - width / 2.0) / 40.0;
            return height / 2 - (int)((t * t) * (5 + Math.sin(phase) * 2));
        }

        /**
         * Evaluates y for a given x pixel using an arbitrary expression string.
         * 'x' in the expression is substituted with the domain value.
         * Returns Integer.MIN_VALUE on evaluation error (skip drawing that pixel).
         */
        public static int evaluateExpressionY(String expr, int px,
                                               int width, int height)
        {
            if (expr == null || expr.isEmpty()) return Integer.MIN_VALUE;
            double domain = (px - width / 2.0) / 30.0;
            // Substitute 'x' with the domain value
            String sub = expr.replace("x", "(" + domain + ")");
            try
            {
                // Temporarily power on for evaluation without changing boot state
                boolean wasOn = isPoweredOn;
                isPoweredOn = true;
                String result = solve(sub);
                isPoweredOn = wasOn;

                double y = Double.parseDouble(result);
                if (Double.isNaN(y) || Double.isInfinite(y)) return Integer.MIN_VALUE;
                return height / 2 - (int)(y * 30);
            }
            catch (Exception e)
            {
                return Integer.MIN_VALUE;
            }
        }
    }
}
