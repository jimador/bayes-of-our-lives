package dev.jimador.bayes.itinerary;

import com.embabel.agent.api.annotation.LlmTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Request-bound feedback for the model's own draft, using the final validator unchanged. */
public final class ItineraryPlanningTools {
    private static final Logger LOG = LoggerFactory.getLogger(ItineraryPlanningTools.class);
    private final ItineraryContext outing;
    private final ResearchFindings research;
    private int reviews;

    public ItineraryPlanningTools(ItineraryContext outing, ResearchFindings research) {
        this.outing = outing;
        this.research = research;
    }

    @LlmTool(description = "Review a complete proposed itinerary draft before final output. Returns actual validated alternatives, trusted baseTravelMinutes and material questions. Repair invalid timing using drive minutes plus ten minutes, supported meal venues and operating windows. Up to three reviews; truthful unresolved evidence gaps are allowed.")
    public synchronized Itinerary review(Itinerary.Draft draft) {
        if (reviews >= 3) throw new IllegalStateException("Itinerary review budget reached");
        reviews++;
        long started = System.nanoTime();
        var checked = ItineraryValidation.plans(draft, research, outing);
        String outcome = checked.alternatives().stream().anyMatch(a -> a.status().equals("invalid")) ? "invalid"
                : checked.alternatives().stream().anyMatch(a -> a.status().equals("unresolved")) ? "unresolved" : "usable";
        LOG.info("stage=plan-review review={} durationMs={} outcome={}", reviews,
                (System.nanoTime() - started) / 1_000_000, outcome);
        return checked;
    }
}
