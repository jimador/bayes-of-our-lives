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
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Bounded public-API lookup. OSM features are leads, not verified kayak access. */
@Component
public final class LiveCoastalProvider implements TripDataProvider {
    public record Location(String label, double latitude, double longitude, String timezone, String sourceUrl) {}
    public record Place(String name, double latitude, double longitude, String sourceUrl) {}
    public record Forecast(Double windKt, Double gustKt, Double waveFt, Double oceanCurrentKt,
                           Double seaLevelMslMeters, Instant forecastHourUtc, String weatherUrl,
                           String marineUrl, double weatherGridLatitude, double weatherGridLongitude,
                           double marineGridLatitude, double marineGridLongitude) {}
    public record LiveOption(Place place, Forecast forecast, Integer driveMinutes,
                             NoaaCoopsProvider.Context noaaContext) {}
    public record Search(Location location, List<LiveOption> options, Instant retrievedAtUtc) {}

    private static final String AGENT = "EmbabelDecisionDesk/0.1 (github.com/jimador/embabel-decision-desk)";
    private final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String nominatimBaseUrl;
    @Autowired private NoaaCoopsProvider noaa;

    public LiveCoastalProvider(@Value("${demo.nominatim-base-url:https://nominatim.openstreetmap.org}") String nominatimBaseUrl) {
        this.nominatimBaseUrl = nominatimBaseUrl.replaceAll("/+$", "");
    }

    public Search search(String destination, Instant windowStartUtc, String origin) {
        try {
            System.err.println("live-provider: geocoding destination");
            Location location = geocode(destination);
            System.err.println("live-provider: resolved " + location.label() + " " + location.latitude() + "," + location.longitude());
            Location startingLocation = origin == null || origin.isBlank() ? null
                    : origin.equalsIgnoreCase(destination) ? location : geocode(origin);
            System.err.println("live-provider: discovering mapped water access features");
            List<Place> places = places(location, destination);
            System.err.println("live-provider: found " + places.size() + " mapped leads; fetching forecasts");
            List<LiveOption> options = new ArrayList<>();
            var driveByPlace = new java.util.HashMap<String, Integer>();
            var noaaByPlace = new java.util.HashMap<String, NoaaCoopsProvider.Context>();
            for (int i=0; i<3 && !places.isEmpty(); i++) {
                Place place = places.get(i < places.size() ? i : 0);
                Integer drive = startingLocation == null ? null : driveByPlace.computeIfAbsent(place.sourceUrl(),
                        ignored -> driveMinutes(startingLocation, place));
                NoaaCoopsProvider.Context noaaContext = noaaByPlace.computeIfAbsent(place.sourceUrl(),
                        ignored -> noaa.context(place.latitude(), place.longitude(), windowStartUtc));
                options.add(new LiveOption(place, forecast(place, windowStartUtc.plusSeconds(i * 3600L)),
                        drive, noaaContext));
                System.err.println("live-provider: forecast " + options.size() + "/3");
            }
            return new Search(location, List.copyOf(options), Instant.now());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IllegalStateException("Live place or forecast provider unavailable: " + e.getClass().getSimpleName(), e);
        }
    }

    Integer driveMinutes(Location origin, Place place) {
        try {
            String url = "http://router.project-osrm.org/route/v1/driving/" + origin.longitude() + ","
                    + origin.latitude() + ";" + place.longitude() + "," + place.latitude() + "?overview=false";
            JsonNode result = get(url);
            if (!result.path("code").asText().equals("Ok")) return null;
            JsonNode first = result.path("routes").path(0).path("duration");
            return first.isNumber() ? (int) Math.ceil(first.asDouble() / 60.0) : null;
        } catch (IOException | InterruptedException failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            return null;
        }
    }

    Location geocode(String destination) throws IOException, InterruptedException {
        if (destination == null || destination.isBlank()) throw new IllegalArgumentException("Destination is required");
        String url = "https://geocoding-api.open-meteo.com/v1/search?name=" + enc(destination)
                + "&count=1&language=en&format=json";
        JsonNode results = get(url).path("results");
        if (!results.isArray() || results.isEmpty()) throw new IllegalArgumentException("No geocoded destination: " + destination);
        JsonNode first = results.get(0);
        String label = first.path("name").asText() + ", " + first.path("admin1").asText() + ", " + first.path("country").asText();
        return new Location(label, first.path("latitude").asDouble(), first.path("longitude").asDouble(),
                first.path("timezone").asText("UTC"), url);
    }

