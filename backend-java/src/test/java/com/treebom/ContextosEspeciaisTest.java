package com.treebom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/** Cenários que exigem uma configuração própria (cada classe aninhada sobe seu contexto). */
class ContextosEspeciaisTest {

    @Nested
    @TestPropertySource(properties = {"treebom.write-rate-limit=3/minute"})
    class LimiteDeEscrita extends BaseTest {
        @Test
        void limitePorClasseEmMemoria() {
            List<Integer> codigos = new java.util.ArrayList<>();
            for (int i = 0; i < 5; i++) codigos.add(post("/api/produtos", "{\"nome\":\"p" + i + "\"}").status());
            assertEquals(List.of(201, 201, 201, 429, 429), codigos);
            assertEquals(200, get("/api/produtos").status()); // leitura tem outro balde
            assertNotNull(post("/api/produtos", "{\"nome\":\"z\"}").cabecalho("Retry-After"));
        }
    }

    @Nested
    @TestPropertySource(properties = {
        "treebom.auth-enabled=true", "treebom.jwt-secret=" + BaseTest.SEGREDO, "treebom.cookie-secure=false",
        "treebom.login-rate-limit=3/minute"})
    class LimiteDeLogin extends BaseTest {
        @Test
        void loginTemSeuProprioLimite() {
            List<Integer> codigos = new java.util.ArrayList<>();
            for (int i = 0; i < 5; i++)
                codigos.add(post("/api/auth/login", "{\"username\":\"x\",\"password\":\"" + senhaAleatoria() + "\"}").status());
            assertEquals(List.of(401, 401, 401, 429, 429), codigos);
        }
    }

    @Nested
    @TestPropertySource(properties = {"treebom.request-timeout-s=1"})
    class Timeout extends BaseTest {
        @Test
        void responde504QuandoPassaDoTempo() {
            assertEquals(504, get("/api/_teste/lento").status());
            assertEquals(200, get("/api/saude").status()); // o servidor segue de pé
        }
    }

    @Nested
    @TestPropertySource(properties = {"treebom.max-filhos-por-no=2", "treebom.max-nos-por-produto=4"})
    class LimiteDeFilhosENos extends BaseTest {
        @Test
        void recusaAlemDoLimite() {
            long p = post("/api/produtos", "{\"nome\":\"R\"}").json().get("id").asLong();
            for (int i = 0; i < 2; i++)
                assertEquals(201, post("/api/componentes", "{\"nome\":\"f" + i + "\",\"parent_id\":" + p + ",\"preco\":\"1\"}").status());
            Resp r = post("/api/componentes", "{\"nome\":\"f3\",\"parent_id\":" + p + ",\"preco\":\"1\"}");
            assertEquals(422, r.status());
            assertTrue(r.json().get("detail").asText().contains("filhos"));
        }

        @Test
        void recusaMuitosNos() {
            long p = post("/api/produtos", "{\"nome\":\"R\"}").json().get("id").asLong();
            long a = post("/api/componentes", "{\"nome\":\"a\",\"parent_id\":" + p + ",\"preco\":\"1\"}").json().get("id").asLong();
            long b = post("/api/componentes", "{\"nome\":\"b\",\"parent_id\":" + a + ",\"preco\":\"1\"}").json().get("id").asLong();
            post("/api/componentes", "{\"nome\":\"c\",\"parent_id\":" + b + ",\"preco\":\"1\"}");
            Resp r = post("/api/componentes", "{\"nome\":\"d\",\"parent_id\":" + b + ",\"preco\":\"1\"}");
            assertEquals(422, r.status());
            assertTrue(r.json().get("detail").asText().contains("nós"));
        }
    }

    @Nested
    @TestPropertySource(properties = {"treebom.max-profundidade=3"})
    class LimiteDeProfundidade extends BaseTest {
        @Test
        void recusaArvoreMuitoProfunda() {
            long no = post("/api/produtos", "{\"nome\":\"n1\"}").json().get("id").asLong();
            for (int i = 2; i <= 3; i++)
                no = post("/api/componentes", "{\"nome\":\"n" + i + "\",\"parent_id\":" + no + ",\"preco\":\"1\"}").json().get("id").asLong();
            Resp r = post("/api/componentes", "{\"nome\":\"n4\",\"parent_id\":" + no + ",\"preco\":\"1\"}");
            assertEquals(422, r.status());
            assertTrue(r.json().get("detail").asText().contains("Profundidade"));
        }
    }

