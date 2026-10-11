package com.embabel.tripper.config

import com.embabel.agent.spi.decision.LlmDecisionServiceFactory
import com.embabel.agent.typesafe.TypeSafeModelFactory
import com.embabel.common.ai.decision.DecisionService
import com.embabel.tripper.agent.LocalPoiDecisionService
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** One bean name for Tripper's researched-POI decision action. Keys stay in process environment. */
@Configuration
class DecisionConfig {
    @Bean("tripperDecisionService")
    fun decisionService(
        @Value("\${tripper.decision.engine:local}") engine: String,
        @Value("\${tripper.decision.openai-model:gpt-4.1-mini}") openAiModel: String,
        llmFactory: ObjectProvider<LlmDecisionServiceFactory>,
    ): DecisionService = when (engine) {
        "local" -> LocalPoiDecisionService()
        "jev" -> TypeSafeModelFactory({ requireKey("TYPESAFE_API_KEY") }).build()
        "openai" -> llmFactory.getObject().decisionService(openAiModel)
        else -> throw IllegalArgumentException("tripper.decision.engine must be local, jev, or openai")
    }
    private fun requireKey(name: String): String = System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: throw IllegalStateException("$name is required for the selected decision engine")
}
