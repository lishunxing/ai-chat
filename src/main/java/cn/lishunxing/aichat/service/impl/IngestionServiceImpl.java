package cn.lishunxing.aichat.service.impl;

import cn.lishunxing.aichat.entity.KnowledgeDocument;
import cn.lishunxing.aichat.etl.*;
import cn.lishunxing.aichat.mapper.KnowledgeDocumentMapper;
import cn.lishunxing.aichat.service.IngestionService;
import cn.lishunxing.aichat.source.BlogDataSource;
import cn.lishunxing.aichat.source.BlogDocument;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 文档摄取服务实现 —— 支持多格式文档自动分发
 *
 * @author lishunxing
 */
@Service
public class IngestionServiceImpl implements IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionServiceImpl.class);

    @Autowired
    private BlogDataSource blogDataSource;

    @Autowired
    private DocumentIndexer indexer;

    @Autowired
    private KnowledgeDocumentMapper docMapper;

    private final TextSplitter splitter = new TextSplitter();

    private Map<String, DocumentParser> parserMap;

    @PostConstruct
    public void init() {
        List<DocumentParser> parsers = List.of(
                new MarkdownParser(),
                new PdfParser(),
                new WordParser(),
                new ExcelParser()
        );
        Map<String, DocumentParser> map = new LinkedHashMap<>();
        for (DocumentParser parser : parsers) {
            for (String ext : parser.supportedExtensions()) {
                map.put(ext.toLowerCase(), parser);
            }
        }
        this.parserMap = Collections.unmodifiableMap(map);
        log.info("Registered parsers for extensions: {}", parserMap.keySet());
    }

    @Override
    public int ingestAll() {
        log.info("Starting full ingestion from data source...");
        List<BlogDocument> docs = blogDataSource.fetchAll();
        log.info("Fetched {} documents", docs.size());

        int totalChunks = 0;
        for (BlogDocument doc : docs) {
            try {
                DocumentParser parser = resolveParser(doc.getPath());
                if (parser == null) continue;

                List<DocumentParser.ParsedSection> sections = parser.parse(doc);
                List<TextSplitter.Chunk> allChunks = new ArrayList<>();
                for (DocumentParser.ParsedSection section : sections) {
                    allChunks.addAll(splitter.split(section));
                }

                if (allChunks.isEmpty()) continue;

                indexer.deleteBySource(doc.getPath());
                indexer.indexChunks(allChunks);

                String title = allChunks.get(0).metadata().get("title").toString();

                KnowledgeDocument kd = docMapper.selectByFilePath(doc.getPath());
                if (kd == null) {
                    kd = new KnowledgeDocument();
                }
                kd.setFilePath(doc.getPath());
                kd.setTitle(title);
                kd.setMd5Hash(doc.getFingerprint());
                kd.setChunkCount(allChunks.size());
                kd.setLastIndexedAt(LocalDateTime.now());
                docMapper.insertOrUpdate(kd);

                totalChunks += allChunks.size();
                log.info("Indexed: {} ({} chunks)", doc.getPath(), allChunks.size());

                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Failed to index: {}", doc.getPath(), e);
            }
        }

        log.info("Ingestion complete: {} files, {} chunks", docs.size(), totalChunks);
        return totalChunks;
    }

    @Override
    public void ingestOne(String path) {
        BlogDocument doc = blogDataSource.fetchOne(path);
        if (doc == null) {
            log.warn("Document not found: {}", path);
            return;
        }

        DocumentParser parser = resolveParser(path);
        if (parser == null) return;

        List<DocumentParser.ParsedSection> sections = parser.parse(doc);
        List<TextSplitter.Chunk> allChunks = new ArrayList<>();
        for (DocumentParser.ParsedSection section : sections) {
            allChunks.addAll(splitter.split(section));
        }
        if (allChunks.isEmpty()) return;

        indexer.deleteBySource(path);
        indexer.indexChunks(allChunks);

        String title = allChunks.get(0).metadata().get("title").toString();

        KnowledgeDocument kd = docMapper.selectByFilePath(path);
        if (kd == null) {
            kd = new KnowledgeDocument();
        }
        kd.setFilePath(path);
        kd.setTitle(title);
        kd.setMd5Hash(doc.getFingerprint());
        kd.setChunkCount(allChunks.size());
        kd.setLastIndexedAt(LocalDateTime.now());
        docMapper.insertOrUpdate(kd);

        log.info("Indexed: {} ({} chunks)", path, allChunks.size());
    }

    @Override
    public void deleteOne(String path) {
        indexer.deleteBySource(path);
        docMapper.deleteByFilePath(path);
        log.info("Deleted: {}", path);
    }

    @Override
    public long getDocumentCount() {
        return docMapper.selectCount(null);
    }

    @Override
    public boolean hasChanged(String path, String existingMd5) {
        BlogDocument doc = blogDataSource.fetchOne(path);
        if (doc == null) return true;
        return !doc.getFingerprint().equals(existingMd5);
    }

    private DocumentParser resolveParser(String path) {
        String lower = path.toLowerCase();
        for (Map.Entry<String, DocumentParser> entry : parserMap.entrySet()) {
            if (lower.endsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        log.warn("Unsupported file format, skipped: {}", path);
        return null;
    }
}
