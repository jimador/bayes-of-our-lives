package dev.jimador.bays;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Port of Tripper's researched-POI → proposed-plan boundary for one bounded city outing. */
@Component
public class ItineraryPlanner {
    public record Stop(String kind, String name, LocalTime startsAt, LocalTime endsAt,
                       Integer baseTravelMinutes, String sourceUrl, String openingHours,
                       String hoursFit, String wheelchairTag, String cuisineTag,
                       String admissionTag, List<String> openQuestions) {}
    public record Alternative(String id, String title, String status, List<Stop> stops,
                              List<String> openQuestions, int elapsedMinutes) {}
    private record Task(String kind, GuidePlaces.Option option, int durationMinutes,
                        LocalTime earliest, LocalTime latest) {}
    @FunctionalInterface public interface BetweenPlaces {
        Integer minutes(GuidePlaces.Option from, GuidePlaces.Option to);
    }
    private final GuidePlaces places;
    public ItineraryPlanner(GuidePlaces places) { this.places = places; }

    public List<Alternative> propose(ItineraryRequest request, ItineraryMemory.Accepted memory,
            ItineraryResearch.Findings findings) {
        return propose(request, memory, findings, (from, to) -> from.longitude() == null || from.latitude() == null
                || to.longitude() == null || to.latitude() == null ? null
                : places.route(from.longitude(), from.latitude(), to.longitude(), to.latitude()));
    }

    static List<Alternative> propose(ItineraryRequest request, ItineraryMemory.Accepted memory,
            ItineraryResearch.Findings findings, BetweenPlaces route) {
        List<GuidePlaces.Option> sights = findings.sights().stream()
                .sorted(Comparator.comparingInt((GuidePlaces.Option o) -> interestScore(o, memory.interests())).reversed()
                        .thenComparing(o -> o.oneWayDriveMinutes() == null ? 999 : o.oneWayDriveMinutes()))
                .limit(2).toList();
        if (sights.isEmpty()) sights = java.util.Collections.singletonList(null);
        List<Alternative> alternatives = new ArrayList<>();
        for (int i = 0; i < sights.size(); i++) {
            GuidePlaces.Option sight = sights.get(i);
            GuidePlaces.Option cafe = pick(findings.cafes(), i), restaurant = pick(findings.restaurants(), i);
            List<Task> tasks = new ArrayList<>();
            if (request.breakfast()) tasks.add(new Task("breakfast", cafe, 40, LocalTime.of(7, 0), LocalTime.of(10, 30)));
            if (request.coffee()) tasks.add(new Task("coffee", cafe, 20, LocalTime.of(7, 0), LocalTime.of(11, 30)));
            boolean lunchFirst = request.lunch() && !request.startTime().isBefore(LocalTime.of(11, 0));
            if (lunchFirst) tasks.add(new Task("lunch", restaurant, 50, LocalTime.of(11, 0), LocalTime.of(15, 0)));
            tasks.add(new Task("sight", sight, switch (memory.pace()) {
                case "busy" -> 45; case "relaxed" -> 90; default -> 60;
            }, request.startTime(), request.endTime()));
            if (request.lunch() && !lunchFirst)
                tasks.add(new Task("lunch", restaurant, 50, LocalTime.of(11, 0), LocalTime.of(15, 0)));
            if (request.dinner())
                tasks.add(new Task("dinner", restaurant, 60, LocalTime.of(17, 0), LocalTime.of(21, 0)));
            alternatives.add(schedule(i == 0 ? "a" : "b", request, tasks, findings.researchGaps(), route));
        }
        return List.copyOf(alternatives);
    }

