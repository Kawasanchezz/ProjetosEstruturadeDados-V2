package com.treebom.web.filtros;

import com.treebom.config.Settings;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * CORS só para as origens listadas (nunca "*"; com credenciais, para o cookie de refresh). Origem desconhecida: a
 * requisição segue, mas sem cabeçalhos CORS, e o navegador bloqueia a leitura da resposta. Fica antes do limite de
 * requisições para que um 429 também carregue os cabeçalhos.
 */
@Component
@Order(-40)
public class CorsFilter extends OncePerRequestFilter {
    private final Settings settings;

    public CorsFilter(Settings settings) {
        this.settings = settings;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String origem = req.getHeader("Origin");
        boolean permitida = origem != null && settings.getCorsOrigins().contains(origem);
        boolean preflight = "OPTIONS".equals(req.getMethod()) && origem != null
            && req.getHeader("Access-Control-Request-Method") != null;

        if (permitida) {
            res.setHeader("Access-Control-Allow-Origin", origem);
            res.setHeader("Access-Control-Allow-Credentials", "true");
            res.addHeader("Vary", "Origin");
        }
        if (preflight) {
            if (permitida) {
                res.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE");
                res.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
                res.setHeader("Access-Control-Max-Age", "600");
                res.setStatus(200);
            } else {
                res.sendError(400, "Origem não permitida");
            }
            return;
        }
        chain.doFilter(req, res);
    }
}
