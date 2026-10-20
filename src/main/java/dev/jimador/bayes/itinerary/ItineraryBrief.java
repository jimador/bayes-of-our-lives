package dev.jimador.bayes.itinerary;

import com.embabel.common.ai.prompt.PromptContributor;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * What a model may know about the outing. It never holds the starting location, profile IDs or
 * private dietary and allergy text.
 */
public record ItineraryBrief(
        String city,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        int partySize,
        int childrenCount,
        String interests,
        String pace,
        boolean stepFreeAccess,
        List<String> requestedStops,
        List<String> cuisineLikes,
        List<String> cuisineDislikes)
        implements PromptContributor {

    public static ItineraryBrief from(ItineraryRequest request, ItineraryMemories.Accepted memory) {
        List<String> stops = new ArrayList<>();
        if (request.breakfast()) stops.add("breakfast");
        if (request.coffee()) stops.add("coffee");
        if (request.lunch()) stops.add("lunch");
        if (request.dinner()) stops.add("dinner");
        stops.add("sight");
        return new ItineraryBrief(
                request.city(),
                request.date(),
                request.startTime(),
                request.endTime(),
                request.partySize(),
                request.childrenCount(),
                memory.interests(),
                memory.pace(),
                request.stepFreeAccess(),
                List.copyOf(stops),
                request.likedCuisines(),
                request.dislikedCuisines());
    }

    @Override
    public String contribution() {
        return """
        The outing
        - City: %s
        - Date: %s, from %s to %s local time
        - Group: %d people, %d of them children
        - Interests: %s
        - Pace: %s
        - Step-free access needed: %s
        - Requested stops: %s
        - Liked cuisines: %s
        - Disliked cuisines: %s\
        """
                .formatted(
                        city,
                        date,
                        startTime,
                        endTime,
                        partySize,
                        childrenCount,
                        interests,
                        pace,
                        stepFreeAccess ? "yes" : "no",
                        String.join(", ", requestedStops),
                        listOrNone(cuisineLikes),
                        listOrNone(cuisineDislikes));
    }

    private static String listOrNone(List<String> values) {
        return values.isEmpty() ? "none" : String.join(", ", values);
    }
}
