/*
 * Copyright 2024-2025 Embabel Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.embabel.tripper

import com.embabel.agent.config.annotation.EnableAgents
import com.embabel.agent.config.annotation.LoggingThemes
import com.embabel.agent.config.annotation.McpServers
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.SpringApplication
import com.embabel.tripper.config.OwnedNeo4j

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAgents(
    loggingTheme = LoggingThemes.SEVERANCE,
    mcpServers = [McpServers.DOCKER_DESKTOP],
)
class TripperApplication

fun main(args: Array<String>) {
    val dice = args.any { it == "--tripper.dice.enabled=true" }
    val outing = args.any { it == "--tripper.outing.run=true" }
    val engine = args.firstOrNull { it.startsWith("--tripper.decision.engine=") }
        ?.substringAfter('=') ?: "local"
    val owned = if (dice) OwnedNeo4j() else null
    val app = SpringApplication(TripperApplication::class.java)
    if (owned != null) app.addInitializers(owned)
    // Embabel registers an LLM for the original journey agent even in a code-only outing.
    // The inert default lets local/JEV outings start without an unrelated OpenAI key.
    if (engine != "openai" && System.getenv("OPENAI_API_KEY").isNullOrBlank()) {
        app.setDefaultProperties(mapOf("embabel.agent.platform.models.openai.api-key" to "unused-local-mode"))
    }
    try {
        val context = app.run(*args)
        if (outing) SpringApplication.exit(context)
    } catch (failure: RuntimeException) {
        owned?.close()
        throw failure
    }
}
