package dev.jimador.bays;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Three fixed research leads or a bounded user-triggered public OSM city search. */
@Component
public class GuidePlaces {
    public record Option(String name, String sourceUrl, String setting, String interestTag,
                         String ambience, Integer oneWayDriveMinutes, int minimumVisitMinutes) {}
    public record Search(String city, String source, Instant retrievedUtc, List<Option> options) {}
    private static final String USER_AGENT = "Bays/0.1 (github.com/jimador/bayes-of-our-lives)";
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String nominatim;

    public GuidePlaces(@Value("${bays.nominatim-base-url:https://nominatim.openstreetmap.org}") String base) {
        this.nominatim = base.replaceAll("/+$", "");
    }

    public Search search(String mode, String city, String intent) {
        return search(mode, city, intent, "lunch");
    }

    public Search search(String mode, String city, String intent, String mealPeriod) {
        if (!List.of("sights", "meal").contains(intent)) throw new IllegalArgumentException("intent must be sights or meal");
        if (!List.of("breakfast", "lunch", "dinner").contains(mealPeriod))
            throw new IllegalArgumentException("meal period must be breakfast, lunch, or dinner");
        return switch (mode) {
            case "fixed" -> fixed(intent, mealPeriod);
            case "live" -> live(city, intent, mealPeriod);
            default -> throw new IllegalArgumentException("bays.mode must be fixed or live");
        };
    }

    static Search fixed(String intent) {
        return fixed(intent, "lunch");
    }
    static Search fixed(String intent, String mealPeriod) {
        List<Option> options = intent.equals("sights") ? List.of(
                new Option("Pocket Art Gallery", "synthetic://bays/art", "indoor", "art", "quiet", 8, 45),
                new Option("Hands-On Science Hall", "synthetic://bays/science", "indoor", "science", "lively", 18, 45),
                new Option("Riverside Garden", "synthetic://bays/garden", "outdoor", "nature", "quiet", 12, 45)) : List.of(
                new Option("Little Bakery", "synthetic://bays/bakery", "indoor", "bakery", "quiet", 9, 40),
                new Option("Bright Noodle Counter", "synthetic://bays/noodles", "indoor", "spicy", "lively", 14, 40),
                new Option("Market Lunch Room", "synthetic://bays/market", "indoor", "market", "lively", 7, 40));
        if (intent.equals("meal")) options = switch (mealPeriod) {
            case "breakfast" -> List.of(options.getFirst());
            case "dinner" -> List.of(options.get(1));
            default -> options;
        };
        return new Search("Harborview (fictional)", "synthetic Bays catalog", Instant.now(), options);
    }

    private Search live(String city, String intent, String mealPeriod) {
        try {
            if (city == null || city.isBlank()) throw new IllegalArgumentException("A city is required");
            String geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=" + enc(city)
                    + "&count=1&language=en&format=json";
            JsonNode matches = get(geoUrl).path("results");
            if (!matches.isArray() || matches.isEmpty()) throw new IllegalArgumentException("City not geocoded: " + city);
            JsonNode center = matches.get(0);
            double lat = center.path("latitude").asDouble(), lon = center.path("longitude").asDouble();
            String placeUrl = nominatim + "/search?q=" + enc((intent.equals("meal")
                    ? (mealPeriod.equals("breakfast") ? "cafe, " : "restaurant, ") : "museum, ") + city)
                    + "&format=jsonv2&limit=3";
            List<Option> options = new ArrayList<>();
            for (JsonNode place : get(placeUrl)) {
                if (!place.hasNonNull("lat") || !place.hasNonNull("lon")) continue;
                double pLat = place.path("lat").asDouble(), pLon = place.path("lon").asDouble();
                if (distanceKm(lat, lon, pLat, pLon) > 25) continue;
                String osmType = place.path("osm_type").asText();
                if (!List.of("node", "way", "relation").contains(osmType)) continue;
                String name = place.path("name").asText();
                if (name.isBlank()) name = place.path("display_name").asText("Mapped place").split(",")[0];
                String source = "https://www.openstreetmap.org/" + osmType + "/" + place.path("osm_id").asText();
                Integer drive = route(lon, lat, pLon, pLat);
                // A literal word in the mapped name can signal interest, not venue quality or safety.
                // Public search results do not establish cuisine, noise, opening hours, or dietary safety.
                options.add(new Option(name, source, "unknown", nameTag(name, intent), "unknown", drive,
                        intent.equals("meal") ? 40 : 45));
            }
            return new Search(center.path("name").asText() + ", " + center.path("country").asText(),
                    geoUrl + " ; " + placeUrl, Instant.now(), List.copyOf(options));
        } catch (IOException | InterruptedException failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IllegalStateException("Public city provider unavailable: " + failure.getClass().getSimpleName(), failure);
        }
    }

    private Integer route(double lon, double lat, double pLon, double pLat) {
        String url = "http://router.project-osrm.org/route/v1/driving/" + lon + "," + lat + ";"
                + pLon + "," + pLat + "?overview=false";
        try {
            JsonNode result = get(url);
            JsonNode seconds = result.path("routes").path(0).path("duration");
            return result.path("code").asText().equals("Ok") && seconds.isNumber()
                    ? (int) Math.ceil(seconds.asDouble() / 60.0) : null;
        } catch (IOException | InterruptedException failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            return null;
        }
    }
    private JsonNode get(String url) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(25))
                .header("User-Agent", USER_AGENT).GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IOException(URI.create(url).getHost() + " HTTP " + response.statusCode());
        return mapper.readTree(response.body());
    }
    private static String enc(String text) { return URLEncoder.encode(text, StandardCharsets.UTF_8); }
    static String nameTag(String name, String intent) {
        String words = " " + name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z]+", " ") + " ";
        List<String> tags = intent.equals("sights") ? List.of("art", "science", "garden")
                : List.of("bakery", "spicy", "market");
        return tags.stream().filter(tag -> words.contains(" " + tag + " ")).findFirst().orElse("unknown");
    }
    private static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double p1 = Math.toRadians(lat1), p2 = Math.toRadians(lat2);
        double dp = p2 - p1, dl = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2)
                + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
