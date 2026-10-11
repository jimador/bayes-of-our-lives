package com.embabel.tripper.agent

import com.embabel.agent.api.annotation.Action
import com.embabel.agent.api.annotation.AchievesGoal
import com.embabel.agent.api.annotation.Agent
import com.embabel.agent.api.common.OperationContext
import com.embabel.agent.api.common.ranking.Ranker
import com.embabel.agent.api.common.ranking.Ranking
import com.embabel.agent.api.common.ranking.Rankings
import com.embabel.common.ai.classification.ClassificationRequest
import com.embabel.common.ai.classification.ClassificationSpec
import com.embabel.common.ai.decision.*
import com.embabel.common.core.types.Described
import com.embabel.common.core.types.Named
import java.time.Instant

/** A small public-data outing beside Tripper's existing full journey planner. */
@Agent(description = "Research a museum afternoon in any geocodable city")
class CityOutingAgent(
    private val provider: CityOutingProvider,
    private val dicePreferenceService: DicePoiPreferenceService? = null,
) {
    data class Request(val destination: String, val origin: String? = null, val startUtc: Instant? = null,
                       val simulatedTrafficDelayMinutes: Int = 0)
    data class Brief(val option: CityOutingProvider.Option, val text: String, val localPolicy: String)
    data class Research(val city: String, val startUtc: Instant, val retrievedUtc: Instant,
                        val geocodingUrl: String, val briefs: List<Brief>, val providerNanos: Long)
    data class Assessment(val option: CityOutingProvider.Option, val localPolicy: String,
                          val proposition: String, val choice: String, val rating: String,
                          val classification: String, val model: String,
                          val decisionNanos: Long, val classificationNanos: Long)
    data class Assessments(val city: String, val startUtc: Instant, val retrievedUtc: Instant,
                           val geocodingUrl: String, val options: List<Assessment>, val providerNanos: Long)
    data class Preferences(val stored: DicePoiPreferenceService.Preference?)
    data class Ranked(val ordered: List<Assessment>, val rankNanos: Long)
    data class Plan(val city: String, val selectedResearchLead: String?, val status: String,
                    val startUtc: Instant, val retrievedUtc: Instant, val geocodingUrl: String,
                    val rankedOptions: List<String>, val assessments: List<Assessment>,
                    val providerNanos: Long, val rankNanos: Long, val preferenceSource: String)

    companion object {
        val questions: DecisionSpec = DecisionSpec.builder()
            .proposition("complete") { it.asking("Are temperature, rain probability, and base drive estimate present for this museum lead?") }
            .choice("outing") { it.asking("Apply the fictional museum-afternoon policy stated in the brief")
                .option("candidate", "Required readings present, rain probability at most 50 percent, total illustrative drive at most 45 minutes")
                .option("review", "A required reading or route is missing")
                .option("defer", "Complete but a fictional rain or drive threshold is exceeded") }
            .rating("effort") { it.asking("Rate effort using the same fictional policy")
                .level("easy", "Candidate research lead")
                .level("unknown", "Missing reading or route")
                .level("hard", "Fictional threshold exceeded") }
            .build()
        val distance: ClassificationSpec = ClassificationSpec.builder()
            .asking("Classify the base drive estimate in the brief")
            .category("nearby", "Base drive at most 30 minutes")
            .category("long-drive", "Base drive over 30 minutes or unavailable")
            .build()

        fun reviewPolicy(option: CityOutingProvider.Option, simulatedDelay: Int): String {
            val total = option.baseDriveMinutes?.plus(simulatedDelay)
            return when {
                option.temperatureC == null || option.rainProbability == null || total == null -> "review"
                option.rainProbability > 50 || total > 45 -> "defer"
                else -> "candidate"
            }
        }
        fun brief(option: CityOutingProvider.Option, simulatedDelay: Int): Brief {
            val policy = reviewPolicy(option, simulatedDelay)
            val category = if (option.baseDriveMinutes != null && option.baseDriveMinutes <= 30) "nearby" else "long-drive"
            return Brief(option, """
                Museum lead: ${option.place.name}
                Mapped source: ${option.place.sourceUrl}
                Forecast source: ${option.weatherUrl}
                Forecast sample UTC: ${option.forecastHourUtc}
                Temperature C: ${option.temperatureC}
                Rain probability percent: ${option.rainProbability}
                Wind knots: ${option.windKt}
                Base drive estimate minutes: ${option.baseDriveMinutes}
                Simulated traffic delay minutes: $simulatedDelay
                Total illustrative drive minutes: ${option.baseDriveMinutes?.plus(simulatedDelay)}
                Outing category: $category
                Outing policy: $policy
                Opening hours, admission, accessibility, and live traffic are unverified.
                Candidate is a research lead, not a confirmed visit. The thresholds and delay are fictional.
            """.trimIndent(), policy)
        }
    }

    @Action fun discover(request: Request): Research {
        require(request.simulatedTrafficDelayMinutes in 0..180) { "Simulated traffic delay must be 0..180 minutes" }
        val start = System.nanoTime()
        val result = provider.search(request.destination, request.origin, request.startUtc)
        return Research(result.city, result.startUtc, result.retrievedUtc, result.geocodingUrl,
            result.options.map { brief(it, request.simulatedTrafficDelayMinutes) }, System.nanoTime() - start)
    }

    @Action fun assess(research: Research, context: OperationContext): Assessments {
        val service = context.ai().decisions().named("tripperDecisionService")
        val classifier = context.ai().classifications().named("tripperDecisionService")
        val values = research.briefs.map { brief ->
            val start = System.nanoTime()
            val response = service.ask(DecisionRequest.of(brief.text, questions))
            val decisionNanos = System.nanoTime() - start
            val proposition = (response.answer("complete") as DecisionAnswer.Proposition).outcome
            val choice = (response.answer("outing") as DecisionAnswer.Choice).outcome
            val rating = (response.answer("effort") as DecisionAnswer.Rating).outcome
            val classStart = System.nanoTime()
            val classification = classifier.classify(ClassificationRequest.of(brief.text, distance))
            val classNanos = System.nanoTime() - classStart
            Assessment(brief.option, brief.localPolicy,
                if (proposition is PropositionResult.Answered) proposition.answer.toString() else proposition.javaClass.simpleName.lowercase(),
                decisionValue(choice), ratingValue(rating), decisionValue(classification),
                (proposition as? PropositionResult.Answered)?.provenance?.let { "${it.provider}/${it.modelName}" } ?: "unreported",
                decisionNanos, classNanos)
        }
        return Assessments(research.city, research.startUtc, research.retrievedUtc,
            research.geocodingUrl, values, research.providerNanos)
    }

    @Action fun preferences(request: Request): Preferences = Preferences(dicePreferenceService?.read())

    @Action fun rank(assessments: Assessments, preferences: Preferences): Ranked {
        val eligible = assessments.options.filter { it.proposition == "true" && it.choice == "candidate"
            && it.rating == "easy" && it.classification in setOf("nearby", "long-drive") }
        val start = System.nanoTime()
        val ranked = OutingRanker(preferences.stored).rank("sourced museum leads", "short drive and low rain",
            eligible.map(::OutingItem)).rankings().map { it.match.assessment }
        return Ranked(ranked, System.nanoTime() - start)
    }

    @Action @AchievesGoal(description = "Return a sourced and explicitly provisional afternoon research plan")
    fun finish(assessments: Assessments, ranked: Ranked, preferences: Preferences): Plan =
        Plan(assessments.city, ranked.ordered.firstOrNull()?.option?.place?.name, "research",
            assessments.startUtc, assessments.retrievedUtc, assessments.geocodingUrl,
            ranked.ordered.map { it.option.place.name }, assessments.options,
            assessments.providerNanos, ranked.rankNanos, if (preferences.stored == null) "default" else "dice-graph")

    data class OutingItem(val assessment: Assessment) : Named, Described {
        override val name: String get() = assessment.option.place.name
        override val description: String get() = assessment.option.place.sourceUrl
    }
    class OutingRanker(private val dice: DicePoiPreferenceService.Preference?) : Ranker {
        override fun <T> rank(description: String, userInput: String, rankables: Collection<T>): Rankings<T>
            where T : Named, T : Described = Rankings(rankables.map { item ->
                require(item is OutingItem)
                val o = item.assessment.option
                val diceBonus = if (dice != null && (item.name + " " + item.description)
                        .contains(dice.keyword, ignoreCase = true)) dice.weight else 0.0
                val score = (1.0 - (o.baseDriveMinutes?.times(0.01) ?: 0.25)
                    - (o.rainProbability?.div(250) ?: 0.25) - (o.windKt?.times(0.01) ?: 0.05)
                    + diceBonus).coerceIn(0.0, 1.0)
                Ranking(item, score)
            })
    }
}
