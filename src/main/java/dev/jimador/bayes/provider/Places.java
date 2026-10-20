package dev.jimador.bayes.provider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class Places {
    public record Location(
            String city,
            String timezone,
            double latitude,
            double longitude,
            double originLatitude,
            double originLongitude) {}

    public record MappedPlace(Place evidence, double latitude, double longitude) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Geocoding(List<City> results) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record City(String name, String country, String timezone, Double latitude, Double longitude) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MapHit(
            String name,
            @JsonProperty("display_name") String displayName,
            @JsonProperty("osm_type") String osmType,
            @JsonProperty("osm_id") String osmId,
            Double lat,
            Double lon,
            Map<String, String> extratags) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Photon(List<Feature> features) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Feature(Geometry geometry, Properties properties) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Geometry(List<Double> coordinates) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Properties(
            String name,
            @JsonProperty("osm_type") String osmType,
            @JsonProperty("osm_id") String osmId,
            Map<String, String> extra) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Routing(String code, List<Leg> routes) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Leg(Double duration) {}

    /** Nominatim's usage policy allows one request per second from an application. */
    private final Throttle nominatim = new Throttle(Duration.ofMillis(1100));

    private final ObjectMapper mapper;
    private final HttpClient client;

    @Autowired
    public Places(ObjectMapper mapper) {
        this(mapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    Places(ObjectMapper mapper, HttpClient client) {
        this.mapper = mapper;
        this.client = client;
    }

    public Location locate(String city, String origin) {
        try {
            Geocoding geo =
                    get(
                            "https://geocoding-api.open-meteo.com/v1/search?name="
                                    + enc(city)
                                    + "&count=1&language=en&format=json",
                            Geocoding.class);
            if (geo.results() == null || geo.results().isEmpty())
                throw new IllegalArgumentException("City could not be located");
            City center = geo.results().getFirst();
            MapHit[] hits =
                    get(
                            "https://nominatim.openstreetmap.org/search?q="
                                    + enc(origin + ", " + city)
                                    + "&format=jsonv2&limit=1",
                            MapHit[].class);
            if (hits.length == 0
                    || !coordinates(center.latitude(), center.longitude())
                    || !coordinates(hits[0].lat(), hits[0].lon())
                    || distance(center.latitude(), center.longitude(), hits[0].lat(), hits[0].lon())
                            > 25)
                throw new IllegalArgumentException(
                        "Starting location could not be located in the selected city");
            return new Location(
                    center.name() + ", " + center.country(),
                    center.timezone(),
                    center.latitude(),
                    center.longitude(),
                    hits[0].lat(),
                    hits[0].lon());
        } catch (IOException failure) {
            throw new IllegalStateException("City lookup unavailable", failure);
        }
    }

    public List<MappedPlace> search(Location location, String category, String cuisine) {
        if (!List.of("museum", "park", "breakfast", "cafe", "restaurant").contains(category))
            throw new IllegalArgumentException("Unsupported place category");
        try {
            List<MappedPlace> found = new ArrayList<>();
            if (List.of("museum", "park").contains(category)) {
                MapHit[] hits =
                        get(
                                "https://nominatim.openstreetmap.org/search?q="
                                        + enc(category + ", " + location.city())
                                        + "&format=jsonv2&extratags=1&limit=3",
                                MapHit[].class);
                for (MapHit hit : hits)
                    if (coordinates(hit.lat(), hit.lon()))
                        add(
                                found,
                                hit.name() == null ? hit.displayName().split(",")[0] : hit.name(),
                                hit.osmType(),
                                hit.osmId(),
                                hit.lat(),
                                hit.lon(),
                                category,
                                hit.extratags(),
                                location);
            } else {
                String query = cuisine == null || cuisine.isBlank() ? category : cuisine;
                String filter = category.equals("cafe") ? "amenity:cafe" : "amenity:restaurant";
                String url =
                        "https://photon.komoot.io/api/?q="
                                + enc(query)
                                + "&lat="
                                + location.originLatitude()
                                + "&lon="
                                + location.originLongitude()
                                + "&limit=6&osm_tag="
                                + enc(filter);
                if (category.equals("breakfast")) url += "&osm_tag=" + enc("amenity:cafe");
                Photon result = get(url, Photon.class);
                if (result.features() != null)
                    for (Feature feature : result.features()) {
                        if (feature.geometry() == null
                                || feature.geometry().coordinates() == null
                                || feature.geometry().coordinates().size() < 2
                                || feature.properties() == null
                                || !coordinates(
                                        feature.geometry().coordinates().get(1),
                                        feature.geometry().coordinates().get(0))) continue;
                        Properties p = feature.properties();
                        String type =
                                switch (p.osmType() == null ? "" : p.osmType()) {
                                    case "N" -> "node";
                                    case "W" -> "way";
                                    case "R" -> "relation";
                                    default -> "";
                                };
                        add(
                                found,
                                p.name(),
                                type,
                                p.osmId(),
                                feature.geometry().coordinates().get(1),
                                feature.geometry().coordinates().get(0),
                                category,
                                p.extra(),
                                location);
                    }
                if (!found.isEmpty()) {
                    String ids =
                            String.join(",", found.stream().map(p -> p.evidence().id()).toList());
                    MapHit[] enriched =
                            get(
                                    "https://nominatim.openstreetmap.org/lookup?osm_ids="
                                            + enc(ids)
                                            + "&format=jsonv2&extratags=1",
                                    MapHit[].class);
                    Map<String, Map<String, String>> tags = new LinkedHashMap<>();
                    for (MapHit hit : enriched)
                        tags.put(id(hit.osmType(), hit.osmId()), safeTags(hit.extratags()));
                    found =
                            found.stream()
                                    .map(
                                            p ->
                                                    new MappedPlace(
                                                            new Place(
                                                                    p.evidence().id(),
                                                                    p.evidence().name(),
                                                                    category,
                                                                    p.evidence().sourceUrl(),
                                                                    tags.getOrDefault(
                                                                            p.evidence().id(),
                                                                            p.evidence().tags())),
                                                            p.latitude(),
                                                            p.longitude()))
                                    .toList();
                }
            }
            Map<String, MappedPlace> unique = new LinkedHashMap<>();
            found.forEach(p -> unique.putIfAbsent(p.evidence().id(), p));
            return unique.values().stream().limit(5).toList();
        } catch (IOException failure) {
            throw new IllegalStateException("Place lookup unavailable", failure);
        }
    }

    public Integer driveMinutes(double fromLat, double fromLon, double toLat, double toLon) {
        try {
            Routing result =
                    get(
                            "https://router.project-osrm.org/route/v1/driving/"
                                    + fromLon
                                    + ","
                                    + fromLat
                                    + ";"
                                    + toLon
                                    + ","
                                    + toLat
                                    + "?overview=false",
                            Routing.class);
            if (!"Ok".equals(result.code()) || result.routes() == null || result.routes().isEmpty())
                return null;
            Double seconds = result.routes().getFirst().duration();
            return seconds != null && Double.isFinite(seconds) && seconds >= 0 && seconds <= 7200
                    ? (int) Math.ceil(seconds / 60)
                    : null;
        } catch (IOException failure) {
            return null;
        }
    }

    private <T> T get(String url, Class<T> type) throws IOException {
        try {
            if (url.startsWith("https://nominatim.openstreetmap.org/")) nominatim.await();
            var response =
                    client.send(
                            HttpRequest.newBuilder(URI.create(url))
                                    .timeout(Duration.ofSeconds(12))
                                    .header(
                                            "User-Agent",
                                            "Bayes/0.1 (github.com/jimador/bayes-of-our-lives)")
                                    .GET()
                                    .build(),
                            HttpResponse.BodyHandlers.ofInputStream());
            try (var body = response.body()) {
                if (response.statusCode() != 200)
                    throw new IOException("Public provider HTTP " + response.statusCode());
                byte[] bytes = body.readNBytes(512_001);
                if (bytes.length > 512_000) throw new IOException("Provider response too large");
                return mapper.readValue(bytes, type);
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IOException("Lookup interrupted");
        }
    }

    private static void add(
            List<MappedPlace> found,
            String name,
            String type,
            String number,
            double lat,
            double lon,
            String category,
            Map<String, String> tags,
            Location location) {
        if (name == null
                || name.isBlank()
                || !List.of("node", "way", "relation").contains(type)
                || number == null
                || !number.matches("[0-9]+")
                || !Double.isFinite(lat)
                || !Double.isFinite(lon)
                || distance(location.originLatitude(), location.originLongitude(), lat, lon) > 25)
            return;
        found.add(
                new MappedPlace(
                        new Place(
                                id(type, number),
                                name,
                                category,
                                "https://www.openstreetmap.org/" + type + "/" + number,
                                safeTags(tags)),
                        lat,
                        lon));
    }

    private static String id(String type, String number) {
        return switch (type) {
                    case "node" -> "N";
                    case "way" -> "W";
                    case "relation" -> "R";
                    default -> "";
                }
                + number;
    }

    static Map<String, String> safeTags(Map<String, String> tags) {
        if (tags == null) return Map.of();
        Map<String, String> safe = new LinkedHashMap<>();
        for (String key :
                List.of(
                        "opening_hours",
                        "cuisine",
                        "breakfast",
                        "lunch",
                        "dinner",
                        "wheelchair",
                        "fee",
                        "website",
                        "contact:website")) {
            String value = tags.get(key);
            if (value != null && value.length() <= 500) safe.put(key, value);
        }
        return Map.copyOf(safe);
    }

    private static boolean coordinates(Double lat, Double lon) {
        return lat != null
                && lon != null
                && Double.isFinite(lat)
                && Double.isFinite(lon)
                && Math.abs(lat) <= 90
                && Math.abs(lon) <= 180;
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static double distance(double a, double b, double c, double d) {
        double p = Math.toRadians(c - a), q = Math.toRadians(d - b);
        double h =
                Math.sin(p / 2) * Math.sin(p / 2)
                        + Math.cos(Math.toRadians(a))
                                * Math.cos(Math.toRadians(c))
                                * Math.sin(q / 2)
                                * Math.sin(q / 2);
        return 6371 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }

    /** Spaces calls at least {@code interval} apart, across every thread that shares it. */
    static final class Throttle {
        private final ReentrantLock lock = new ReentrantLock();
        private final long intervalNanos;
        private long last;
        private boolean called;

        Throttle(Duration interval) {
            this.intervalNanos = interval.toNanos();
        }

        void await() throws InterruptedException {
            lock.lockInterruptibly();
            try {
                if (called) {
                    long wait = intervalNanos - (System.nanoTime() - last);
                    if (wait > 0) TimeUnit.NANOSECONDS.sleep(wait);
                }
                called = true;
                last = System.nanoTime();
            } finally {
                lock.unlock();
            }
        }
    }
}
