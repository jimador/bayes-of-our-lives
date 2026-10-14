package dev.jimador.bayes.itinerary;

import dev.jimador.bayes.provider.GuidePlaces;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Small, attributed cuisine vocabulary. A name clue is an inference, never a menu claim. */
public final class CuisineEvidence {
    private CuisineEvidence() {}

    public static String normalize(String raw) {
        String value = raw.trim().toLowerCase(Locale.ROOT).replace('_', ' ').replaceAll("\\s+", " ");
        return switch (value) {
            case "sea food", "fish", "fish and chips" -> "seafood";
            case "cubano", "cubana" -> "cuban";
            case "italiano", "italiana" -> "italian";
            default -> value;
        };
    }

    public static Set<String> labels(String raw) {
        if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("unknown")) return Set.of();
        return Arrays.stream(raw.split("[;,]"))
                .map(CuisineEvidence::normalize)
                .filter(s -> s.matches("[a-z][a-z '-]{0,39}"))
                .limit(8).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static Set<String> nameClues(String name) {
        String words = " " + name.toLowerCase(Locale.ROOT).replaceAll("[^a-z]+", " ").replaceAll("\\s+", " ").trim() + " ";
        Set<String> found = new LinkedHashSet<>();
        if (words.contains(" seafood ") || words.contains(" raw bar ") || words.contains(" fish market ")) found.add("seafood");
        if (words.contains(" cuban ") || words.contains(" cubano ") || words.contains(" cubana ")) found.add("cuban");
        if (words.contains(" italian ") || words.contains(" italiano ") || words.contains(" italiana ")) found.add("italian");
        if (words.contains(" sushi ")) found.add("sushi");
        if (words.contains(" pizza ") || words.contains(" pizzeria ")) found.add("pizza");
        // "Latin" and "Latin American" are too broad to establish Cuban cuisine.
        return found;
    }

    public static Set<String> known(GuidePlaces.Option place) {
        Set<String> found = new LinkedHashSet<>(labels(place.osmTags().get("cuisine")));
        found.addAll(nameClues(place.name()));
        return found;
    }

    public static String preference(Set<String> known, ItineraryRequest request) {
        if (known.stream().anyMatch(request.dislikedCuisines()::contains)) return "disliked";
        if (known.stream().anyMatch(request.likedCuisines()::contains)) return "liked";
        return known.isEmpty() ? "unknown" : "neutral";
    }

    public static int score(GuidePlaces.Option place, ItineraryRequest request, boolean meal) {
        String fit = preference(known(place), request);
        int value = switch (fit) {
            case "liked" -> 50; case "disliked" -> -100; case "neutral" -> 0; default -> -5;
        };
        if (fit.equals("liked") && preference(labels(place.osmTags().get("cuisine")), request).equals("liked"))
            value += 20; // A mapped cuisine tag is stronger than a clue in the venue name.
        if (meal && request.date() != null) {
            LocalTime at = request.dinner() && !request.lunch() ? LocalTime.of(18, 0) : LocalTime.of(12, 0);
            var hours = OpeningHours.fit(place.osmTags().get("opening_hours"), request.date(), at, at.plusMinutes(50));
            if (hours == OpeningHours.Fit.TAGGED_CLOSED) value -= 150;
            else if (hours == OpeningHours.Fit.TAGGED_OPEN) value += 8;
        }
        if (place.oneWayDriveMinutes() != null) value -= Math.min(30, place.oneWayDriveMinutes()) / 3;
        return value;
    }

    public static List<GuidePlaces.Option> ranked(List<GuidePlaces.Option> options, ItineraryRequest request,
            boolean meal) {
        List<GuidePlaces.Option> ordered = options.stream().distinct()
                .sorted(java.util.Comparator.comparingInt((GuidePlaces.Option o) -> score(o, request, meal))
                        .reversed().thenComparing(GuidePlaces.Option::name)).toList();
        boolean hasOther = ordered.stream().anyMatch(o -> !preference(known(o), request).equals("disliked")
                && score(o, request, meal) > -100);
        return hasOther ? ordered.stream().filter(o -> !preference(known(o), request).equals("disliked")).toList()
                : ordered;
    }

    public static String display(Set<String> values) { return values.isEmpty() ? "unknown" : String.join(", ", values); }
}
