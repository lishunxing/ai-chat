package cn.lishunxing.aichat.service.impl;

import cn.lishunxing.aichat.dto.ChatResponse;
import cn.lishunxing.aichat.entity.ChatMemory;
import cn.lishunxing.aichat.mapper.ChatMemoryMapper;
import cn.lishunxing.aichat.service.RagChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.*;

/**
 * RAG 对话服务实现 —— 流式 SSE
 *
 * @author lishunxing
 */
@Service
public class RagChatServiceImpl implements RagChatService {

    private static final Logger log = LoggerFactory.getLogger(RagChatServiceImpl.class);

    @Autowired
    private OpenAiChatModel chatModel;

    @Autowired
    private MilvusVectorStore vectorStore;

    @Autowired
    private ChatMemoryMapper chatMemoryMapper;

    private static final int TOP_K = 3;
    private static final int MAX_KNOWLEDGE_LEN = 300;
    private static final int MAX_HISTORY = 5;

    private static final String SYSTEM_PROMPT = """
            你是"果壳AI"，一个技术博客助手，专注于 Spring、Spring Cloud、JUC、MySQL、Redis、JVM、RabbitMQ、算法。
            回答规则：
            1. 优先使用【知识片段】回答，末尾标注来源
            2. 片段不足时可结合通用知识，区分来源
            3. 准确简洁，中文回答
            4. 问题与博客无关时引导用户提问技术问题
            """;

    @Override
    public Flux<ChatResponse> chatStream(String conversationId, String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return Flux.error(new IllegalArgumentException("消息不能为空"));
        }

        final String cid = (conversationId == null || conversationId.isBlank())
                ? UUID.randomUUID().toString()
                : conversationId;

        List<Document> relevantDocs = searchKnowledge(userMessage);
        String knowledge = formatKnowledge(relevantDocs);
        String history = loadHistory(cid);

        SystemMessage sysMsg = new SystemMessage(SYSTEM_PROMPT);
        String contextBlock = buildContextBlock(knowledge, history);
        UserMessage userMsg = new UserMessage(contextBlock + "\n\n【用户问题】\n" + userMessage);
        Prompt prompt = new Prompt(List.of(sysMsg, userMsg));

        List<ChatResponse.SourceRef> sources = extractSources(relevantDocs);

        final StringBuilder fullContent = new StringBuilder();

        return chatModel.stream(prompt)
                .<ChatResponse>handle((resp, sink) -> {
                    String text = resp != null && resp.getResult() != null && resp.getResult().getOutput() != null
                            ? resp.getResult().getOutput().getText()
                            : null;
                    if (text != null) {
                        fullContent.append(text);
                        sink.next(new ChatResponse(cid, text, null));
                    }
                })
                .concatWith(Flux.defer(() -> {
                    saveHistory(cid, userMessage, fullContent.toString());
                    return Flux.just(new ChatResponse(cid, fullContent.toString(), sources));
                }))
                .doOnError(e -> log.error("DeepSeek stream failed", e));
    }

    private String buildContextBlock(String knowledge, String history) {
        StringBuilder sb = new StringBuilder();
        sb.append("【知识片段】\n");
        sb.append(knowledge.isEmpty() ? "暂无相关知识片段" : knowledge);
        sb.append("\n【历史对话】\n");
        sb.append(history.isEmpty() ? "暂无历史对话" : history);
        return sb.toString();
    }

    private List<Document> searchKnowledge(String query) {
        try {
            return vectorStore.doSimilaritySearch(
                    SearchRequest.builder().query(query).topK(TOP_K).build());
        } catch (Exception e) {
            log.warn("Milvus search failed", e);
            return List.of();
        }
    }

    private String formatKnowledge(List<Document> docs) {
        if (docs.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < docs.size(); i++) {
            Document doc = docs.get(i);
            String source = doc.getMetadata() != null ? (String) doc.getMetadata().getOrDefault("source", "未知") : "未知";
            String title = doc.getMetadata() != null ? (String) doc.getMetadata().getOrDefault("title", "未知") : "未知";
            String text = doc.getText();
            if (text != null && text.length() > MAX_KNOWLEDGE_LEN) {
                text = text.substring(0, MAX_KNOWLEDGE_LEN) + "...";
            }
            sb.append("--- 片段 ").append(i + 1).append(" ---\n");
            sb.append("来源: ").append(title).append(" (").append(source).append(")\n");
            sb.append(text).append("\n\n");
        }
        return sb.toString();
    }

    private String loadHistory(String conversationId) {
        List<ChatMemory> messages = chatMemoryMapper.selectByConversationId(conversationId, MAX_HISTORY);
        if (messages.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        Collections.reverse(messages);
        for (ChatMemory msg : messages) {
            String role = "user".equals(msg.getMessageType()) ? "用户" : "助手";
            sb.append(role).append(": ").append(msg.getContent()).append("\n");
        }
        return sb.toString();
    }

    private List<ChatResponse.SourceRef> extractSources(List<Document> docs) {
        List<ChatResponse.SourceRef> sources = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Document doc : docs) {
            String source = doc.getMetadata() != null ?
                    (String) doc.getMetadata().getOrDefault("source", "") : "";
            String title = doc.getMetadata() != null ?
                    (String) doc.getMetadata().getOrDefault("title", "") : "";
            if (!source.isEmpty() && seen.add(source)) {
                sources.add(new ChatResponse.SourceRef(title, source));
            }
        }
        return sources;
    }

    private void saveHistory(String conversationId, String userMessage, String assistantResponse) {
        ChatMemory userMsg = new ChatMemory();
        userMsg.setConversationId(conversationId);
        userMsg.setMessageType("user");
        userMsg.setContent(userMessage);
        chatMemoryMapper.insertMessage(userMsg);

        ChatMemory assistantMsg = new ChatMemory();
        assistantMsg.setConversationId(conversationId);
        assistantMsg.setMessageType("assistant");
        assistantMsg.setContent(assistantResponse);
        chatMemoryMapper.insertMessage(assistantMsg);
    }
}
