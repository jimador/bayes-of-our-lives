package dev.jimador.bayes.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Independent, bounded public sources for Tripper-style place research. No map tags become facts. */
@Component
public class PublicWebResearch {
    public record Page(String url, String status, String excerpt) {}
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private static final Pattern HREF = Pattern.compile("(?is)<a\\b[^>]*href=[\"']([^\"']+)[\"']");

    public Page wikipedia(String venue, String city) {
        try {
            String query = URLEncoder.encode(venue + " " + city, StandardCharsets.UTF_8);
            URI uri = URI.create("https://en.wikipedia.org/w/api.php?action=query&list=search&format=json&srlimit=1&srsearch=" + query);
            var response = client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(6))
                    .header("User-Agent", "Bayes/0.1 (github.com/jimador/bayes-of-our-lives)").GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200 || response.body().length() > 256_000)
                return new Page(null, "unavailable", "");
            var first = mapper.readTree(response.body()).path("query").path("search");
            if (!first.isArray() || first.isEmpty()) return new Page(null, "no_article", "");
            var hit = first.get(0);
            String title = hit.path("title").asText("");
            // A broad search hit is a lead, never evidence of today's venue details.
            String snippet = hit.path("snippet").asText("").replaceAll("<[^>]*>", " ")
                    .replaceAll("\\s+", " ").trim();
            String url = "https://en.wikipedia.org/?curid=" + hit.path("pageid").asInt();
            return new Page(url, "search_lead_unverified", (title + ": " + snippet).substring(0,
                    Math.min(300, title.length() + 2 + snippet.length())));
        } catch (Exception failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            return new Page(null, "unavailable", "");
        }
    }

    /** One targeted follow-up from the venue's own site, if it links to a relevant page. */
    public Page targetedOfficialPage(String website, String question) {
        try {
            if (website == null) return new Page(null, "no_website", "");
            URI home = URI.create(website);
            if (!OfficialPageResearch.safePublicHttps(home)) return new Page(null, "unsafe_website", "");
            var root = PinnedHttps.get(home, 256_000);
            if (root.status() != 200 || !root.contentType().toLowerCase(Locale.ROOT).contains("text/html"))
                return new Page(null, "unavailable", "");
            URI target = relevantLink(home, root.body(), question);
            if (target == null) return new Page(null, "no_relevant_link", "");
            var page = PinnedHttps.get(target, 256_000);
            if (page.status() != 200 || !page.contentType().toLowerCase(Locale.ROOT).contains("text/html"))
                return new Page(target.toString(), "unavailable", "");
            var clues = OfficialPageResearch.clues(page.body());
            return new Page(target.toString(), "retrieved_unverified", String.join(" | ", clues));
        } catch (Exception failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            return new Page(null, "unavailable", "");
        }
    }
    static URI relevantLink(URI home, String html, String question) {
        List<String> terms = question.toLowerCase(Locale.ROOT).contains("menu")
                ? List.of("menu", "food") : List.of("visit", "hours", "ticket", "accessib", "admission");
        var matches = HREF.matcher(html);
        int inspected = 0;
        while (matches.find() && inspected++ < 200) {
            String raw = matches.group(1);
            if (terms.stream().noneMatch(t -> raw.toLowerCase(Locale.ROOT).contains(t))) continue;
            try {
                URI uri = home.resolve(raw);
                if (home.getHost().equalsIgnoreCase(uri.getHost()) && OfficialPageResearch.safePublicHttps(uri)
                        && !uri.equals(home)) return uri;
            } catch (Exception ignored) { /* Ignore invalid links. */ }
        }
        return null;
    }
}
