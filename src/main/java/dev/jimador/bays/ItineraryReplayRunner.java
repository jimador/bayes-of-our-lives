package dev.jimador.bays;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Replays one captured real-city decision boundary without re-querying public map servers. */
@Component
@ConditionalOnProperty(name = "bays.replay", havingValue = "true")
public class ItineraryReplayRunner implements CommandLineRunner {
    private final BayesAgent agent;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final String capture, engine;
    private final int warmups, runs;
    public ItineraryReplayRunner(BayesAgent agent,
            @Value("${bays.replay.capture:benchmarks/itinerary-capture.json}") String capture,
            @Value("${bays.replay.engine:local}") String engine,
            @Value("${bays.replay.warmups:1}") int warmups,
            @Value("${bays.replay.runs:2}") int runs) {
        this.agent = agent; this.capture = capture; this.engine = engine;
        this.warmups = warmups; this.runs = runs;
    }
    @Override public void run(String... args) throws Exception {
        if (warmups < 0 || warmups > 2 || runs < 1 || runs > 3)
            throw new IllegalArgumentException("Replay bounds are 0–2 warmups and 1–3 measured runs");
        var root = mapper.readTree(Path.of(capture).toFile());
        var request = mapper.treeToValue(root.path("request"), ItineraryRequest.class).withEngine(engine);
        request.validateShape();
        var memory = mapper.treeToValue(root.path("memory"), ItineraryMemory.Accepted.class);
        var alternatives = mapper.readerForListOf(ItineraryPlanner.Alternative.class)
                .<List<ItineraryPlanner.Alternative>>readValue(root.path("alternatives"));
        var researched = mapper.readerForListOf(BayesAgent.ResearchedPointOfInterest.class)
                .<List<BayesAgent.ResearchedPointOfInterest>>readValue(root.path("researchedPlaces"));
        var findings = new ItineraryResearch.Findings(request.city(), Instant.now(), List.of(),
                List.of(), List.of(), List.of(), List.of());
        var profile = new BayesAgent.OutingProfile(memory, request);
        var research = new BayesAgent.PointOfInterestFindings(findings, researched);
        var proposal = new BayesAgent.ProposedTravelPlan(alternatives);
        for (int i = -warmups; i < runs; i++) {
            var review = agent.decideItinerary(profile, research, proposal);
            String hash = BaysRunner.sha256(review.decisionInput());
            System.out.println("itineraryReplay engine=" + engine + " run=" + (i < 0 ? "warmup" : i + 1)
                    + " inputSha256=" + hash + " completeness=" + review.completeness()
                    + " choice=" + review.choice() + " rating=" + review.rating()
                    + " classification=" + review.classification() + " model=" + review.model()
                    + " decisionMs=" + review.decisionNanos() / 1_000_000.0
                    + " classificationMs=" + review.classificationNanos() / 1_000_000.0);
            if (review.choice().startsWith("failure:") || review.choice().equals("inconclusive")
                    || review.rating().startsWith("failure:") || review.classification().startsWith("failure:"))
                throw new IllegalStateException("Typed itinerary replay failed");
        }
    }
}
