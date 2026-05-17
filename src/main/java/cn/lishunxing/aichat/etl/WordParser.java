package cn.lishunxing.aichat.etl;

import cn.lishunxing.aichat.source.BlogDocument;
import org.apache.poi.xwpf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Word 解析器 —— 使用 Apache POI 解析 .docx，按标题样式分段
 *
 * @author lishunxing
 */
public class WordParser implements DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(WordParser.class);

    @Override
    public List<String> supportedExtensions() {
        return List.of(".docx");
    }

    @Override
    public List<ParsedSection> parse(BlogDocument document) {
        byte[] data = document.getBinaryContent();
        if (data == null || data.length == 0) {
            log.warn("Empty Word content: {}", document.getPath());
            return List.of();
        }

        String sourcePath = document.getPath();
        String title = extractFileName(sourcePath);
        List<ParsedSection> sections = new ArrayList<>();

        try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
             XWPFDocument docx = new XWPFDocument(bis)) {

            String currentHeading = "正文";
            StringBuilder currentContent = new StringBuilder();
            boolean titleFound = false;

            for (IBodyElement element : docx.getBodyElements()) {
                if (element instanceof XWPFParagraph para) {
                    String styleId = para.getStyleID();
                    String text = para.getText();

                    if (text == null || text.isBlank()) {
                        continue;
                    }

                    if (styleId != null && isHeadingStyle(styleId)) {
                        int level = getHeadingLevel(styleId);

                        if (!titleFound && level == 1) {
                            title = text;
                            titleFound = true;
                        }

                        if (!currentContent.isEmpty()) {
                            sections.add(new ParsedSection(
                                    currentHeading,
                                    currentContent.toString().trim(),
                                    sourcePath,
                                    title
                            ));
                        }
                        currentHeading = text;
                        currentContent = new StringBuilder();
                    } else {
                        currentContent.append(text).append("\n");
                    }
                }
            }

            if (!currentContent.isEmpty()) {
                sections.add(new ParsedSection(
                        currentHeading,
                        currentContent.toString().trim(),
                        sourcePath,
                        title
                ));
            }

            // If no heading-structured sections created, return whole document as one section
            if (sections.isEmpty()) {
                StringBuilder allText = new StringBuilder();
                for (XWPFParagraph para : docx.getParagraphs()) {
                    String text = para.getText();
                    if (text != null && !text.isBlank()) {
                        allText.append(text).append("\n");
                    }
                }
                if (!allText.isEmpty()) {
                    sections.add(new ParsedSection("正文", allText.toString().trim(), sourcePath, title));
                }
            }
        } catch (IOException e) {
            log.error("Failed to parse Word document: {}", sourcePath, e);
            throw new RuntimeException("Word 文档解析失败: " + sourcePath, e);
        }

        return sections;
    }

    private boolean isHeadingStyle(String styleId) {
        String lower = styleId.toLowerCase();
        return lower.contains("heading") || lower.contains("标题");
    }

    private int getHeadingLevel(String styleId) {
        String lower = styleId.toLowerCase();
        for (int level = 1; level <= 9; level++) {
            if (lower.contains(String.valueOf(level))) {
                return level;
            }
        }
        return 1;
    }

    private String extractFileName(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        return name.replace(".docx", "");
    }
}
