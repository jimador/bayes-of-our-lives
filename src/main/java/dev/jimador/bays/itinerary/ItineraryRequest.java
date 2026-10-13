package dev.jimador.bays.itinerary;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** One outing's confirmed requirements. Sensitive fields are never logged or sent to place APIs. */
public record ItineraryRequest(String city, String startingLocation, LocalDate date,
        LocalTime startTime, LocalTime endTime, boolean breakfast, boolean coffee,
        boolean earlyFoodDeclined,
        boolean lunch, boolean dinner, int partySize, int childrenCount,
        String interests, String pace, boolean stepFreeAccess, String dietaryOrAllergyNeeds,
        String engine, boolean publicMapConsent, boolean remoteModelConsent,
        String profileId, Boolean rememberPreferences, Boolean useRememberedPreferences,
        String cuisineLikes, String cuisineDislikes) {
    // Keep food preferences separate from free-text medical needs, which never leave this app.
    private static final java.util.Set<String> CUISINES = java.util.Set.of(
            "american", "argentinian", "barbecue", "brazilian", "british", "caribbean",
            "chinese", "colombian", "cuban", "ethiopian", "filipino", "french", "german",
            "greek", "indian", "indonesian", "italian", "jamaican", "japanese", "korean",
            "lebanese", "malaysian", "mediterranean", "mexican", "middle eastern", "moroccan",
            "nepali", "pakistani", "persian", "peruvian", "pizza", "portuguese", "seafood",
            "southern", "spanish", "sushi", "thai", "turkish", "vietnamese");

    @com.fasterxml.jackson.annotation.JsonCreator public ItineraryRequest {
        rememberPreferences = Boolean.TRUE.equals(rememberPreferences);
        useRememberedPreferences = Boolean.TRUE.equals(useRememberedPreferences);
        cuisineLikes = normalizeCuisinePreferences(cuisineLikes);
        cuisineDislikes = normalizeCuisinePreferences(cuisineDislikes);
    }

    public ItineraryRequest(String city, String startingLocation, LocalDate date, LocalTime startTime,
            LocalTime endTime, boolean breakfast, boolean coffee, boolean earlyFoodDeclined,
            boolean lunch, boolean dinner, int partySize, int childrenCount, String interests,
            String pace, boolean stepFreeAccess, String dietaryOrAllergyNeeds, String engine,
            boolean publicMapConsent, boolean remoteModelConsent) {
        this(city, startingLocation, date, startTime, endTime, breakfast, coffee, earlyFoodDeclined,
                lunch, dinner, partySize, childrenCount, interests, pace, stepFreeAccess,
                dietaryOrAllergyNeeds, engine, publicMapConsent, remoteModelConsent, null, false, false, "", "");
    }

    public ItineraryRequest(String city, String startingLocation, LocalDate date, LocalTime startTime,
            LocalTime endTime, boolean breakfast, boolean coffee, boolean earlyFoodDeclined,
            boolean lunch, boolean dinner, int partySize, int childrenCount, String interests,
            String pace, boolean stepFreeAccess, String dietaryOrAllergyNeeds, String engine,
            boolean publicMapConsent, boolean remoteModelConsent, String profileId,
            Boolean rememberPreferences, Boolean useRememberedPreferences) {
        this(city, startingLocation, date, startTime, endTime, breakfast, coffee, earlyFoodDeclined,
                lunch, dinner, partySize, childrenCount, interests, pace, stepFreeAccess,
                dietaryOrAllergyNeeds, engine, publicMapConsent, remoteModelConsent, profileId,
                rememberPreferences, useRememberedPreferences, "", "");
    }

    public void validateShape() {
        if (city == null || city.isBlank() || city.length() > 100 || hasControl(city))
            throw new IllegalArgumentException("Enter a city of up to 100 characters");
        if (startingLocation == null || startingLocation.isBlank() || startingLocation.length() > 120
                || hasControl(startingLocation))
            throw new IllegalArgumentException("Enter a starting location of up to 120 characters");
        if (interests != null && (interests.length() > 100 || hasControl(interests)))
            throw new IllegalArgumentException("Interests must be at most 100 characters");
        if (dietaryOrAllergyNeeds != null && (dietaryOrAllergyNeeds.length() > 120 || hasControl(dietaryOrAllergyNeeds)))
            throw new IllegalArgumentException("Dietary notes must be at most 120 characters");
        if (likedCuisines().stream().anyMatch(dislikedCuisines()::contains))
            throw new IllegalArgumentException("A cuisine cannot be both liked and disliked");
        if (partySize < 1 || partySize > 8 || childrenCount < 0 || childrenCount >= partySize)
            throw new IllegalArgumentException("Enter a party of 1 to 8, with fewer children than travelers");
        if ((!useRememberedPreferences || (pace != null && !pace.isBlank()))
                && (pace == null || !List.of("relaxed", "steady", "busy").contains(pace)))
            throw new IllegalArgumentException("Choose relaxed, steady, or busy pace");
        if ((rememberPreferences || useRememberedPreferences)
                && (profileId == null || !profileId.matches(
                        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")))
            throw new IllegalArgumentException("A private profile ID is required to remember preferences");
        if (engine == null || !List.of("local", "jev", "openai").contains(engine))
            throw new IllegalArgumentException("Choose a listed decision engine");
        if (!publicMapConsent)
            throw new IllegalArgumentException("Confirm sharing the city and start location with public map services");
        if (List.of("jev", "openai").contains(engine) && !remoteModelConsent)
            throw new IllegalArgumentException("Confirm remote model sharing before selecting Jev or GPT-4.1");
    }

    /** Missing requirements are returned as targeted questions before any provider call. */
    public List<String> questions() {
        List<String> missing = new ArrayList<>();
        if (date == null || startTime == null || endTime == null)
            missing.add("What date and start/end times should the itinerary cover?");
        else {
            // The destination's timezone is resolved during place lookup. Only reject dates
            // already past in every timezone here; the exact local-day check follows geocoding.
            if (date.isBefore(LocalDate.now(java.time.ZoneOffset.UTC).minusDays(1)))
                missing.add("Choose a current or future date in the destination city.");
            if (!endTime.isAfter(startTime) || java.time.Duration.between(startTime, endTime).toHours() > 12)
                missing.add("Choose an end time after the start, within a 12-hour outing.");
            if (startTime.isBefore(LocalTime.of(9, 0)) && !breakfast && !coffee && !earlyFoodDeclined)
                missing.add("With an early start, would you like breakfast or coffee, or neither? Confirm one before planning.");
            if (lunch && (endTime.isBefore(LocalTime.of(11, 0)) || startTime.isAfter(LocalTime.of(14, 30))))
                missing.add("Lunch falls outside this time window. Change the window or lunch requirement.");
            if (dinner && (endTime.isBefore(LocalTime.of(17, 0)) || startTime.isAfter(LocalTime.of(21, 0))))
                missing.add("Dinner falls outside this time window. Change the window or dinner requirement.");
        }
        if (!useRememberedPreferences && (interests == null || interests.isBlank()))
            missing.add("What would you like to see or do? Add an interest before planning.");
        return List.copyOf(missing);
    }

    public String safeInterests() { return interests == null ? "" : interests.trim(); }
    public List<String> likedCuisines() { return cuisineLikes.isBlank() ? List.of() : List.of(cuisineLikes.split(", ")); }
    public List<String> dislikedCuisines() { return cuisineDislikes.isBlank() ? List.of() : List.of(cuisineDislikes.split(", ")); }
    public boolean hasDietaryNeed() {
        return dietaryOrAllergyNeeds != null && !dietaryOrAllergyNeeds.isBlank();
    }
    public ItineraryRequest resolvedPreferences(String resolvedInterests, String resolvedPace) {
        return new ItineraryRequest(city, startingLocation, date, startTime, endTime, breakfast, coffee,
                earlyFoodDeclined, lunch, dinner, partySize, childrenCount, resolvedInterests, resolvedPace,
                stepFreeAccess, dietaryOrAllergyNeeds, engine, publicMapConsent, remoteModelConsent,
                profileId, rememberPreferences, useRememberedPreferences, cuisineLikes, cuisineDislikes);
    }
    public ItineraryRequest withEngine(String selectedEngine) {
        return new ItineraryRequest(city, startingLocation, date, startTime, endTime, breakfast, coffee,
                earlyFoodDeclined, lunch, dinner, partySize, childrenCount, interests, pace,
                stepFreeAccess, dietaryOrAllergyNeeds, selectedEngine, publicMapConsent, true,
                profileId, rememberPreferences, useRememberedPreferences, cuisineLikes, cuisineDislikes);
    }
    private static String normalizeCuisinePreferences(String raw) {
        if (raw == null || raw.isBlank()) return "";
        if (raw.length() > 120 || hasControl(raw))
            throw new IllegalArgumentException("Cuisine preferences must be at most 120 characters");
        var values = new java.util.LinkedHashSet<String>();
        for (String part : raw.split("[,;]")) {
            String value = part.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ");
            if (value.isBlank()) continue;
            if (!CUISINES.contains(value))
                throw new IllegalArgumentException("Choose listed cuisines only; put dietary or allergy needs in the private dietary field");
            values.add(value);
        }
        if (values.size() > 8) throw new IllegalArgumentException("Enter at most eight cuisine preferences");
        return String.join(", ", values);
    }
    private static boolean hasControl(String text) {
        return text.chars().anyMatch(Character::isISOControl);
    }
}
