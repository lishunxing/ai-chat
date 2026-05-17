package cn.lishunxing.aichat.etl;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.dml.DeleteParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 文档索引器 —— 将文本块写入 Milvus 向量数据库
 * <p>
 * indexChunks: 批量向量化并 upsert 到 Milvus
 * deleteBySource: 根据 source 路径删除旧数据(Collection 不存在时静默跳过)
 * </p>
 *
 * @author lishunxing
 */
@Component
public class DocumentIndexer {

    private static final Logger log = LoggerFactory.getLogger(DocumentIndexer.class);

    /** 千问 Embedding API 单次最多接受 10 条 */
    private static final int BATCH_SIZE = 10;

    private final MilvusVectorStore vectorStore;
    private final MilvusServiceClient milvusClient;
    private final String collectionName;

    public DocumentIndexer(MilvusVectorStore vectorStore,
                           MilvusServiceClient milvusClient,
                           @Value("${milvus.collection}") String collectionName) {
        this.vectorStore = vectorStore;
        this.milvusClient = milvusClient;
        this.collectionName = collectionName;
    }

    /**
     * 批量索引文本块到 Milvus, 自动按 10 条一批调用千问 Embedding API
     * <p>
     * MilvusVectorStore.doAdd 内部会自动调用 EmbeddingModel 向量化,
     * 若 Collection 不存在则自动创建。
     * </p>
     */
    public void indexChunks(List<TextSplitter.Chunk> chunks) {
        if (chunks.isEmpty()) return;

        List<Document> docs = chunks.stream().map(chunk -> {
            String id = UUID.randomUUID().toString();
            return new Document(id, chunk.text(), chunk.metadata());
        }).collect(Collectors.toList());

        // 分批写入, 尊重千问 API 的 10 条限制
        for (int i = 0; i < docs.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, docs.size());
            List<Document> batch = docs.subList(i, end);
            vectorStore.doAdd(batch);
            log.debug("Indexed batch {}-{}/{} chunks", i + 1, end, docs.size());
        }
    }

    /**
     * 根据 source 路径删除 Milvus 中的旧数据
     * <p>
     * Spring AI 的 MilvusVectorStore 将 metadata 存储在 JSON 字段中,
     * 需要通过 metadata["key"] 语法过滤。若 Collection 不存在则静默跳过。
     * </p>
     */
    public void deleteBySource(String sourcePath) {
        try {
            milvusClient.delete(
                DeleteParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withExpr("metadata[\"source\"] == \"" + sourcePath + "\"")
                    .build()
            );
        } catch (Exception e) {
            log.debug("Delete by source skipped: {}", e.getMessage());
        }
    }
}
