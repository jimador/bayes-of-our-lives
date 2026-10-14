package dev.jimador.bayes.itinerary;

import dev.jimador.bayes.provider.GuidePlaces;
import dev.jimador.bayes.provider.TripperCityPort;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Optional;
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
        // Cache base routes within this one planning call. The search below never asks the
        // public route service for the same leg twice.
        Map<String, Optional<Integer>> routes = new LinkedHashMap<>();
        BetweenPlaces cachedRoute = (from, to) -> routes.computeIfAbsent(
                from.sourceUrl() + "|" + to.sourceUrl(),
                ignored -> Optional.ofNullable(route.minutes(from, to))).orElse(null);
        List<Alternative> alternatives = new ArrayList<>();
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        int index = 0;
        for (var choice : draft.alternatives()) {
            var sight = named(findings.sights(), choice.sight());
            var cafe = named(findings.cafes(), choice.cafe());
            var restaurant = preferredRestaurant(request, findings.restaurants(),
                    named(findings.restaurants(), choice.restaurant()));
            List<GuidePlaces.Option> sights = options(sight, findings.sights(), 3);
            List<GuidePlaces.Option> cafes = request.breakfast() || request.coffee()
                    ? options(cafe, findings.cafes(), 2) : java.util.Collections.singletonList(null);
            List<GuidePlaces.Option> restaurants = request.lunch() || request.dinner()
                    ? options(restaurant, CuisineEvidence.ranked(findings.restaurants(), request, true), 3)
                    : java.util.Collections.singletonList(null);
            List<RepairCandidate> attempts = new ArrayList<>();
            for (var s : sights) for (var c : cafes) for (var r : restaurants)
                for (boolean lunchFirst : request.lunch()
                        ? new boolean[] {choice.lunchBeforeSight(), !choice.lunchBeforeSight()}
                        : new boolean[] {false}) {
                    var plan = schedule("candidate", request, tasks(request, s, c, r, lunchFirst),
                            findings.researchGaps(), cachedRoute, researched);
                    String key = plan.stops().stream().map(stop -> stop.kind() + ":" + stop.name())
                            .reduce((a, b) -> a + "|" + b).orElse("");
                    if (attempts.stream().noneMatch(existing -> existing.key().equals(key)))
                        attempts.add(new RepairCandidate(plan, key, s == sight && c == cafe
                                && r == restaurant && lunchFirst == choice.lunchBeforeSight(),
                                r == null ? -1000 : CuisineEvidence.score(r, request, true)));
                }
            attempts.sort(Comparator.comparingInt((RepairCandidate a) -> repairScore(a)).reversed());
            for (var attempt : attempts) {
                if (!selected.add(attempt.key())) continue;
                var scheduled = attempt.plan();
                String id = index++ == 0 ? "a" : "b";
                String title = attempt.original() && choice.title() != null && !choice.title().isBlank()
                        ? choice.title().substring(0, Math.min(80, choice.title().length()))
                        : "Outing " + id.toUpperCase(Locale.ROOT);
                String reason = attempt.original() && choice.reason() != null
                        ? choice.reason().replaceAll("https?://\\S+", "[source in research panel]")
                        : "Adjusted mapped venues or order to fit known hours, base routes, and the outing window.";
                alternatives.add(new Alternative(id, title, scheduled.status(), scheduled.stops(),
                        scheduled.openQuestions(), scheduled.elapsedMinutes(),
                        reason.substring(0, Math.min(400, reason.length()))));
                break;
            }
            if (alternatives.size() == 2) break;
        }
        return List.copyOf(alternatives);
    }

    private record RepairCandidate(Alternative plan, String key, boolean original, int cuisineScore) {}

    private static int repairScore(RepairCandidate candidate) {
        var plan = candidate.plan();
        int missing = (int) plan.stops().stream().filter(s -> s.name().equals("Venue needed")).count();
        return (plan.status().equals("invalid") ? -100_000 : 0) - missing * 10_000
                + candidate.cuisineScore() * 10 + (candidate.original() ? 5 : 0)
                - plan.openQuestions().size() - Math.max(0, plan.elapsedMinutes()) / 60;
    }

    private static List<GuidePlaces.Option> options(GuidePlaces.Option first,
            List<GuidePlaces.Option> available, int maximum) {
        var values = new LinkedHashSet<GuidePlaces.Option>();
        if (first != null) values.add(first);
        values.addAll(available);
        if (values.isEmpty()) values.add(null);
        return values.stream().limit(maximum).toList();
    }

    private static List<Task> tasks(ItineraryRequest request, GuidePlaces.Option sight,
            GuidePlaces.Option cafe, GuidePlaces.Option restaurant, boolean lunchFirst) {
        List<Task> tasks = new ArrayList<>();
        if (request.breakfast()) tasks.add(new Task("breakfast", cafe, 40, LocalTime.of(7, 0), LocalTime.of(10, 30)));
        if (request.coffee()) tasks.add(new Task("coffee", cafe, 20, LocalTime.of(7, 0), LocalTime.of(11, 30)));
        if (request.lunch() && lunchFirst)
            tasks.add(new Task("lunch", restaurant, 50, LocalTime.of(11, 0), LocalTime.of(15, 0)));
        tasks.add(new Task("sight", sight, switch (request.pace()) {
            case "busy" -> 45; case "relaxed" -> 90; default -> 60;
        }, request.startTime(), request.endTime()));
        if (request.lunch() && !lunchFirst)
            tasks.add(new Task("lunch", restaurant, 50, LocalTime.of(11, 0), LocalTime.of(15, 0)));
        if (request.dinner()) tasks.add(new Task("dinner", restaurant, 60, LocalTime.of(17, 0), LocalTime.of(21, 0)));
        return tasks;
    }

    private static GuidePlaces.Option preferredRestaurant(ItineraryRequest request,
            List<GuidePlaces.Option> options, GuidePlaces.Option proposed) {
        if (options.isEmpty()) return proposed;
        var ranked = CuisineEvidence.ranked(options, request, true);
        if (ranked.isEmpty()) return proposed;
        var best = ranked.getFirst();
        if (proposed != null && CuisineEvidence.preference(CuisineEvidence.known(proposed), request)
                .equals("disliked") && !CuisineEvidence.preference(CuisineEvidence.known(best), request)
                .equals("disliked")) return best;
        if (request.likedCuisines().isEmpty()
                || !CuisineEvidence.preference(CuisineEvidence.known(best), request).equals("liked"))
            return proposed;
        return proposed == null || CuisineEvidence.score(best, request, true)
                > CuisineEvidence.score(proposed, request, true) ? best : proposed;
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
            Map<String, String> tags = option.osmTags();
            String hours = tags.get("opening_hours");
            LocalTime starts = null, ends = null;
            if (cursor != null) {
                LocalTime ready = cursor.plusMinutes(travel);
                if (ready.isBefore(cursor)) {
                    invalid = true; // A same-day outing cannot wrap past midnight.
                    questions.add("Travel to " + option.name() + " crosses the requested day.");
                    cursor = null;
                } else {
                    if (ready.isBefore(task.earliest())) ready = task.earliest();
                    LocalTime latestEnd = task.latest().isBefore(request.endTime())
                            ? task.latest() : request.endTime();
                    LocalTime openStart = OpeningHours.nextStart(hours, request.date(), ready,
                            task.durationMinutes(), latestEnd);
                    starts = openStart == null ? ready : openStart;
                    ends = starts.plusMinutes(task.durationMinutes());
                    if (openStart == null || !ends.isAfter(starts) || ends.isAfter(latestEnd)) {
                        invalid = true;
                        questions.add("No supported " + task.kind() + " slot at " + option.name()
                                + " fits mapped hours, travel, and the outing window.");
                    }
                    cursor = ends;
                }
            }
            OpeningHours.Fit hoursFit = OpeningHours.fit(hours, request.date(), starts, ends);
            List<String> stopQuestions = new ArrayList<>();
            if (hoursFit == OpeningHours.Fit.UNKNOWN)
                stopQuestions.add("Verify opening hours for " + option.name() + " on the chosen date.");
            if (hoursFit == OpeningHours.Fit.TAGGED_OPEN)
                stopQuestions.add("Confirm current opening hours for " + option.name()
                        + "; mapped hours are not live confirmation.");
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
