package dev.jimador.bays.provider;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Bounded independent page retrieval from a website recorded for a mapped venue. */
@Component
public class OfficialPageResearch {
    public record Evidence(String venue, String pageUrl, String status, Instant retrievedUtc,
                           String pageTitle, List<String> topicClues) {}
    private static final Pattern TITLE = Pattern.compile("(?is)<title[^>]*>(.*?)</title>");
    private static final Pattern TOPIC = Pattern.compile("(?i)hours|open|admission|tickets|accessib|wheelchair|menu|allerg|cuisine");

    public Evidence research(GuidePlaces.Option place) {
        String link = place.osmTags().getOrDefault("website", place.osmTags().get("contact:website"));
        if (link == null) return new Evidence(place.name(), null, "no_website_tag", Instant.now(), null, List.of());
        try {
            URI uri = URI.create(link);
            if (!safePublicHttps(uri)) return new Evidence(place.name(), null, "unsafe_website_tag", Instant.now(), null, List.of());
            var page = PinnedHttps.get(uri, 256 * 1024);
            if (page.status() != 200 || !page.contentType().toLowerCase(Locale.ROOT).contains("text/html"))
                return new Evidence(place.name(), link, "unavailable", Instant.now(), null, List.of());
            return new Evidence(place.name(), link, "retrieved", Instant.now(),
                    extractTitle(page.body()), clues(page.body()));
        } catch (Exception failure) {
            return new Evidence(place.name(), link, "unavailable", Instant.now(), null, List.of());
        }
    }

    static boolean safePublicHttps(URI uri) {
        try { PinnedHttps.validatedAddress(uri); return true; }
        catch (Exception failure) { return false; }
    }
    static String extractTitle(String html) {
        var match = TITLE.matcher(html);
        return match.find() ? clean(match.group(1)).substring(0, Math.min(120, clean(match.group(1)).length())) : null;
    }
    static List<String> clues(String html) {
        String visible = html.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
                .replaceAll("(?is)<[^>]+>", " ").replaceAll("\\s+", " ");
        var found = TOPIC.matcher(visible);
        List<String> result = new ArrayList<>();
        while (found.find() && result.size() < 4) {
            int left = Math.max(0, found.start() - 45), right = Math.min(visible.length(), found.end() + 75);
            String clue = clean(visible.substring(left, right));
            if (!clue.isBlank() && result.stream().noneMatch(x -> x.equals(clue))) result.add(clue);
        }
        return List.copyOf(result);
    }
    private static String clean(String text) {
        return text.replace("&amp;", "&").replace("&nbsp;", " ").replace("&#39;", "'")
                .replace("&quot;", "\"").trim();
    }
}
