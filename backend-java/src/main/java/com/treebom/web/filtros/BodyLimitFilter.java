package com.treebom.web.filtros;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treebom.config.Settings;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Rejeita corpos grandes (413), sem Content-Length em chunked (411) e tipos não-JSON (415). */
@Component
@Order(-10)
public class BodyLimitFilter extends OncePerRequestFilter {
    private static final Set<String> METODOS = Set.of("POST", "PUT", "PATCH");

    private final Settings settings;
    private final ObjectMapper mapper;

    public BodyLimitFilter(Settings settings, ObjectMapper mapper) {
        this.settings = settings;
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (METODOS.contains(req.getMethod())) {
            long tamanho = req.getContentLengthLong(); // -1 = desconhecido (ex.: chunked)
            if (tamanho < 0 && req.getHeader("Transfer-Encoding") != null) {
                Respostas.escrever(mapper, res, 411, "Content-Length é obrigatório");
                return;
            }
            if (tamanho > settings.getMaxBodyBytes()) {
                Respostas.escrever(mapper, res, 413, "Corpo da requisição grande demais");
                return;
            }
            String tipo = req.getContentType();
            if (tamanho > 0 && (tipo == null || !tipo.toLowerCase(Locale.ROOT).startsWith("application/json"))) {
                Respostas.escrever(mapper, res, 415, "Use Content-Type: application/json");
                return;
            }
        }
        chain.doFilter(req, res);
    }
}
