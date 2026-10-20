package dev.jimador.bayes.itinerary;

import dev.jimador.bayes.itinerary.ResearchFindings.Issue;
import dev.jimador.bayes.provider.Place;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Source and schedule invariants. Venue meaning and itinerary order remain model reasoning. */
public final class ItineraryValidation {
    private static final Logger LOG = LoggerFactory.getLogger(ItineraryValidation.class);

    /** Time added to every drive for parking and walking in. */
    static final int PARKING_MINUTES = 10;

    private ItineraryValidation() {}

    public static void date(ItineraryBrief brief, String timezone) {
        final ZoneId zone;
        try {
            zone = ZoneId.of(timezone);
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("Destination timezone could not be determined");
        }
        LocalDate today = LocalDate.now(zone);
        if (brief.date().isBefore(today)
                || (brief.date().equals(today) && !brief.endTime().isAfter(LocalTime.now(zone))))
            throw new IllegalArgumentException(
                    "Choose a future outing window in the destination's local timezone");
        if (zone.getRules().getValidOffsets(brief.date().atTime(brief.startTime())).size() != 1
                || zone.getRules().getValidOffsets(brief.date().atTime(brief.endTime())).size() != 1
                || !brief.date()
                        .atTime(brief.startTime())
                        .atZone(zone)
                        .getOffset()
                        .equals(brief.date().atTime(brief.endTime()).atZone(zone).getOffset()))
            throw new IllegalArgumentException(
                    "Choose an outing window without an ambiguous daylight-saving transition");
    }

    public static ResearchFindings research(
            ResearchFindings findings, List<String> selected, ItineraryContext context) {
        Map<Issue, Integer> issues = new EnumMap<>(Issue.class);
        Map<String, ResearchFindings.PlaceFinding> byId = new LinkedHashMap<>();
        Set<String> duplicates = new HashSet<>();
        if (findings == null || findings.places() == null)
            issues.merge(Issue.OUTPUT_SCHEMA, 1, Integer::sum);
        else
            for (var fact : findings.places()) {
                if (fact == null || fact.placeId() == null || !selected.contains(fact.placeId())) {
                    issues.merge(Issue.FOREIGN_PLACE, 1, Integer::sum);
                } else if (byId.putIfAbsent(fact.placeId(), fact) != null)
                    duplicates.add(fact.placeId());
            }
        List<ResearchFindings.PlaceFinding> normalized = new ArrayList<>();
        for (String id : selected) {
            context.tools().place(id); // The selected ID must really belong to this outing.
            var fact = byId.get(id);
            Issue rejected =
                    duplicates.contains(id)
                            ? Issue.DUPLICATE_PLACE
                            : fact == null
                                    ? Issue.MISSING_PLACE
                                    : fact.sourceIds() == null
                                                    || fact.sourceIds().isEmpty()
                                                    || fact.sourceIds().stream()
                                                            .anyMatch(
                                                                    source ->
                                                                            !context.tools()
                                                                                    .hasSource(
                                                                                            id,
                                                                                            source))
                                            ? Issue.INVALID_SOURCES
                                            : null;
            if (rejected != null) {
                issues.merge(rejected, 1, Integer::sum);
                normalized.add(ResearchFindings.unknown(id, rejected));
                continue;
            }
            normalized.add(normalizeFields(fact, context, issues));
        }
        issues.forEach(
                (issue, count) ->
                        LOG.info("stage=research issue={} count={}", issue.name(), count));
        return new ResearchFindings(List.copyOf(normalized));
    }

