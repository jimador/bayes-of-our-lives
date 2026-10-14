package dev.jimador.bayes.itinerary;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Conservative subset of OSM opening_hours; unsupported expressions remain unknown. */
public final class OpeningHours {
    public enum Fit { TAGGED_OPEN, TAGGED_CLOSED, UNKNOWN }
    private static final String[] DAYS = {"Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"};
    private static final Pattern SEGMENT = Pattern.compile("([A-Za-z,-]+)\\s+(off|[0-2][0-9]:[0-5][0-9]-[0-2][0-9]:[0-5][0-9])");
    private OpeningHours() {}

    public static Fit fit(String expression, LocalDate date, LocalTime start, LocalTime end) {
        if (expression == null || expression.isBlank() || date == null || start == null || end == null)
            return Fit.UNKNOWN;
        if (expression.equals("24/7")) return Fit.TAGGED_OPEN;
        int wanted = date.getDayOfWeek().getValue() - 1;
        boolean matched = false, closed = false;
        List<LocalTime[]> intervals = new ArrayList<>();
        for (String raw : expression.split(";")) {
            var part = SEGMENT.matcher(raw.trim());
            if (!part.matches()) return Fit.UNKNOWN; // PH, seasonal rules, multiple intervals, etc.
            Set<Integer> days = parseDays(part.group(1));
            if (days == null) return Fit.UNKNOWN;
            if (!days.contains(wanted)) continue;
            matched = true;
            if (part.group(2).equals("off")) { closed = true; continue; }
            String[] times = part.group(2).split("-");
            LocalTime opens, closes;
            try { opens = LocalTime.parse(times[0]); closes = LocalTime.parse(times[1]); }
            catch (RuntimeException failure) { return Fit.UNKNOWN; }
            if (!closes.isAfter(opens)) return Fit.UNKNOWN;
            intervals.add(new LocalTime[] {opens, closes});
        }
        // Conflicting open/off rules need the full OSM precedence grammar, outside this subset.
        if (closed && !intervals.isEmpty()) return Fit.UNKNOWN;
        if (!matched || intervals.isEmpty()) return Fit.TAGGED_CLOSED;
        intervals.sort(Comparator.comparing(interval -> interval[0]));
        LocalTime coveredUntil = start;
        for (LocalTime[] interval : intervals) {
            if (interval[0].isAfter(coveredUntil)) break;
            if (interval[1].isAfter(coveredUntil)) coveredUntil = interval[1];
            if (!coveredUntil.isBefore(end)) return Fit.TAGGED_OPEN;
        }
        return Fit.TAGGED_CLOSED;
    }

    /** Earliest whole-minute slot known open; unknown tags leave the proposed time provisional. */
    public static LocalTime nextStart(String expression, LocalDate date, LocalTime ready,
            int durationMinutes, LocalTime latestEnd) {
        if (ready == null || latestEnd == null) return null;
        LocalTime end = ready.plusMinutes(durationMinutes);
        if (!end.isAfter(ready) || end.isAfter(latestEnd)) return null;
        Fit initial = fit(expression, date, ready, end);
        if (initial != Fit.TAGGED_CLOSED) return ready;
        LocalTime candidate = ready.plusMinutes(1);
        while (candidate.isAfter(ready) && !candidate.plusMinutes(durationMinutes).isAfter(latestEnd)
                && candidate.plusMinutes(durationMinutes).isAfter(candidate)) {
            if (fit(expression, date, candidate, candidate.plusMinutes(durationMinutes)) == Fit.TAGGED_OPEN)
                return candidate;
            candidate = candidate.plusMinutes(1);
        }
        return null;
    }

    private static Set<Integer> parseDays(String expression) {
        Set<Integer> result = new HashSet<>();
        for (String segment : expression.split(",")) {
            String[] parts = segment.split("-");
            if (parts.length == 1) {
                int index = index(parts[0]); if (index < 0) return null; result.add(index);
            } else if (parts.length == 2) {
                int first = index(parts[0]), last = index(parts[1]);
                if (first < 0 || last < 0) return null;
                for (int i = first; ; i = (i + 1) % 7) { result.add(i); if (i == last) break; }
            } else return null;
        }
        return result;
    }
    private static int index(String value) {
        for (int i = 0; i < DAYS.length; i++) if (DAYS[i].equals(value)) return i;
        return -1;
    }
}
