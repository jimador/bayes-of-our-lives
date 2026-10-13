package dev.jimador.bays.itinerary;

import dev.jimador.bays.provider.GuidePlaces;
import dev.jimador.bays.provider.TripperCityPort;

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
                       String admissionTag, List<String> openQuestions, String cuisineEvidence,
                       String cuisinePreferenceFit, String mealServiceEvidence, String mealTimeFit) {}
    public record Alternative(String id, String title, String status, List<Stop> stops,
                              List<String> openQuestions, int elapsedMinutes, String planReason) {
        public Alternative(String id, String title, String status, List<Stop> stops,
                List<String> openQuestions, int elapsedMinutes) {
            this(id, title, status, stops, openQuestions, elapsedMinutes, "");
        }
    }
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

    /** Validate and schedule the LLM's Tripper-style proposal; it cannot bypass mapped hard rules. */
    public List<Alternative> proposeFromDraft(ItineraryRequest request, ItineraryResearch.Findings findings,
            TripperCityPort.PlanDraft draft) {
        return proposeFromDraft(request, findings, draft, List.of());
    }

    public List<Alternative> proposeFromDraft(ItineraryRequest request, ItineraryResearch.Findings findings,
            TripperCityPort.PlanDraft draft, List<BayesAgent.ResearchedPointOfInterest> researched) {
        return proposeFromDraft(request, findings, draft, (from, to) -> from.longitude() == null
                || from.latitude() == null || to.longitude() == null || to.latitude() == null ? null
                : places.route(from.longitude(), from.latitude(), to.longitude(), to.latitude()), researched);
    }

    static List<Alternative> proposeFromDraft(ItineraryRequest request, ItineraryResearch.Findings findings,
            TripperCityPort.PlanDraft draft, BetweenPlaces route) {
        return proposeFromDraft(request, findings, draft, route, List.of());
    }

    static List<Alternative> proposeFromDraft(ItineraryRequest request, ItineraryResearch.Findings findings,
            TripperCityPort.PlanDraft draft, BetweenPlaces route,
            List<BayesAgent.ResearchedPointOfInterest> researched) {
        List<Alternative> alternatives = new ArrayList<>();
        int index = 0;
        for (var choice : draft.alternatives()) {
            var sight = named(findings.sights(), choice.sight());
            var cafe = named(findings.cafes(), choice.cafe());
            var restaurant = named(findings.restaurants(), choice.restaurant());
            List<Task> tasks = new ArrayList<>();
            if (request.breakfast()) tasks.add(new Task("breakfast", cafe, 40, LocalTime.of(7, 0), LocalTime.of(10, 30)));
            if (request.coffee()) tasks.add(new Task("coffee", cafe, 20, LocalTime.of(7, 0), LocalTime.of(11, 30)));
            if (request.lunch() && choice.lunchBeforeSight())
                tasks.add(new Task("lunch", restaurant, 50, LocalTime.of(11, 0), LocalTime.of(15, 0)));
            tasks.add(new Task("sight", sight, switch (request.pace()) {
                case "busy" -> 45; case "relaxed" -> 90; default -> 60;
            }, request.startTime(), request.endTime()));
            if (request.lunch() && !choice.lunchBeforeSight())
                tasks.add(new Task("lunch", restaurant, 50, LocalTime.of(11, 0), LocalTime.of(15, 0)));
            if (request.dinner()) tasks.add(new Task("dinner", restaurant, 60, LocalTime.of(17, 0), LocalTime.of(21, 0)));
            var scheduled = schedule(index++ == 0 ? "a" : "b", request, tasks, findings.researchGaps(), route, researched);
            List<String> questions = new ArrayList<>(scheduled.openQuestions());
            if (cafe == null && (request.breakfast() || request.coffee()) && !findings.cafes().isEmpty())
                questions.add("Mapped cafe leads to check for hours and meal service: "
                        + findings.cafes().stream().map(GuidePlaces.Option::name).toList() + ".");
            String title = choice.title() == null || choice.title().isBlank() ? scheduled.title()
                    : choice.title().substring(0, Math.min(80, choice.title().length()));
            String reason = choice.reason() == null ? "" : choice.reason()
                    .replaceAll("https?://\\S+", "[source in research panel]");
            reason = reason.substring(0, Math.min(400, reason.length()));
            alternatives.add(new Alternative(scheduled.id(), title, scheduled.status(), scheduled.stops(),
                    List.copyOf(questions), scheduled.elapsedMinutes(), reason));
        }
        return List.copyOf(alternatives);
    }

    private static GuidePlaces.Option named(List<GuidePlaces.Option> options, String name) {
        if (name == null || name.isBlank()) return null;
        return options.stream().filter(o -> o.name().equals(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("A proposal named an unmapped venue"));
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
            alternatives.add(schedule(i == 0 ? "a" : "b", request, tasks, findings.researchGaps(), route, List.of()));
        }
        return List.copyOf(alternatives);
    }

    private static Alternative schedule(String id, ItineraryRequest request, List<Task> tasks,
            List<String> researchGaps, BetweenPlaces route,
            List<BayesAgent.ResearchedPointOfInterest> researched) {
        List<Stop> stops = new ArrayList<>();
        List<String> questions = new ArrayList<>(researchGaps);
        LocalTime cursor = request.startTime();
        GuidePlaces.Option prior = null;
        boolean invalid = false;
        for (Task task : tasks) {
            GuidePlaces.Option option = task.option();
            if (option == null) {
                questions.add("Confirm a " + task.kind() + " venue before this itinerary can be confirmed.");
                stops.add(new Stop(task.kind(), "Venue needed", null, null, null, null,
                        null, "unknown", "unknown", "unknown", "unknown", List.of("No suitable venue was selected"),
                        "unknown", "unknown", "unknown", "unknown"));
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
            if (hoursFit == OpeningHours.Fit.TAGGED_CLOSED) {
                invalid = true;
                stopQuestions.add("Mapped hours mark " + option.name() + " closed at the planned time.");
            }
            String wheelchair = tags.getOrDefault("wheelchair", "unknown");
            if (request.stepFreeAccess()) {
                if (wheelchair.equals("no")) invalid = true;
                stopQuestions.add("Confirm step-free access through " + option.name()
                        + " and its approach; a mapped wheelchair tag cannot establish the whole route.");
            }
            boolean food = !task.kind().equals("sight");
            if (food && request.hasDietaryNeed())
                stopQuestions.add("Confirm dietary and allergy needs directly with " + option.name() + ".");
            BayesAgent.ResearchedPointOfInterest venueResearch = researched.stream()
                    .filter(r -> r.sourceUrl().equals(option.sourceUrl()) && r.name().equals(option.name()))
                    .findFirst().orElse(null);
            FoodEvidence.Assessment foodEvidence = food
                    ? FoodEvidence.assess(task.kind(), option, venueResearch, request, hoursFit)
                    : new FoodEvidence.Assessment("unknown", "unknown", "not_food", "not_food");
            if (food && foodEvidence.cuisine().equals("unknown"))
                stopQuestions.add("Check cuisine and menu for " + option.name() + ".");
            if (food && task.kind().equals("breakfast"))
                stopQuestions.add("Confirm full breakfast service at " + option.name() + " on "
                        + request.date() + " at " + (starts == null ? "the planned time" : starts) + ".");
            if (food && foodEvidence.service().equals("conflicting"))
                stopQuestions.add("Resolve conflicting meal-service evidence for " + option.name() + ".");
            if (request.childrenCount() > 0)
                stopQuestions.add("Confirm suitability for children at " + option.name() + ".");
            if (task.kind().equals("sight") && !"no".equals(tags.get("fee")))
                stopQuestions.add("Confirm admission price and availability for " + option.name() + ".");
            questions.addAll(stopQuestions);
            stops.add(new Stop(task.kind(), option.name(), starts, ends, travel, option.sourceUrl(),
                    hours, hoursFit == OpeningHours.Fit.TAGGED_OPEN ? "tagged_open_unverified"
                            : hoursFit.name().toLowerCase(Locale.ROOT), wheelchair,
                    tags.getOrDefault("cuisine", "unknown"), tags.getOrDefault("fee", "unknown"),
                    List.copyOf(stopQuestions), foodEvidence.cuisine(), foodEvidence.preference(),
                    foodEvidence.service(), foodEvidence.timeFit()));
            prior = option;
        }
        int elapsed = cursor == null ? -1 : (int) java.time.Duration.between(request.startTime(), cursor).toMinutes();
        if (cursor != null && java.time.Duration.between(cursor, request.endTime()).toMinutes() < 30)
            questions.add("Check live traffic before relying on this tight arrival window; routes are base estimates.");
        String status = invalid ? "invalid" : questions.isEmpty() ? "provisional" : "unresolved";
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
