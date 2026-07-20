package com.demo.providerb.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * MySQL 数据源配置。
 * 仅当 app.middleware.mysql.enabled=true 时生效（ENABLE_MYSQL=true）。
 * 关闭时该 Config 不创建任何 Bean，应用回退到内存硬编码数据。
 *
 * 启动时自动执行 schema.sql 建表并灌入初始数据。
 */
@Configuration
@ConditionalOnProperty(prefix = "app.middleware.mysql", name = "enabled", havingValue = "true")
public class DataSourceConfig {

    @Value("${app.middleware.mysql.host:localhost}")
    private String host;
    @Value("${app.middleware.mysql.port:3306}")
    private int port;
    @Value("${app.middleware.mysql.database:provider_b_db}")
    private String database;
    @Value("${app.middleware.mysql.username:root}")
    private String username;
    @Value("${app.middleware.mysql.password:root}")
    private String password;

    @Bean
    public DataSource dataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai");
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds.setMaximumPoolSize(5);
        ds.setConnectionTimeout(3000);
        ds.setPoolName("provider-b-mysql");
        return ds;
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        try (Connection conn = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(conn, new ClassPathResource("schema.sql"));
        } catch (Exception e) {
            System.err.println("[provider-b] schema.sql 执行失败(后续查询将回退内存数据): " + e.getMessage());
        }
        return new JdbcTemplate(dataSource);
    }
}
