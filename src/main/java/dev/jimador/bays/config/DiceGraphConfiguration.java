package dev.jimador.bays.config;

import org.drivine.autoconfigure.EnableDrivine;
import org.drivine.autoconfigure.EnableDrivinePropertiesConfig;
import org.drivine.manager.GraphObjectManager;
import org.drivine.manager.GraphObjectManagerFactory;
import org.drivine.manager.PersistenceManager;
import org.drivine.manager.PersistenceManagerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "demo.dice.enabled", havingValue = "true")
@EnableDrivine
@EnableDrivinePropertiesConfig
class DiceGraphConfiguration {
    @Bean GraphObjectManager graphObjectManager(GraphObjectManagerFactory factory) { return factory.get("neo"); }
    @Bean PersistenceManager persistenceManager(PersistenceManagerFactory factory) { return factory.get("neo"); }

}
