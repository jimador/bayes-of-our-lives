package com.embabel.example.travel.agent

import com.embabel.common.ai.classification.ClassificationRequest
import com.embabel.common.ai.decision.DecisionAnswer
import com.embabel.common.ai.decision.DecisionRequest
import com.embabel.common.ai.decision.PropositionResult
import com.embabel.tripper.agent.CityOutingAgent
import com.embabel.tripper.agent.CityOutingProvider
import com.embabel.tripper.agent.LocalPoiDecisionService
import com.embabel.tripper.agent.decisionValue
import com.embabel.tripper.agent.ratingValue
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant

class CityOutingAgentTest {
    private val hour = Instant.parse("2026-10-11T18:00:00Z")
    private fun option(name: String, rain: Double?, drive: Int?) = CityOutingProvider.Option(
        CityOutingProvider.Place(name, 25.78, -80.18, "https://www.openstreetmap.org/way/1"),
        28.0, rain, 5.0, hour, "https://api.open-meteo.com/v1/forecast", drive)

    @Test fun `local service answers every typed outing question`() {
        val brief = CityOutingAgent.brief(option("Museum", 10.0, 12), 15)
        val service = LocalPoiDecisionService()
        val response = service.ask(DecisionRequest.of(brief.text, CityOutingAgent.questions))
        assertTrue(((response.answer("complete") as DecisionAnswer.Proposition).outcome as PropositionResult.Answered).answer)
        assertEquals("candidate", decisionValue((response.answer("outing") as DecisionAnswer.Choice).outcome))
        assertEquals("easy", ratingValue((response.answer("effort") as DecisionAnswer.Rating).outcome))
        assertEquals("nearby", decisionValue(service.classify(ClassificationRequest.of(brief.text, CityOutingAgent.distance))))
    }

    @Test fun `missing route and high rain stay distinct`() {
        assertEquals("review", CityOutingAgent.reviewPolicy(option("Unknown", 10.0, null), 0))
        assertEquals("defer", CityOutingAgent.reviewPolicy(option("Rain", 70.0, 5), 0))
    }

    @Test fun `decision failure excludes an otherwise short drive from ranking`() {
        val agent = CityOutingAgent(CityOutingProvider("https://nominatim.openstreetmap.org"))
        fun assessment(name: String, drive: Int, choice: String) = CityOutingAgent.Assessment(
            option(name, 10.0, drive), "candidate", "true", choice, "easy", "nearby", "test", 1, 1)
        val assessments = CityOutingAgent.Assessments("Miami", hour, hour, "geo",
            listOf(assessment("failed", 1, "failure:INVALID_RESPONSE"), assessment("eligible", 4, "candidate")), 1)
        val preferences = CityOutingAgent.Preferences(null)
        val ranked = agent.rank(assessments, preferences)
        assertEquals(listOf("eligible"), ranked.ordered.map { it.option.place.name })
        val plan = agent.finish(assessments, ranked, preferences)
        assertEquals("research", plan.status)
        assertEquals("eligible", plan.selectedResearchLead)
    }
}
