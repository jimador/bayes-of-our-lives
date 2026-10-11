package com.embabel.tripper.agent

import com.embabel.agent.api.common.ranking.Ranker
import com.embabel.agent.api.common.ranking.Ranking
import com.embabel.agent.api.common.ranking.Rankings
import com.embabel.common.ai.classification.ClassificationRequest
import com.embabel.common.ai.classification.ClassificationResult
import com.embabel.common.ai.classification.ClassificationSpec
import com.embabel.common.ai.classification.ClassificationService
import com.embabel.common.ai.classification.ModelProvenance
import com.embabel.common.ai.decision.*
import com.embabel.common.ai.decision.spi.RatingAssessment
import com.embabel.common.core.types.Described
import com.embabel.common.core.types.Named
import java.time.LocalDate
import java.util.EnumSet

/** A compact typed decision contract over Tripper's existing researched points of interest. */
object PointOfInterestQuestions {
    val decisions: DecisionSpec = DecisionSpec.builder()
        .proposition("usable") { it.asking("Are a mapped location and at least one research source present, with visit dates overlapping the trip?") }
        .choice("next") { it.asking("Apply the explicit research policy in the brief")
            .option("prioritize", "Location and source present, and visit dates overlap the travel dates")
            .option("review", "Location or source is missing")
            .option("skip", "Visit dates do not overlap the travel dates") }
        .rating("effort") { it.asking("Rate research effort from the same explicit policy")
            .level("easy", "Prioritize")
            .level("unknown", "Review")
            .level("hard", "Skip") }
        .build()
    val setting: ClassificationSpec = ClassificationSpec.builder()
        .asking("Classify the point of interest setting from the supplied name and description; choose unknown when unclear")
        .category("indoor", "An indoor venue such as a museum or gallery")
        .category("outdoor", "An outdoor place such as a park or trail")
        .category("unknown", "Setting cannot be established")
        .build()

    fun brief(poi: ResearchedPointOfInterest, travel: JourneyTravelBrief): String {
        val point = poi.pointOfInterest
        val hasLocation = point.location.isNotBlank()
        val sourceCount = poi.links.count { it.url.isNotBlank() }
        val overlaps = !point.toDate.isBefore(travel.departureDate) && !point.fromDate.isAfter(travel.returnDate)
        val policy = if (!overlaps) "skip" else if (!hasLocation || sourceCount == 0) "review" else "prioritize"
        return """
            Point of interest: ${point.name}
            Description: ${point.description}
            Mapped location: ${point.location}
            Location present: $hasLocation
            Visit dates: ${point.fromDate} to ${point.toDate}
            Travel dates: ${travel.departureDate} to ${travel.returnDate}
            Dates overlap: $overlaps
            Research source count: $sourceCount
            Research policy: $policy
            Research excerpt: ${poi.research.take(500)}
            Source URLs: ${poi.links.take(3).joinToString { it.url }}
            The policy is a research filter, not proof that the venue is open or suitable.
        """.trimIndent()
    }
}

data class ReviewedPointOfInterest(
    val finding: ResearchedPointOfInterest,
    val proposition: String,
    val choice: String,
    val rating: String,
    val setting: String,
    val model: String,
) : Named, Described {
    override val name: String get() = finding.pointOfInterest.name
    override val description: String get() = finding.pointOfInterest.description
}

data class ReviewedPointsOfInterest(
    val ranked: List<ReviewedPointOfInterest>,
    val excluded: List<ReviewedPointOfInterest>,
)

/** Pure orchestration of all requested decision families before Tripper's existing plan draft. */
object PointOfInterestReviewEngine {
    fun review(
        travelBrief: JourneyTravelBrief,
        travelers: Travelers,
        findings: PointOfInterestFindings,
        decisions: DecisionService,
        classifications: ClassificationService,
        dicePreference: DicePoiPreferenceService.Preference? = null,
    ): ReviewedPointsOfInterest {
        val reviewed = findings.pointsOfInterest.map { finding ->
            val brief = PointOfInterestQuestions.brief(finding, travelBrief)
            val response = decisions.ask(DecisionRequest.of(brief, PointOfInterestQuestions.decisions))
            val proposition = (response.answer("usable") as DecisionAnswer.Proposition).outcome
            val choice = (response.answer("next") as DecisionAnswer.Choice).outcome
            val rating = (response.answer("effort") as DecisionAnswer.Rating).outcome
            val setting = classifications.classify(ClassificationRequest.of(brief, PointOfInterestQuestions.setting))
            ReviewedPointOfInterest(
                finding = finding,
                proposition = when (proposition) {
                    is PropositionResult.Answered -> proposition.answer.toString()
                    else -> proposition.javaClass.simpleName.lowercase()
                },
                choice = decisionValue(choice),
                rating = ratingValue(rating),
                setting = decisionValue(setting),
                model = (proposition as? PropositionResult.Answered)?.provenance?.let {
                    "${it.provider}/${it.modelName}"
                } ?: "unreported",
            )
        }
        val eligible = reviewed.filter { it.proposition == "true" && it.choice == "prioritize"
                && it.rating == "easy" }
        val ranked = PreferenceAwarePoiRanker(travelers, dicePreference)
            .rank("researched points of interest", travelBrief.brief, eligible)
            .rankings().map { it.match }
        return ReviewedPointsOfInterest(ranked, reviewed - ranked.toSet())
    }
}

