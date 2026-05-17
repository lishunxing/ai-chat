package cn.lishunxing.aichat.dto;

import lombok.Data;

/**
 * 对话请求
 *
 * @author lishunxing
 */
@Data
public class ChatRequest {

    /** 用户问题 */
    private String message;

    /** 会话ID, 为空则自动生成 */
    private String conversationId;
}
