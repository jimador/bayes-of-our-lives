package dev.jimador.bayes.itinerary;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * The briefs that are being decided on right now, keyed by their rendered input.
 *
 * <p>Decision services only receive text, and the framework hands that exact string to every hook.
 * Local rules look the typed brief up here instead of parsing the text back.
 */
@Component
public class DecisionBriefs {

    private record Open(DecisionBrief brief, int users) {}

    private final Map<String, Open> open = new ConcurrentHashMap<>();

    /**
     * Renders the brief, keeps it available while {@code ask} runs, and returns what {@code ask}
     * returns.
     */
    public <T> T asking(DecisionBrief brief, Function<String, T> ask) {
        String input = brief.render();
        // Two identical outings decided at the same time share one entry until both finish.
        open.merge(
                input,
                new Open(brief, 1),
                (held, added) -> new Open(held.brief(), held.users() + 1));
        try {
            return ask.apply(input);
        } finally {
            open.computeIfPresent(
                    input,
                    (key, held) ->
                            held.users() == 1 ? null : new Open(held.brief(), held.users() - 1));
        }
    }

    public Optional<DecisionBrief> lookup(String input) {
        return Optional.ofNullable(open.get(input)).map(Open::brief);
    }
}
