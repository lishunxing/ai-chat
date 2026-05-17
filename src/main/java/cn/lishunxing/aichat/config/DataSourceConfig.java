package cn.lishunxing.aichat.config;

import cn.lishunxing.aichat.source.BlogDataSource;
import cn.lishunxing.aichat.source.GitHubBlogDataSource;
import cn.lishunxing.aichat.source.LocalFileBlogDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 数据源配置 —— 根据 blog.source 选择 GitHub 或本地文件
 *
 * @author lishunxing
 */
@Configuration
public class DataSourceConfig {

    @Value("${blog.source:github}")
    private String source;

    @Value("${blog.github.owner:lishunxing}")
    private String githubOwner;

    @Value("${blog.github.repo:blog}")
    private String githubRepo;

    @Value("${blog.github.branch:master}")
    private String githubBranch;

    @Value("${blog.local.path:../blog}")
    private String localPath;

    @Bean
    public BlogDataSource blogDataSource() {
        if ("local".equalsIgnoreCase(source)) {
            return new LocalFileBlogDataSource(localPath);
        }
        if ("prod".equalsIgnoreCase(source)) {
            return new BlogDataSource() {
                @Override
                public List<cn.lishunxing.aichat.source.BlogDocument> fetchAll() {
                    return List.of();
                }
                @Override
                public cn.lishunxing.aichat.source.BlogDocument fetchOne(String path) {
                    return null;
                }
            };
        }
        return new GitHubBlogDataSource(githubOwner, githubRepo, githubBranch);
    }
}
