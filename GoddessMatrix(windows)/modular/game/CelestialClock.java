package modular.game;

/*
 * CelestialClock.java
 *
 * Three-mode time calculation engine for the Goddess Matrix game engine.
 * All three modes derive from milliseconds since midnight so they
 * synchronize exactly at 00:00:00 standard time each day.
 *
 * MODES:
 *   STANDARD   — 12-hour AM/PM, seconds 0-59
 *   HYBRID     — 13-hour face, standard H:M, Aengloria seconds
 *                Aengloria second 60-80 = drift territory, displayed [bracketed]
 *                Produces intentional mechanical drift — two incommensurate
 *                subdivisions sharing one display. Not an error.
 *   AENGLORIA  — Full 26/81/81 system, seconds 0-80
 *
 * MIDNIGHT SYNC:
 *   All three modes start from stdMsec=0 (midnight).
 *   They diverge during the day and reconverge at the next midnight.
 *   atMidnightSync = true within the first second after midnight.
 *
 * PHYSICS MODE:
 *   In AENGLORIA physics mode, environment systems (orbital mechanics,
 *   day/night progression, atmospheric events) use Aengloria seconds
 *   as the time unit. One Aengloria second ≈ 507ms real time.
 *   Player physics tick values stay calibrated to the 81ms loop.
 *
 * MATH:
 *   Exact 64-bit integer arithmetic — same accuracy as temporal_math.asm.
 *   ASM advantage is call throughput; for sidebar display rates Java
 *   long arithmetic is equivalent in precision.
 *
 *   Multiplier: 170586 / 86400 (Aengloria seconds per standard day /
 *               standard seconds per day)
 *   1 standard second = 1.974375 Aengloria seconds
 *   1 Aengloria second ≈ 506.6ms real time
 *   1 Aengloria minute = 81 Aengloria seconds ≈ 41 real seconds
 *   1 Aengloria hour   = 6561 Aengloria seconds ≈ 55.5 real minutes
 *   1 Aengloria day    = 170586 seconds = same wall-clock duration as
 *                        1 standard day (both measure the same 24h)
 *
 * CALENDAR MODES:
 *   0 — Standard Gregorian (12 months)
 *   1 — Celestial/Aenglorian (13 months on leap years, 12 otherwise)
 *   2 — Cotsworth Perpetual (always 13 months, always 28 days each)
 *
 * Contributors:
 *   Derek Jason Gilhousen — three-phase clock design, drift mechanics,
 *                           Aengloria time system, calendar systems
 *   Gemini (Google)        — original temporal_math.asm implementation,
 *                           celestial_calendar.c, celestial_gui.c
 *   Claude (Anthropic)    — CelestialClock Java port and calendar engine
 */

import java.util.Calendar;

public class CelestialClock
{
    // ── PHYSICS MODES ─────────────────────────────────────────────────────────
    public static final int PHYSICS_STANDARD  = 0;
    public static final int PHYSICS_AENGLORIA = 1;
    public static final int PHYSICS_HYBRID    = 2;

    // ── CALENDAR MODES ────────────────────────────────────────────────────────
    public static final int CAL_STANDARD   = 0;
    public static final int CAL_CELESTIAL  = 1;
    public static final int CAL_COTSWORTH  = 2;

    // ── AENGLORIA TIME CONSTANTS ──────────────────────────────────────────────
    public static final long AENG_MULT        = 170586L;  // Aengloria seconds/day
    public static final long STD_SECONDS_DAY  = 86400L;
    public static final long AENG_SECS_HOUR   = 6561L;    // 81 * 81
    public static final long AENG_SECS_MINUTE = 81L;
    public static final double AENG_MS_PER_SEC = 86400000.0 / 170586.0; // ~506.6ms

    // ── STANDARD TIME ─────────────────────────────────────────────────────────
    public long stdH, stdM, stdS;
    public boolean stdPM;

    // ── AENGLORIA TIME ────────────────────────────────────────────────────────
    public long aengH, aengM, aengS;
    public long aengDispH;     // 13-hour display value
    public boolean aengPM;

    // ── HYBRID TIME ───────────────────────────────────────────────────────────
    public long hybH, hybM, hybS;
    public long hybDispH;
    public boolean hybPM;
    public boolean hybridDrift;  // true when Aengloria second ≥ 60 (drift territory)

    // ── SYNC STATE ────────────────────────────────────────────────────────────
    public boolean atMidnightSync;  // true within first second after midnight
    public long    stdMsecCache;    // last computed stdMsec, for physics use

    // ── CALENDAR STATE ────────────────────────────────────────────────────────
    public int calMode     = CAL_STANDARD;
    public int calYear, calMonth, calDay;
    public int calStartWday, calDaysInMonth;
    public int todayYear, todayMonth, todayDay; // today in active calendar system

