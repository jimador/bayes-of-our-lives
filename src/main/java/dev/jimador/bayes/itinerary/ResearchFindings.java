package dev.jimador.bayes.itinerary;

import java.time.LocalDate;
import dev.jimador.bayes.provider.Place;
import dev.jimador.bayes.provider.PageEvidence;
import java.time.LocalTime;
import java.util.List;

/** Model interpretations of attributed evidence; these are never verified venue facts. */
public record ResearchFindings(List<PlaceFinding> places) {
    public enum Issue {
        OUTPUT_SCHEMA, FOREIGN_PLACE, DUPLICATE_PLACE, MISSING_PLACE, INVALID_SOURCES,
        INVALID_FIELDS, INVALID_WINDOWS, UNSUPPORTED_HOURS, MODEL_OUTPUT_FORMAT
    }
    static PlaceFinding unknown(String placeId, Issue issue) {
        return new PlaceFinding(placeId, "No reliable research interpretation is available.", List.of(), List.of(),
                "unknown", List.of(), "unknown", "unknown", List.of(), List.of("Research issue: " + issue.name() + "; model claims were omitted. Retrieved map/page evidence is listed separately."));
    }
    static ResearchFindings unavailable(List<String> selected, Issue issue) {
        return new ResearchFindings(selected.stream().map(id -> unknown(id, issue)).toList());
    }
    public record PlaceView(Place place, PlaceFinding interpretation, List<PageEvidence> pages) {}
    public record Window(LocalDate date, LocalTime opensAt, LocalTime closesAt) {}
    public record PlaceFinding(String placeId, String summary, List<String> cuisines, List<String> supportedMeals,
                               String hoursStatus, List<Window> operatingWindows, String access,
                               String suitability, List<String> sourceIds, List<String> notes) {}
    public PlaceFinding place(String id) {
        return places.stream().filter(p -> p.placeId().equals(id)).findFirst().orElse(null);
    }
}
