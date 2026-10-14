package dev.jimador.bayes.itinerary;

import java.time.LocalDate;
import dev.jimador.bayes.provider.Place;
import dev.jimador.bayes.provider.PageEvidence;
import java.time.LocalTime;
import java.util.List;

/** Model interpretations of attributed evidence; these are never verified venue facts. */
public record ResearchFindings(List<PlaceFinding> places) {
    public record PlaceView(Place place, PlaceFinding interpretation, List<PageEvidence> pages) {}
    public record Window(LocalDate date, LocalTime opensAt, LocalTime closesAt) {}
    public record PlaceFinding(String placeId, String summary, List<String> cuisines, List<String> supportedMeals,
                               String hoursStatus, List<Window> operatingWindows, String access,
                               String suitability, List<String> sourceIds, List<String> notes) {}
    public PlaceFinding place(String id) {
        return places.stream().filter(p -> p.placeId().equals(id)).findFirst().orElse(null);
    }
}