    List<Place> places(Location location, String destination) throws IOException, InterruptedException {
        // One end-user-initiated, activity-specific search. Public Nominatim is not a bulk POI feed.
        String url = nominatimBaseUrl + "/search?q=" + enc("kayak launch, " + destination)
                + "&format=jsonv2&limit=3";
        JsonNode elements = get(url);
        List<Place> found = new ArrayList<>();
        for (JsonNode element : elements) {
            if (!element.hasNonNull("lat") || !element.hasNonNull("lon")) continue;
            double lat = element.path("lat").asDouble(), lon = element.path("lon").asDouble();
            if (distanceKm(location.latitude(), location.longitude(), lat, lon) > 25) continue;
            String type = switch (element.path("osm_type").asText()) {
                case "node" -> "node"; case "way" -> "way"; case "relation" -> "relation"; default -> "";
            };
            if (type.isEmpty()) continue;
            String id = element.path("osm_id").asText();
            String name = element.path("name").asText("");
            if (name.isBlank()) name = element.path("display_name").asText("Mapped water access").split(",")[0];
            found.add(new Place(name + " (OSM " + type + "/" + id + ")", lat, lon,
                    "https://www.openstreetmap.org/" + type + "/" + id));
        }
        if (found.isEmpty()) return List.of(new Place(location.label() + " forecast area (no mapped launch found)",
                location.latitude(), location.longitude(), location.sourceUrl()));
        return List.copyOf(found);
    }

    private static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double p1=Math.toRadians(lat1), p2=Math.toRadians(lat2);
        double dp=p2-p1, dl=Math.toRadians(lon2-lon1);
        double a=Math.sin(dp/2)*Math.sin(dp/2)+Math.cos(p1)*Math.cos(p2)*Math.sin(dl/2)*Math.sin(dl/2);
        return 6371.0 * 2.0 * Math.atan2(Math.sqrt(a),Math.sqrt(1-a));
    }

    Forecast forecast(Place place, Instant at) throws IOException, InterruptedException {
        String base = "latitude=" + place.latitude() + "&longitude=" + place.longitude() + "&timezone=UTC&forecast_days=7";
        String weatherUrl = "https://api.open-meteo.com/v1/forecast?" + base
                + "&hourly=wind_speed_10m,wind_gusts_10m&wind_speed_unit=kn";
        String marineUrl = "https://marine-api.open-meteo.com/v1/marine?" + base
                + "&hourly=wave_height,ocean_current_velocity,sea_level_height_msl";
        JsonNode weather = get(weatherUrl), marine = get(marineUrl);
        JsonNode wt = weather.path("hourly").path("time");
        int wi = nearestHour(wt, at);
        JsonNode mt = marine.path("hourly").path("time");
        int mi = nearestHour(mt, at);
        Double currentKmh = number(marine.path("hourly").path("ocean_current_velocity"), mi);
        Double waveMeters = number(marine.path("hourly").path("wave_height"), mi);
        return new Forecast(number(weather.path("hourly").path("wind_speed_10m"), wi),
                number(weather.path("hourly").path("wind_gusts_10m"), wi),
                waveMeters == null ? null : waveMeters * 3.28084,
                currentKmh == null ? null : currentKmh / 1.852,
                number(marine.path("hourly").path("sea_level_height_msl"), mi),
                wi < 0 ? null : utcHour(wt.get(wi).asText()), weatherUrl, marineUrl,
                weather.path("latitude").asDouble(), weather.path("longitude").asDouble(),
                marine.path("latitude").asDouble(), marine.path("longitude").asDouble());
    }

    private JsonNode get(String url) throws IOException, InterruptedException {
        System.err.println("live-provider: GET " + URI.create(url).getHost());
        var request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30))
                .header("User-Agent", AGENT).GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.err.println("live-provider: HTTP " + response.statusCode() + " from " + URI.create(url).getHost());
        if (response.statusCode() != 200) throw new IOException("Provider HTTP " + response.statusCode());
        return mapper.readTree(response.body());
    }
    private static int nearestHour(JsonNode times, Instant at) {
        if (!times.isArray()) return -1;
        long best = Long.MAX_VALUE; int index = -1;
        for (int i=0; i<times.size(); i++) {
            long distance = Math.abs(Duration.between(utcHour(times.get(i).asText()), at).toSeconds());
            if (distance < best) { best=distance; index=i; }
        }
        return best <= 3600 ? index : -1;
    }
    private static Double number(JsonNode array, int index) {
        return index < 0 || !array.isArray() || index >= array.size() || !array.get(index).isNumber()
                ? null : array.get(index).asDouble();
    }
    static Instant utcHour(String value) {
        return java.time.LocalDateTime.parse(value).toInstant(java.time.ZoneOffset.UTC);
    }
    private static String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
