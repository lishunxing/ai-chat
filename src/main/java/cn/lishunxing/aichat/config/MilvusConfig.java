package cn.lishunxing.aichat.config;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MilvusConfig {

    @Value("${milvus.host}")
    private String host;

    @Value("${milvus.port}")
    private int port;

    @Value("${milvus.collection}")
    private String collectionName;

    @Value("${milvus.vector-dimension}")
    private int vectorDimension;

    @Bean
    public MilvusServiceClient milvusServiceClient() {
        return new MilvusServiceClient(
                ConnectParam.newBuilder()
                        .withHost(host)
                        .withPort(port)
                        .build());
    }

    @Bean
    public MilvusVectorStore vectorStore(MilvusServiceClient milvusServiceClient,
                                         EmbeddingModel embeddingModel) {
        return MilvusVectorStore.builder(milvusServiceClient, embeddingModel)
                .collectionName(collectionName)
                .embeddingDimension(vectorDimension)
                .initializeSchema(true)
                .build();
    }
}
