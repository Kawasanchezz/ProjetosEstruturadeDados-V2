package com.treebom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.treebom.persistence.NoRepository;
import com.treebom.persistence.NoRepository.ConflitoConcorrencia;
import com.treebom.persistence.NoRepository.NoRow;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/** Bloqueio otimista e CLI de administração (com login ligado). */
@TestPropertySource(properties = {"treebom.auth-enabled=true", "treebom.jwt-secret=" + BaseTest.SEGREDO, "treebom.cookie-secure=false"})
class InfraTest extends BaseTest {
    @Autowired NoRepository repo;
    @Autowired AdminCli cli;

    @Test
    void bloqueioOtimistaDetectaEscritaConcorrente() {
        NoRow criado = repo.inserir("N", null, 1, new BigDecimal("1"));
        NoRow a = repo.get(criado.id(), false).orElseThrow();
        NoRow b = repo.get(criado.id(), false).orElseThrow();
        repo.atualizar(a, "N", 1, new BigDecimal("2"), null);
        assertThrows(ConflitoConcorrencia.class, () -> repo.atualizar(b, "N", 1, new BigDecimal("3"), null));
        assertEquals(0, repo.get(criado.id(), false).orElseThrow().preco().compareTo(new BigDecimal("2")));
        assertEquals(2, repo.get(criado.id(), false).orElseThrow().version());
    }

    @Test
    void checkConstraintsDoBancoProtegemOsDados() {
        assertThrows(Exception.class, () -> jdbc.update("INSERT INTO componentes (nome, parent_id, quantidade, preco) VALUES ('x', NULL, 0, 1)"));
        assertThrows(Exception.class, () -> jdbc.update("INSERT INTO componentes (nome, parent_id, quantidade, preco) VALUES ('x', NULL, 1, -1)"));
        assertThrows(Exception.class, () -> jdbc.update("INSERT INTO componentes (nome, parent_id, quantidade, preco) VALUES ('x', 999, 1, 1)")); // FK
    }

    @Test
    void cliRedefineSenhaEDerrubaTodasAsSessoes() {
        PrintStream out = new PrintStream(new ByteArrayOutputStream());
        String inicial = senhaAleatoria();
        String nova = senhaAleatoria();
        assertEquals(0, cli.executar(new String[] {"criar-usuario", "chefe", "admin"}, () -> inicial, out));

        Resp login = post("/api/auth/login", "{\"username\":\"chefe\",\"password\":\"" + inicial + "\"}");
        assertStatus(200, login);
        String token = login.json().get("access_token").asText();
        assertStatus(200, get("/api/produtos", token));

        assertEquals(0, cli.executar(new String[] {"redefinir-senha", "chefe"}, () -> nova, out));
        assertStatus(401, get("/api/produtos", token)); // sessão antiga morreu
        assertStatus(401, post("/api/auth/refresh", "{}")); // refresh também
        assertStatus(401, post("/api/auth/login", "{\"username\":\"chefe\",\"password\":\"" + inicial + "\"}"));
        assertStatus(200, post("/api/auth/login", "{\"username\":\"chefe\",\"password\":\"" + nova + "\"}"));

        assertEquals(1, cli.executar(new String[] {"redefinir-senha", "chefe"}, () -> "curta", out)); // política de senha vale na CLI
        assertEquals(1, cli.executar(new String[] {"redefinir-senha", "fantasma"}, () -> nova, out));
        assertEquals(2, cli.executar(new String[] {"x"}, () -> nova, out));
    }

    @Test
    void cliMostraUsoSemArgumentosValidos() {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        assertEquals(2, cli.executar(new String[] {}, () -> "x", new PrintStream(buf, true, StandardCharsets.UTF_8)));
        assertTrue(buf.toString(StandardCharsets.UTF_8).contains("criar-usuario"));
    }
}
