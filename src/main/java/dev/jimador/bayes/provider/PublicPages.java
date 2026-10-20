package dev.jimador.bayes.provider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Bounded public evidence; a search lead is explicitly weaker than a retrieved venue page. */
@Component
public class PublicPages {
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Wikipedia(Query query) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Query(List<Hit> search) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Hit(long pageid, String title, String snippet) {}

    private static final Pattern LINK = Pattern.compile("(?is)<a\\b[^>]*href=[\"']([^\"']+)[\"']");
    private final ObjectMapper mapper;
    private final HttpClient client =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(4))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();

    public PublicPages(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public List<PageEvidence> read(Place place, String city) {
        List<PageEvidence> evidence = new ArrayList<>();
        String website = place.tags().getOrDefault("website", place.tags().get("contact:website"));
        if (website != null)
            try {
                URI home = URI.create(website);
                if (home.getRawQuery() == null && PinnedHttps.validatedAddress(home) != null) {
                    var page = PinnedHttps.get(home, 256_000);
                    if (page.status() == 200
                            && page.contentType().toLowerCase(Locale.ROOT).contains("text/html")) {
                        evidence.add(
                                new PageEvidence(
                                        place.id() + "-site",
                                        place.id(),
                                        home.toString(),
                                        "retrieved_unverified",
                                        text(page.body())));
                        URI detail = detail(home, page.body());
                        if (detail != null) {
                            var extra = PinnedHttps.get(detail, 256_000);
                            if (extra.status() == 200
                                    && extra.contentType()
                                            .toLowerCase(Locale.ROOT)
                                            .contains("text/html"))
                                evidence.add(
                                        new PageEvidence(
                                                place.id() + "-detail",
                                                place.id(),
                                                detail.toString(),
                                                "retrieved_unverified",
                                                text(extra.body())));
                        }
                    }
                }
            } catch (Exception unavailable) {
                /* A failed page is a missing source, never a positive claim. */
            }
        if (evidence.isEmpty()) {
            PageEvidence lead = wikipedia(place, city);
            if (lead != null) evidence.add(lead);
        }
        return List.copyOf(evidence);
    }

    private PageEvidence wikipedia(Place place, String city) {
        try {
            String query = URLEncoder.encode(place.name() + " " + city, StandardCharsets.UTF_8);
            URI uri =
                    URI.create(
                            "https://en.wikipedia.org/w/api.php?action=query&list=search&format=json&srlimit=1&srsearch="
                                    + query);
            var response =
                    client.send(
                            HttpRequest.newBuilder(uri)
                                    .timeout(Duration.ofSeconds(6))
                                    .header("User-Agent", "Bayes/0.1")
                                    .GET()
                                    .build(),
                            HttpResponse.BodyHandlers.ofInputStream());
            try (var body = response.body()) {
                if (response.statusCode() != 200) return null;
                byte[] bytes = body.readNBytes(256_001);
                if (bytes.length > 256_000) return null;
                Wikipedia result = mapper.readValue(bytes, Wikipedia.class);
                if (result.query() == null
                        || result.query().search() == null
                        || result.query().search().isEmpty()) return null;
                Hit hit = result.query().search().getFirst();
                return new PageEvidence(
                        place.id() + "-wiki",
                        place.id(),
                        "https://en.wikipedia.org/?curid=" + hit.pageid(),
                        "search_lead_unverified",
                        text(hit.title() + ": " + hit.snippet()));
            }
        } catch (Exception failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            return null;
        }
    }

    static URI detail(URI home, String html) {
        var matches = LINK.matcher(html);
        int count = 0;
        while (matches.find() && count++ < 100)
            try {
                URI candidate = home.resolve(matches.group(1));
                String path = candidate.getPath().toLowerCase(Locale.ROOT);
                if (path.matches(".*(menu|hours|visit|accessib).*")
                        && home.getHost().equalsIgnoreCase(candidate.getHost())
                        && candidate.getRawQuery() == null
                        && candidate.getRawFragment() == null
                        && "https".equalsIgnoreCase(candidate.getScheme())
                        && candidate.getUserInfo() == null
                        && candidate.getPort() == -1
                        && !candidate.equals(home)) return candidate;
            } catch (IllegalArgumentException ignored) {
                /* Ignore malformed external links. */
            }
        return null;
    }

    static String text(String html) {
        String visible =
                html.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
                        .replaceAll("(?is)<[^>]+>", " ")
                        .replace("&nbsp;", " ")
                        .replace("&amp;", "&")
                        .replaceAll("\\s+", " ")
                        .trim();
        return visible.substring(0, Math.min(6000, visible.length()));
    }
}
