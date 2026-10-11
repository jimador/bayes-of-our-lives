package com.embabel.tripper.agent

import com.embabel.agent.core.AgentPlatform
import com.embabel.agent.core.AgentProcessStatusCode
import com.embabel.agent.core.ProcessOptions
import com.embabel.agent.core.last
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.Instant

/** A bounded CLI entrypoint for the public-data profile; the full journey UI is unchanged. */
@Component
@ConditionalOnProperty(name = ["tripper.outing.run"], havingValue = "true")
class CityOutingRunner(
    private val platform: AgentPlatform,
    @Value("\${tripper.decision.engine:local}") private val engine: String,
    @Value("\${tripper.outing.destination:Miami, Florida}") private val destination: String,
    @Value("\${tripper.outing.origin:}") private val origin: String,
    @Value("\${tripper.outing.start-utc:}") private val startUtc: String,
    @Value("\${tripper.outing.simulated-traffic-delay-minutes:0}") private val simulatedDelay: Int,
) : CommandLineRunner {
    override fun run(vararg args: String) {
        val agent = platform.agents().firstOrNull { it.name.contains("CityOutingAgent") }
            ?: error("CityOutingAgent was not deployed")
        val request = CityOutingAgent.Request(destination, origin.ifBlank { null },
            startUtc.takeIf { it.isNotBlank() }?.let(Instant::parse), simulatedDelay)
        val start = System.nanoTime()
        val process = platform.createAgentProcessFrom(agent, ProcessOptions(), request).run()
        val processMs = (System.nanoTime() - start) / 1_000_000.0
        require(process.status == AgentProcessStatusCode.COMPLETED) { "Outing process: ${process.status} ${process.failureInfo}" }
        val plan = process.last<CityOutingAgent.Plan>() ?: error("No outing plan")
        println("engine=$engine mode=city-outing city=${plan.city}" +
            " status=${plan.status} selectedResearchLead=${plan.selectedResearchLead} ranked=${plan.rankedOptions}" +
            " startUtc=${plan.startUtc} retrievedUtc=${plan.retrievedUtc} preferenceSource=${plan.preferenceSource}" +
            " providerMs=${plan.providerNanos / 1_000_000.0} rankMs=${plan.rankNanos / 1_000_000.0} processMs=$processMs" +
            " geocodingSource=${plan.geocodingUrl}")
        println("placeData=© OpenStreetMap contributors (ODbL) https://www.openstreetmap.org/copyright")
        plan.assessments.forEach { a ->
            val o = a.option
            println("place=${o.place.name} source=${o.place.sourceUrl} sampleUtc=${o.forecastHourUtc}" +
                " weatherSource=${o.weatherUrl} temperatureC=${o.temperatureC}" +
                " rainProbabilityPercent=${o.rainProbability} windKt=${o.windKt}" +
                " osrmBaseDriveMinutes=${o.baseDriveMinutes} simulatedTrafficDelayMinutes=$simulatedDelay" +
                " localPolicy=${a.localPolicy} proposition=${a.proposition} choice=${a.choice}" +
                " rating=${a.rating} classification=${a.classification} model=${a.model}" +
                " decisionMs=${a.decisionNanos / 1_000_000.0} classificationMs=${a.classificationNanos / 1_000_000.0}")
        }
        println("hoursAdmissionAccessibilityUnverified=true liveTrafficUnknown=true fictionalThresholds=true")
        println("actions=" + process.history.joinToString(" -> ") { it.actionName })
    }
}
