package com.treebom.config;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Configuração via variáveis de ambiente (12-factor), com padrões seguros e validação na partida. */
@Component
@ConfigurationProperties(prefix = "treebom")
public class Settings implements InitializingBean {
    public static final int SEGREDO_MINIMO = 32;

    /** Fail-closed: configuração insegura impede a inicialização da aplicação. */
    @Override
    public void afterPropertiesSet() {
        validar();
    }

    /** A configuração viola uma regra de segurança e a aplicação se recusa a iniciar. */
    public static class ConfiguracaoInsegura extends RuntimeException {
        public ConfiguracaoInsegura(String mensagem) {
            super(mensagem);
        }
    }

    // --- ambiente
    private String appEnv = "development"; // "production" liga os padrões restritivos
    private String databaseUrl = "jdbc:sqlite:./bom.db?foreign_keys=on&journal_mode=WAL&busy_timeout=5000";
    private String databaseUser = "";
    private String databasePassword = "";
    private String staticDir = "";

    // --- HTTP
    private List<String> corsOrigins = new ArrayList<>(List.of("http://localhost:5173"));
    private String rateLimit = "120/minute"; // leituras
    private String writeRateLimit = "30/minute"; // POST/PUT/PATCH/DELETE (inclui seed)
    private String loginRateLimit = "10/minute";
    private int maxBodyBytes = 65_536;
    private int requestTimeoutS = 15; // 504 se a requisição passar disso
    private Boolean enableSeed; // null = ligado só fora de produção
    private Boolean hsts; // null = ligado só em produção

    // --- limites da árvore (anti-DoS e anti-overflow)
    private int maxProfundidade = 50;
    private int maxNosPorProduto = 2_000;
    private int maxFilhosPorNo = 100;
    private long maxQuantidadeTotal = 1_000_000_000_000L;
    private BigDecimal maxValorTotal = new BigDecimal("1000000000000"); // R$ 1 trilhão

    // --- autenticação (opcional)
    private boolean authEnabled = false;
    private String jwtSecret = "";
    private String jwtSecretAnterior = ""; // só para VERIFICAR tokens durante a rotação do segredo
    private int accessTtlMinutos = 10;
    private int refreshTtlDias = 7;
    private Boolean cookieSecure; // null = ligado em produção
    private String adminUsername = "";
    private String adminPassword = "";

    // ------------------------------------------------------------------ derivados
    public boolean producao() {
        return appEnv.equalsIgnoreCase("production");
    }

    public boolean seedAtivo() {
        return enableSeed != null ? enableSeed : !producao();
    }

    public boolean hstsAtivo() {
        return hsts != null ? hsts : producao();
    }

    public boolean cookieSeguro() {
        return cookieSecure != null ? cookieSecure : producao();
    }

    private static boolean vazio(String s) {
        return s == null || s.isBlank();
    }

    /** Falha cedo (fail-closed) quando a configuração é perigosa. */
    public void validar() {
        if (corsOrigins.contains("*"))
            throw new ConfiguracaoInsegura("CORS_ORIGINS não pode conter '*'. Liste as origens explicitamente.");
        if (!vazio(jwtSecretAnterior) && jwtSecretAnterior.length() < SEGREDO_MINIMO)
            throw new ConfiguracaoInsegura("JWT_SECRET_ANTERIOR precisa ter " + SEGREDO_MINIMO + "+ caracteres.");
        if (authEnabled && (vazio(jwtSecret) || jwtSecret.length() < SEGREDO_MINIMO))
            throw new ConfiguracaoInsegura("JWT_SECRET é obrigatório com AUTH_ENABLED=true e precisa ter " + SEGREDO_MINIMO + "+ caracteres.");
        if (producao()) {
            if (!authEnabled) throw new ConfiguracaoInsegura("APP_ENV=production exige AUTH_ENABLED=true.");
            if (!cookieSeguro())
                throw new ConfiguracaoInsegura("Em produção o cookie de refresh precisa ser Secure (COOKIE_SECURE=true).");
            if (!vazio(adminPassword) && adminPassword.length() < 12)
                throw new ConfiguracaoInsegura("ADMIN_PASSWORD precisa ter 12+ caracteres.");
        }
    }

