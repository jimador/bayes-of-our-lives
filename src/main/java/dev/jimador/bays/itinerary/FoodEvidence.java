package dev.jimador.bays.itinerary;

import dev.jimador.bays.provider.GuidePlaces;
import java.util.List;
import java.util.Set;

/** Bounded, attributed food clues; none of these establish a current menu or dietary safety. */
final class FoodEvidence {
    record Assessment(String cuisine, String preference, String service, String timeFit) {}

    static Assessment assess(String slot, GuidePlaces.Option place,
            BayesAgent.ResearchedPointOfInterest research, ItineraryRequest request, OpeningHours.Fit hours) {
        Set<String> mapped = CuisineEvidence.labels(place.osmTags().get("cuisine"));
        Set<String> inferred = CuisineEvidence.nameClues(place.name());
        Set<String> researched = research == null || research.researchLinks().isEmpty()
                ? Set.of() : CuisineEvidence.labels(research.researchCuisine());
        boolean conflict = !mapped.isEmpty() && !researched.isEmpty()
                && mapped.stream().noneMatch(researched::contains);
        Set<String> all = new java.util.LinkedHashSet<>(mapped);
        all.addAll(inferred);
        all.addAll(researched);
        String cuisine = all.isEmpty() ? "unknown"
                : (conflict ? "conflicting; " : "") + "map=" + CuisineEvidence.display(mapped)
                        + "; name=" + CuisineEvidence.display(inferred) + " (inferred, unverified)"
                        + "; research=" + CuisineEvidence.display(researched) + " (unverified)";
        String preference = CuisineEvidence.preference(all, request);
        if (!preference.equals("disliked") && conflict) preference = "conflicting";

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

    private FoodEvidence() {}
}
