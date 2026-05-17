package cn.lishunxing.aichat.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 知识文档索引记录实体
 * <p>
 * 对应数据库表 knowledge_document, 记录每篇博客 Markdown 文件的索引状态。
 * 用于 WatchService 和定时轮询的增量索引判断。
 * </p>
 *
 * @author lishunxing
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("knowledge_document")
public class KnowledgeDocument {

    /** 主键ID, 自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 博客文档相对路径, 如 java/spring/IoC.md */
    private String filePath;

    /** 文档标题, 从 # 一级标题提取, 无标题时使用文件名 */
    private String title;

    /** 文件内容 MD5 哈希值, 用于变更检测 */
    private String md5Hash;

    /** 该文档被拆分为多少个切片 */
    private int chunkCount;

    /** 最后索引时间 */
    private LocalDateTime lastIndexedAt;

    /**
     * 便捷构造方法：记录新索引的文档
     *
     * @param filePath   文档相对路径
     * @param title      文档标题
     * @param md5Hash    内容 MD5
     * @param chunkCount 切片数量
     */
    public KnowledgeDocument(String filePath, String title, String md5Hash, int chunkCount) {
        this.filePath = filePath;
        this.title = title;
        this.md5Hash = md5Hash;
        this.chunkCount = chunkCount;
        this.lastIndexedAt = LocalDateTime.now();
    }
}
