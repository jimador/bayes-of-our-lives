package dev.jimador.bayes.itinerary;

import java.time.LocalTime;
import java.util.List;

public record Itinerary(List<Alternative> alternatives) {
    public record Draft(List<AlternativeDraft> alternatives) {}
    public record ReviewedDraft(Draft draft, List<Alternative> alternatives) {}
    public record AlternativeDraft(String id, List<StopDraft> stops, String reason, List<String> notes) {}
    public record StopDraft(String kind, String placeId, LocalTime startsAt, LocalTime endsAt) {}
    public record Stop(String kind, String placeId, String name, LocalTime startsAt, LocalTime endsAt,
                       Integer baseTravelMinutes, String routeBasis, String sourceUrl,
                       List<String> cuisines, String preferenceFit, String mealServiceBasis,
                       String hoursBasis, List<String> sourceIds, List<String> notes) {}
    public record Alternative(String id, String title, String status, List<Stop> stops,
                              String reason, List<String> openQuestions, List<String> notes) {}
}
