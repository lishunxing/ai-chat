package cn.lishunxing.aichat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.lishunxing.aichat.entity.KnowledgeDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 知识文档索引 Mapper
 * <p>
 * XML 映射文件: resources/mapper/KnowledgeDocumentMapper.xml
 * </p>
 *
 * @author lishunxing
 */
@Mapper
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocument> {

    /**
     * 根据文件路径查询索引记录
     *
     * @param filePath 博客文档相对路径
     * @return 匹配的索引记录, 不存在返回 null
     */
    KnowledgeDocument selectByFilePath(@Param("filePath") String filePath);

    /**
     * 根据文件路径删除索引记录
     *
     * @param filePath 博客文档相对路径
     * @return 删除行数
     */
    int deleteByFilePath(@Param("filePath") String filePath);

    /**
     * 查询所有索引记录（按最后索引时间倒序）
     *
     * @return 全部索引记录列表
     */
    List<KnowledgeDocument> selectAll();
}
