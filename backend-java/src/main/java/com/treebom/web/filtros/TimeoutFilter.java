package com.treebom.web.filtros;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treebom.config.Settings;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Responde 504 se a requisição à API passar de REQUEST_TIMEOUT_S.
 *
 * <p>Limite honesto: o trabalho roda numa thread virtual que é interrompida ({@code cancel(true)}), mas código que
 * não reage a interrupção termina por conta própria; o cliente recebe o 504 e a conexão é liberada. Por isso os
 * tetos de tamanho da árvore continuam sendo a defesa principal (no PostgreSQL o {@code statement_timeout}
 * interrompe a consulta no servidor).
 */
@Component
@Order(-20)
public class TimeoutFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger("bom");

    private final Settings settings;
    private final ObjectMapper mapper;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public TimeoutFilter(Settings settings, ObjectMapper mapper) {
        this.settings = settings;
        this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        return settings.getRequestTimeoutS() <= 0 || !req.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        // O corpo é guardado até o fim: se estourar o tempo, nada do trabalho "atrasado" chega ao cliente.
        RespostaDescartavel descartavel = new RespostaDescartavel(res);
        ContentCachingResponseWrapper embrulho = new ContentCachingResponseWrapper(descartavel);
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        Future<?> trabalho = executor.submit(() -> {
            if (mdc != null) MDC.setContextMap(mdc);
            try {
                chain.doFilter(req, embrulho);
            } catch (IOException | ServletException e) {
                throw new RuntimeException(e);
            } finally {
                MDC.clear();
            }
            return null;
        });
        try {
            trabalho.get(settings.getRequestTimeoutS(), TimeUnit.SECONDS);
            embrulho.copyBodyToResponse();
        } catch (TimeoutException e) {
            descartavel.descartar(); // antes de cancelar: o que o trabalho tentar escrever daqui em diante é ignorado
            trabalho.cancel(true);
            log.warn("Timeout de {}s em {} {}", settings.getRequestTimeoutS(), Respostas.limparLog(req.getMethod()),
                Respostas.limparLog(req.getRequestURI()));
            if (!res.isCommitted()) Respostas.escrever(mapper, res, 504, "Tempo limite da requisição excedido");
        } catch (InterruptedException e) {
            trabalho.cancel(true);
            Thread.currentThread().interrupt();
            throw new ServletException(e);
        } catch (ExecutionException e) {
            Throwable causa = e.getCause() instanceof RuntimeException r && r.getCause() != null ? r.getCause() : e.getCause();
            if (causa instanceof IOException io) throw io;
            if (causa instanceof ServletException se) throw se;
            if (causa instanceof RuntimeException re) throw re;
            throw new ServletException(causa);
        }
    }
}
