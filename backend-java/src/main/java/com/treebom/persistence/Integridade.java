package com.treebom.persistence;

import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Reconhece violações de integridade (UNIQUE, CHECK, FK). O driver do SQLite nem sempre preenche o SQLState, então o
 * Spring pode entregá-las como "erro não categorizado"; aqui olhamos a causa raiz também.
 */
public final class Integridade {
    private Integridade() {}

    public static boolean violacao(Throwable e) {
        if (e instanceof DataIntegrityViolationException) return true;
        Throwable raiz = e;
        while (raiz.getCause() != null && raiz.getCause() != raiz) raiz = raiz.getCause();
        String msg = raiz.getMessage() == null ? "" : raiz.getMessage().toUpperCase(Locale.ROOT);
        return msg.contains("SQLITE_CONSTRAINT") || msg.contains("VIOLATES") || msg.contains("UNIQUE CONSTRAINT");
    }
}
