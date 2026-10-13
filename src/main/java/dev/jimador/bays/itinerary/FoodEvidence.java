package dev.jimador.bays.itinerary;

import dev.jimador.bays.provider.GuidePlaces;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Bounded, attributed food clues; none of these establish a current menu or dietary safety. */
final class FoodEvidence {
    record Assessment(String cuisine, String preference, String service, String timeFit) {}

    static Assessment assess(String slot, GuidePlaces.Option place,
            BayesAgent.ResearchedPointOfInterest research, ItineraryRequest request, OpeningHours.Fit hours) {
        Set<String> mapped = labels(place.osmTags().get("cuisine"));
        Set<String> researched = research == null || research.researchLinks().isEmpty()
                ? Set.of() : labels(research.researchCuisine());
        boolean conflict = !mapped.isEmpty() && !researched.isEmpty()
                && mapped.stream().noneMatch(researched::contains);
        Set<String> all = java.util.stream.Stream.concat(mapped.stream(), researched.stream())
                .collect(Collectors.toSet());
        String cuisine = mapped.isEmpty() && researched.isEmpty() ? "unknown"
                : (conflict ? "conflicting; " : "") + "map=" + display(mapped)
                        + "; research=" + display(researched) + " (unverified)";
        String preference = all.stream().anyMatch(request.dislikedCuisines()::contains) ? "disliked"
                : conflict ? "conflicting"
                : all.stream().anyMatch(request.likedCuisines()::contains) ? "liked"
                : all.isEmpty() ? "unknown" : "neutral";

        boolean mappedBreakfast = mapped.contains("breakfast");
        boolean mappedCafe = mapped.contains("cafe") || mapped.contains("coffee shop")
                || "cafe".equals(place.osmTags().get("amenity"));
        String researchService = research == null || research.researchLinks().isEmpty() ? "unknown"
                : research.researchBreakfastService();
        if (researchService == null || !List.of("full_breakfast", "coffee_only", "none", "unknown")
                .contains(researchService)) researchService = "unknown";
        boolean serviceConflict = (mappedBreakfast && List.of("coffee_only", "none").contains(researchService))
                || (mappedCafe && researchService.equals("none"));
        String service = switch (slot) {
            case "breakfast" -> serviceConflict ? "conflicting"
                    : mappedBreakfast ? "map_breakfast_tag"
                    : researchService.equals("full_breakfast") ? "research_full_breakfast_claim"
                    : mappedCafe ? "cafe_or_coffee_tag_full_breakfast_unknown"
                    : researchService.equals("coffee_only") ? "research_coffee_only_full_breakfast_unknown"
                    : "full_breakfast_unknown";
            case "coffee" -> serviceConflict ? "conflicting"
                    : mappedCafe || mappedBreakfast ? "map_cafe_or_breakfast_tag"
                    : List.of("full_breakfast", "coffee_only").contains(researchService)
                    ? "research_coffee_claim" : "coffee_service_unknown";
            case "lunch", "dinner" -> "restaurant_candidate_meal_service_unverified";
            default -> "unknown";
        };
        String timeFit = hours == OpeningHours.Fit.TAGGED_CLOSED ? "tagged_closed"
                : service.equals("conflicting") ? "service_conflicting"
                : (hours == OpeningHours.Fit.TAGGED_OPEN ? "tagged_open_" : "hours_unknown_")
                        + (service.equals("map_breakfast_tag") || service.equals("research_full_breakfast_claim")
                            || service.equals("map_cafe_or_breakfast_tag") || service.equals("research_coffee_claim")
                            ? "service_supported_unverified" : "service_unverified");
        return new Assessment(cuisine, preference, service, timeFit);
    }

    private static Set<String> labels(String raw) {
        if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("unknown")) return Set.of();
        return Arrays.stream(raw.toLowerCase(Locale.ROOT).split("[;,]"))
                .map(s -> s.trim().replace('_', ' ').replaceAll("\\s+", " "))
                .filter(s -> s.matches("[\\p{L}][\\p{L} '-]{0,39}"))
                .limit(8).collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }
    private static String display(Set<String> labels) { return labels.isEmpty() ? "unknown" : String.join(", ", labels); }
    private FoodEvidence() {}
}