    // ─────────────────────────────────────────────────────────────────────────

    public CelestialClock() { update(); }

    // ── UPDATE ────────────────────────────────────────────────────────────────

    /**
     * Recomputes all three time modes from the current wall clock.
     * Call once per logic tick (81ms) from IceSandbox.updateLogic().
     */
    public void update()
    {
        Calendar cal = Calendar.getInstance();
        long h   = cal.get(Calendar.HOUR_OF_DAY);
        long m   = cal.get(Calendar.MINUTE);
        long s   = cal.get(Calendar.SECOND);
        long ms  = cal.get(Calendar.MILLISECOND);

        long stdMsec = h * 3_600_000L + m * 60_000L + s * 1_000L + ms;
        stdMsecCache = stdMsec;

        // ── STANDARD ──────────────────────────────────────────────────────────
        stdH  = h % 12;
        if (stdH == 0) stdH = 12;
        stdM  = m;
        stdS  = s;
        stdPM = (h >= 12);

        // ── AENGLORIA (exact integer math — matches temporal_math.asm) ────────
        long aengMsec    = (stdMsec * AENG_MULT) / STD_SECONDS_DAY;
        long aengSecTotal = aengMsec / 1000L;
        aengH = aengSecTotal / AENG_SECS_HOUR;
        aengM = (aengSecTotal % AENG_SECS_HOUR) / AENG_SECS_MINUTE;
        aengS = aengSecTotal % AENG_SECS_MINUTE;

        // 13-hour AM/PM display (matches temporal_math.asm display convention)
        if      (aengH == 0)  { aengDispH = 13; aengPM = false; }
        else if (aengH < 13)  { aengDispH = aengH; aengPM = false; }
        else if (aengH == 13) { aengDispH = 13; aengPM = true; }
        else                  { aengDispH = aengH - 13; aengPM = true; }

        // ── HYBRID (13-hour face, standard H:M, Aengloria S) ─────────────────
        // Standard hours on 13-hour face
        long hybHour24 = h;
        hybH = hybHour24 % 13;
        if (hybH == 0) hybH = 13;
        hybDispH = hybH;
        hybPM    = (hybHour24 >= 13);
        hybM     = m;

        // Aengloria second — when ≥ 60 we are in drift territory
        // (standard second range 0-59 is exceeded)
        hybS          = aengS;
        hybridDrift   = (aengS >= 60L);

        // ── MIDNIGHT SYNC ─────────────────────────────────────────────────────
        atMidnightSync = (stdMsec < 1000L);

        // ── CALENDAR ──────────────────────────────────────────────────────────
        updateCalendar(cal);
    }

    // ── CALENDAR ENGINE ───────────────────────────────────────────────────────
    // Ported from celestial_calendar.c (Gemini / Derek Jason Gilhousen)

    private void updateCalendar(Calendar jcal)
    {
        int realYear = jcal.get(Calendar.YEAR);
        int realYday = jcal.get(Calendar.DAY_OF_YEAR) - 1; // 0-indexed

        todayYear  = realYear;
        ydayToDate(realYear, realYday, calMode,
                   todayResult = new int[2]);
        todayMonth = todayResult[0];
        todayDay   = todayResult[1];

        // Default view: current month
        if (calYear == 0) calYear = realYear;
        if (calMonth == 0 && calDay == 0) { calMonth = todayMonth; calDay = todayDay; }

        getMonthInfo(calYear, calMonth, calMode,
                     monthResult = new int[2]);
        calStartWday   = monthResult[0];
        calDaysInMonth = monthResult[1];
    }

    private int[] todayResult  = new int[2];
    private int[] monthResult  = new int[2];

    // ── CALENDAR MATH ─────────────────────────────────────────────────────────

    public static boolean isLeap(int year)
    {
        return (year % 4 == 0) && (year % 100 != 0 || year % 400 == 0);
    }

    /** Day of week for January 1st of the given year (0=Sunday). */
    public static int jan1Wday(int year)
    {
        int[] t = {0, 3, 2, 5, 0, 3, 5, 1, 4, 6, 2, 4};
        int y = year;
        y -= 1; // month = 1 (January)
        return (y + y/4 - y/100 + y/400 + t[0] + 1) % 7;
    }

