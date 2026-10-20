package dev.jimador.bayes.itinerary;

import com.embabel.common.ai.prompt.PromptContributor;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
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

    public record Window(
            @JsonPropertyDescription("The outing date") LocalDate date,
            @JsonPropertyDescription("Opening time, HH:mm") LocalTime opensAt,
            @JsonPropertyDescription("Closing time, HH:mm") LocalTime closesAt) {}

    public record PlaceFinding(
            @JsonPropertyDescription("The venue's place ID, exactly as given") String placeId,
            @JsonPropertyDescription(
                            "What the evidence says about this venue, in a sentence or two")
                    String summary,
            @JsonPropertyDescription("Cuisines the evidence states, never inferred from the name")
                    List<String> cuisines,
            @JsonPropertyDescription(
                            "Meals the evidence supports: breakfast, coffee, lunch or dinner")
                    List<String> supportedMeals,
            @JsonPropertyDescription("open, closed or unknown on the outing date")
                    String hoursStatus,
            @JsonPropertyDescription(
                            "Opening windows on the outing date, empty when the hours are unknown")
                    List<Window> operatingWindows,
            @JsonPropertyDescription(
                            "supported, incompatible or unknown for the group's access needs")
                    String access,
            @JsonPropertyDescription(
                            "supported, incompatible or unknown for this group and its interests")
                    String suitability,
            @JsonPropertyDescription(
                            "Sources relied on: the place ID for map tags, a page ID for a page")
                    List<String> sourceIds,
            @JsonPropertyDescription("Advisory notes, such as prices or menu caveats")
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
