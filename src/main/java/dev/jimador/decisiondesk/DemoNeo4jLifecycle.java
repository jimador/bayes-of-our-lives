package dev.jimador.decisiondesk;

import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.core.env.MapPropertySource;
import org.testcontainers.containers.Neo4jContainer;

/** Starts and stops only this app's disposable Neo4j container. */
final class DemoNeo4jLifecycle implements ApplicationContextInitializer<ConfigurableApplicationContext>, AutoCloseable {
    private Neo4jContainer<?> container;

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        String password = UUID.randomUUID().toString().replace("-", "");
        var owned = new Neo4jContainer<>("neo4j:5.26.1-community").withAdminPassword(password);
        try {
            owned.start();
        } catch (RuntimeException failure) {
            try { owned.stop(); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
        container = owned;
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("owned-demo-neo4j", Map.of(
                "embabel.dice.store.type", "graph",
                "database.datasources.neo.type", "NEO4J",
                "database.datasources.neo.host", owned.getHost(),
                "database.datasources.neo.port", owned.getMappedPort(7687),
                "database.datasources.neo.user-name", "neo4j",
                "database.datasources.neo.password", password,
                "database.datasources.neo.database-name", "neo4j")));
        context.addApplicationListener(event -> {
            if (event instanceof ContextClosedEvent) close();
        });
    }

    @Override
    public synchronized void close() {
        if (container != null) {
            container.stop();
            container = null;
        }
    }
}
