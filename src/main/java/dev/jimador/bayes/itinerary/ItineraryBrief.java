package dev.jimador.bayes.itinerary;

import com.embabel.common.ai.prompt.PromptContributor;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * What a model may know about the outing. It never holds the starting location, profile IDs or
 * private dietary and allergy text.
 */
public record ItineraryBrief(
        String city,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        String timezone,
        int partySize,
        int childrenCount,
        String interests,
        String pace,
        boolean stepFreeAccess,
        List<String> requestedStops,
        Map<String, MealTimes.Window> mealWindows,
        List<String> cuisineLikes,
        List<String> cuisineDislikes)
        implements PromptContributor {

    public static ItineraryBrief from(
            ItineraryRequest request,
            ItineraryMemories.Accepted memory,
            MealTimes meals,
            String timezone) {
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
                timezone,
                request.partySize(),
                request.childrenCount(),
                memory.interests(),
                memory.pace(),
                request.stepFreeAccess(),
                List.copyOf(stops),
                Map.copyOf(meals.forStops(stops)),
                request.likedCuisines(),
                request.dislikedCuisines());
    }

    @Override
    public String contribution() {
        return """
        The outing
        - City: %s
        - Date: %s, from %s to %s local time (%s)
        - Group: %d people, %d of them children
        - Interests: %s
        - Pace: %s
        - Step-free access needed: %s
        - Requested stops: %s
        - Meal windows: %s
        - Liked cuisines: %s
        - Disliked cuisines: %s\
        """
                .formatted(
                        city,
                        date,
                        startTime,
                        endTime,
                        timezone,
                        partySize,
                        childrenCount,
                        interests,
                        pace,
                        stepFreeAccess ? "yes" : "no",
                        String.join(", ", requestedStops),
                        windows(),
                        listOrNone(cuisineLikes),
                        listOrNone(cuisineDislikes));
    }

    // Map.copyOf loses order, so list the windows in the order the stops were requested.
    private String windows() {
        var meals =
                requestedStops.stream()
                        .filter(mealWindows::containsKey)
                        .map(stop -> stop + " " + mealWindows.get(stop))
                        .collect(Collectors.joining(", "));
        return meals.isEmpty() ? "none" : meals;
    }

    private static String listOrNone(List<String> values) {
        return values.isEmpty() ? "none" : String.join(", ", values);
    }
}
