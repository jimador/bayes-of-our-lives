package dev.jimador.bayes.itinerary;

import com.embabel.common.ai.prompt.PromptContributor;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

/** Validated alternatives for one outing. Only finalization produces one. */
public record Itinerary(List<Alternative> alternatives) {

    /** What the planner returns before Java has checked it. */
    public record Draft(
            @JsonPropertyDescription("One or two alternatives, with IDs a and b")
                    List<AlternativeDraft> alternatives) {}

    public record AlternativeDraft(
            @JsonPropertyDescription("a or b") String id,
            @JsonPropertyDescription("The stops in time order") List<StopDraft> stops,
            @JsonPropertyDescription("Why this alternative suits the outing") String reason,
            @JsonPropertyDescription("Caveats and evidence gaps") List<String> notes) {}

    public record StopDraft(
            @JsonPropertyDescription("breakfast, coffee, lunch, dinner or sight") String kind,
            @JsonPropertyDescription("A researched place ID") String placeId,
            @JsonPropertyDescription("Local start time, HH:mm") LocalTime startsAt,
            @JsonPropertyDescription("Local end time, HH:mm") LocalTime endsAt) {}

    /**
     * A draft together with the validator's verdict on each alternative, which drives the one
     * refinement.
     */
    public record ReviewedDraft(Draft draft, List<Alternative> alternatives)
            implements PromptContributor {

        @Override
        public String contribution() {
            return "Review of your draft\n"
                    + alternatives.stream()
                            .map(ReviewedDraft::review)
                            .collect(Collectors.joining("\n"));
        }

        private static String review(Alternative alternative) {
            var stops = new StringBuilder();
            Stop previous = null;
            for (var stop : alternative.stops()) {
                stops.append(
                        "  - %s at %s, %s-%s, "
                                .formatted(
                                        stop.kind(),
                                        stop.placeId(),
                                        stop.startsAt(),
                                        stop.endsAt()));
                stops.append(drive(previous, stop)).append('\n');
                previous = stop;
            }
            var problems =
                    alternative.openQuestions().isEmpty()
                            ? "  No problems found."
                            : alternative.openQuestions().stream()
                                    .map(q -> "  - " + q)
                                    .collect(Collectors.joining("\n"));
            return """
            Alternative %s is %s.
            Stops:
            %s
            Problems:
            %s\
            """
                    .formatted(
                            alternative.id(),
                            alternative.status(),
                            stops.toString().stripTrailing(),
                            problems);
        }

        // Spell out the earliest legal start so the model doesn't have to redo the arithmetic.
        private static String drive(Stop previous, Stop stop) {
            if (stop.baseTravelMinutes() == null) return "no trusted drive time";
            int minutes = stop.baseTravelMinutes();
            if (previous == null) return minutes + " min drive from the origin";
            var earliest =
                    previous.endsAt().plusMinutes(minutes + ItineraryValidation.PARKING_MINUTES);
            return "%d min drive from the previous stop, so it can start at %s at the earliest"
                    .formatted(minutes, earliest);
        }
    }

    public record Stop(
            String kind,
            String placeId,
            String name,
            LocalTime startsAt,
            LocalTime endsAt,
            Integer baseTravelMinutes,
            String routeBasis,
            String sourceUrl,
            List<String> cuisines,
            String preferenceFit,
            String mealServiceBasis,
            String hoursBasis,
            List<String> sourceIds,
            List<String> notes) {}

    public record Alternative(
            String id,
            String title,
            String status,
            List<Stop> stops,
            String reason,
            List<String> openQuestions,
            List<String> notes) {}
}
