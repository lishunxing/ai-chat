package cn.lishunxing.aichat.source;

import java.util.List;

/**
 * 博客数据源接口 —— 统一从不同来源获取 Markdown 文档
 *
 * @author lishunxing
 */
public interface BlogDataSource {

    /**
     * 获取所有博客文档
     */
    List<BlogDocument> fetchAll();

    /**
     * 获取单个文档 (增量更新时使用)
     */
    BlogDocument fetchOne(String path);
}
