package com.treebom.web.filtros;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treebom.config.Settings;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Camada mais externa: request_id, cabeçalhos de segurança e resposta 500 sem detalhes internos. */
@Component
@Order(-50)
public class SecurityFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger("bom");

    // CSP compatível com o build do Vite: scripts e estilos só do próprio domínio (sem 'unsafe-inline'/'unsafe-eval').
    // Framer Motion, GSAP e React alteram estilos via CSSOM (element.style), que a CSP não bloqueia.
    static final String CSP = "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; "
        + "connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'";

    private final Settings settings;
    private final ObjectMapper mapper;

    public SecurityFilter(Settings settings, ObjectMapper mapper) {
        this.settings = settings;
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString().replace("-", "");
        MDC.put("request_id", requestId);
        try {
            res.setHeader("X-Request-Id", requestId);
            res.setHeader("X-Content-Type-Options", "nosniff");
            res.setHeader("Referrer-Policy", "no-referrer");
            res.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=()");
            res.setHeader("X-Frame-Options", "DENY");
            res.setHeader("Cross-Origin-Opener-Policy", "same-origin");
            res.setHeader("Cross-Origin-Resource-Policy", "same-origin");
            res.setHeader("Content-Security-Policy", CSP);
            if (req.getRequestURI().startsWith("/api/")) res.setHeader("Cache-Control", "no-store");
            if (settings.hstsAtivo()) res.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
            chain.doFilter(req, res);
        } catch (Exception e) { // última barreira: nada de stack trace para o cliente
            log.error("Erro não tratado em {} {}", Respostas.limparLog(req.getMethod()), Respostas.limparLog(req.getRequestURI()), e);
            if (!res.isCommitted()) {
                res.resetBuffer();
                Map<String, Object> corpo = new LinkedHashMap<>();
                corpo.put("detail", "Erro interno do servidor");
                corpo.put("request_id", requestId);
                Respostas.escrever(mapper, res, 500, corpo, Map.of());
            }
        } finally {
            MDC.remove("request_id");
        }
    }
}
