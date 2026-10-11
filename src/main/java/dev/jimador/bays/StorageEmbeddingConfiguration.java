package dev.jimador.bays;

import com.embabel.common.ai.model.EmbeddingService;
import com.embabel.common.ai.model.PricingModel;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/** Dice needs vectors on save; this exact-context demo never performs semantic search. */
@Configuration
class StorageEmbeddingConfiguration {
    @Bean @Primary EmbeddingService storageEmbedding() {
        return new EmbeddingService() {
            @Override public String getName() { return "bays-storage-vector"; }
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
