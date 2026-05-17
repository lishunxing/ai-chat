package cn.lishunxing.aichat.etl;

import cn.lishunxing.aichat.source.BlogDocument;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Markdown 解析器 —— 将博客文档按 ## 标题拆分为逻辑段落
 *
 * @author lishunxing
 */
public class MarkdownParser implements DocumentParser {

    private static final Pattern HEADING_PATTERN = Pattern.compile("^##\\s+(.+)$");

    @Override
    public List<String> supportedExtensions() {
        return List.of(".md");
    }

    /**
     * 解析所有文档
     */
    public List<DocumentParser.ParsedSection> parseAll(List<BlogDocument> docs) {
        List<DocumentParser.ParsedSection> sections = new ArrayList<>();
        for (BlogDocument doc : docs) {
            sections.addAll(parseDocument(doc));
        }
        return sections;
    }

    /**
     * 解析单个文档
     */
    public List<DocumentParser.ParsedSection> parseDocument(BlogDocument doc) {
        String fullContent = doc.getContent();
        if (fullContent == null || fullContent.isBlank()) return List.of();

        String sourcePath = doc.getPath();
        String title = extractTitle(fullContent, sourcePath);

        List<DocumentParser.ParsedSection> sections = new ArrayList<>();
        List<String> lines = List.of(fullContent.split("\n", -1));

        String currentHeading = title;
        StringBuilder currentContent = new StringBuilder();
        boolean inCodeBlock = false;

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.startsWith("```")) {
                inCodeBlock = !inCodeBlock;
                currentContent.append(line).append("\n");
                continue;
            }

            java.util.regex.Matcher matcher = HEADING_PATTERN.matcher(line);
            if (!inCodeBlock && matcher.matches()) {
                if (!currentContent.isEmpty()) {
                    sections.add(new DocumentParser.ParsedSection(currentHeading,
                            currentContent.toString().trim(), sourcePath, title));
                }
                currentHeading = matcher.group(1).trim();
                currentContent = new StringBuilder();
            } else {
                currentContent.append(line).append("\n");
            }
        }

        if (!currentContent.isEmpty()) {
            sections.add(new DocumentParser.ParsedSection(currentHeading,
                    currentContent.toString().trim(), sourcePath, title));
        }

        return sections;
    }

    @Override
    public List<DocumentParser.ParsedSection> parse(BlogDocument document) {
        return parseDocument(document);
    }

    private String extractTitle(String content, String fallback) {
        for (String line : content.split("\n")) {
            if (line.startsWith("# ") && !line.startsWith("## ")) {
                return line.substring(2).trim();
            }
        }
        String name = fallback.substring(fallback.lastIndexOf('/') + 1);
        return name.replace(".md", "");
    }
}