    private static ResearchFindings.PlaceFinding normalizeFields(
            ResearchFindings.PlaceFinding fact,
            ItineraryContext context,
            Map<Issue, Integer> issues) {
        List<String> notes = new ArrayList<>();
        if (fact.notes() != null)
            fact.notes().stream().filter(Objects::nonNull).limit(8).forEach(notes::add);
        List<String> cuisines =
                fact.cuisines() == null
                        ? List.of()
                        : fact.cuisines().stream()
                                .filter(Objects::nonNull)
                                .map(String::trim)
                                .filter(s -> !s.isBlank() && s.length() <= 40)
                                .map(s -> s.toLowerCase(java.util.Locale.ROOT))
                                .distinct()
                                .limit(8)
                                .toList();
        List<String> meals =
                fact.supportedMeals() == null
                        ? List.of()
                        : fact.supportedMeals().stream()
                                .filter(
                                        s ->
                                                s != null
                                                        && List.of(
                                                                        "breakfast",
                                                                        "coffee",
                                                                        "lunch",
                                                                        "dinner")
                                                                .contains(s))
                                .distinct()
                                .toList();
        String hours =
                fact.hoursStatus() != null
                                && List.of("open", "closed", "unknown").contains(fact.hoursStatus())
                        ? fact.hoursStatus()
                        : "unknown";
        String access = suitability(fact.access());
        String suitability = suitability(fact.suitability());
        boolean fieldsChanged =
                fact.summary() == null
                        || fact.cuisines() == null
                        || fact.supportedMeals() == null
                        || fact.notes() == null
                        || !cuisines.equals(fact.cuisines())
                        || !meals.equals(fact.supportedMeals())
                        || !Objects.equals(hours, fact.hoursStatus())
                        || !Objects.equals(access, fact.access())
                        || !Objects.equals(suitability, fact.suitability());
        if (fieldsChanged) {
            issues.merge(Issue.INVALID_FIELDS, 1, Integer::sum);
            notes.add("Research issue: INVALID_FIELDS; unsupported fields are unknown or omitted.");
        }
        boolean hoursSource =
                fact.sourceIds().stream()
                        .anyMatch(source -> context.tools().hasHoursSource(fact.placeId(), source));
        List<ResearchFindings.Window> proposed =
                fact.operatingWindows() == null ? List.of() : fact.operatingWindows();
        List<ResearchFindings.Window> windows =
                hoursSource && hours.equals("open")
                        ? proposed.stream()
                                .filter(
                                        w ->
                                                w != null
                                                        && context.brief().date().equals(w.date())
                                                        && w.opensAt() != null
                                                        && w.closesAt() != null
                                                        && w.closesAt().isAfter(w.opensAt()))
                                .distinct()
                                .toList()
                        : List.of();
        if (fact.operatingWindows() == null || windows.size() != proposed.size()) {
            issues.merge(Issue.INVALID_WINDOWS, 1, Integer::sum);
            notes.add(
                    "Research issue: INVALID_WINDOWS; unsupported or incoherent operating windows"
                            + " were omitted.");
        }
        if (!hoursSource || (hours.equals("open") && windows.isEmpty())) {
            if (!hours.equals("unknown")) {
                issues.merge(Issue.UNSUPPORTED_HOURS, 1, Integer::sum);
                notes.add(
                        "Research issue: UNSUPPORTED_HOURS; opening or closure claims could not be"
                                + " supported.");
            }
            hours = "unknown";
        }
        return new ResearchFindings.PlaceFinding(
                fact.placeId(),
                fact.summary() == null ? "Research interpretation is incomplete." : fact.summary(),
                cuisines,
                meals,
                hours,
                windows,
                access,
                suitability,
                fact.sourceIds().stream().distinct().toList(),
                List.copyOf(notes));
    }

    private static String suitability(String value) {
        return value != null && List.of("supported", "incompatible", "unknown").contains(value)
                ? value
                : "unknown";
    }