    /**
     * Returns [startWday, daysInMonth] for the given year/month/mode.
     * month is 0-indexed.
     */
    public static void getMonthInfo(int year, int month, int calMode, int[] out)
    {
        int[] days;

        if (calMode == CAL_COTSWORTH)
        {
            // 13 months, 28 days each.
            // Leap Day in month 5 (June-ish): 29 days.
            // Year Day in month 12 (December): 29 days.
            days = new int[]{28, 28, 28, 28, 28,
                             isLeap(year) ? 29 : 28,
                             28, 28, 28, 28, 28, 28, 29};
        }
        else if (calMode == CAL_CELESTIAL && isLeap(year))
        {
            // 13-month Aengloria leap year
            days = new int[]{28, 29, 27, 29, 27, 29, 27, 29, 29, 27, 29, 27, 29};
        }
        else
        {
            // Standard Gregorian 12 months
            days = new int[]{31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
            if (isLeap(year)) days[1] = 29;
        }

        out[1] = days[month]; // days in month

        // Calculate start weekday
        int d = jan1Wday(year);
        for (int i = 0; i < month && i < days.length; i++) d = (d + days[i]) % 7;
        out[0] = d;
    }

    /** Convert day-of-year (0-indexed) to [month, day] in the given calendar mode. */
    public static void ydayToDate(int year, int yday, int calMode, int[] out)
    {
        int maxMonths = maxMonths(year, calMode);
        int[] dummy = new int[2];
        int temp = yday;
        int m = 0;
        while (m < maxMonths)
        {
            getMonthInfo(year, m, calMode, dummy);
            if (temp >= dummy[1]) { temp -= dummy[1]; m++; }
            else break;
        }
        out[0] = m;
        out[1] = temp + 1; // 1-indexed day
    }

    public static int maxMonths(int year, int calMode)
    {
        if (calMode == CAL_COTSWORTH) return 13;
        if (calMode == CAL_CELESTIAL && isLeap(year)) return 13;
        return 12;
    }

    // ── CALENDAR NAVIGATION ───────────────────────────────────────────────────

    public void calNextMonth()
    {
        calMonth++;
        if (calMonth >= maxMonths(calYear, calMode)) { calYear++; calMonth = 0; }
        refreshCalendar();
    }

    public void calPrevMonth()
    {
        calMonth--;
        if (calMonth < 0)
        {
            calYear--;
            calMonth = maxMonths(calYear, calMode) - 1;
        }
        refreshCalendar();
    }

    public void calCycleMode()
    {
        calMode = (calMode + 1) % 3;
        // Revalidate month index for new mode
        if (calMonth >= maxMonths(calYear, calMode))
            calMonth = maxMonths(calYear, calMode) - 1;
        refreshCalendar();
    }

    private void refreshCalendar()
    {
        getMonthInfo(calYear, calMonth, calMode, monthResult);
        calStartWday   = monthResult[0];
        calDaysInMonth = monthResult[1];
    }

    // ── CALENDAR NAMES ────────────────────────────────────────────────────────

    public static final String[][] MONTH_NAMES = {
        // Standard
        {"JAN","FEB","MAR","APR","MAY","JUN",
         "JUL","AUG","SEP","OCT","NOV","DEC"},
        // Celestial (13-month leap year)
        {"AENGLORIA","JAN","FEB","MAR","APR","MAY","JUN",
         "JUL","AUG","SEP","OCT","NOV","DEC"},
        // Cotsworth
        {"JAN","FEB","MAR","APR","MAY","JUN","SOL",
         "JUL","AUG","SEP","OCT","NOV","DEC"}
    };

    public static final String[] DAY_ABBR = {"Su","Mo","Tu","We","Th","Fr","Sa"};

    public String monthName()
    {
        String[] names = MONTH_NAMES[calMode];
        if (calMonth < 0 || calMonth >= names.length) return "???";
        return names[calMonth];
    }

    public String calModeName()
    {
        switch (calMode)
        {
            case CAL_CELESTIAL: return "CELESTIAL";
            case CAL_COTSWORTH: return "COTSWORTH";
            default:            return "STANDARD";
        }
    }

    // ── PHYSICS TIME UNIT ─────────────────────────────────────────────────────

    /**
     * Returns the physics time unit in milliseconds for the current mode.
     * STANDARD:  1000ms (one standard second)
     * AENGLORIA: ~507ms (one Aengloria second)
     * HYBRID:    average of both
     */
    public double physicsUnitMs(int physicsMode)
    {
        switch (physicsMode)
        {
            case PHYSICS_AENGLORIA: return AENG_MS_PER_SEC;
            case PHYSICS_HYBRID:    return (1000.0 + AENG_MS_PER_SEC) / 2.0;
            default:                return 1000.0;
        }
    }

    /**
     * Returns the fraction of the current day elapsed (0.0 to 1.0).
     * Used for day/night cycle and orbital mechanics.
     */
    public double dayFraction()
    {
        return stdMsecCache / 86_400_000.0;
    }
}
