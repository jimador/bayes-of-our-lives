package dev.jimador.decisiondesk;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcessStatusCode;
import com.embabel.agent.core.ProcessOptions;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DeskRunner implements CommandLineRunner {
    public record Case(PaddleDayAgent.Conditions conditions, String expected) {}
    private static PaddleDayAgent.Conditions c(String id, String issued, String valid,
            Double wind, Double gust, Double swell, Boolean storm, Double height, String phase, Double current) {
        return new PaddleDayAgent.Conditions(id, issued, valid, wind, gust, swell, storm, height, phase, current);
    }
    private static final String FRESH = "2026-10-10T10:00:00Z";
    private static final String VALID = "2026-10-10T18:00:00Z";
    public static final List<Case> CASES = List.of(
            new Case(c("calm", FRESH, VALID, 7.0, 10.0, 1.0, false, 3.0, "rising", 0.4), "candidate"),
            new Case(c("boundary", FRESH, VALID, 12.0, 16.0, 2.0, false, 2.0, "falling", 1.0), "candidate"),
            new Case(c("gust", FRESH, VALID, 8.0, 19.0, 1.0, false, 3.0, "rising", 0.4), "defer"),
            new Case(c("storm", FRESH, VALID, 7.0, 10.0, 1.0, true, 3.0, "falling", 0.4), "defer"),
            new Case(c("lowtide", FRESH, VALID, 7.0, 10.0, 1.0, false, 1.5, "rising", 0.4), "defer"),
            new Case(c("current", FRESH, VALID, 7.0, 10.0, 1.0, false, 3.0, "falling", 1.4), "defer"),
            new Case(c("stale", "2026-10-10T03:00:00Z", VALID, 7.0, 10.0, 1.0, false, 3.0, "rising", 0.4), "review"),
            new Case(c("missingcurrent", FRESH, VALID, 7.0, 10.0, 1.0, false, 3.0, "rising", null), "review"),
            new Case(c("trafficlate", FRESH, VALID, 7.0, 10.0, 1.0, false, 3.0, "rising", 0.4), "defer"),
            new Case(c("trafficunknown", FRESH, VALID, 7.0, 10.0, 1.0, false, 3.0, "rising", 0.4), "review"));

    private final AgentPlatform platform;
    private final String engine;
    private final int warmups;
    private final int runs;

    public DeskRunner(AgentPlatform platform, @Value("${demo.engine:local}") String engine,
                      @Value("${demo.warmups:1}") int warmups, @Value("${demo.runs:1}") int runs) {
        this.platform = platform;
        this.engine = engine;
        this.warmups = warmups;
        this.runs = runs;
    }

    @Override
    public void run(String... args) {
        if (warmups < 0 || runs < 1 || warmups + runs > 10) {
            throw new IllegalArgumentException("Use 0..9 warmups and 1..10 runs, at most 10 rounds total");
        }
        var agent = platform.agents().stream().filter(a -> a.getName().contains("PaddleDayAgent"))
                .findFirst().orElseThrow(() -> new IllegalStateException("PaddleDayAgent was not deployed"));
        System.out.println("engine,phase,round,case_id,expected,raw_choice,card_choice,raw_correct,valid,guarded,decision_ms,process_ms,model,status");
        for (int round = 0; round < warmups + runs; round++) {
            String phase = round < warmups ? "warmup" : "measured";
            for (Case c : CASES) {
                long start = System.nanoTime();
                var process = platform.createAgentProcessFrom(agent, new ProcessOptions(), c.conditions()).run();
                double processMs = (System.nanoTime() - start) / 1_000_000.0;
                var card = process.last(PaddleDayAgent.DayCard.class);
                String raw = card == null ? "none" : card.rawChoice();
                String cardChoice = card == null ? "none" : card.choice();
                String status = card == null ? String.valueOf(process.getStatus()) : card.status();
                boolean valid = process.getStatus() == AgentProcessStatusCode.COMPLETED && card != null
                        && card.nextSteps().length > 0 && (status.equals("selected") || status.equals("guarded"));
                double decisionMs = card == null ? 0 : card.decisionNanos() / 1_000_000.0;
                String model = card == null ? "none" : card.model();
                System.out.printf(java.util.Locale.ROOT, "%s,%s,%d,%s,%s,%s,%s,%s,%s,%s,%.3f,%.3f,%s,%s%n",
                        engine, phase, round, c.conditions().id(), c.expected(), raw, cardChoice, c.expected().equals(raw),
                        valid, status.equals("guarded"), decisionMs, processMs, model, status);
            }
        }
    }
}
