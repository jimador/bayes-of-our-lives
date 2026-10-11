package com.embabel.example.travel.agent

import com.embabel.agent.typesafe.TypeSafeModelFactory
import com.embabel.common.ai.classification.ClassificationRequest
import com.embabel.common.ai.decision.DecisionAnswer
import com.embabel.common.ai.decision.DecisionRequest
import com.embabel.tripper.agent.*
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import java.time.LocalDate

/** Explicit opt-in network smoke: one mixed decision and one classification call. */
@EnabledIfSystemProperty(named = "tripper.external-smoke", matches = "true")
class JevDecisionSmokeTest {
    @Test fun `jev answers Tripper's actual point of interest questions`() {
        val service = TypeSafeModelFactory({ System.getenv("TYPESAFE_API_KEY")
            ?: error("TYPESAFE_API_KEY is required") }).build()
        val travel = JourneyTravelBrief("Miami", "Miami", "walk", "art museums",
            LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 12))
        val finding = ResearchedPointOfInterest(
            PointOfInterest("Art Museum", "Indoor art museum", "Miami, Florida",
                LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 11)),
            "Museum research", listOf(com.embabel.agent.domain.library.InternetResource("https://example.org/museum", "source")),
            emptyList(), emptyList())
        val brief = PointOfInterestQuestions.brief(finding, travel)
        val decisionStart = System.nanoTime()
        val result = service.ask(DecisionRequest.of(brief, PointOfInterestQuestions.decisions))
        val decisionMs = (System.nanoTime() - decisionStart) / 1_000_000.0
        val choice = (result.answer("next") as DecisionAnswer.Choice).outcome
        assertNotEquals("failure:UNAVAILABLE", decisionValue(choice))
        val classificationStart = System.nanoTime()
        val setting = service.classify(ClassificationRequest.of(brief, PointOfInterestQuestions.setting))
        val classificationMs = (System.nanoTime() - classificationStart) / 1_000_000.0
        assertNotEquals("failure:UNAVAILABLE", decisionValue(setting))
        println("TRIPPER_DECISION_SMOKE engine=jev choice=${decisionValue(choice)} " +
            "setting=${decisionValue(setting)} decisionMs=$decisionMs classificationMs=$classificationMs")
    }
}
