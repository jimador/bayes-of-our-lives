package dev.jimador.bayes.itinerary;

import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * When each meal can be served, set under {@code bayes.meals}. The request checks, the planner's
 * prompt and the plan validator all read these, so they always agree.
 *
 * @param breakfast when a breakfast stop can start and end
 * @param lunch when a lunch stop can start and end
 * @param dinner when a dinner stop can start and end
 * @param earlyStart outings starting before this are asked about breakfast or coffee
 * @param shortestMeal a meal only fits an outing that overlaps its window by at least this many
 *     minutes
 */
@ConfigurationProperties("bayes.meals")
public record MealTimes(
        Window breakfast, Window lunch, Window dinner, LocalTime earlyStart, int shortestMeal) {

    /** A meal's service window, in the destination's local time. */
    public record Window(LocalTime from, LocalTime to) {

        boolean contains(LocalTime start, LocalTime end) {
            return !start.isBefore(from) && !end.isAfter(to);
        }

        @Override
        public String toString() {
            return from + "-" + to;
        }
    }

    /** The window for a meal kind, or null for stops that aren't meals. */
    public Window window(String kind) {
        return switch (kind) {
            case "breakfast" -> breakfast;
            case "lunch" -> lunch;
            case "dinner" -> dinner;
            default -> null;
        };
    }

    /** Whether a meal's window leaves room for it in an outing running from start to end. */
    boolean fits(String meal, LocalTime start, LocalTime end) {
        var window = window(meal);
        return !end.isBefore(window.from().plusMinutes(shortestMeal))
                && !start.isAfter(window.to().minusMinutes(shortestMeal));
    }

    /** The windows for whichever of these stops are meals, in order. */
    Map<String, Window> forStops(List<String> stops) {
        Map<String, Window> windows = new LinkedHashMap<>();
        for (String stop : stops) if (window(stop) != null) windows.put(stop, window(stop));
        return windows;
    }
}
