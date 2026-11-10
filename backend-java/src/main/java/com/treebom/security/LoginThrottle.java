package com.treebom.security;

import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Atraso progressivo por (IP, usuário): 3 falhas livres, depois bloqueio 2^n s (máx. 15 min). */
@Component
public class LoginThrottle {
    public static final int LIVRES = 3;
    public static final int TETO = 900;
    public static final int MAX_CHAVES = 10_000;

    private record Estado(int falhas, long bloqueadoAteNanos) {}

    private final Map<String, Estado> estado = new HashMap<>(); // protegido por "this"

    public synchronized int segundosRestantes(String chave) {
        Estado e = estado.get(chave);
        if (e == null) return 0;
        double restante = (e.bloqueadoAteNanos() - System.nanoTime()) / 1e9;
        return Math.max(0, (int) (restante + 0.999));
    }

    public synchronized void falha(String chave) {
        if (estado.size() >= MAX_CHAVES) { // limite de memória: descarta as entradas já liberadas
            long agora = System.nanoTime();
            estado.values().removeIf(v -> v.bloqueadoAteNanos() <= agora);
        }
        int falhas = estado.getOrDefault(chave, new Estado(0, 0)).falhas() + 1;
        long espera = falhas <= LIVRES ? 0 : Math.min(1L << Math.min(falhas - LIVRES, 20), TETO);
        estado.put(chave, new Estado(falhas, System.nanoTime() + espera * 1_000_000_000L));
    }

    public synchronized void sucesso(String chave) {
        estado.remove(chave);
    }

    /** Zera todo o estado (usado pelos testes). */
    public synchronized void limpar() {
        estado.clear();
    }

    public synchronized int tamanho() {
        return estado.size();
    }
}
