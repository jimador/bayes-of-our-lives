package dev.jimador.bayes.itinerary;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** Consented model input, deliberately excluding origin, profile IDs and private restriction text. */
public record ItineraryBrief(String city, LocalDate date, LocalTime startTime, LocalTime endTime,
                             int partySize, int childrenCount, String interests, String pace,
                             boolean stepFreeAccess, List<String> requestedStops,
                             List<String> cuisineLikes, List<String> cuisineDislikes) {
    public static ItineraryBrief from(ItineraryRequest request, ItineraryMemories.Accepted memory) {
        List<String> kinds = new ArrayList<>();
        if (request.breakfast()) kinds.add("breakfast");
        if (request.coffee()) kinds.add("coffee");
        if (request.lunch()) kinds.add("lunch");
        if (request.dinner()) kinds.add("dinner");
        kinds.add("sight");
        return new ItineraryBrief(request.city(), request.date(), request.startTime(), request.endTime(),
                request.partySize(), request.childrenCount(), memory.interests(), memory.pace(), request.stepFreeAccess(),
                List.copyOf(kinds), request.likedCuisines(), request.dislikedCuisines());
    }
}
