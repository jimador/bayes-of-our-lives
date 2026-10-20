package dev.jimador.bayes.itinerary;

import com.embabel.common.ai.prompt.PromptContributor;
import dev.jimador.bayes.provider.PageEvidence;
import dev.jimador.bayes.provider.Place;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

/** Model interpretations of attributed evidence. These are never verified venue facts. */
public record ResearchFindings(List<PlaceFinding> places) {

    public enum Issue {
        OUTPUT_SCHEMA,
        FOREIGN_PLACE,
        DUPLICATE_PLACE,
        MISSING_PLACE,
        INVALID_SOURCES,
        INVALID_FIELDS,
        INVALID_WINDOWS,
        UNSUPPORTED_HOURS,
        MODEL_OUTPUT_FORMAT
    }

    public record PlaceView(Place place, PlaceFinding interpretation, List<PageEvidence> pages) {}

    public record Window(LocalDate date, LocalTime opensAt, LocalTime closesAt) {}

    public record PlaceFinding(
            String placeId,
            String summary,
            List<String> cuisines,
            List<String> supportedMeals,
            String hoursStatus,
            List<Window> operatingWindows,
            String access,
            String suitability,
            List<String> sourceIds,
            List<String> notes) {}

    static PlaceFinding unknown(String placeId, Issue issue) {
        return new PlaceFinding(
                placeId,
                "No reliable research interpretation is available.",
                List.of(),
                List.of(),
                "unknown",
                List.of(),
                "unknown",
                "unknown",
                List.of(),
                List.of(
                        "Research issue: "
                                + issue.name()
                                + "; model claims were omitted. Retrieved map/page evidence is"
                                + " listed separately."));
    }

    static ResearchFindings unavailable(List<String> selected, Issue issue) {
        return new ResearchFindings(selected.stream().map(id -> unknown(id, issue)).toList());
    }

    public PlaceFinding place(String id) {
        return places.stream().filter(p -> p.placeId().equals(id)).findFirst().orElse(null);
    }

    /**
     * The interpretations as planner context. This record is also what the researcher returns, so
     * it is not itself a prompt contributor.
     */
    PromptContributor asPrompt() {
        return PromptContributor.fixed(
                "Research interpretations (attributed, not verified)\n"
                        + places.stream()
                                .map(ResearchFindings::line)
                                .collect(Collectors.joining("\n")));
    }

    private static String line(PlaceFinding f) {
        var windows =
                f.operatingWindows().isEmpty()
                        ? "none"
                        : f.operatingWindows().stream()
                                .map(w -> w.opensAt() + "-" + w.closesAt() + " on " + w.date())
                                .collect(Collectors.joining(", "));
        return "- %s: %s Cuisines: %s. Meals: %s. Hours: %s (%s). Access: %s. Suitability: %s. Sources: %s.%s"
                .formatted(
                        f.placeId(),
                        f.summary(),
                        orNone(f.cuisines()),
                        orNone(f.supportedMeals()),
                        f.hoursStatus(),
                        windows,
                        f.access(),
                        f.suitability(),
                        orNone(f.sourceIds()),
                        f.notes().isEmpty() ? "" : " Notes: " + String.join(" ", f.notes()));
    }

    private static String orNone(List<String> values) {
        return values.isEmpty() ? "none" : String.join(", ", values);
    }
}