    public static Itinerary plans(
            Itinerary.Draft draft, ResearchFindings research, ItineraryContext context) {
        if (draft == null
                || draft.alternatives() == null
                || draft.alternatives().isEmpty()
                || draft.alternatives().size() > 2)
            throw new IllegalStateException("Planning must return one or two alternatives");
        List<Itinerary.Alternative> checked = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (var alternative : draft.alternatives()) {
            if (alternative == null
                    || !List.of("a", "b").contains(alternative.id())
                    || !ids.add(alternative.id()))
                throw new IllegalStateException("Planning returned invalid alternative IDs");
            checked.add(plan(alternative, research, context));
        }
        Map<List<String>, Itinerary.Alternative> unique = new LinkedHashMap<>();
        Set<List<String>> repeated = new HashSet<>();
        for (var alternative : checked) {
            var signature =
                    alternative.stops().stream()
                            .map(stop -> stop.kind() + ":" + stop.placeId())
                            .toList();
            var previous = unique.putIfAbsent(signature, alternative);
            if (previous != null) {
                repeated.add(signature);
                if (statusRank(alternative.status()) < statusRank(previous.status()))
                    unique.put(signature, alternative);
            }
        }
        var retained =
                unique.entrySet().stream()
                        .map(
                                entry -> {
                                    var a = entry.getValue();
                                    return repeated.contains(entry.getKey())
                                            ? new Itinerary.Alternative(
                                                    a.id(),
                                                    a.title(),
                                                    a.status(),
                                                    a.stops(),
                                                    a.reason(),
                                                    a.openQuestions(),
                                                    java.util.stream.Stream.concat(
                                                                    a.notes().stream(),
                                                                    java.util.stream.Stream.of(
                                                                            "Equivalent venue/order"
                                                                                    + " alternative"
                                                                                    + " omitted."))
                                                            .distinct()
                                                            .toList())
                                            : a;
                                })
                        .toList();
        List<String> gaps = new ArrayList<>();
        var valid = checked.stream().filter(a -> !a.status().equals("invalid")).toList();
        var available =
                context.brief().cuisineLikes().stream()
                        .filter(
                                cuisine ->
                                        research.places().stream()
                                                .anyMatch(
                                                        candidate ->
                                                                candidate
                                                                                .cuisines()
                                                                                .contains(cuisine)
                                                                        && valid.stream()
                                                                                .anyMatch(
                                                                                        a ->
                                                                                                fitsMealSlot(
                                                                                                        candidate,
                                                                                                        a,
                                                                                                        context))))
                        .limit(2)
                        .toList();
        var represented =
                valid.stream()
                        .flatMap(a -> a.stops().stream())
                        .filter(stop -> !stop.kind().equals("sight"))
                        .flatMap(stop -> stop.cuisines().stream())
                        .toList();
        var missing = available.stream().filter(cuisine -> !represented.contains(cuisine)).toList();
        if (!missing.isEmpty())
            gaps.add(
                    "Include a distinct alternative for the supported, reachable liked cuisines: "
                            + String.join(", ", missing)
                            + ".");
        if (gaps.isEmpty()) return new Itinerary(retained);
        return new Itinerary(
                retained.stream()
                        .map(
                                a ->
                                        a.status().equals("invalid")
                                                ? a
                                                : new Itinerary.Alternative(
                                                        a.id(),
                                                        a.title(),
                                                        "unresolved",
                                                        a.stops(),
                                                        a.reason(),
                                                        java.util.stream.Stream.concat(
                                                                        a.openQuestions().stream(),
                                                                        gaps.stream())
                                                                .distinct()
                                                                .toList(),
                                                        a.notes()))
                        .toList());
    }

    private static int statusRank(String status) {
        return switch (status) {
            case "usable" -> 0;
            case "unresolved" -> 1;
            default -> 2;
        };
    }

    /**
     * Check existing meal slots against attributed candidates without creating a replacement plan.
     */
    private static boolean fitsMealSlot(
            ResearchFindings.PlaceFinding candidate,
            Itinerary.Alternative alternative,
            ItineraryContext context) {
        Place place = context.tools().place(candidate.placeId());
        if (!candidate.hoursStatus().equals("open")
                || candidate.suitability().equals("incompatible")
                || candidate.sourceIds().stream()
                        .anyMatch(id -> !context.tools().hasSource(place.id(), id))
                || candidate.sourceIds().stream()
                        .noneMatch(id -> context.tools().hasHoursSource(place.id(), id))
                || candidate.cuisines().stream()
                        .anyMatch(context.brief().cuisineDislikes()::contains)
                || List.of("closed", "off")
                        .contains(
                                place.tags()
                                        .getOrDefault("opening_hours", "")
                                        .toLowerCase(java.util.Locale.ROOT))
                || (context.brief().stepFreeAccess()
                        && (candidate.access().equals("incompatible")
                                || "no".equalsIgnoreCase(place.tags().get("wheelchair"))
                                || (!candidate.access().equals("supported")
                                        && !"yes"
                                                .equalsIgnoreCase(
                                                        place.tags().get("wheelchair"))))))
            return false;
        for (int i = 0; i < alternative.stops().size(); i++) {
            var slot = alternative.stops().get(i);
            boolean service =
                    candidate.supportedMeals().contains(slot.kind())
                            || (slot.kind().equals("breakfast")
                                    && "yes".equalsIgnoreCase(place.tags().get("breakfast")));
            if (slot.kind().equals("sight")
                    || !service
                    || "no".equalsIgnoreCase(place.tags().get(slot.kind()))
                    || candidate.operatingWindows().stream()
                            .noneMatch(
                                    w ->
                                            context.brief().date().equals(w.date())
                                                    && !slot.startsAt().isBefore(w.opensAt())
                                                    && !slot.endsAt().isAfter(w.closesAt())))
                continue;
            var previous = i == 0 ? null : alternative.stops().get(i - 1);
            var incoming =
                    context.tools()
                            .route(previous == null ? "origin" : previous.placeId(), place.id());
            LocalTime departure =
                    previous == null ? context.brief().startTime() : previous.endsAt();
            if (incoming.driveMinutes() == null
                    || Duration.between(departure, slot.startsAt()).toMinutes()
                            < incoming.driveMinutes() + PARKING_MINUTES) continue;
            if (i + 1 < alternative.stops().size()) {
                var next = alternative.stops().get(i + 1);
                var outgoing = context.tools().route(place.id(), next.placeId());
                if (outgoing.driveMinutes() == null
                        || Duration.between(slot.endsAt(), next.startsAt()).toMinutes()
                                < outgoing.driveMinutes() + PARKING_MINUTES) continue;
            }
            return true;
        }
        return false;
    }

