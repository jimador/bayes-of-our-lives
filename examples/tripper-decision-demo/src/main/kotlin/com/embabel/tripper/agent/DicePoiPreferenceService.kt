package com.embabel.tripper.agent

import com.embabel.dice.proposition.Proposition
import com.embabel.dice.proposition.PropositionRepository
import com.embabel.dice.proposition.PropositionStatus
import com.embabel.dice.proposition.PropositionStoreType
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.io.ResourceLoader
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.Properties

/** Optional synthetic preference persisted and read through Dice's graph repository. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = ["tripper.dice.enabled"], havingValue = "true")
class DicePoiPreferenceService(
    private val repository: PropositionRepository,
    private val resources: ResourceLoader,
) : CommandLineRunner {
    data class Preference(val keyword: String, val weight: Double)
    private val contextId = "fictional-tripper-preferences"

    override fun run(vararg args: String) {
        require(repository.storeType == PropositionStoreType.STORED) { "Dice graph store was not selected" }
        val seed = Properties()
        resources.getResource("classpath:tripper-dice.properties").inputStream.use(seed::load)
        val keyword = seed.getProperty("keyword").trim().lowercase()
        val weight = seed.getProperty("weight").toDouble()
        require(keyword.matches(Regex("[a-z]{3,24}"))) { "Invalid fictional interest keyword" }
        require(weight.isFinite() && weight in 0.0..0.3) { "Invalid fictional interest weight" }
        val now = Instant.now()
        val proposition = Proposition.create("tripper-fictional-interest", contextId,
            "This fictional traveler has an extra interest in $keyword.", emptyList(), 1.0, 0.0,
            0.5, "synthetic seed", emptyList(), now, now, now, PropositionStatus.ACTIVE)
            .withMetadataValue("keyword", keyword).withMetadataValue("weight", weight)
        repository.save(proposition)
    }

    fun read(): Preference {
        val metadata = repository.findByContextIdValue(contextId)
            .firstOrNull { it.id == "tripper-fictional-interest" }?.metadata
            ?: error("Fictional Dice preference missing")
        return Preference(metadata["keyword"] as String, (metadata["weight"] as Number).toDouble())
    }
}
