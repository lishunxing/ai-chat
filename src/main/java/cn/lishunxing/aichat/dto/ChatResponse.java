package cn.lishunxing.aichat.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 对话响应
 *
 * @author lishunxing
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    /** 会话ID */
    private String conversationId;

    /** AI 回复内容 */
    private String content;

    /** 知识来源列表 */
    private List<SourceRef> sources;

    /**
     * 知识来源引用
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceRef {
        /** 文档标题 */
        private String title;
        /** 文档相对路径 */
        private String path;
    }
}
