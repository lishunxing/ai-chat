package cn.lishunxing.aichat.etl;

import cn.lishunxing.aichat.source.BlogDocument;

import java.util.List;

/**
 * 文档解析器接口 —— 将不同格式的文档解析为统一的分段结构
 *
 * @author lishunxing
 */
public interface DocumentParser {

    /**
     * 解析后的段落
     */
    record ParsedSection(String heading, String content, String sourcePath, String title) {}

    /**
     * 该解析器支持的文件扩展名列表（含点号，如 ".md"）
     */
    List<String> supportedExtensions();

    /**
     * 解析单个文档，返回分段列表
     */
    List<ParsedSection> parse(BlogDocument document);
}
