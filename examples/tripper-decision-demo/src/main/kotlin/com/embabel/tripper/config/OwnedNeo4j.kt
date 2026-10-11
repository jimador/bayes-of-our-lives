package com.embabel.tripper.config

import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.event.ContextClosedEvent
import org.springframework.core.env.MapPropertySource
import org.testcontainers.containers.Neo4jContainer
import java.util.UUID

/** Runs only when the explicit Dice option is set; owns and stops its disposable container. */
class OwnedNeo4j : ApplicationContextInitializer<ConfigurableApplicationContext>, AutoCloseable {
    private var container: Neo4jContainer<*>? = null
    override fun initialize(context: ConfigurableApplicationContext) {
        val password = UUID.randomUUID().toString().replace("-", "")
        val owned: Neo4jContainer<*> = Neo4jContainer<Nothing>("neo4j:5.26.1-community")
        owned.withAdminPassword(password)
        try { owned.start() } catch (failure: RuntimeException) {
            try { owned.stop() } catch (cleanup: RuntimeException) { failure.addSuppressed(cleanup) }
            throw failure
        }
        container = owned
        context.environment.propertySources.addFirst(MapPropertySource("owned-tripper-neo4j", mapOf(
            "embabel.dice.store.type" to "graph",
            "database.datasources.neo.type" to "NEO4J",
            "database.datasources.neo.host" to owned.host,
            "database.datasources.neo.port" to owned.getMappedPort(7687),
            "database.datasources.neo.user-name" to "neo4j",
            "database.datasources.neo.password" to password,
            "database.datasources.neo.database-name" to "neo4j",
        )))
        context.addApplicationListener { event -> if (event is ContextClosedEvent) close() }
    }
    @Synchronized override fun close() {
        container?.stop()
        container = null
    }
}