    private static Itinerary.Alternative plan(
            Itinerary.AlternativeDraft draft, ResearchFindings research, ItineraryContext context) {
        List<String> invalid = new ArrayList<>(),
                questions = new ArrayList<>(),
                notes = new ArrayList<>();
        List<Itinerary.Stop> stops = new ArrayList<>();
        if (draft.notes() != null) notes.addAll(draft.notes());
        List<Itinerary.StopDraft> proposed = draft.stops() == null ? List.of() : draft.stops();
        if (proposed.isEmpty() || proposed.size() > 8)
            invalid.add("Provide between one and eight timed stops.");
        var brief = context.brief();
        Set<String> covered = new HashSet<>();
        String previous = "origin";
        LocalTime previousEnd = brief.startTime();
        for (var stop : proposed.stream().limit(8).toList()) {
            if (stop == null || !brief.requestedStops().contains(stop.kind())) {
                invalid.add("An unsupported stop kind was proposed.");
                continue;
            }
            covered.add(stop.kind());
            ResearchFindings.PlaceFinding fact = research.place(stop.placeId());
            if (fact == null) {
                invalid.add("A stop uses a venue that was not researched.");
                continue;
            }
            Place place = context.tools().place(stop.placeId());
            if (fact.sourceIds().stream()
                    .anyMatch(id -> !context.tools().hasSource(stop.placeId(), id))) {
                invalid.add("A stop has an unknown source.");
                continue;
            }
            var route = context.tools().route(previous, place.id());
            List<String> stopNotes = new ArrayList<>(fact.notes());
            if (stop.startsAt() == null || stop.endsAt() == null)
                invalid.add("Every stop needs a start and end time.");
            else {
                if (!stop.endsAt().isAfter(stop.startsAt())
                        || stop.startsAt().isBefore(brief.startTime())
                        || stop.endsAt().isAfter(brief.endTime())
                        || stop.startsAt().isBefore(previousEnd))
                    invalid.add("Stop times must be chronological and inside the outing window.");
                if (route.driveMinutes() != null
                        && Duration.between(previousEnd, stop.startsAt()).toMinutes()
                                < route.driveMinutes() + PARKING_MINUTES) {
                    long shortfall =
                            route.driveMinutes()
                                    + 10L
                                    - Duration.between(previousEnd, stop.startsAt()).toMinutes();
                    var earliest =
                            brief.date()
                                    .atTime(previousEnd)
                                    .plusMinutes(route.driveMinutes() + (long) PARKING_MINUTES);
                    invalid.add(
                            "Travel, parking and walking do not fit on leg "
                                    + previous
                                    + " -> "
                                    + place.id()
                                    + " ("
                                    + place.name()
                                    + "): departure "
                                    + previousEnd
                                    + " + trusted drive "
                                    + route.driveMinutes()
                                    + " minutes + "
                                    + PARKING_MINUTES
                                    + "-minute buffer = earliest "
                                    + earliest
                                    + "; requested start "
                                    + stop.startsAt()
                                    + "; shortfall "
                                    + shortfall
                                    + " minutes.");
                }
                var window = context.brief().mealWindows().get(stop.kind());
                if (window != null && !window.contains(stop.startsAt(), stop.endsAt()))
                    invalid.add(
                            "The "
                                    + stop.kind()
                                    + " stop at "
                                    + stop.startsAt()
                                    + "-"
                                    + stop.endsAt()
                                    + " falls outside its "
                                    + window
                                    + " service window.");
            }
            if (route.driveMinutes() == null)
                questions.add("Driving time to " + place.name() + " is unavailable.");
            String mappedHours = place.tags().getOrDefault("opening_hours", "");
            if (fact.hoursStatus().equals("closed")
                    || List.of("closed", "off")
                            .contains(mappedHours.toLowerCase(java.util.Locale.ROOT)))
                invalid.add(place.name() + " is reported closed during this outing.");
            boolean fits =
                    stop.startsAt() != null
                            && stop.endsAt() != null
                            && fact.operatingWindows().stream()
                                    .anyMatch(
                                            w ->
                                                    !stop.startsAt().isBefore(w.opensAt())
                                                            && !stop.endsAt()
                                                                    .isAfter(w.closesAt()));
            if (!fits && !fact.operatingWindows().isEmpty())
                invalid.add(
                        place.name()
                                + " is open "
                                + fact.operatingWindows().stream()
                                        .map(w -> w.opensAt() + "-" + w.closesAt())
                                        .collect(java.util.stream.Collectors.joining(", "))
                                + ", so the stop at "
                                + stop.startsAt()
                                + "-"
                                + stop.endsAt()
                                + " must move inside those hours.");
            else if (!fits) questions.add("Operating hours for " + place.name() + " are unknown.");
            boolean breakfastTag = "yes".equalsIgnoreCase(place.tags().get("breakfast"));
            boolean meal = !stop.kind().equals("sight");
            boolean service =
                    fact.supportedMeals().contains(stop.kind())
                            || (stop.kind().equals("breakfast") && breakfastTag);
            String mealBasis =
                    !meal
                            ? "not_applicable"
                            : stop.kind().equals("breakfast") && breakfastTag
                                    ? "mapped_breakfast_tag_unverified"
                                    : service ? "attributed_research_claim" : "unknown";
            if (meal && "no".equalsIgnoreCase(place.tags().get(stop.kind())))
                invalid.add(place.name() + " is mapped as not serving " + stop.kind() + ".");
            else if (meal && !service)
                questions.add(
                        "Meal service for " + stop.kind() + " at " + place.name() + " is unknown.");
            if (fact.suitability().equals("incompatible")
                    || (brief.stepFreeAccess()
                            && (fact.access().equals("incompatible")
                                    || "no".equalsIgnoreCase(place.tags().get("wheelchair")))))
                invalid.add(place.name() + " is incompatible with the outing requirements.");
            else if (brief.stepFreeAccess()
                    && !fact.access().equals("supported")
                    && !"yes".equalsIgnoreCase(place.tags().get("wheelchair")))
                questions.add("Step-free access at " + place.name() + " needs confirmation.");
            String fit =
                    fact.cuisines().stream().anyMatch(brief.cuisineDislikes()::contains)
                            ? "disliked"
                            : fact.cuisines().stream().anyMatch(brief.cuisineLikes()::contains)
                                    ? "liked"
                                    : "unknown";
            stops.add(
                    new Itinerary.Stop(
                            stop.kind(),
                            place.id(),
                            place.name(),
                            stop.startsAt(),
                            stop.endsAt(),
                            route.driveMinutes(),
                            route.basis(),
                            place.sourceUrl(),
                            fact.cuisines(),
                            fit,
                            mealBasis,
                            fits ? "attributed_operating_window_unverified" : "unknown",
                            fact.sourceIds(),
                            List.copyOf(stopNotes)));
            previous = place.id();
            if (stop.endsAt() != null) previousEnd = stop.endsAt();
        }
        for (String kind : brief.requestedStops())
            if (!covered.contains(kind)) invalid.add("Missing requested " + kind + " stop.");
        if (context.privateRestrictionCheck())
            questions.add(
                    "Check your private dietary or allergy needs directly with the venues before"
                            + " relying on a meal plan.");
        notes.add(
                "Map and page interpretations are unverified; check changes before visiting."
                        + " Driving estimates exclude traffic delays.");
        List<String> blockers =
                java.util.stream.Stream.concat(invalid.stream(), questions.stream())
                        .distinct()
                        .toList();
        String status =
                !invalid.isEmpty() ? "invalid" : !questions.isEmpty() ? "unresolved" : "usable";
        String title =
                stops.isEmpty()
                        ? "Incomplete outing"
                        : String.join(
                                " → ",
                                stops.stream().map(Itinerary.Stop::name).distinct().toList());
        return new Itinerary.Alternative(
                draft.id(),
                title,
                status,
                List.copyOf(stops),
                draft.reason(),
                blockers,
                notes.stream().distinct().toList());
    }
}
