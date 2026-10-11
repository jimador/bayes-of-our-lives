package dev.jimador.decisiondesk;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Named test scenarios, not surveyed launch sites or current conditions. */
public final class SyntheticCityCatalog {
    public record Place(String name, String waterway, String routeSetting, int windowOffsetMinutes) {}

    private static final Map<String, List<Place>> CITIES = Map.of(
            "miami", List.of(
                    new Place("Miami Demo Intracoastal A", "Miami synthetic reach A", "intracoastal", 0),
                    new Place("Miami Demo Ocean B", "Miami synthetic reach B", "ocean", 60),
                    new Place("Miami Demo Intracoastal C", "Miami synthetic reach C", "intracoastal", 30)),
            "charleston", List.of(
                    new Place("Charleston Demo Intracoastal A", "Charleston synthetic reach A", "intracoastal", 0),
                    new Place("Charleston Demo Ocean B", "Charleston synthetic reach B", "ocean", 60),
                    new Place("Charleston Demo Intracoastal C", "Charleston synthetic reach C", "intracoastal", 30)),
            "savannah", List.of(
                    new Place("Savannah Demo Intracoastal A", "Savannah synthetic reach A", "intracoastal", 0),
                    new Place("Savannah Demo Ocean B", "Savannah synthetic reach B", "ocean", 60),
                    new Place("Savannah Demo Intracoastal C", "Savannah synthetic reach C", "intracoastal", 30)),
            "new york city", List.of(
                    new Place("New York Demo Intracoastal A", "New York synthetic reach A", "intracoastal", 0),
                    new Place("New York Demo Ocean B", "New York synthetic reach B", "ocean", 60),
                    new Place("New York Demo Intracoastal C", "New York synthetic reach C", "intracoastal", 30)));

    private SyntheticCityCatalog() {}

    public static String normalizeCity(String city) {
        if (city == null) throw new IllegalArgumentException("City is required; supported: " + CITIES.keySet());
        String normalized = city.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("nyc")) normalized = "new york city";
        if (!CITIES.containsKey(normalized))
            throw new IllegalArgumentException("Unknown city: " + city + "; supported: " + CITIES.keySet());
        return normalized;
    }

    public static List<Place> places(String city) { return CITIES.get(normalizeCity(city)); }
}
