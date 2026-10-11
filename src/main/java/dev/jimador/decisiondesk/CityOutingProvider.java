package dev.jimador.decisiondesk;

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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** A bounded, user-triggered museum lookup for an afternoon in a geocodable city. */
@Component
public final class CityOutingProvider {
    public record Place(String name, double latitude, double longitude, String url) {}
    public record Option(Place place, Double temperatureC, Double precipitationProbability,
                         Double windKt, Instant sampleUtc, String weatherUrl, Integer baseDriveMinutes) {}
    public record Search(String resolvedCity, String geocodingUrl, Instant startUtc,
                         List<Option> options, Instant retrievedUtc) {}
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String nominatimBaseUrl;
    private static final String AGENT = "EmbabelDecisionDesk/0.1 (github.com/jimador/embabel-decision-desk)";

    public CityOutingProvider(@Value("${demo.nominatim-base-url:https://nominatim.openstreetmap.org}") String base) {
        this.nominatimBaseUrl = base.replaceAll("/+$", "");
    }

    public Search search(String city, String origin, Instant startUtc) {
        try {
            JsonNode destination = geocode(city);
            if (startUtc == null) {
                ZoneId zone = ZoneId.of(destination.path("timezone").asText("UTC"));
                ZonedDateTime localStart = ZonedDateTime.now(zone).withHour(14).withMinute(0).withSecond(0).withNano(0);
                if (!localStart.toInstant().isAfter(Instant.now())) localStart = localStart.plusDays(1);
                startUtc = localStart.toInstant();
            }
            double lat = destination.path("latitude").asDouble(), lon = destination.path("longitude").asDouble();
            JsonNode originNode = origin == null || origin.isBlank() ? destination
                    : origin.equalsIgnoreCase(city) ? destination : geocode(origin);
            String geoUrl = geocodeUrl(city);
            String url = nominatimBaseUrl + "/search?q=" + enc("museum, " + city) + "&format=jsonv2&limit=3";
            List<Option> options = new ArrayList<>();
            for (JsonNode point : get(url)) {
                if (!point.hasNonNull("lat") || !point.hasNonNull("lon")) continue;
                double pLat = point.path("lat").asDouble(), pLon = point.path("lon").asDouble();
                if (distanceKm(lat, lon, pLat, pLon) > 25) continue;
                String type = point.path("osm_type").asText(), id = point.path("osm_id").asText();
                if (!List.of("node", "way", "relation").contains(type)) continue;
                String name = point.path("name").asText("");
                if (name.isBlank()) name = point.path("display_name").asText("Mapped museum").split(",")[0];
                Place place = new Place(name, pLat, pLon, "https://www.openstreetmap.org/" + type + "/" + id);
                String weatherUrl = "https://api.open-meteo.com/v1/forecast?latitude=" + pLat
                        + "&longitude=" + pLon + "&timezone=UTC&forecast_days=7"
                        + "&hourly=temperature_2m,precipitation_probability,wind_speed_10m&wind_speed_unit=kn";
                JsonNode weather = get(weatherUrl).path("hourly");
                int index = nearest(weather.path("time"), startUtc);
                Integer drive = route(originNode, place);
                options.add(new Option(place, number(weather.path("temperature_2m"), index),
                        number(weather.path("precipitation_probability"), index),
                        number(weather.path("wind_speed_10m"), index),
                        index < 0 ? null : LocalDateTime.parse(weather.path("time").get(index).asText()).toInstant(ZoneOffset.UTC),
                        weatherUrl, drive));
            }
            String resolved = destination.path("name").asText() + ", " + destination.path("country").asText();
            return new Search(resolved, geoUrl, startUtc, List.copyOf(options), Instant.now());
        } catch (IOException | InterruptedException failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IllegalStateException("City data provider unavailable: " + failure.getClass().getSimpleName(), failure);
        }
    }

    private JsonNode geocode(String name) throws IOException, InterruptedException {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("A city is required");
        JsonNode results = get(geocodeUrl(name)).path("results");
        if (!results.isArray() || results.isEmpty()) throw new IllegalArgumentException("City was not geocoded: " + name);
        return results.get(0);
    }
    private static String geocodeUrl(String name) {
        return "https://geocoding-api.open-meteo.com/v1/search?name=" + enc(name) + "&count=1&language=en&format=json";
    }
    private Integer route(JsonNode origin, Place place) {
        try {
            String url = "http://router.project-osrm.org/route/v1/driving/" + origin.path("longitude").asDouble()
                    + "," + origin.path("latitude").asDouble() + ";" + place.longitude() + "," + place.latitude()
                    + "?overview=false";
            JsonNode data = get(url);
            JsonNode seconds = data.path("routes").path(0).path("duration");
            return data.path("code").asText().equals("Ok") && seconds.isNumber()
                    ? (int) Math.ceil(seconds.asDouble() / 60.0) : null;
        } catch (IOException | InterruptedException failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            return null;
        }
    }
    private JsonNode get(String url) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(25))
                .header("User-Agent", AGENT).GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IOException(URI.create(url).getHost() + " HTTP " + response.statusCode());
        return mapper.readTree(response.body());
    }
    private static int nearest(JsonNode times, Instant at) {
        if (!times.isArray()) return -1;
        int bestIndex = -1; long best = Long.MAX_VALUE;
        for (int i = 0; i < times.size(); i++) {
            long seconds = Math.abs(Duration.between(LocalDateTime.parse(times.get(i).asText()).toInstant(ZoneOffset.UTC), at).toSeconds());
            if (seconds < best) { best = seconds; bestIndex = i; }
        }
        return best <= 3600 ? bestIndex : -1;
    }
    private static Double number(JsonNode values, int i) {
        return i < 0 || !values.isArray() || i >= values.size() || !values.get(i).isNumber()
                ? null : values.get(i).asDouble();
    }
    private static String enc(String text) { return URLEncoder.encode(text, StandardCharsets.UTF_8); }
    private static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double p1 = Math.toRadians(lat1), p2 = Math.toRadians(lat2);
        double dp = p2 - p1, dl = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2)
                + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
