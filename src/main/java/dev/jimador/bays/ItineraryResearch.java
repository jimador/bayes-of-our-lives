package dev.jimador.bays;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Tripper's idea/research stages adapted to bounded public OSM place and base-route lookups. */
@Component
public class ItineraryResearch {
    public record Findings(String city, Instant retrievedAt, List<GuidePlaces.Option> sights,
                           List<GuidePlaces.Option> cafes, List<GuidePlaces.Option> restaurants,
                           List<String> sources, List<String> researchGaps) {}
    private final GuidePlaces places;
    public ItineraryResearch(GuidePlaces places) { this.places = places; }

    public Findings findAndResearch(ItineraryRequest request) {
        List<GuidePlaces.Option> sights = new ArrayList<>(), cafes = new ArrayList<>(), restaurants = new ArrayList<>();
        List<String> sources = new ArrayList<>(), gaps = new ArrayList<>();
        String city = request.city();
        var museum = lookup(request, "museum", sights, sources, gaps);
        if (museum != null) city = museum.city();
        if (request.safeInterests().toLowerCase(java.util.Locale.ROOT).matches(".*(park|walk|nature|outdoor).*"))
            lookup(request, "park", sights, sources, gaps);
        if (request.breakfast() || request.coffee()) lookup(request, "cafe", cafes, sources, gaps);
        if (request.lunch() || request.dinner()) lookup(request, "restaurant", restaurants, sources, gaps);
        return new Findings(city, Instant.now(), List.copyOf(sights), List.copyOf(cafes),
                List.copyOf(restaurants), List.copyOf(sources), List.copyOf(gaps));
    }

    private GuidePlaces.Search lookup(ItineraryRequest request, String category,
            List<GuidePlaces.Option> into, List<String> sources, List<String> gaps) {
        try {
            var found = places.searchCategory(request.city(), request.startingLocation(), category);
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
