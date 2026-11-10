package com.treebom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.databind.JsonNode;
import com.treebom.security.AuthService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** Testes de segurança (regressão): cada teste prova uma proteção do sistema. */
@TestPropertySource(properties = {
    "treebom.auth-enabled=true",
    "treebom.jwt-secret=" + BaseTest.SEGREDO,
    "treebom.jwt-secret-anterior=" + BaseTest.SEGREDO_ANTERIOR,
    "treebom.cookie-secure=false",
})
class AuthTest extends BaseTest {
    @Autowired AuthService auth;
    @Autowired RequestMappingHandlerMapping mapeamento;

    String senhaAdmin;
    String senhaViewer;

    @BeforeEach
    void criarUsuarios() {
        senhaAdmin = senhaAleatoria();
        senhaViewer = senhaAleatoria();
        auth.criarUsuario("admin1", senhaAdmin, "admin"); // id 1
        auth.criarUsuario("viewer1", senhaViewer, "viewer");
    }

    Resp login(String u, String s) {
        return post("/api/auth/login", "{\"username\":\"" + u + "\",\"password\":\"" + s + "\"}");
    }

    String token(String u, String s) {
        Resp r = login(u, s);
        assertStatus(200, r);
        return r.json().get("access_token").asText();
    }

    static String forjar(String segredo, Map<String, Object> extra) {
        Instant agora = Instant.now();
        var b = JWT.create().withSubject("1").withIssuedAt(agora).withExpiresAt(agora.plusSeconds(600)).withJWTId("x")
            .withClaim("typ", "access").withClaim("tv", 1);
        extra.forEach((k, v) -> {
            if (v instanceof Instant i) b.withClaim(k, i);
            else b.withClaim(k, (String) v);
        });
        return b.sign(Algorithm.HMAC256(segredo));
    }

    // ------------------------------------------------------------- JWT e login
    @Test
    void semTokenMalformadoExpiradoAlgNoneEOutroSegredo() {
        assertStatus(401, get("/api/produtos"));
        String b64 = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String corpo = Base64.getUrlEncoder().withoutPadding().encodeToString(
            ("{\"sub\":\"1\",\"exp\":" + (Instant.now().getEpochSecond() + 600) + ",\"iat\":1,\"jti\":\"x\",\"typ\":\"access\",\"tv\":1}").getBytes(StandardCharsets.UTF_8));
        List<String> tokens = List.of("lixo", "a.b.c", forjar(SEGREDO, Map.of("exp", Instant.now().minusSeconds(5))),
            b64 + "." + corpo + ".", forjar("x".repeat(48), Map.of()), forjar(SEGREDO, Map.of("typ", "refresh")));
        for (String t : tokens) assertStatus(401, get("/api/produtos", t));
        assertTrue(get("/api/produtos").cabecalho("WWW-Authenticate").contains("Bearer"));
    }

    @Test
    void loginNaoRevelaUsuarioEBloqueiaForcaBruta() {
        Resp a = login("ninguem", "qualquer-coisa-123");
        Resp b = login("admin1", "errada-errada-1");
        assertStatus(401, a);
        assertStatus(401, b);
        assertEquals(a.corpo(), b.corpo());
        for (int i = 0; i < 3; i++) login("admin1", "errada-errada-1");
        Resp bloqueado = login("admin1", senhaAdmin);
        assertStatus(429, bloqueado); // bloqueado mesmo com a senha certa
        assertNotEquals(null, bloqueado.cabecalho("Retry-After"));
    }

    @Test
    void senhaEmArgon2idEPoliticaDeSenha() {
        List<String> hashes = jdbc.queryForList("SELECT password_hash FROM usuarios", String.class);
        assertTrue(hashes.stream().allMatch(h -> h.startsWith("$argon2id$")));
        assertFalse(String.join("", hashes).contains(senhaAdmin));
        String t = token("admin1", senhaAdmin);
        assertStatus(422, post("/api/auth/users", "{\"username\":\"novo\",\"password\":\"curta\",\"role\":\"viewer\"}", t));
        assertStatus(201, post("/api/auth/users", "{\"username\":\"Novo.User\",\"password\":\"" + senhaAleatoria() + "\",\"role\":\"viewer\"}", t));
        assertStatus(409, post("/api/auth/users", "{\"username\":\"novo.user\",\"password\":\"" + senhaAleatoria() + "\",\"role\":\"viewer\"}", t));
        assertEquals("admin1", get("/api/auth/me", t).json().get("username").asText());
    }

    @Test
    void logoutRevogaAccessTokenNaHoraEORefresh() {
        Resp r = login("admin1", senhaAdmin);
        String t = r.json().get("access_token").asText();
        String refresh = cookies.get("bom_refresh");
        assertStatus(200, get("/api/produtos", t));
        assertStatus(204, post("/api/auth/logout", "{}"));
        assertStatus(401, get("/api/produtos", t)); // token_version mudou
        cookies.put("bom_refresh", refresh);
        assertStatus(401, post("/api/auth/refresh", "{}"));
    }

    @Test
    void rotacaoDeRefreshEReusoDerrubaAFamilia() {
        login("admin1", senhaAdmin);
        String antigo = cookies.get("bom_refresh");
        assertStatus(200, post("/api/auth/refresh", "{}"));
        String novo = cookies.get("bom_refresh");
        assertNotEquals(antigo, novo);
        cookies.put("bom_refresh", antigo);
        assertStatus(401, post("/api/auth/refresh", "{}")); // reuso detectado
        cookies.put("bom_refresh", novo);
        assertStatus(401, post("/api/auth/refresh", "{}")); // a família inteira caiu
    }

