package cn.lishunxing.aichat.source;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/**
 * 本地文件数据源 —— 从磁盘读取博客 Markdown 文件 (开发环境使用)
 *
 * @author lishunxing
 */
public class LocalFileBlogDataSource implements BlogDataSource {

    private static final Logger log = LoggerFactory.getLogger(LocalFileBlogDataSource.class);

    private final Path blogDir;

    private static final Set<String> SKIP_DIRS = Set.of(
            ".vitepress", "node_modules", ".git", ".claude", "openspec", "assets", "public");

    private static final Set<String> SUPPORTED_EXTENSIONS =
            Set.of(".md", ".pdf", ".docx", ".xlsx");

    private static final Set<String> BINARY_EXTENSIONS =
            Set.of(".pdf", ".docx", ".xlsx");

    public LocalFileBlogDataSource(String blogPath) {
        this.blogDir = Path.of(blogPath).toAbsolutePath().normalize();
    }

    @Override
    public List<BlogDocument> fetchAll() {
        List<BlogDocument> docs = new ArrayList<>();
        try (java.util.stream.Stream<Path> stream = Files.walk(blogDir)) {
            stream.filter(Files::isRegularFile)
                  .filter(this::isSupportedFormat)
                  .filter(this::notSkipped)
                  .forEach(p -> {
                      BlogDocument doc = fetchOne(blogDir.relativize(p).toString().replace('\\', '/'));
                      if (doc != null) docs.add(doc);
                  });
        } catch (IOException e) {
            log.error("Failed to walk blog directory: {}", blogDir, e);
        }
        return docs;
    }

    @Override
    public BlogDocument fetchOne(String path) {
        try {
            Path filePath = blogDir.resolve(path);
            String lower = path.toLowerCase();
            BlogDocument doc;

            if (isBinaryFormat(lower)) {
                byte[] bytes = Files.readAllBytes(filePath);
                String md5 = computeMd5(bytes);
                doc = new BlogDocument(path, null, null, md5);
                doc.setBinaryContent(bytes);
            } else {
                String content = Files.readString(filePath);
                String md5 = computeMd5(content);
                doc = new BlogDocument(path, content, null, md5);
            }
            return doc;
        } catch (IOException e) {
            log.warn("Failed to read file: {}", path);
            return null;
        }
    }

    private boolean notSkipped(Path path) {
        Path rel = blogDir.relativize(path);
        for (Path part : rel) {
            if (SKIP_DIRS.contains(part.toString())) return false;
        }
        return true;
    }

    private boolean isSupportedFormat(Path path) {
        String name = path.toString().toLowerCase();
        return SUPPORTED_EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    private boolean isBinaryFormat(String path) {
        return BINARY_EXTENSIONS.stream().anyMatch(path::endsWith);
    }

    private String computeMd5(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String computeMd5(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
