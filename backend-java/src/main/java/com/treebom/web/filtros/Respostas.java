package com.treebom.web.filtros;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Pattern;

/** Utilidades comuns aos filtros. */
final class Respostas {
    private Respostas() {}

    private static final Pattern CONTROLE = Pattern.compile("[\\x00-\\x1f\\x7f]");

    /** Neutraliza quebras de linha e controles (log injection) e limita o tamanho. */
    static String limparLog(Object valor) {
        String s = CONTROLE.matcher(String.valueOf(valor)).replaceAll("?");
        return s.length() > 200 ? s.substring(0, 200) : s;
    }

    static void escrever(ObjectMapper mapper, HttpServletResponse res, int status, Map<String, ?> corpo,
                         Map<String, String> cabecalhos) throws IOException {
        byte[] bytes = mapper.writeValueAsBytes(corpo);
        res.setStatus(status);
        cabecalhos.forEach(res::setHeader);
        res.setContentType("application/json");
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        res.setContentLength(bytes.length);
        res.getOutputStream().write(bytes);
        res.getOutputStream().flush();
    }

    static void escrever(ObjectMapper mapper, HttpServletResponse res, int status, String detalhe) throws IOException {
        escrever(mapper, res, status, Map.of("detail", detalhe), Map.of());
    }
}