    @Test
    void cookieHttpOnlySameSiteStrictEOrigemNaoConfiavel() {
        String cookie = login("admin1", senhaAdmin).cabecalho("Set-Cookie").toLowerCase();
        assertTrue(cookie.contains("httponly") && cookie.contains("samesite=strict") && cookie.contains("path=/api/auth"), cookie);
        assertStatus(403, req(HttpMethod.POST, "/api/auth/refresh", "{}", null, "Origin", "http://evil.example"));
        assertStatus(200, req(HttpMethod.POST, "/api/auth/refresh", "{}", null, "Origin", "http://localhost:5173"));
    }

    @Test
    void rotacaoDeSegredoJwt() {
        assertStatus(200, get("/api/produtos", forjar(SEGREDO_ANTERIOR, Map.of())));
        assertStatus(401, get("/api/produtos", forjar("z".repeat(48), Map.of())));
    }

    @Test
    void rotasDeAuthExistemComAuthLigada() {
        assertEquals(true, get("/api/auth/config").json().get("auth_enabled").asBoolean());
    }

    // ------------------------------------------------------------ autorização
    @Test
    void viewerLeESimulaMasNaoEscreve() {
        String admin = token("admin1", senhaAdmin);
        String viewer = token("viewer1", senhaViewer);
        JsonNode p = post("/api/seed/caneta", "{}", admin).json();
        long id = p.get("id").asLong();
        assertStatus(200, get("/api/produtos/" + id + "/explosao", viewer));
        assertStatus(200, post("/api/produtos/" + id + "/simulacao", "{\"quantidade\":2}", viewer));
        List<Resp> proibidos = List.of(
            post("/api/produtos", "{\"nome\":\"X\"}", viewer),
            delete("/api/produtos/" + id, viewer),
            patch("/api/componentes/" + id + "/preco", "{\"preco\":\"9\"}", viewer),
            post("/api/seed/computador", "{}", viewer),
            get("/api/auditoria", viewer),
            post("/api/auth/users", "{\"username\":\"hack\",\"password\":\"" + "x".repeat(14) + "\",\"role\":\"admin\"}", viewer));
        assertEquals(List.of(403, 403, 403, 403, 403, 403), proibidos.stream().map(Resp::status).toList());
        assertEquals("7.50", get("/api/produtos/" + id, viewer).json().get("custo_total").asText());
    }

    @Test
    void papelVemDoBancoEDesativadoPerdeAcesso() {
        String h = token("admin1", senhaAdmin);
        String hv = token("viewer1", senhaViewer);
        jdbc.update("UPDATE usuarios SET role = 'viewer' WHERE username = 'admin1'");
        jdbc.update("UPDATE usuarios SET ativo = 0 WHERE username = 'viewer1'");
        assertStatus(403, post("/api/produtos", "{\"nome\":\"X\"}", h));
        assertStatus(401, get("/api/produtos", hv));
    }

    @Test
    void negarPorPadraoToda_rotaDaApiExigePapel() {
        List<String> publicas = new java.util.ArrayList<>();
        int verificadas = 0;
        for (Map.Entry<RequestMappingInfo, HandlerMethod> e : mapeamento.getHandlerMethods().entrySet()) {
            HandlerMethod hm = e.getValue();
            if (!hm.getBeanType().getName().startsWith("com.treebom.web.")) continue;
            if (hm.getBeanType().getName().contains("teste")) continue;
            var padroes = e.getKey().getPathPatternsCondition().getPatternValues();
            if (hm.hasMethodAnnotation(com.treebom.web.Publico.class)) {
                publicas.addAll(padroes);
                continue;
            }
            // sem credencial, qualquer outra rota responde 401
            for (String padrao : padroes) {
                String url = padrao.replaceAll("\\{[^}]+}", "1");
                for (var metodo : e.getKey().getMethodsCondition().getMethods()) {
                    boolean semCorpo = metodo.name().equals("GET") || metodo.name().equals("DELETE");
                    assertStatus(401, req(HttpMethod.valueOf(metodo.name()), url, semCorpo ? null : "{}", null));
                    verificadas++;
                }
            }
        }
        assertTrue(verificadas >= 15, "rotas verificadas: " + verificadas);
        assertEquals(java.util.Set.of("/api/saude", "/api/auth/config", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout"),
            new java.util.HashSet<>(publicas));
    }

    // ------------------------------------------------------------- auditoria
    @Test
    void trilhaDeAuditoriaComAtorReal() {
        String h = token("admin1", senhaAdmin);
        JsonNode p = post("/api/seed/caneta", "{}", h).json();
        JsonNode raiz = get("/api/produtos/" + p.get("id").asLong() + "/explosao", h).json().get("raiz");
        long plastico = achar(raiz, "Plástico", "Corpo").get("id").asLong();
        patch("/api/componentes/" + plastico + "/preco", "{\"preco\":\"1.70\"}", h);
        JsonNode itens = get("/api/auditoria", h).json().get("itens");
        JsonNode preco = null;
        for (JsonNode i : itens) if (i.get("acao").asText().equals("preco")) preco = i;
        assertEquals("admin1", preco.get("ator").asText());
    }
}
