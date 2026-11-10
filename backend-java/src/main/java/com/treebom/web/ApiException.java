package com.treebom.web;

import java.util.Map;

/** Erro HTTP com mensagem segura para o cliente: {@code {"detail": "..."}}. */
public class ApiException extends RuntimeException {
    private final int status;
    private final Map<String, String> cabecalhos;

    public ApiException(int status, String detail) {
        this(status, detail, Map.of());
    }

    public ApiException(int status, String detail, Map<String, String> cabecalhos) {
        super(detail);
        this.status = status;
        this.cabecalhos = cabecalhos;
    }

    public int status() {
        return status;
    }

    public Map<String, String> cabecalhos() {
        return cabecalhos;
    }

    public static ApiException naoAutenticado() {
        return new ApiException(401, "Autenticação necessária", Map.of("WWW-Authenticate", "Bearer"));
    }

    public static ApiException naoEncontrado() {
        return new ApiException(404, "Não encontrado");
    }
}