fun ReviewedPointsOfInterest.planResearchText(): String = ranked.joinToString("\n") {
    """
        ${it.finding.pointOfInterest.name} (decision: ${it.choice}, setting: ${it.setting})
        ${it.finding.research}
        ${it.finding.links.joinToString { link -> "${link.url}: ${link.summary}" }}
        Images: ${it.finding.imageLinks.joinToString { link -> "${link.url}: ${link.summary}" }}
        Videos: ${it.finding.videoLinks.joinToString { link -> "${link.url}: ${link.summary}" }}
    """.trimIndent()
}

/** Local ranking remains separate from the interchangeable decision service. */
class PreferenceAwarePoiRanker(
    private val travelers: Travelers,
    private val dicePreference: DicePoiPreferenceService.Preference? = null,
) : Ranker {
    override fun <T> rank(description: String, userInput: String, rankables: Collection<T>): Rankings<T>
        where T : Named, T : Described {
        val words = (travelers.travelers.joinToString(" ") { it.about + " " + it.name } + " " + userInput)
            .lowercase().split(Regex("[^a-z]+"))
            .filter { it.length >= 4 }.toSet()
        return Rankings(rankables.map { item ->
            require(item is ReviewedPointOfInterest)
            val text = (item.name + " " + item.description).lowercase()
            val match = words.count { text.contains(it) }.coerceAtMost(3)
            val diceBonus = if (dicePreference != null && text.contains(dicePreference.keyword))
                dicePreference.weight else 0.0
            val score = (0.5 + match * 0.12 + diceBonus + if (item.setting != "unknown") 0.04 else 0.0)
                .coerceIn(0.0, 1.0)
            Ranking(item, score)
        })
    }
}

/** Transparent third implementation of Embabel's DecisionService interface. */
class LocalPoiDecisionService : DecisionService, RatingAssessment {
    private val provenance = ModelProvenance("tripper-poi-rules-v1", "local")
    override val name: String = "tripper-poi-rules-v1"
    override val provider: String = "local"
    override fun capabilities(): DecisionCapabilities = DecisionCapabilities.of(EnumSet.allOf(QuestionKind::class.java))
    override fun assess(request: PropositionRequest): PropositionResult =
        PropositionResult.Answered(if (field(request.input, "Outing policy").isNotBlank())
            field(request.input, "Outing policy") != "review" else field(request.input, "Location present") == "true"
            && field(request.input, "Research source count").toIntOrNull()?.let { it > 0 } == true
            && field(request.input, "Dates overlap") == "true", provenance)
    override fun classify(request: ClassificationRequest): ClassificationResult {
        val ids = request.spec.categories.map { it.id }.toSet()
        if ("candidate" in ids && field(request.input, "Outing policy").isNotBlank())
            return request.spec.selected(field(request.input, "Outing policy"), provenance)
        if ("nearby" in ids)
            return request.spec.selected(field(request.input, "Outing category"), provenance)
        if ("prioritize" in ids) return request.spec.selected(field(request.input, "Research policy"), provenance)
        val text = (field(request.input, "Point of interest") + " " + field(request.input, "Description")).lowercase()
        val setting = when {
            listOf("museum", "gallery", "theatre", "theater").any(text::contains) -> "indoor"
            listOf("park", "trail", "garden", "beach").any(text::contains) -> "outdoor"
            else -> "unknown"
        }
        return request.spec.selected(setting, provenance)
    }
    override fun rate(input: String, question: RatingQuestionSpec): RatingResult = RatingResult.Answered(
        provenance, if (field(input, "Outing policy").isNotBlank())
            when (field(input, "Outing policy")) { "candidate" -> "easy"; "defer" -> "hard"; else -> "unknown" }
        else when (field(input, "Research policy")) { "prioritize" -> "easy"; "skip" -> "hard"; else -> "unknown" })
    private fun field(input: String, key: String): String = input.lineSequence()
        .firstOrNull { it.startsWith("$key: ") }?.substringAfter(": ")?.trim().orEmpty()
}

fun decisionValue(result: ClassificationResult): String = when (result) {
    is ClassificationResult.Selected -> result.categoryId
    is ClassificationResult.Failure -> "failure:${result.reason}"
    else -> result.javaClass.simpleName.lowercase()
}
fun ratingValue(result: RatingResult): String = when (result) {
    is RatingResult.Answered -> result.selectedLevelId
        ?: result.distribution.maxByOrNull { it.probability }?.takeIf { it.probability > 0.5 }?.levelId
        ?: "unselected"
    is RatingResult.Failure -> "failure:${result.reason}"
    else -> result.javaClass.simpleName.lowercase()
}
