package com.embabel.example.travel.agent

import com.embabel.agent.core.AgentPlatform
import com.embabel.tripper.TripperApplication
import com.embabel.tripper.agent.DicePoiPreferenceService
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext

@SpringBootTest(classes = [TripperApplication::class], properties = [
    "spring.main.web-application-type=none",
    "tripper.decision.engine=local",
    "spring.security.oauth2.client.registration.google.client-id=unused",
    "spring.security.oauth2.client.registration.google.client-secret=unused",
])
class DefaultPlanningContextTest @Autowired constructor(
    private val context: ApplicationContext,
    private val platform: AgentPlatform,
) {
    @Test fun `default planning agent deploys without Dice or a graph container`() {
        assertTrue(context.getBeansOfType(DicePoiPreferenceService::class.java).isEmpty())
        assertTrue(platform.agents().any { it.name.contains("TripperAgent") })
    }
}
