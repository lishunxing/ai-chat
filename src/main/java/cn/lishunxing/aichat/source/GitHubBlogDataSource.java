package cn.lishunxing.aichat.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * GitHub 数据源 —— 通过 GitHub REST API 获取博客仓库中的 Markdown 文件
 * <p>
 * 对公开仓库无需认证。API 地址格式:
 * GET /repos/{owner}/{repo}/git/trees/{branch}?recursive=1  → 列出所有文件
 * GET /repos/{owner}/{repo}/contents/{path}                  → 获取单个文件内容 (Base64)
 * </p>
 *
 * @author lishunxing
 */
public class GitHubBlogDataSource implements BlogDataSource {

    private static final Logger log = LoggerFactory.getLogger(GitHubBlogDataSource.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiBase;

    public GitHubBlogDataSource(String owner, String repo, String branch) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.apiBase = "https://api.github.com/repos/" + owner + "/" + repo;
        this.branch = branch;
    }

    private final String branch;

    @Override
    public List<BlogDocument> fetchAll() {
        List<BlogDocument> docs = new ArrayList<>();
        try {
            // 1. 获取仓库文件树
            String treeUrl = apiBase + "/git/trees/" + branch + "?recursive=1";
            String treeJson = restTemplate.getForObject(treeUrl, String.class);
            JsonNode tree = objectMapper.readTree(treeJson);

            JsonNode items = tree.get("tree");
            if (items == null) return docs;

            for (JsonNode item : items) {
                String path = item.get("path").asText();
                String type = item.get("type").asText();
                String sha = item.get("sha").asText();

                // 只处理 Markdown 文件, 跳过 node_modules / .vitepress 等目录
                if (!"blob".equals(type) || !isSupportedFormat(path)) continue;
                if (isSkipped(path)) continue;

                // 2. 获取文件内容
                BlogDocument doc = fetchOne(path);
                if (doc != null) {
                    doc.setFingerprint(sha);
                    docs.add(doc);
                    log.debug("Fetched: {}", path);
                }

                // GitHub API 限流: 未认证 60次/小时, 稍作延迟
                Thread.sleep(100);
            }
        } catch (Exception e) {
            log.error("Failed to fetch blog documents from GitHub", e);
        }
        return docs;
    }

    @Override
    public BlogDocument fetchOne(String path) {
        try {
            String url = apiBase + "/contents/" + path;
            String json = restTemplate.getForObject(url, String.class);
            JsonNode node = objectMapper.readTree(json);

            String content = node.get("content").asText();
            String sha = node.get("sha").asText();

            byte[] decoded = Base64.getMimeDecoder().decode(content.replace("\n", ""));

            if (isBinaryFormat(path)) {
                BlogDocument doc = new BlogDocument(path, null, null, sha);
                doc.setBinaryContent(decoded);
                return doc;
            } else {
                return new BlogDocument(path, new String(decoded, java.nio.charset.StandardCharsets.UTF_8), null, sha);
            }
        } catch (Exception e) {
            log.warn("Failed to fetch file: {}", path);
            return null;
        }
    }

    private boolean isSkipped(String path) {
        for (String part : path.split("/")) {
            if (SKIP_DIRS.contains(part)) return true;
        }
        return false;
    }

    private boolean isSupportedFormat(String path) {
        String lower = path.toLowerCase();
        return SUPPORTED_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    private boolean isBinaryFormat(String path) {
        String lower = path.toLowerCase();
        return BINARY_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    private static final Set<String> SKIP_DIRS = Set.of(
            ".vitepress", "node_modules", ".git", ".claude", "openspec", "assets", "public");

    private static final Set<String> SUPPORTED_EXTENSIONS =
            Set.of(".md", ".pdf", ".docx", ".xlsx");

    private static final Set<String> BINARY_EXTENSIONS =
            Set.of(".pdf", ".docx", ".xlsx");
}
