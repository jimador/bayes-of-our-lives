package dev.jimador.decisiondesk;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** Deterministic illustrative guard over the normalized synthetic brief. Not a marine safety rule. */
final class CoastalPolicy {
    private CoastalPolicy() {}

    static String evaluate(String input) {
        Map<String, String> fields = new HashMap<>();
        for (String line : input.split("\\R")) {
            int colon = line.indexOf(": ");
            if (colon > 0) fields.put(line.substring(0, colon), line.substring(colon + 2).trim());
        }
        try {
            double age = number(fields, "Forecast age hours");
            Instant arrival = Instant.parse(fields.get("Simulated arrival UTC"));
            Instant windowStart = Instant.parse(fields.get("Proposed window UTC").split(" to ")[0]);
            boolean covers = booleanValue(fields, "Forecast covers whole window");
            double wind = number(fields, "Sustained wind kt");
            double gust = number(fields, "Wind gust kt");
            double swell = number(fields, "Swell ft");
            boolean storm = booleanValue(fields, "Storm signal");
            double height = finiteNumber(fields, "Predicted water height ft above MLLW");
            double current = number(fields, "Tidal current kt at separate synthetic current station");
            if (fields.getOrDefault("Tide phase", "unknown").equals("unknown") ||
                    fields.getOrDefault("Forecast issued UTC", "unknown").equals("unknown") ||
                    fields.getOrDefault("Forecast valid through UTC", "unknown").equals("unknown") ||
                    age > 6 || !covers) return "review";
            if (storm || wind > 12 || gust > 16 || swell > 2 || height < 2 || current > 1 || arrival.isAfter(windowStart)) return "defer";
            return "candidate";
        } catch (RuntimeException missingOrInvalid) {
            return "review";
        }
    }

    private static double number(Map<String, String> fields, String name) {
        double value = finiteNumber(fields, name);
        if (value < 0) throw new IllegalArgumentException(name);
        return value;
    }

    private static double finiteNumber(Map<String, String> fields, String name) {
        double value = Double.parseDouble(fields.get(name));
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name);
        return value;
    }

    private static boolean booleanValue(Map<String, String> fields, String name) {
        String value = fields.get(name);
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        throw new IllegalArgumentException(name);
    }
}
