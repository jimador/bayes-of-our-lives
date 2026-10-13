package dev.jimador.bays.itinerary;

import dev.jimador.bays.provider.GuidePlaces;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Tripper's idea/research stages adapted to bounded public OSM place and base-route lookups. */
@Component
public class ItineraryResearch {
    public record Findings(String city, Instant retrievedAt, List<GuidePlaces.Option> sights,
                           List<GuidePlaces.Option> cafes, List<GuidePlaces.Option> restaurants,
                           List<String> sources, List<String> researchGaps, String timezone) {
        public Findings(String city, Instant retrievedAt, List<GuidePlaces.Option> sights,
                List<GuidePlaces.Option> cafes, List<GuidePlaces.Option> restaurants,
                List<String> sources, List<String> researchGaps) {
            this(city, retrievedAt, sights, cafes, restaurants, sources, researchGaps, null);
        }
    }
    private final GuidePlaces places;
    public ItineraryResearch(GuidePlaces places) { this.places = places; }

    public Findings findAndResearch(ItineraryRequest request) {
        List<GuidePlaces.Option> sights = new ArrayList<>(), cafes = new ArrayList<>(), restaurants = new ArrayList<>();
        List<String> sources = new ArrayList<>(), gaps = new ArrayList<>();
        String city = request.city();
        var museum = lookup(request, "museum", sights, sources, gaps);
        if (museum != null) city = museum.city();
        GuidePlaces.Search park = null, breakfast = null, cafe = null, restaurant = null;
        if (request.safeInterests().toLowerCase(java.util.Locale.ROOT).matches(".*(park|walk|nature|outdoor).*"))
            park = lookup(request, "park", sights, sources, gaps);
        if (request.breakfast()) breakfast = lookup(request, "breakfast", cafes, sources, gaps);
        if (request.breakfast() || request.coffee()) cafe = lookup(request, "cafe", cafes, sources, gaps);
        if (request.lunch() || request.dinner()) restaurant = lookup(request, "restaurant", restaurants, sources, gaps);
        String timezone = java.util.stream.Stream.of(museum, park, breakfast, cafe, restaurant)
                .filter(java.util.Objects::nonNull).map(GuidePlaces.Search::timezone)
                .filter(s -> s != null && !s.isBlank()).findFirst().orElse(null);
        validateDestinationTime(request, timezone, gaps);
        return new Findings(city, Instant.now(), List.copyOf(sights), List.copyOf(cafes),
                CuisineEvidence.ranked(restaurants, request, true).stream().limit(5).toList(),
                List.copyOf(sources), List.copyOf(gaps), timezone);
    }

    static void validateDestinationTime(ItineraryRequest request, String timezone, List<String> gaps) {
        if (timezone == null || timezone.isBlank()) {
            gaps.add("Confirm the destination's timezone before relying on the date and times.");
            return;
        }
        final ZoneId zone;
        try { zone = ZoneId.of(timezone); }
        catch (RuntimeException invalidZone) {
            gaps.add("Confirm the destination's timezone before relying on the date and times.");
            return;
        }
        LocalDate today = LocalDate.now(zone);
        if (request.date().isBefore(today) || (request.date().equals(today)
                && !request.endTime().isAfter(java.time.LocalTime.now(zone))))
            throw new IllegalArgumentException("Choose a future outing window in the destination's local timezone.");
        ZonedDateTime start = request.date().atTime(request.startTime()).atZone(zone);
        ZonedDateTime end = request.date().atTime(request.endTime()).atZone(zone);
        if (!start.getOffset().equals(end.getOffset())
                || zone.getRules().getValidOffsets(request.date().atTime(request.startTime())).size() != 1
                || zone.getRules().getValidOffsets(request.date().atTime(request.endTime())).size() != 1)
            gaps.add("Confirm the local time window across a daylight-saving change in " + timezone + ".");
    }

    private GuidePlaces.Search lookup(ItineraryRequest request, String category,
            List<GuidePlaces.Option> into, List<String> sources, List<String> gaps) {
        try {
            var found = category.equals("restaurant") && !request.likedCuisines().isEmpty()
                    ? places.searchCategory(request.city(), request.startingLocation(), category, request.likedCuisines())
                    : places.searchCategory(request.city(), request.startingLocation(), category);
            into.addAll(found.options());
            sources.add(found.source());
            if (found.options().isEmpty()) gaps.add("No mapped " + category + " lead was returned for this city.");
            return found;
        } catch (RuntimeException failure) {
            // A failed public category lookup cannot turn into invented options.
            gaps.add("The " + category + " place lookup could not finish.");
            return null;
        }
    }
}
