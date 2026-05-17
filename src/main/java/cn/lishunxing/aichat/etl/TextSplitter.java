package cn.lishunxing.aichat.etl;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextSplitter {

    private static final int CHUNK_SIZE = 500;
    private static final int OVERLAP = 50;
    private static final Pattern CODE_FENCE = Pattern.compile("```[\\s\\S]*?```");
    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile("[。！？\\n]");

    public record Chunk(String text, Map<String, Object> metadata) {}

    public List<Chunk> split(DocumentParser.ParsedSection section) {
        int totalSections = 1;
        List<Chunk> chunks = new ArrayList<>();
        String text = section.content();

        if (text.length() <= CHUNK_SIZE) {
            chunks.add(new Chunk(text, createMetadata(section, 1, 1)));
            return chunks;
        }

        // 提取代码块并用占位符替换, 避免分块时截断代码
        List<String> codeBlocks = new ArrayList<>();
        Matcher matcher = CODE_FENCE.matcher(text);
        StringBuilder processed = new StringBuilder();
        int lastEnd = 0;
        int idx = 0;
        while (matcher.find()) {
            processed.append(text, lastEnd, matcher.start());
            processed.append("【代码块").append(idx).append("】");
            codeBlocks.add(matcher.group());
            lastEnd = matcher.end();
            idx++;
        }
        processed.append(text.substring(lastEnd));
        String cleanText = processed.toString();

        // 按句子边界拆分后合并为约 500 字的块
        List<String> segments = splitByBoundary(cleanText);
        List<String> mergedChunks = mergeSegments(segments, codeBlocks);
        mergedChunks = ensureOverlap(mergedChunks);

        int total = mergedChunks.size();
        for (int i = 0; i < mergedChunks.size(); i++) {
            chunks.add(new Chunk(restoreCodeBlocks(mergedChunks.get(i), codeBlocks),
                    createMetadata(section, i + 1, total)));
        }

        return chunks;
    }

    private Map<String, Object> createMetadata(DocumentParser.ParsedSection section,
                                                int chunkIndex, int totalChunks) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("source", section.sourcePath());
        meta.put("title", section.title());
        meta.put("heading", section.heading());
        meta.put("chunk_index", chunkIndex);
        meta.put("total_chunks", totalChunks);
        return meta;
    }

    private List<String> splitByBoundary(String text) {
        List<String> segments = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '。' || c == '！' || c == '？' || c == '\n') {
                if (i - start > 10) {
                    segments.add(text.substring(start, i + 1));
                    start = i + 1;
                }
            }
        }
        if (start < text.length()) {
            segments.add(text.substring(start));
        }
        return segments;
    }

    private List<String> mergeSegments(List<String> segments, List<String> codeBlocks) {
        List<String> merged = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String seg : segments) {
            if (current.length() + seg.length() > CHUNK_SIZE && !current.isEmpty()) {
                merged.add(current.toString().trim());
                current = new StringBuilder();
            }
            current.append(seg);
        }
        if (!current.isEmpty()) {
            merged.add(current.toString().trim());
        }
        return merged;
    }

    private List<String> ensureOverlap(List<String> chunks) {
        if (chunks.size() <= 1) return chunks;
        List<String> result = new ArrayList<>();
        result.add(chunks.get(0));
        for (int i = 1; i < chunks.size(); i++) {
            String prev = chunks.get(i - 1);
            String curr = chunks.get(i);
            if (prev.length() > OVERLAP) {
                String overlap = prev.substring(prev.length() - OVERLAP);
                int boundary = findBoundary(overlap);
                if (boundary > 0) {
                    curr = overlap.substring(boundary) + curr;
                }
            }
            result.add(curr);
        }
        return result;
    }

    private int findBoundary(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '。' || c == '！' || c == '？' || c == '\n') return i + 1;
        }
        return 0;
    }

    private String restoreCodeBlocks(String text, List<String> codeBlocks) {
        for (int i = 0; i < codeBlocks.size(); i++) {
            text = text.replace("【代码块" + i + "】", "\n" + codeBlocks.get(i) + "\n");
        }
        return text;
    }
}
