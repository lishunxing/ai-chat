package cn.lishunxing.aichat.watch;

import cn.lishunxing.aichat.entity.KnowledgeDocument;
import cn.lishunxing.aichat.mapper.KnowledgeDocumentMapper;
import cn.lishunxing.aichat.service.IngestionService;
import cn.lishunxing.aichat.source.BlogDataSource;
import cn.lishunxing.aichat.source.BlogDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 博客同步调度器 —— 定期从数据源拉取文档并增量索引
 * <p>
 * 当 blog.source=prod 时跳过所有同步操作。
 * </p>
 *
 * @author lishunxing
 */
@Component
public class BlogSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(BlogSyncScheduler.class);

    private final BlogDataSource blogDataSource;
    private final IngestionService ingestionService;
    private final KnowledgeDocumentMapper docMapper;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Value("${blog.source:local}")
    private String blogSource;

    public BlogSyncScheduler(BlogDataSource blogDataSource,
                             IngestionService ingestionService,
                             KnowledgeDocumentMapper docMapper) {
        this.blogDataSource = blogDataSource;
        this.ingestionService = ingestionService;
        this.docMapper = docMapper;
    }

    /**
     * 启动后异步执行首次索引
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if ("prod".equalsIgnoreCase(blogSource)) {
            log.info("blog.source=prod, skipping all sync operations");
            return;
        }
        executor.submit(() -> {
            try {
                log.info("Starting initial index check...");
                if (docMapper.selectCount(null) == 0) {
                    log.info("Empty knowledge_document table, performing full ingestion...");
                    ingestionService.ingestAll();
                } else {
                    log.info("Existing index found, running incremental sync...");
                    incrementalSync();
                }
            } catch (Exception e) {
                log.error("Initial sync failed, will retry via scheduled task: {}", e.getMessage());
            }
        });
    }

    /**
     * 定时增量同步 —— 每 10 分钟执行
     */
    @Scheduled(fixedRate = 600_000)
    public void incrementalSync() {
        if ("prod".equalsIgnoreCase(blogSource)) {
            return;
        }
        log.info("Running incremental sync...");
        try {
            // 数据源上的所有文档
            List<BlogDocument> sourceDocs = blogDataSource.fetchAll();
            Map<String, String> sourceMap = new HashMap<>();
            for (BlogDocument doc : sourceDocs) {
                sourceMap.put(doc.getPath(), doc.getFingerprint());
            }

            // 数据库中的所有记录
            List<KnowledgeDocument> dbDocs = docMapper.selectAll();
            Map<String, String> dbMap = new HashMap<>();
            for (KnowledgeDocument kd : dbDocs) {
                dbMap.put(kd.getFilePath(), kd.getMd5Hash());
            }

            // 新增或变更
            for (Map.Entry<String, String> entry : sourceMap.entrySet()) {
                String path = entry.getKey();
                String fingerprint = entry.getValue();
                String dbFingerprint = dbMap.get(path);

                if (dbFingerprint == null) {
                    log.info("New document: {}", path);
                    ingestionService.ingestOne(path);
                } else if (!fingerprint.equals(dbFingerprint)) {
                    log.info("Changed document: {}", path);
                    ingestionService.ingestOne(path);
                }
            }

            // 已删除
            for (String dbPath : dbMap.keySet()) {
                if (!sourceMap.containsKey(dbPath)) {
                    log.info("Deleted document: {}", dbPath);
                    ingestionService.deleteOne(dbPath);
                }
            }
        } catch (Exception e) {
            log.error("Incremental sync failed", e);
        }
    }
}
