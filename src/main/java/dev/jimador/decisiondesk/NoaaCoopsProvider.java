package dev.jimador.decisiondesk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/** NOAA CO-OPS station context; proximity does not establish hydrological equivalence. */
@Component
public final class NoaaCoopsProvider {
    public record Station(String id, String name, double latitude, double longitude, double distanceKm,
                          String type, Integer bin) {}
    public record TideEvent(Instant timeUtc, double heightFtMllw, String type) {}
    public record Context(Station nearestTide, String tideStatus, List<TideEvent> tideEvents,
                          String tidePredictionUrl, Station nearestCurrent, String currentStatus) {}

    private static final String META = "https://api.tidesandcurrents.noaa.gov/mdapi/prod/webapi/stations.json?type=";
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private List<JsonNode> tideStations;
    private List<JsonNode> currentStations;

    public Context context(double latitude, double longitude, Instant windowStartUtc) {
        try {
            Station tide = nearest(stations("tidepredictions"), latitude, longitude);
            Station current = nearest(stations("currentpredictions"), latitude, longitude);
            String tideStatus = tide == null ? "No NOAA tide-prediction station in metadata"
                    : tide.distanceKm() > 1.0 ? "Nearby regional station is beyond 1 km; reference high/low only, no local tide prediction selected"
                    : "Nearby station high/low is context only; waterway correspondence unverified";
            List<TideEvent> events = List.of();
            String url = "none";
            if (tide != null && tide.distanceKm() <= 10.0) {
                url = predictionUrl(tide.id(), LocalDate.ofInstant(windowStartUtc, ZoneOffset.UTC));
                events = tideEvents(get(url));
                if (events.isEmpty()) tideStatus = "Nearby station returned no MLLW high/low prediction";
            }
            String currentStatus = current == null ? "No NOAA current-prediction station in metadata"
                    : "No local current station selected; nearest is a different/unchecked channel and is context only";
            return new Context(tide, tideStatus, events, url, current, currentStatus);
        } catch (IOException | InterruptedException failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            return new Context(null, "NOAA station service unavailable: " + failure.getClass().getSimpleName(),
                    List.of(), "none", null, "NOAA current station service unavailable");
        }
    }

    private synchronized List<JsonNode> stations(String type) throws IOException, InterruptedException {
        if (type.equals("tidepredictions") && tideStations != null) return tideStations;
        if (type.equals("currentpredictions") && currentStations != null) return currentStations;
        JsonNode list = get(META + type).path("stations");
        if (!list.isArray()) throw new IOException("NOAA station list missing");
        List<JsonNode> rows = new ArrayList<>();
        list.forEach(rows::add);
        if (type.equals("tidepredictions")) tideStations = List.copyOf(rows);
        else currentStations = List.copyOf(rows);
        return rows;
    }

    private static Station nearest(List<JsonNode> rows, double latitude, double longitude) {
        return rows.stream().filter(n -> n.hasNonNull("lat") && n.hasNonNull("lng"))
                .map(n -> new Station(n.path("id").asText(), n.path("name").asText(),
                        n.path("lat").asDouble(), n.path("lng").asDouble(),
                        distanceKm(latitude, longitude, n.path("lat").asDouble(), n.path("lng").asDouble()),
                        n.path("type").asText(), n.path("currbin").isInt() ? n.path("currbin").asInt() : null))
                .min(Comparator.comparingDouble(Station::distanceKm)).orElse(null);
    }

    private static String predictionUrl(String id, LocalDate date) {
        String day = date.format(DateTimeFormatter.BASIC_ISO_DATE);
        return "https://api.tidesandcurrents.noaa.gov/api/prod/datagetter?begin_date=" + day
                + "&end_date=" + date.plusDays(1).format(DateTimeFormatter.BASIC_ISO_DATE)
                + "&station=" + id + "&product=predictions&datum=MLLW&time_zone=gmt"
                + "&interval=hilo&units=english&format=json&application=EmbabelDecisionDesk";
    }

    private static List<TideEvent> tideEvents(JsonNode response) {
        List<TideEvent> events = new ArrayList<>();
        for (JsonNode row : response.path("predictions")) {
            try {
                events.add(new TideEvent(LocalDateTime.parse(row.path("t").asText(),
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")).toInstant(ZoneOffset.UTC),
                        Double.parseDouble(row.path("v").asText()), row.path("type").asText()));
            } catch (RuntimeException ignored) { /* An invalid NOAA row stays unavailable. */ }
        }
        return List.copyOf(events);
    }

    private JsonNode get(String url) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(25))
                .header("User-Agent", "EmbabelDecisionDesk/0.1 (github.com/jimador/embabel-decision-desk)").GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IOException("NOAA HTTP " + response.statusCode());
        return mapper.readTree(response.body());
    }

    static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double p1=Math.toRadians(lat1), p2=Math.toRadians(lat2);
        double dp=p2-p1, dl=Math.toRadians(lon2-lon1);
        double a=Math.sin(dp/2)*Math.sin(dp/2)+Math.cos(p1)*Math.cos(p2)*Math.sin(dl/2)*Math.sin(dl/2);
        return 6371.0 * 2.0 * Math.atan2(Math.sqrt(a),Math.sqrt(1-a));
    }
}
