package cn.lishunxing.aichat.config;

import com.alibaba.druid.filter.Filter;
import com.alibaba.druid.filter.stat.StatFilter;
import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.support.jakarta.StatViewServlet;
import com.alibaba.druid.support.jakarta.WebStatFilter;
import com.alibaba.druid.wall.WallFilter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.util.*;

/**
 * Druid 连接池配置 —— 含 SQL 监控页面
 * <p>
 * 监控页面: http://localhost:8080/druid/
 * 登录账号: admin / admin123
 * </p>
 *
 * @author lishunxing
 */
@Configuration
public class DruidConfig {

    @Bean
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource")
    public DataSource druidDataSource() {
        DruidDataSource dataSource = new DruidDataSource();

        // 连接池配置
        dataSource.setInitialSize(5);
        dataSource.setMinIdle(5);
        dataSource.setMaxActive(20);
        dataSource.setMaxWait(60000);
        dataSource.setTimeBetweenEvictionRunsMillis(60000);
        dataSource.setMinEvictableIdleTimeMillis(300000);
        dataSource.setValidationQuery("SELECT 1");
        dataSource.setTestWhileIdle(true);
        dataSource.setTestOnBorrow(false);
        dataSource.setTestOnReturn(false);

        // 监控过滤器
        StatFilter statFilter = new StatFilter();
        statFilter.setSlowSqlMillis(1000);
        statFilter.setLogSlowSql(true);

        WallFilter wallFilter = new WallFilter();

        List<Filter> filters = new ArrayList<>();
        filters.add(statFilter);
        filters.add(wallFilter);
        dataSource.setProxyFilters(filters);

        return dataSource;
    }

    /**
     * Druid 监控页面 Servlet
     */
    @Bean
    public ServletRegistrationBean<StatViewServlet> druidStatViewServlet() {
        ServletRegistrationBean<StatViewServlet> bean =
                new ServletRegistrationBean<>(new StatViewServlet(), "/druid/*");

        Map<String, String> initParams = new HashMap<>();
        initParams.put("loginUsername", "admin");
        initParams.put("loginPassword", "admin123");
        initParams.put("resetEnable", "false");
        bean.setInitParameters(initParams);

        return bean;
    }

    /**
     * Druid Web 请求统计 Filter
     */
    @Bean
    public FilterRegistrationBean<WebStatFilter> druidWebStatFilter() {
        FilterRegistrationBean<WebStatFilter> bean =
                new FilterRegistrationBean<>(new WebStatFilter());

        bean.addUrlPatterns("/*");
        bean.addInitParameter("exclusions", "*.js,*.gif,*.jpg,*.png,*.css,*.ico,/druid/*");

        return bean;
    }
}
