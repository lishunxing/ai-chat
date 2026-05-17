package cn.lishunxing.aichat.config;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QwenEmbeddingConfig {

    @Value("${qwen.api-key}")
    private String apiKey;

    @Value("${qwen.base-url}")
    private String baseUrl;

    @Value("${qwen.embedding.model}")
    private String model;

    @Bean
    public OpenAiApi qwenApi() {
        return new OpenAiApi(baseUrl, apiKey);
    }

    @Bean
    public OpenAiEmbeddingModel embeddingModel(OpenAiApi qwenApi) {
        return new OpenAiEmbeddingModel(qwenApi,
                MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder()
                        .model(model)
                        .dimensions(1536)
                        .build());
    }
}
