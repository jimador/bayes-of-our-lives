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
            new Case("morning", "I have the morning free and want to paddle today, if I decide conditions are suitable.", "paddle"),
            new Case("afterwork", "I have time after work and would like to get the kayak out today, subject to my own conditions check.", "paddle"),
            new Case("weekend", "I cannot paddle today. I want to plan a route for next weekend.", "plan"),
            new Case("map", "No outing today; I want to research launch access and map a future kayak trip.", "plan"),
            new Case("book", "I want a day off from kayaking and planning. I am going to read a book.", "rest"),
            new Case("garden", "No kayak or route planning today. I want to rest and spend the afternoon in the garden.", "rest"));

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
        System.out.println("engine,phase,round,case_id,expected,actual,correct,valid,decision_ms,process_ms,model,status");
        for (int round = 0; round < warmups + runs; round++) {
            String phase = round < warmups ? "warmup" : "measured";
            for (Case c : CASES) {
                long start = System.nanoTime();
                var process = platform.createAgentProcessFrom(agent, new ProcessOptions(),
                        new PaddleDayAgent.Note(c.id(), c.note())).run();
                double processMs = (System.nanoTime() - start) / 1_000_000.0;
                var card = process.last(PaddleDayAgent.DayCard.class);
                String actual = card == null ? "none" : card.choice();
                String status = card == null ? String.valueOf(process.getStatus()) : card.status();
                boolean valid = process.getStatus() == AgentProcessStatusCode.COMPLETED && card != null
                        && card.nextSteps().length > 0 && status.equals("selected");
                double decisionMs = card == null ? 0 : card.decisionNanos() / 1_000_000.0;
                String model = card == null ? "none" : card.model();
                System.out.printf(java.util.Locale.ROOT, "%s,%s,%d,%s,%s,%s,%s,%s,%.3f,%.3f,%s,%s%n",
                        engine, phase, round, c.id(), c.expected(), actual, c.expected().equals(actual),
                        valid, decisionMs, processMs, model, status);
            }
        }
    }
}
