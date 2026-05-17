package cn.lishunxing.aichat.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 对话历史记录实体
 * <p>
 * 对应数据库表 chat_memory, 存储用户与 AI 助手的对话消息。
 * 建表语句在 resources/schema.sql。
 * </p>
 *
 * @author lishunxing
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("chat_memory")
public class ChatMemory {

    /** 主键ID, 自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话ID, UUID格式, 同一会话共享一个ID */
    private String conversationId;

    /** 消息类型: user(用户) / assistant(助手) */
    private String messageType;

    /** 消息内容 */
    private String content;

    /** 创建时间, 数据库自动填充 */
    private LocalDateTime createdAt;
}
