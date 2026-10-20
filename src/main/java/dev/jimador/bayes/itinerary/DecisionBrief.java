package dev.jimador.bayes.itinerary;

import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * What every decision engine is told about an outing: the consented profile plus each alternative
 * that passed validation. Local rules, Jev and OpenAI all receive the same {@link #render()
 * rendered text}.
 */
public record DecisionBrief(ItineraryBrief profile, List<Option> alternatives) {

    public record TimedStop(
            String kind,
            LocalTime startsAt,
            LocalTime endsAt,
            List<String> cuisines,
            String preferenceFit,
            String mealServiceBasis,
            String hoursBasis) {}

    public record Option(
            String id,
            String status,
            int materialQuestions,
            int likedMeals,
            int dislikedMeals,
            List<TimedStop> stops) {}

    public static DecisionBrief from(
            ItineraryBrief profile, List<Itinerary.Alternative> alternatives) {
        var options =
                alternatives.stream()
                        .filter(a -> !a.status().equals("invalid"))
                        .map(DecisionBrief::option)
                        .toList();
        return new DecisionBrief(profile, options);
    }

    private static Option option(Itinerary.Alternative alternative) {
        var stops =
                alternative.stops().stream()
                        .map(
                                s ->
                                        new TimedStop(
                                                s.kind(),
                                                s.startsAt(),
                                                s.endsAt(),
                                                s.cuisines(),
                                                s.preferenceFit(),
                                                s.mealServiceBasis(),
                                                s.hoursBasis()))
                        .toList();
        return new Option(
                alternative.id(),
                alternative.status(),
                alternative.openQuestions().size(),
                count(alternative, "liked"),
                count(alternative, "disliked"),
                stops);
    }

    private static int count(Itinerary.Alternative alternative, String fit) {
        return (int)
                alternative.stops().stream().filter(s -> s.preferenceFit().equals(fit)).count();
    }

    /** At least one usable outline, valid outlines with material gaps only, or nothing valid. */
    public String readiness() {
        if (alternatives.stream().anyMatch(a -> a.status().equals("usable")))
            return "usable-outline";
        return alternatives.isEmpty() ? "no-valid-outline" : "material-gaps";
    }

    /** The decision input: plain labelled lines, the same for every engine. */
    public String render() {
        return """
        %s

        Alternatives that passed validation:
        %s
        """
                .formatted(
                        profile.contribution(),
                        alternatives.stream()
                                .map(DecisionBrief::render)
                                .collect(Collectors.joining("\n")));
    }

    private static String render(Option option) {
        var stops =
                option.stops().stream()
                        .map(
                                s ->
                                        "  - %s %s-%s, cuisines: %s, preference: %s, meal service: %s, hours: %s"
                                                .formatted(
                                                        s.kind(),
                                                        s.startsAt(),
                                                        s.endsAt(),
                                                        s.cuisines().isEmpty()
                                                                ? "none"
                                                                : String.join(", ", s.cuisines()),
                                                        s.preferenceFit(),
                                                        s.mealServiceBasis(),
                                                        s.hoursBasis()))
                        .collect(Collectors.joining("\n"));
        return """
        Alternative %s (%s): %d material questions, %d liked-cuisine stops, %d disliked-cuisine stops
        %s\
        """
                .formatted(
                        option.id(),
                        option.status(),
                        option.materialQuestions(),
                        option.likedMeals(),
                        option.dislikedMeals(),
                        stops);
    }
}