    private static Alternative schedule(String id, ItineraryRequest request, List<Task> tasks,
            List<String> researchGaps, BetweenPlaces route) {
        List<Stop> stops = new ArrayList<>();
        List<String> questions = new ArrayList<>(researchGaps);
        questions.add("Check live traffic and travel conditions before leaving; route times are base estimates.");
        LocalTime cursor = request.startTime();
        GuidePlaces.Option prior = null;
        boolean invalid = false;
        for (Task task : tasks) {
            GuidePlaces.Option option = task.option();
            if (option == null) {
                questions.add("Find a sourced " + task.kind() + " venue before this itinerary can be confirmed.");
                stops.add(new Stop(task.kind(), "Venue needed", null, null, null, null,
                        null, "unknown", "unknown", "unknown", "unknown", List.of("No mapped venue was returned")));
                cursor = null;
                continue;
            }
            Integer travel = prior == null ? option.oneWayDriveMinutes() : route.minutes(prior, option);
            if (travel == null || travel < 0) {
                questions.add("Get a base route to " + option.name() + " before relying on the timeline.");
                cursor = null;
            }
            LocalTime starts = null, ends = null;
            if (cursor != null) {
                starts = cursor.plusMinutes(travel);
                if (starts.isBefore(task.earliest())) starts = task.earliest();
                ends = starts.plusMinutes(task.durationMinutes());
                if (ends.isAfter(task.latest()) || ends.isAfter(request.endTime()) || ends.isBefore(starts)) invalid = true;
                cursor = ends;
            }
            Map<String, String> tags = option.osmTags();
            String hours = tags.get("opening_hours");
            OpeningHours.Fit hoursFit = OpeningHours.fit(hours, request.date(), starts, ends);
            List<String> stopQuestions = new ArrayList<>();
            if (hoursFit == OpeningHours.Fit.UNKNOWN)
                stopQuestions.add("Verify opening hours for " + option.name() + " on the chosen date.");
            else if (hoursFit == OpeningHours.Fit.TAGGED_OPEN)
                stopQuestions.add("Confirm current opening hours with " + option.name() + "; map tags may be stale.");
            if (hoursFit == OpeningHours.Fit.TAGGED_CLOSED) {
                invalid = true;
                stopQuestions.add("Mapped hours mark " + option.name() + " closed at the planned time.");
            }
            String wheelchair = tags.getOrDefault("wheelchair", "unknown");
            if (request.stepFreeAccess()) {
                if (wheelchair.equals("no")) invalid = true;
                if (!wheelchair.equals("yes"))
                    stopQuestions.add("Confirm step-free access for " + option.name() + ".");
            }
            boolean food = !task.kind().equals("sight");
            if (food && request.hasDietaryNeed())
                stopQuestions.add("Confirm dietary and allergy needs directly with " + option.name() + ".");
            if (request.childrenCount() > 0 && !tags.containsKey("kids_area"))
                stopQuestions.add("Confirm suitability for children at " + option.name() + ".");
            if (task.kind().equals("sight") && !tags.containsKey("fee"))
                stopQuestions.add("Check admission details for " + option.name() + ".");
            questions.addAll(stopQuestions);
            stops.add(new Stop(task.kind(), option.name(), starts, ends, travel, option.sourceUrl(),
                    hours, hoursFit.name().toLowerCase(Locale.ROOT), wheelchair,
                    tags.getOrDefault("cuisine", "unknown"), tags.getOrDefault("fee", "unknown"),
                    List.copyOf(stopQuestions)));
            prior = option;
        }
        int elapsed = cursor == null ? -1 : (int) java.time.Duration.between(request.startTime(), cursor).toMinutes();
        String status = invalid ? "invalid" : questions.isEmpty() ? "sourced" : "unresolved";
        return new Alternative(id, "Outing " + id.toUpperCase(Locale.ROOT), status,
                List.copyOf(stops), List.copyOf(questions), elapsed);
    }

    private static GuidePlaces.Option pick(List<GuidePlaces.Option> values, int index) {
        return values.isEmpty() ? null : values.get(index % values.size());
    }
    private static int interestScore(GuidePlaces.Option option, String interests) {
        String name = option.name().toLowerCase(Locale.ROOT);
        int score = 0;
        for (String word : interests.toLowerCase(Locale.ROOT).split("[^a-z]+"))
            if (word.length() >= 3 && name.contains(word)) score++;
        return score;
    }
}
