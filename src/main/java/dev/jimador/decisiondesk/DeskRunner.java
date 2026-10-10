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
    public record Case(String id, String note, String expected) {}
    public static final List<Case> CASES = List.of(
            new Case("dragon", "The dragon head prop is stuck just before its entrance.", "props"),
            new Case("cape", "The lead actor's cape clasp snapped backstage.", "props"),
            new Case("feedback", "A microphone is squealing through the house speakers.", "sound"),
            new Case("monitor", "The stage monitor is silent during the opening song.", "sound"),
            new Case("exit", "A scenery cart is blocking the marked exit.", "safety"),
            new Case("cable", "An exposed electrical cable is sparking beside the wing.", "safety"));

    private final AgentPlatform platform;
    private final String engine;
    private final int warmups;
    private final int runs;

    public DeskRunner(AgentPlatform platform, @Value("${demo.engine:local}") String engine,
                      @Value("${demo.warmups:1}") int warmups, @Value("${demo.runs:2}") int runs) {
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
        var agent = platform.agents().stream().filter(a -> a.getName().contains("StageDeskAgent"))
                .findFirst().orElseThrow(() -> new IllegalStateException("StageDeskAgent was not deployed"));
        System.out.println("engine,phase,round,case_id,expected,actual,correct,valid,decision_ms,process_ms,model,status");
        for (int round = 0; round < warmups + runs; round++) {
            String phase = round < warmups ? "warmup" : "measured";
            for (Case c : CASES) {
                long start = System.nanoTime();
                var process = platform.createAgentProcessFrom(agent, new ProcessOptions(),
                        new StageDeskAgent.Ticket(c.id(), c.note())).run();
                double processMs = (System.nanoTime() - start) / 1_000_000.0;
                var card = process.last(StageDeskAgent.DispatchCard.class);
                String actual = card == null ? "none" : card.crew();
                String status = card == null ? String.valueOf(process.getStatus()) : card.status();
                boolean valid = process.getStatus() == AgentProcessStatusCode.COMPLETED && card != null
                        && card.checklist().length > 0 && status.equals("selected");
                double decisionMs = card == null ? 0 : card.decisionNanos() / 1_000_000.0;
                String model = card == null ? "none" : card.model();
                System.out.printf(java.util.Locale.ROOT, "%s,%s,%d,%s,%s,%s,%s,%s,%.3f,%.3f,%s,%s%n",
                        engine, phase, round, c.id(), c.expected(), actual, c.expected().equals(actual),
                        valid, decisionMs, processMs, model, status);
            }
        }
    }
}
