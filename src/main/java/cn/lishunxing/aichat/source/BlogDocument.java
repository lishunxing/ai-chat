package cn.lishunxing.aichat.source;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 博客文档 —— 从数据源获取的原始文档
 *
 * @author lishunxing
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BlogDocument {

    /** 相对路径, 如 java/spring/IoC.md */
    private String path;

    /** 文件原始内容 (Markdown 等文本格式) */
    private String content;

    /** 二进制文件内容 (PDF/Word/Excel), 文本文件为 null */
    private byte[] binaryContent;

    /** 最后修改标识 (文件MD5 / Git SHA / 时间戳, 用于变更检测) */
    private String fingerprint;
}
