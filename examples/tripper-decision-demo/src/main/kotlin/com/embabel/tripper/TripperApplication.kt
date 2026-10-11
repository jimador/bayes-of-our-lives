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
import org.springframework.boot.runApplication
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
    if (!dice) {
        val context = runApplication<TripperApplication>(*args)
        if (outing) SpringApplication.exit(context)
        return
    }
    val owned = OwnedNeo4j()
    val app = SpringApplication(TripperApplication::class.java)
    app.addInitializers(owned)
    try {
        val context = app.run(*args)
        if (outing) SpringApplication.exit(context)
    } catch (failure: RuntimeException) {
        owned.close()
        throw failure
    }
}
