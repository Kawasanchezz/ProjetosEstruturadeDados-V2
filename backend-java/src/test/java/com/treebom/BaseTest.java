package com.treebom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.treebom.security.LoginThrottle;
import jakarta.servlet.http.Cookie;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Base dos testes de API: sobe o app inteiro (SQLite em arquivo), limpa o banco antes de cada teste. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class BaseTest {
    public static final String SEGREDO = "ssssssssssssssssssssssssssssssssssssssssssssssss";
    public static final String SEGREDO_ANTERIOR = "oooooooooooooooooooooooooooooooooooooooooooooooo";
    static final SecureRandom RANDOM = new SecureRandom();

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper mapper;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected LoginThrottle throttle;

    /** Cookies recebidos (nome → valor), como um navegador faria. */
    protected final java.util.Map<String, String> cookies = new java.util.HashMap<>();

    /** Resposta resumida. */
    public record Resp(int status, String corpo, MockHttpServletResponse raw, ObjectMapper mapper) {
        public JsonNode json() {
            try {
                return mapper.readTree(corpo);
            } catch (Exception e) {
                throw new IllegalStateException("Corpo não é JSON: " + corpo, e);
            }
        }

        public String cabecalho(String nome) {
            return raw.getHeader(nome);
        }
    }

    @BeforeEach
    void limparBanco() {
        jdbc.update("DELETE FROM refresh_tokens");
        jdbc.update("DELETE FROM usuarios");
        jdbc.update("DELETE FROM auditoria");
        jdbc.update("DELETE FROM componentes");
        jdbc.update("DELETE FROM sqlite_sequence"); // ids voltam a 1 em cada teste
        throttle.limpar();
        cookies.clear();
    }

    protected static String senhaAleatoria() {
        byte[] b = new byte[18];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    protected Resp req(HttpMethod metodo, String url, String corpo, String token, String... cabecalhos) {
        try {
            MockHttpServletRequestBuilder b = request(metodo, url);
            if (corpo != null) b.contentType("application/json").content(corpo);
            if (token != null) b.header("Authorization", "Bearer " + token);
            for (int i = 0; i < cabecalhos.length; i += 2) b.header(cabecalhos[i], cabecalhos[i + 1]);
            if (!cookies.isEmpty()) {
                List<Cookie> lista = new ArrayList<>();
                cookies.forEach((k, v) -> lista.add(new Cookie(k, v)));
                b.cookie(lista.toArray(new Cookie[0]));
            }
            MockHttpServletResponse r = mvc.perform(b).andReturn().getResponse();
            for (String sc : r.getHeaders("Set-Cookie")) {
                String[] par = sc.split(";", 2)[0].split("=", 2);
                if (sc.toLowerCase().contains("max-age=0")) cookies.remove(par[0]);
                else cookies.put(par[0], par.length > 1 ? par[1] : "");
            }
            return new Resp(r.getStatus(), r.getContentAsString(java.nio.charset.StandardCharsets.UTF_8), r, mapper);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected Resp get(String url) { return req(HttpMethod.GET, url, null, null); }
    protected Resp get(String url, String token) { return req(HttpMethod.GET, url, null, token); }
    protected Resp post(String url, String corpo) { return req(HttpMethod.POST, url, corpo, null); }
    protected Resp post(String url, String corpo, String token) { return req(HttpMethod.POST, url, corpo, token); }
    protected Resp put(String url, String corpo) { return req(HttpMethod.PUT, url, corpo, null); }
    protected Resp put(String url, String corpo, String token) { return req(HttpMethod.PUT, url, corpo, token); }
    protected Resp patch(String url, String corpo) { return req(HttpMethod.PATCH, url, corpo, null); }
    protected Resp patch(String url, String corpo, String token) { return req(HttpMethod.PATCH, url, corpo, token); }
    protected Resp delete(String url) { return req(HttpMethod.DELETE, url, null, null); }
    protected Resp delete(String url, String token) { return req(HttpMethod.DELETE, url, null, token); }

    protected static void assertStatus(int esperado, Resp r) {
        assertEquals(esperado, r.status(), () -> "corpo: " + r.corpo());
    }

    protected JsonNode seed(String nome) {
        Resp r = post("/api/seed/" + nome, "{}");
        assertStatus(201, r);
        return r.json();
    }

    /** Busca (pré-ordem) na árvore da explosão por nome e, opcionalmente, pelo nome do pai. */
    protected static JsonNode achar(JsonNode no, String nome, String pai) {
        return achar(no, nome, pai, null);
    }

    private static JsonNode achar(JsonNode no, String nome, String pai, String nomePai) {
        if (no.get("nome").asText().equals(nome) && (pai == null || pai.equals(nomePai))) return no;
        for (JsonNode f : no.get("filhos")) {
            JsonNode achado = achar(f, nome, pai, no.get("nome").asText());
            if (achado != null) return achado;
        }
        return null;
    }
}
