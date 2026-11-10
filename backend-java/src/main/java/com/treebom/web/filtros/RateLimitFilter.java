package com.treebom.web.filtros;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treebom.config.Settings;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limite de requisições por IP e por classe de rota, em janela deslizante na memória (1 processo).
 * Classes: "login" (/api/auth/login), "escrita" (POST/PUT/PATCH/DELETE) e "leitura" (o resto).
 * Atrás de proxy, ative {@code server.forward-headers-strategy=native} (FORWARD_HEADERS=native) para enxergar o IP real.
 */
@Component
@Order(-30)
public class RateLimitFilter extends OncePerRequestFilter {
    private static final Map<String, Integer> JANELAS = Map.of("second", 1, "minute", 60, "hour", 3600);
    private static final Set<String> METODOS_ESCRITA = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final int LIMPEZA_A_CADA = 2_000; // requisições

    private record Limite(int maximo, int janelaSegundos) {}

    private final Settings settings;
    private final ObjectMapper mapper;
    private final Map<String, ArrayDeque<Long>> acessos = new HashMap<>(); // protegido por "this"
    private long contador = 0;

    public RateLimitFilter(Settings settings, ObjectMapper mapper) {
        this.settings = settings;
        this.mapper = mapper;
    }

    /** "120/minute" -> (120, 60). */
    static Limite parse(String texto) {
        String[] partes = texto.split("/", 2);
        Integer janela = JANELAS.get(partes[1].strip().toLowerCase());
        if (janela == null) throw new IllegalArgumentException("Unidade de limite inválida: " + texto);
        return new Limite(Integer.parseInt(partes[0].strip()), janela);
    }

    private String classe(String metodo, String caminho) {
        String limpo = caminho.endsWith("/") ? caminho.substring(0, caminho.length() - 1) : caminho;
        if (limpo.equals("/api/auth/login")) return "login";
        return METODOS_ESCRITA.contains(metodo) ? "escrita" : "leitura";
    }

    private Limite limite(String classe) {
        return parse(switch (classe) {
            case "login" -> settings.getLoginRateLimit();
            case "escrita" -> settings.getWriteRateLimit();
            default -> settings.getRateLimit();
        });
    }

    /** Remove IPs inativos: sem isso o mapa cresceria sem limite (DoS de memória). */
    private void limpar(long agora) {
        long janelaMaxima = 3600L * 1_000_000_000L;
        acessos.values().removeIf(fila -> fila.isEmpty() || agora - fila.peekLast() >= janelaMaxima);
    }

    private synchronized boolean permitido(String cliente, String classe, Limite l) {
        long agora = System.nanoTime();
        if (++contador % LIMPEZA_A_CADA == 0) limpar(agora);
        ArrayDeque<Long> fila = acessos.computeIfAbsent(classe + "|" + cliente, k -> new ArrayDeque<>());
        long janela = l.janelaSegundos() * 1_000_000_000L;
        while (!fila.isEmpty() && agora - fila.peekFirst() >= janela) fila.pollFirst();
        if (fila.size() >= l.maximo()) return false;
        fila.addLast(agora);
        return true;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (!"OPTIONS".equals(req.getMethod())) {
            String classe = classe(req.getMethod(), req.getRequestURI());
            Limite l = limite(classe);
            if (!permitido(req.getRemoteAddr(), classe, l)) {
                Respostas.escrever(mapper, res, 429, Map.of("detail", "Muitas requisições. Tente novamente em instantes."),
                    Map.of("Retry-After", String.valueOf(l.janelaSegundos())));
                return;
            }
        }
        chain.doFilter(req, res);
    }
}
