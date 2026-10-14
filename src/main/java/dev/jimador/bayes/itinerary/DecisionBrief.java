package dev.jimador.bayes.itinerary;

import java.time.LocalTime;
import java.util.List;

/** The same safe typed decision data reaches every backend, including local rules. */
public record DecisionBrief(ItineraryBrief profile, List<Option> alternatives) {
    public record TimedStop(String kind, LocalTime startsAt, LocalTime endsAt, List<String> cuisines,
                            String preferenceFit, String mealServiceBasis, String hoursBasis) {}
    public record Option(String id, String status, int materialQuestions, int likedMeals,
                         int dislikedMeals, List<TimedStop> stops) {}
    public static DecisionBrief from(ItineraryBrief profile, List<Itinerary.Alternative> alternatives) {
        return new DecisionBrief(profile, alternatives.stream().filter(a -> !a.status().equals("invalid"))
                .map(a -> new Option(a.id(), a.status(), a.openQuestions().size(),
                        (int) a.stops().stream().filter(s -> s.preferenceFit().equals("liked")).count(),
                        (int) a.stops().stream().filter(s -> s.preferenceFit().equals("disliked")).count(),
                        a.stops().stream().map(s -> new TimedStop(s.kind(), s.startsAt(), s.endsAt(), s.cuisines(),
                                s.preferenceFit(), s.mealServiceBasis(), s.hoursBasis())).toList())).toList());
    }
    public String readiness() {
        return alternatives.stream().anyMatch(a -> a.status().equals("usable")) ? "usable-outline"
                : alternatives.isEmpty() ? "no-valid-outline" : "material-gaps";
    }
}
