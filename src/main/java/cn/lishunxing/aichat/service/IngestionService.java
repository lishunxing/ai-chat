package cn.lishunxing.aichat.service;

/**
 * 文档摄取服务接口
 *
 * @author lishunxing
 */
public interface IngestionService {

    /**
     * 全量索引 —— 从数据源获取所有文档并入库
     *
     * @return 总切片数
     */
    int ingestAll();

    /**
     * 增量索引单个文档
     */
    void ingestOne(String path);

    /**
     * 删除文档的索引
     */
    void deleteOne(String path);

    /**
     * 获取已索引文档数量
     */
    long getDocumentCount();

    /**
     * 检查文档内容是否有变化
     */
    boolean hasChanged(String path, String existingMd5);
}