    @Nested
    @TestPropertySource(properties = {"treebom.max-profundidade=10"})
    class ArvoreAdulterada extends BaseTest {
        @Test
        void arvoreAdulteradaNoBancoResponde422SemDerrubar() {
            Long pai = null;
            for (int i = 0; i < 300; i++) {
                jdbc.update("INSERT INTO componentes (nome, parent_id, quantidade, preco, version) VALUES (?, ?, 1, 100, 1)", "n" + i, pai);
                pai = jdbc.queryForObject("SELECT max(id) FROM componentes", Long.class);
            }
            assertEquals(422, get("/api/produtos/1/explosao").status());
            assertEquals(200, get("/api/saude").status());
        }
    }

    @Nested
    @TestPropertySource(properties = {
        "treebom.app-env=production", "treebom.auth-enabled=true", "treebom.jwt-secret=" + BaseTest.SEGREDO})
    class Producao extends BaseTest {
        @Autowired com.treebom.security.AuthService auth;

        @Test
        void producaoDesligaSeedLigaHstsEExigeLogin() {
            for (String url : new String[] {"/api/docs", "/api/redoc", "/api/openapi.json"}) assertEquals(404, get(url).status());
            assertTrue(get("/api/saude").cabecalho("Strict-Transport-Security").contains("max-age"));
            assertEquals(401, get("/api/produtos").status());
            String senha = senhaAleatoria();
            auth.criarUsuario("adm", senha, "admin");
            Resp login = post("/api/auth/login", "{\"username\":\"adm\",\"password\":\"" + senha + "\"}");
            assertEquals(200, login.status());
            assertTrue(login.cabecalho("Set-Cookie").toLowerCase().contains("secure"));
            assertEquals(404, post("/api/seed/caneta", "{}", login.json().get("access_token").asText()).status());
        }
    }

    // ------------------------------------------------------------------ SPA
    static final Path ESTATICO = Path.of("target", "spa-teste");

    @Nested
    @TestPropertySource(properties = {"treebom.static-dir=target/spa-teste"})
    class Spa extends BaseTest {
        @DynamicPropertySource
        static void arquivos(DynamicPropertyRegistry registry) throws IOException {
            Files.createDirectories(ESTATICO);
            Files.writeString(ESTATICO.resolve("index.html"), "<div id=\"root\"></div>");
            Files.writeString(ESTATICO.resolve("app.js"), "console.log('ok')");
        }

        @Test
        void servePastaEFazFallbackParaOIndex() {
            assertEquals("/index.html", get("/").raw().getForwardedUrl()); // MockMvc não executa o forward; o servidor real sim
            for (String caminho : List.of("/explosao/1", "/index.html")) {
                Resp r = get(caminho);
                assertTrue(r.corpo().contains("id=\"root\""), caminho + " -> " + r.status() + " " + r.corpo());
            }
            assertTrue(get("/app.js").corpo().contains("console.log"));
            // API desconhecida é 404 (JSON), nunca o HTML da SPA
            Resp r = get("/api/nao-existe");
            assertEquals(404, r.status());
            assertFalse(r.corpo().contains("root"));
            // sem path traversal
            try {
                var resp = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .get(java.net.URI.create("/..%2f..%2fbom.db"))).andReturn().getResponse();
                assertFalse(resp.getContentAsString().contains("SQLite format"));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            assertEquals(200, get("/api/saude").status());
        }
    }

    @Nested
    @TestPropertySource(properties = {"treebom.auth-enabled=false"})
    class Misc extends BaseTest {
        @Test
        void authDesligadaDevolve404NasRotasDeLogin() {
            assertEquals(404, post("/api/auth/login", "{\"username\":\"a\",\"password\":\"b\"}").status());
            assertEquals(false, get("/api/auth/config").json().get("auth_enabled").asBoolean());
        }
    }
}
