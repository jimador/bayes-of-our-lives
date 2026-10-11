package com.embabel.tripper.config

import com.embabel.common.ai.model.EmbeddingService
import com.embabel.common.ai.model.PricingModel
import org.drivine.autoconfigure.EnableDrivine
import org.drivine.autoconfigure.EnableDrivinePropertiesConfig
import org.drivine.manager.GraphObjectManager
import org.drivine.manager.GraphObjectManagerFactory
import org.drivine.manager.PersistenceManager
import org.drivine.manager.PersistenceManagerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

@Configuration
@ConditionalOnProperty(name = ["tripper.dice.enabled"], havingValue = "true")
@EnableDrivine
@EnableDrivinePropertiesConfig
class DicePreferenceConfig {
    @Bean fun graphObjectManager(factory: GraphObjectManagerFactory): GraphObjectManager = factory.get("neo")
    @Bean fun persistenceManager(factory: PersistenceManagerFactory): PersistenceManager = factory.get("neo")

    /** Storage-only fixed vectors; no semantic similarity claim or external embedding service. */
    @Bean @Primary fun diceStorageEmbedding(): EmbeddingService = object : EmbeddingService {
        override val name: String = "tripper-storage-vector"
        override val provider: String = "local"
        override val pricingModel: PricingModel? = null
        override val dimensions: Int = 8
        override fun embed(text: String): FloatArray {
            val vector = FloatArray(8)
            text.forEachIndexed { index, char -> vector[index % 8] += char.code / 65535.0f }
            return vector
        }
        override fun embed(texts: List<String>): List<FloatArray> = texts.map(::embed)
    }
}