    // ------------------------------------------------------- getters / setters (binding)
    public String getAppEnv() { return appEnv; }
    public void setAppEnv(String v) { this.appEnv = v; }
    public String getDatabaseUrl() { return databaseUrl; }
    public void setDatabaseUrl(String v) { this.databaseUrl = v; }
    public String getDatabaseUser() { return databaseUser; }
    public void setDatabaseUser(String v) { this.databaseUser = v; }
    public String getDatabasePassword() { return databasePassword; }
    public void setDatabasePassword(String v) { this.databasePassword = v; }
    public String getStaticDir() { return staticDir; }
    public void setStaticDir(String v) { this.staticDir = v; }
    public List<String> getCorsOrigins() { return corsOrigins; }
    public void setCorsOrigins(List<String> v) { this.corsOrigins = v; }
    public String getRateLimit() { return rateLimit; }
    public void setRateLimit(String v) { this.rateLimit = v; }
    public String getWriteRateLimit() { return writeRateLimit; }
    public void setWriteRateLimit(String v) { this.writeRateLimit = v; }
    public String getLoginRateLimit() { return loginRateLimit; }
    public void setLoginRateLimit(String v) { this.loginRateLimit = v; }
    public int getMaxBodyBytes() { return maxBodyBytes; }
    public void setMaxBodyBytes(int v) { this.maxBodyBytes = v; }
    public int getRequestTimeoutS() { return requestTimeoutS; }
    public void setRequestTimeoutS(int v) { this.requestTimeoutS = v; }
    public Boolean getEnableSeed() { return enableSeed; }
    public void setEnableSeed(Boolean v) { this.enableSeed = v; }
    public Boolean getHsts() { return hsts; }
    public void setHsts(Boolean v) { this.hsts = v; }
    public int getMaxProfundidade() { return maxProfundidade; }
    public void setMaxProfundidade(int v) { this.maxProfundidade = v; }
    public int getMaxNosPorProduto() { return maxNosPorProduto; }
    public void setMaxNosPorProduto(int v) { this.maxNosPorProduto = v; }
    public int getMaxFilhosPorNo() { return maxFilhosPorNo; }
    public void setMaxFilhosPorNo(int v) { this.maxFilhosPorNo = v; }
    public long getMaxQuantidadeTotal() { return maxQuantidadeTotal; }
    public void setMaxQuantidadeTotal(long v) { this.maxQuantidadeTotal = v; }
    public BigDecimal getMaxValorTotal() { return maxValorTotal; }
    public void setMaxValorTotal(BigDecimal v) { this.maxValorTotal = v; }
    public boolean isAuthEnabled() { return authEnabled; }
    public void setAuthEnabled(boolean v) { this.authEnabled = v; }
    public String getJwtSecret() { return jwtSecret; }
    public void setJwtSecret(String v) { this.jwtSecret = v; }
    public String getJwtSecretAnterior() { return jwtSecretAnterior; }
    public void setJwtSecretAnterior(String v) { this.jwtSecretAnterior = v; }
    public int getAccessTtlMinutos() { return accessTtlMinutos; }
    public void setAccessTtlMinutos(int v) { this.accessTtlMinutos = v; }
    public int getRefreshTtlDias() { return refreshTtlDias; }
    public void setRefreshTtlDias(int v) { this.refreshTtlDias = v; }
    public Boolean getCookieSecure() { return cookieSecure; }
    public void setCookieSecure(Boolean v) { this.cookieSecure = v; }
    public String getAdminUsername() { return adminUsername; }
    public void setAdminUsername(String v) { this.adminUsername = v; }
    public String getAdminPassword() { return adminPassword; }
    public void setAdminPassword(String v) { this.adminPassword = v; }
}
