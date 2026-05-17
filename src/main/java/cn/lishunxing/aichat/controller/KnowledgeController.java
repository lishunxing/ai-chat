package cn.lishunxing.aichat.controller;

import cn.lishunxing.aichat.entity.KnowledgeDocument;
import cn.lishunxing.aichat.mapper.KnowledgeDocumentMapper;
import cn.lishunxing.aichat.service.IngestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 知识库管理 REST API
 *
 * @author lishunxing
 */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    @Autowired
    private IngestionService ingestionService;

    @Autowired
    private KnowledgeDocumentMapper docMapper;

    /**
     * 获取知识库整体状态
     */
    @GetMapping("/status")
    public Map<String, Object> status() {
        long indexed = docMapper.selectCount(null);
        Optional<LocalDateTime> latest = docMapper.selectAll().stream()
                .map(KnowledgeDocument::getLastIndexedAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("documentsIndexed", indexed);
        result.put("lastIndexedAt", latest.orElse(null));
        result.put("hasErrors", false);
        return result;
    }

    /**
     * 获取已索引文档列表
     */
    @GetMapping("/documents")
    public List<Map<String, Object>> documents() {
        List<Map<String, Object>> list = new ArrayList<>();
        docMapper.selectAll().forEach(doc -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("filePath", doc.getFilePath());
            item.put("title", doc.getTitle());
            item.put("chunkCount", doc.getChunkCount());
            item.put("lastIndexedAt", doc.getLastIndexedAt());
            list.add(item);
        });
        return list;
    }

    /**
     * 手动触发全量重索引
     */
    @PostMapping("/reindex")
    public Map<String, Object> reindex() {
        int chunks = ingestionService.ingestAll();
        return Map.of("success", true, "chunksIndexed", chunks);
    }
}
