package dev.jimador.bays;

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
        String engine, boolean publicMapConsent, boolean remoteModelConsent) {

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
        if (partySize < 1 || partySize > 8 || childrenCount < 0 || childrenCount >= partySize)
            throw new IllegalArgumentException("Enter a party of 1 to 8, with fewer children than travelers");
        if (pace == null || !List.of("relaxed", "steady", "busy").contains(pace))
            throw new IllegalArgumentException("Choose relaxed, steady, or busy pace");
        if (engine == null || !List.of("local", "jev", "openai").contains(engine))
            throw new IllegalArgumentException("Choose a listed decision engine");
        if (!publicMapConsent)
            throw new IllegalArgumentException("Confirm sharing the city and start location with public map services");
        if (!engine.equals("local") && !remoteModelConsent)
            throw new IllegalArgumentException("Confirm remote model sharing before selecting Jev or GPT-4.1");
    }

    /** Missing requirements are returned as targeted questions before any provider call. */
    public List<String> questions() {
        List<String> missing = new ArrayList<>();
        if (date == null || startTime == null || endTime == null)
            missing.add("What date and start/end times should the itinerary cover?");
        else {
            if (date.isBefore(LocalDate.now()))
                missing.add("Choose today or a future date; past opening hours cannot establish a current plan.");
            if (!endTime.isAfter(startTime) || java.time.Duration.between(startTime, endTime).toHours() > 12)
                missing.add("Choose an end time after the start, within a 12-hour outing.");
            if (startTime.isBefore(LocalTime.of(9, 0)) && !breakfast && !coffee && !earlyFoodDeclined)
                missing.add("With an early start, would you like breakfast or coffee, or neither? Confirm one before planning.");
            if (lunch && (endTime.isBefore(LocalTime.of(11, 0)) || startTime.isAfter(LocalTime.of(14, 30))))
                missing.add("Lunch falls outside this time window. Change the window or lunch requirement.");
            if (dinner && (endTime.isBefore(LocalTime.of(17, 0)) || startTime.isAfter(LocalTime.of(21, 0))))
                missing.add("Dinner falls outside this time window. Change the window or dinner requirement.");
        }
        if (interests == null || interests.isBlank())
            missing.add("What would you like to see or do? Add an interest before planning.");
        return List.copyOf(missing);
    }

    public String safeInterests() { return interests == null ? "" : interests.trim(); }
    public boolean hasDietaryNeed() {
        return dietaryOrAllergyNeeds != null && !dietaryOrAllergyNeeds.isBlank();
    }
    private static boolean hasControl(String text) {
        return text.chars().anyMatch(Character::isISOControl);
    }
}
