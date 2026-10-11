package dev.jimador.decisiondesk;

import com.embabel.common.ai.model.EmbeddingService;
import com.embabel.common.ai.model.PricingModel;
import org.drivine.autoconfigure.EnableDrivine;
import org.drivine.autoconfigure.EnableDrivinePropertiesConfig;
import org.drivine.manager.GraphObjectManager;
import org.drivine.manager.GraphObjectManagerFactory;
import org.drivine.manager.PersistenceManager;
import org.drivine.manager.PersistenceManagerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import java.util.List;

@Configuration
@ConditionalOnProperty(name = "demo.dice.enabled", havingValue = "true")
@EnableDrivine
@EnableDrivinePropertiesConfig
class DiceDemoConfiguration {
    @Bean GraphObjectManager graphObjectManager(GraphObjectManagerFactory factory) { return factory.get("neo"); }
    @Bean PersistenceManager persistenceManager(PersistenceManagerFactory factory) { return factory.get("neo"); }

    /** Fixed local vectors permit proposition storage; this demo never performs semantic vector search. */
    @Bean @Primary
    EmbeddingService demoEmbeddingService() {
        return new EmbeddingService() {
            @Override public String getName() { return "synthetic-text-vector"; }
            @Override public String getProvider() { return "local"; }
            @Override public PricingModel getPricingModel() { return null; }
            @Override public int getDimensions() { return 8; }
            @Override public float[] embed(String text) {
                float[] vector = new float[8];
                for (int i = 0; i < text.length(); i++) vector[i % 8] += text.charAt(i) / 65535.0f;
                return vector;
            }
            @Override public List<float[]> embed(List<String> texts) { return texts.stream().map(this::embed).toList(); }
        };
    }
}
