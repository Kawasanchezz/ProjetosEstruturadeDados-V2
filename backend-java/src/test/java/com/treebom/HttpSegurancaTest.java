package com.treebom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/** Cabeçalhos, erros genéricos, limites de corpo e CORS (sem login). */
class HttpSegurancaTest extends BaseTest {

    @Test
    void cabecalhosDeSeguranca() {
        Resp r = get("/api/produtos");
        String csp = r.cabecalho("Content-Security-Policy");
        assertTrue(csp.contains("script-src 'self'") && !csp.contains("unsafe"));
        assertEquals("nosniff", r.cabecalho("X-Content-Type-Options"));
        assertEquals("DENY", r.cabecalho("X-Frame-Options"));
        assertEquals("no-referrer", r.cabecalho("Referrer-Policy"));
        assertEquals("no-store", r.cabecalho("Cache-Control"));
        assertNotNull(r.cabecalho("X-Request-Id"));
        assertNull(r.cabecalho("Strict-Transport-Security"));
        assertNull(r.cabecalho("Server"));
    }

    @Test
    void saudeEPublicaEDocsNaoExistem() {
        assertEquals("ok", get("/api/saude").json().get("status").asText());
        for (String url : new String[] {"/api/docs", "/api/redoc", "/api/openapi.json", "/api/nao-existe"})
            assertStatus(404, get(url));
        assertStatus(405, req(HttpMethod.DELETE, "/api/produtos", null, null));
    }

    @Test
    void erroInternoNaoVazaDetalhes() {
        Resp r = get("/api/_teste/quebra");
        assertStatus(500, r);
        assertFalse(r.corpo().contains("segredo-interno"));
        assertFalse(r.corpo().contains("Traceback") || r.corpo().contains("Exception"));
        assertEquals(r.cabecalho("X-Request-Id"), r.json().get("request_id").asText());
    }

    @Test
    void corpoETipoSaoLimitados() {
        assertStatus(413, post("/api/produtos", "{\"nome\":\"x\",\"z\":\"" + "y".repeat(70_000) + "\"}"));
        Resp r = req(HttpMethod.POST, "/api/produtos", null, null);
        assertTrue(r.status() == 422 || r.status() == 415, "sem corpo: " + r.status());
        try {
            var resp = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/produtos")
                .contentType("text/plain").content("{\"nome\":\"a\"}")).andReturn().getResponse();
            assertEquals(415, resp.getStatus());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void corsSoAceitaOrigemConfigurada() {
        Resp ok = req(HttpMethod.OPTIONS, "/api/produtos", null, null, "Origin", "http://localhost:5173", "Access-Control-Request-Method", "GET");
        Resp mau = req(HttpMethod.OPTIONS, "/api/produtos", null, null, "Origin", "http://evil.example", "Access-Control-Request-Method", "GET");
        assertEquals("http://localhost:5173", ok.cabecalho("Access-Control-Allow-Origin"));
        assertEquals("true", ok.cabecalho("Access-Control-Allow-Credentials"));
        assertNull(mau.cabecalho("Access-Control-Allow-Origin"));
        Resp simples = req(HttpMethod.GET, "/api/produtos", null, null, "Origin", "http://evil.example");
        assertNull(simples.cabecalho("Access-Control-Allow-Origin"));
        assertNotNull(req(HttpMethod.GET, "/api/produtos", null, null, "Origin", "http://localhost:5173").cabecalho("Access-Control-Allow-Origin"));
    }
}
