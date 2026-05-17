package cn.lishunxing.aichat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.lishunxing.aichat.entity.ChatMemory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 对话历史 Mapper
 * <p>
 * XML 映射文件: resources/mapper/ChatMemoryMapper.xml
 * </p>
 *
 * @author lishunxing
 */
@Mapper
public interface ChatMemoryMapper extends BaseMapper<ChatMemory> {

    /**
     * 根据会话ID查询最近 N 条消息（按时间倒序）
     *
     * @param conversationId 会话ID
     * @param limit          最大返回条数
     * @return 消息列表（最新的在前）
     */
    List<ChatMemory> selectByConversationId(@Param("conversationId") String conversationId,
                                            @Param("limit") int limit);

    /**
     * 插入消息记录
     *
     * @param message 消息实体
     * @return 插入行数
     */
    int insertMessage(ChatMemory message);
}
