package cn.lishunxing.aichat.service;

import cn.lishunxing.aichat.dto.ChatResponse;
import reactor.core.publisher.Flux;

/**
 * RAG 对话服务接口
 *
 * @author lishunxing
 */
public interface RagChatService {

    /**
     * RAG 流式对话 —— 逐 token 返回内容，最后一个 ChatResponse 携带来源
     *
     * @param conversationId 会话ID, 为空则自动生成 UUID
     * @param userMessage    用户问题
     */
    Flux<ChatResponse> chatStream(String conversationId, String userMessage);
}
