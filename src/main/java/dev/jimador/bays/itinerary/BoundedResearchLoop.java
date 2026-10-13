package dev.jimador.bays.itinerary;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/** One explicit research → replan → redecision retry; no unbounded agent cycle. */
final class BoundedResearchLoop {
    private BoundedResearchLoop() {}
    static <T> T run(T initial, int maxRetries, Predicate<T> asksResearch,
            Function<T, Optional<T>> researchReplanDecide) {
        if (maxRetries < 0 || maxRetries > 1) throw new IllegalArgumentException("Research retry cap is one");
        T current = initial;
        for (int i = 0; i < maxRetries && asksResearch.test(current); i++) {
            var improved = researchReplanDecide.apply(current);
            if (improved.isEmpty()) break;
            current = improved.get();
        }
        return current;
    }
}
