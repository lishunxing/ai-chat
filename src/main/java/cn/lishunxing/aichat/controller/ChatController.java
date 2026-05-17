package cn.lishunxing.aichat.controller;

import cn.lishunxing.aichat.dto.ChatRequest;
import cn.lishunxing.aichat.dto.ChatResponse;
import cn.lishunxing.aichat.service.RagChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * 对话 REST API —— 流式 SSE
 *
 * @author lishunxing
 */
@RestController
@RequestMapping("/api")
public class ChatController {

    @Autowired
    private RagChatService ragChatService;

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatResponse> chat(@RequestBody ChatRequest request) {
        return ragChatService.chatStream(request.getConversationId(), request.getMessage());
    }
}
