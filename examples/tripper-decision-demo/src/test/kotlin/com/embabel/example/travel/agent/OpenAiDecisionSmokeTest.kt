package com.embabel.example.travel.agent

import com.embabel.common.ai.classification.ClassificationRequest
import com.embabel.common.ai.decision.DecisionAnswer
import com.embabel.common.ai.decision.DecisionRequest
import com.embabel.common.ai.decision.DecisionService
import com.embabel.tripper.TripperApplication
import com.embabel.tripper.agent.*
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate

/** Explicit opt-in network smoke of the actual Tripper OpenAI bean. */
@EnabledIfSystemProperty(named = "tripper.external-smoke", matches = "true")
@SpringBootTest(classes = [TripperApplication::class], properties = [
    "spring.main.web-application-type=none",
    "tripper.decision.engine=openai",
    "spring.security.oauth2.client.registration.google.client-id=unused",
    "spring.security.oauth2.client.registration.google.client-secret=unused",
])
class OpenAiDecisionSmokeTest @Autowired constructor(
    @Qualifier("tripperDecisionService") private val service: DecisionService,
) {
    @Test fun `openai answers Tripper's mixed questions`() {
        val travel = JourneyTravelBrief("Miami", "Miami", "walk", "art museums",
            LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 12))
        val finding = ResearchedPointOfInterest(
            PointOfInterest("Art Museum", "Indoor art museum", "Miami, Florida",
                LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 11)),
            "Museum research", listOf(com.embabel.agent.domain.library.InternetResource("https://example.org/museum", "source")),
            emptyList(), emptyList())
        val brief = PointOfInterestQuestions.brief(finding, travel)
        val decisionStart = System.nanoTime()
        val response = service.ask(DecisionRequest.of(brief, PointOfInterestQuestions.decisions))
        val decisionMs = (System.nanoTime() - decisionStart) / 1_000_000.0
        val choice = decisionValue((response.answer("next") as DecisionAnswer.Choice).outcome)
        assertNotEquals("failure:UNAVAILABLE", choice)
        val classificationStart = System.nanoTime()
        val setting = decisionValue(service.classify(ClassificationRequest.of(brief, PointOfInterestQuestions.setting)))
        val classificationMs = (System.nanoTime() - classificationStart) / 1_000_000.0
        assertNotEquals("failure:UNAVAILABLE", setting)
        println("TRIPPER_DECISION_SMOKE engine=openai choice=$choice " +
            "setting=$setting decisionMs=$decisionMs classificationMs=$classificationMs")
    }
}
