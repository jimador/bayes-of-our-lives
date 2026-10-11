package dev.jimador.decisiondesk;

import static org.junit.jupiter.api.Assertions.*;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcessStatusCode;
import com.embabel.agent.core.ProcessOptions;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "demo.engine=local", "demo.warmups=0", "demo.runs=1", "demo.trace=false",
        "embabel.agent.platform.models.openai.api-key=unused-local-mode"
})
class PlannerPathTest {
    @Autowired AgentPlatform platform;

    @Test void plannerBuildsTypedFactsBeforeDecisionAndGoal() {
        var agent = platform.agents().stream().filter(a -> a.getName().contains("PaddleDayAgent"))
                .findFirst().orElseThrow();
        var request = DeskRunner.request(DeskRunner.CASES.getFirst().conditions());
        var process = platform.createAgentProcessFrom(agent, new ProcessOptions(), request).run();
        assertEquals(AgentProcessStatusCode.COMPLETED, process.getStatus());
        var card = process.last(PaddleDayAgent.DayCard.class);
        assertNotNull(card);
        assertEquals("Palmetto Islands County Park", card.destination());
        assertEquals("candidate", card.choice());
        List<String> names = process.getHistory().stream().map(h -> h.getActionName().split("\\.")[4]).toList();
        for (String required : List.of("destinations", "destination", "season", "weather", "tides", "traffic", "prepare", "choose", "finish")) {
            assertTrue(names.contains(required), required + " missing from " + names);
        }
        for (String prerequisite : List.of("destination", "season", "weather", "tides", "traffic")) {
            assertTrue(names.indexOf(prerequisite) < names.indexOf("prepare"), prerequisite);
        }
        assertTrue(names.indexOf("prepare") < names.indexOf("choose"));
        assertTrue(names.indexOf("choose") < names.indexOf("finish"));
    }

    @Test void locationAndSeasonAreExplicitPlanningFacts() {
        var agent = new PaddleDayAgent();
        var springConditions = new PaddleDayAgent.Conditions("calm", "2026-04-10T10:00:00Z",
                "2026-04-10T18:00:00Z", 7.0, 10.0, 1.0, false, 3.0, "rising", 0.4);
        var spring = new PaddleDayAgent.PlanningRequest(springConditions,
                LocalDate.of(2026, 4, 10), "Church Creek", Instant.parse("2026-04-10T12:00:00Z"),
                Instant.parse("2026-04-10T14:00:00Z"), Instant.parse("2026-04-10T15:00:00Z"),
                Instant.parse("2026-04-10T17:00:00Z"), Instant.parse("2026-04-10T16:00:00Z"),
                Instant.parse("2026-04-10T16:00:00Z"));
        assertEquals("spring", agent.season(spring).season());
        assertEquals("Pierpoint Boat Landing", agent.destination(spring, agent.destinations(spring)).name());
        var deployed = platform.agents().stream().filter(a -> a.getName().contains("PaddleDayAgent"))
                .findFirst().orElseThrow();
        var process = platform.createAgentProcessFrom(deployed, new ProcessOptions(), spring).run();
        assertEquals(AgentProcessStatusCode.COMPLETED, process.getStatus());
        var card = process.last(PaddleDayAgent.DayCard.class);
        assertNotNull(card);
        assertEquals("Pierpoint Boat Landing", card.destination());
        assertEquals("defer", card.choice());
    }
}
