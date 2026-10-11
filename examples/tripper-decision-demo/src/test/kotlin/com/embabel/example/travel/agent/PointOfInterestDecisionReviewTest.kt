package com.embabel.example.travel.agent

import com.embabel.agent.domain.library.InternetResource
import com.embabel.common.ai.classification.ClassificationRequest
import com.embabel.common.ai.classification.ClassificationResult
import com.embabel.common.ai.decision.DecisionAnswer
import com.embabel.common.ai.decision.DecisionRequest
import com.embabel.common.ai.decision.PropositionResult
import com.embabel.tripper.agent.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PointOfInterestDecisionReviewTest {
    private val travel = JourneyTravelBrief("Miami", "Miami", "walk", "art museums",
        LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 12))
    private val service = LocalPoiDecisionService()
    private fun finding(name: String, from: LocalDate, source: Boolean = true) = ResearchedPointOfInterest(
        PointOfInterest(name, if (name == "Zoo") "Animal exhibits" else "Contemporary art museum", "Miami, Florida", from, from),
        "Museum research", if (source) listOf(InternetResource("https://example.org/museum", "venue")) else emptyList(),
        emptyList(), emptyList())

    @Test fun `same mixed request gets typed local answers and separate classification`() {
        val brief = PointOfInterestQuestions.brief(finding("Art Museum", LocalDate.of(2026, 10, 11)), travel)
        val decisionStart = System.nanoTime()
        val response = service.ask(DecisionRequest.of(brief, PointOfInterestQuestions.decisions))
        val decisionMs = (System.nanoTime() - decisionStart) / 1_000_000.0
        assertTrue(((response.answer("usable") as DecisionAnswer.Proposition).outcome as PropositionResult.Answered).answer)
        assertEquals("prioritize", decisionValue((response.answer("next") as DecisionAnswer.Choice).outcome))
        assertEquals("easy", ratingValue((response.answer("effort") as DecisionAnswer.Rating).outcome))
        val classificationStart = System.nanoTime()
        val setting = service.classify(ClassificationRequest.of(brief, PointOfInterestQuestions.setting))
        val classificationMs = (System.nanoTime() - classificationStart) / 1_000_000.0
        assertEquals("indoor", (setting as ClassificationResult.Selected).categoryId)
        println("TRIPPER_DECISION_SMOKE engine=local choice=prioritize setting=indoor " +
            "decisionMs=$decisionMs classificationMs=$classificationMs")
    }

    @Test fun `missing source and out of range date remain distinct`() {
        val noSource = PointOfInterestQuestions.brief(finding("Art Museum", LocalDate.of(2026, 10, 11), false), travel)
        val oldDate = PointOfInterestQuestions.brief(finding("Art Museum", LocalDate.of(2026, 10, 1)), travel)
        assertEquals("review", decisionValue((service.ask(DecisionRequest.of(noSource, PointOfInterestQuestions.decisions))
            .answer("next") as DecisionAnswer.Choice).outcome))
        assertEquals("skip", decisionValue((service.ask(DecisionRequest.of(oldDate, PointOfInterestQuestions.decisions))
            .answer("next") as DecisionAnswer.Choice).outcome))
    }

    @Test fun `ranker uses traveler interests after decision eligibility`() {
        val travelers = Travelers(listOf(Traveler("Pat", "loves art museums")))
        fun reviewed(name: String) = ReviewedPointOfInterest(finding(name, LocalDate.of(2026, 10, 11)),
            "true", "prioritize", "easy", "indoor", "test")
        val ranked = PreferenceAwarePoiRanker(travelers).rank("places", "art", listOf(reviewed("Art Museum"), reviewed("Zoo")))
        assertEquals("Art Museum", ranked.rankings().first().match.name)
    }

    @Test fun `review path passes only eligible researched places to plan text`() {
        val travelers = Travelers(listOf(Traveler("Pat", "art museums")))
        val findings = PointOfInterestFindings(listOf(
            finding("Art Museum", LocalDate.of(2026, 10, 11)),
            finding("Zoo", LocalDate.of(2026, 10, 11), false),
            finding("Old Gallery", LocalDate.of(2026, 10, 1)),
        ))
        val reviewed = PointOfInterestReviewEngine.review(travel, travelers, findings, service, service)
        assertEquals(listOf("Art Museum"), reviewed.ranked.map { it.name })
        assertEquals(setOf("Zoo", "Old Gallery"), reviewed.excluded.map { it.name }.toSet())
        assertTrue(reviewed.planResearchText().contains("Art Museum"))
        assertTrue(!reviewed.planResearchText().contains("Zoo"))
        assertTrue(!reviewed.planResearchText().contains("Old Gallery"))
    }

    @Test fun `optional Dice preference adds a bounded art bonus`() {
        val travelers = Travelers(emptyList())
        val art = ReviewedPointOfInterest(finding("Art Museum", LocalDate.of(2026, 10, 11)),
            "true", "prioritize", "easy", "indoor", "test")
        val plain = PreferenceAwarePoiRanker(travelers).rank("places", "visit", listOf(art))
            .rankings().first().score
        val withDice = PreferenceAwarePoiRanker(travelers,
            DicePoiPreferenceService.Preference("art", 0.15)).rank("places", "visit", listOf(art))
            .rankings().first().score
        assertEquals(0.15, withDice - plain, 1e-9)
    }
}
