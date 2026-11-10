package com.treebom.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/** Pool de conexões. SQLite por padrão; PostgreSQL opcional (aborta consultas longas no servidor). */
@Configuration
public class DatabaseConfig {

    public static boolean ehPostgres(String url) {
        return url.startsWith("jdbc:postgresql");
    }

    @Bean(destroyMethod = "close")
    public DataSource dataSource(Settings settings) {
        String url = settings.getDatabaseUrl();
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(url);
        cfg.setPoolName("treebom");
        if (StringUtils.hasText(settings.getDatabaseUser())) cfg.setUsername(settings.getDatabaseUser());
        if (StringUtils.hasText(settings.getDatabasePassword())) cfg.setPassword(settings.getDatabasePassword());
        if (ehPostgres(url)) {
            cfg.setMaximumPoolSize(10);
            // PostgreSQL interrompe a consulta no servidor: ao contrário do timeout da aplicação, isso PARA o trabalho.
            if (settings.getRequestTimeoutS() > 0)
                cfg.setConnectionInitSql("SET statement_timeout = " + settings.getRequestTimeoutS() * 1000);
        } else {
            cfg.setMaximumPoolSize(4);
        }
        return new HikariDataSource(cfg);
    }
}
