package com.treebom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Endpoints (sem login): totais, explosão, simulação, implosão, CRUD, cascata, validação e migração. */
class ApiTest extends BaseTest {

    @Test
    void totaisDosTestesObrigatorios() {
        assertEquals("7.50", seed("caneta").get("custo_total").asText());
        assertEquals("8080.00", seed("computador").get("custo_total").asText());
    }

    @Test
    void seedInvalidoEIdempotente() {
        assertStatus(422, post("/api/seed/foguete", "{}"));
        seed("caneta");
        seed("caneta");
        assertEquals(1, get("/api/produtos").json().get("total").asInt());
    }

    @Test
    void listagemEmLoteTemTotaisEContagensCorretos() {
        seed("caneta");
        seed("computador");
        Map<String, JsonNode> itens = new HashMap<>();
        get("/api/produtos").json().get("itens").forEach(i -> itens.put(i.get("nome").asText(), i));
        assertEquals(9, itens.get("Caneta").get("total_componentes").asInt());
        assertEquals(3, itens.get("Caneta").get("profundidade").asInt());
        assertEquals(15, itens.get("Computador").get("total_componentes").asInt());
        assertEquals("8080.00", itens.get("Computador").get("custo_total").asText());
    }

    @Test
    void explosaoENomesRepetidos() {
        JsonNode p = seed("caneta");
        JsonNode raiz = get("/api/produtos/" + p.get("id").asLong() + "/explosao").json().get("raiz");
        JsonNode a = achar(raiz, "Plástico", "Corpo");
        JsonNode b = achar(raiz, "Plástico", "Tampa");
        assertNotEquals(a.get("id").asLong(), b.get("id").asLong());
        assertEquals("0.70", a.get("preco").asText());
        assertEquals("0.50", b.get("preco").asText());
        assertEquals("2.00", achar(raiz, "Corpo", null).get("custo_unitario").asText());
        // ordem dos irmãos preservada (pré-ordem original)
        assertEquals("Tinta", raiz.get("filhos").get(0).get("nome").asText());
        assertEquals("Corpo", raiz.get("filhos").get(1).get("nome").asText());
        assertEquals("Tampa", raiz.get("filhos").get(2).get("nome").asText());
    }

    @Test
    void simulacao() {
        JsonNode p = seed("caneta");
        long id = p.get("id").asLong();
        JsonNode r = post("/api/produtos/" + id + "/simulacao", "{\"quantidade\": 10}").json();
        assertEquals("75.00", r.get("total").asText());
        assertEquals(10, r.get("linhas").size());
        assertStatus(422, post("/api/produtos/" + id + "/simulacao", "{\"quantidade\": 0}"));

        long pc = seed("computador").get("id").asLong();
        JsonNode ram = null;
        for (JsonNode l : post("/api/produtos/" + pc + "/simulacao", "{\"quantidade\": 5}").json().get("linhas"))
            if (l.get("nome").asText().equals("Memória RAM")) ram = l;
        assertEquals(10, ram.get("quantidade_total").asInt());
        assertEquals("2000.00", ram.get("custo").asText());
    }

    @Test
    void implosaoRetornaIdsDoNoAteARaiz() {
        JsonNode p = seed("caneta");
        long pid = p.get("id").asLong();
        JsonNode raiz = get("/api/produtos/" + pid + "/explosao").json().get("raiz");
        JsonNode plastico = achar(raiz, "Plástico", "Corpo");
        JsonNode corpo = achar(raiz, "Corpo", null);
        JsonNode r = patch("/api/componentes/" + plastico.get("id").asLong() + "/preco", "{\"preco\": \"1.70\"}").json();
        assertEquals(List.of(plastico.get("id").asLong(), corpo.get("id").asLong(), pid),
            java.util.stream.StreamSupport.stream(r.get("ids_afetados").spliterator(), false).map(JsonNode::asLong).toList());
        assertEquals("7.50", r.get("total_antes").asText());
        assertEquals("8.50", r.get("total_depois").asText());
        assertEquals("8.50", get("/api/produtos/" + pid).json().get("custo_total").asText());
    }

    @Test
    void crudEExclusaoEmCascata() {
        JsonNode p = post("/api/produtos", "{\"nome\": \"  Mesa  \", \"preco\": \"10\"}").json();
        assertEquals("Mesa", p.get("nome").asText());
        long pid = p.get("id").asLong();
        JsonNode c = post("/api/componentes", "{\"nome\":\"Perna\",\"parent_id\":" + pid + ",\"quantidade\":4,\"preco\":\"5.00\"}").json();
        long cid = c.get("id").asLong();
        assertEquals("30.00", get("/api/produtos/" + pid).json().get("custo_total").asText());
        assertEquals(2, put("/api/componentes/" + cid, "{\"quantidade\": 2}").json().get("quantidade").asInt());
        assertEquals("[\"Mesa\",\"Perna\"]", get("/api/componentes/" + cid).json().get("caminho").toString());

        JsonNode s = seed("caneta");
        long sid = s.get("id").asLong();
        JsonNode raiz = get("/api/produtos/" + sid + "/explosao").json().get("raiz");
        long tinta = achar(raiz, "Tinta", null).get("id").asLong();
        assertEquals(3, delete("/api/componentes/" + tinta).json().get("removidos").asInt());
        assertEquals(7, delete("/api/produtos/" + sid).json().get("removidos").asInt());
        assertStatus(404, get("/api/componentes/" + tinta));
    }

    @Test
    void editarProdutoEListarComponentes() {
        long pid = post("/api/produtos", "{\"nome\": \"Mesa\", \"preco\": \"10\"}").json().get("id").asLong();
        JsonNode r = put("/api/produtos/" + pid, "{\"nome\": \"Mesa 2\", \"preco\": \"12.50\", \"quantidade\": null}").json();
        assertEquals("Mesa 2", r.get("nome").asText());
        assertEquals("12.50", r.get("preco").asText());
        post("/api/componentes", "{\"nome\":\"Perna\",\"parent_id\":" + pid + ",\"preco\":\"1\"}");
        JsonNode comps = get("/api/produtos/" + pid + "/componentes").json();
        assertEquals(2, comps.get("total").asInt());
        assertEquals(1, get("/api/produtos/" + pid + "/componentes?limit=1&offset=1").json().get("itens").size());
        // um componente não é produto
        long cid = comps.get("itens").get(1).get("id").asLong();
        assertStatus(404, get("/api/produtos/" + cid));
    }

    @Test
    void validacaoDeEntradaEPaginacao() {
        JsonNode p = seed("caneta");
        long pid = p.get("id").asLong();
        String base = "\"nome\":\"X\",\"parent_id\":" + pid + ",\"quantidade\":1,\"preco\":\"1\"";
        for (String ruim : List.of("\"nome\":\"   \"", "\"quantidade\":0", "\"preco\":\"-0.01\"", "\"preco\":\"abc\"", "\"extra\":1")) {
            assertStatus(422, post("/api/componentes", "{" + juntar(base, ruim) + "}"));
        }
        assertStatus(422, post("/api/componentes", "{\"nome\":\"X\",\"parent_id\":999,\"quantidade\":1,\"preco\":\"1\"}"));
        for (int i = 0; i < 3; i++) post("/api/produtos", "{\"nome\": \"P" + i + "\"}");
        JsonNode pagina = get("/api/produtos?limit=2&offset=1").json();
        assertEquals(4, pagina.get("total").asInt());
        assertEquals(2, pagina.get("itens").size());
        assertStatus(422, get("/api/produtos?limit=1000"));
        assertStatus(422, get("/api/produtos?limit=abc"));
    }

    /** Sobrescreve (ou acrescenta) o campo de {@code ruim} em {@code base}. */
    private static String juntar(String base, String ruim) {
        String chave = ruim.substring(0, ruim.indexOf(':'));
        String semChave = java.util.Arrays.stream(base.split(","))
            .filter(par -> !par.startsWith(chave + ":")).collect(Collectors.joining(","));
        return semChave + "," + ruim;
    }

    @Test
    void migracoesCriamTabelasEIndices() {
        Set<String> colunas = jdbc.queryForList("PRAGMA table_info(componentes)").stream()
            .map(m -> (String) m.get("name")).collect(Collectors.toSet());
        assertEquals(Set.of("id", "nome", "parent_id", "quantidade", "preco", "version"), colunas);
        Set<String> usuarios = jdbc.queryForList("PRAGMA table_info(usuarios)").stream()
            .map(m -> (String) m.get("name")).collect(Collectors.toSet());
        assertTrue(usuarios.contains("token_version"));
        Set<String> tabelas = jdbc.queryForList("SELECT name FROM sqlite_master WHERE type='table'", String.class)
            .stream().collect(Collectors.toSet());
        assertTrue(tabelas.containsAll(Set.of("refresh_tokens", "auditoria")));
        assertTrue(jdbc.queryForList("PRAGMA index_list(componentes)").stream()
            .anyMatch(i -> ((String) i.get("name")).equals("ix_componentes_parent_id")));
    }

    // ------------------------------------------------ entradas inválidas (segurança)
    @ParameterizedTest
    @ValueSource(strings = {"\"NaN\"", "\"Infinity\"", "\"1e999\"", "-1", "\"-1\"", "\"0.001\"", "\"99999999999999\"", "\"abc\"", "null", "[]", "true", "{}"})
    void precosInvalidos(String preco) {
        long p = post("/api/produtos", "{\"nome\":\"B\"}").json().get("id").asLong();
        assertStatus(422, post("/api/componentes", "{\"nome\":\"X\",\"parent_id\":" + p + ",\"preco\":" + preco + "}"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.5", "\"5\"", "true", "null", "1000001", "1000000000000000000000000000000"})
    void quantidadesInvalidas(String qtd) {
        long p = post("/api/produtos", "{\"nome\":\"B\"}").json().get("id").asLong();
        assertStatus(422, post("/api/componentes", "{\"nome\":\"X\",\"parent_id\":" + p + ",\"preco\":\"1\",\"quantidade\":" + qtd + "}"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a\\nb", "a\\u0000b", "a\\u202eb", "a\\u001b[31mb", ""})
    void nomesInvalidos(String nome) {
        assertStatus(422, post("/api/produtos", "{\"nome\":\"" + nome + "\"}"));
    }

    @Test
    void nomeComLimiteDeTamanho() {
        assertStatus(201, post("/api/produtos", "{\"nome\":\"" + "x".repeat(120) + "\"}"));
        assertStatus(422, post("/api/produtos", "{\"nome\":\"" + "x".repeat(121) + "\"}"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"1.50\"", "1.5", "\"100\"", "100", "\"0\"", "\"9999999999.99\"", "\"1.500\""})
    void precosValidos(String preco) {
        long p = post("/api/produtos", "{\"nome\":\"B\"}").json().get("id").asLong();
        assertStatus(201, post("/api/componentes", "{\"nome\":\"X\",\"parent_id\":" + p + ",\"preco\":" + preco + "}"));
    }

    @Test
    void camposExtrasIdsGigantesEInjecao() {
        for (String extra : List.of("\"id\":9", "\"version\":5", "\"role\":\"admin\"", "\"parent_id\":1"))
            assertStatus(422, post("/api/produtos", "{\"nome\":\"X\"," + extra + "}"));
        for (String url : List.of("/api/produtos/99999999999999999999", "/api/componentes/0", "/api/produtos/-1", "/api/produtos/abc"))
            assertStatus(422, get(url));
        for (String nome : List.of("x'); DROP TABLE componentes;--", "<img src=x onerror=\\\"alert(1)\\\">", "<script>alert(1)</script>")) {
            JsonNode r = post("/api/produtos", "{\"nome\":\"" + nome + "\"}").json();
            assertEquals(nome.replace("\\\"", "\""), r.get("nome").asText()); // texto inerte
        }
        assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM componentes", Integer.class));
    }

    @Test
    void jsonMalformadoEhRejeitadoSemEcoar() {
        Resp r = post("/api/produtos", "{\"nome\": SEGREDO-123");
        assertStatus(422, r);
        assertFalse(r.corpo().contains("SEGREDO-123"));
        assertFalse(post("/api/produtos", "{\"nome\":\"ok\",\"preco\":\"SEGREDO-123\"}").corpo().contains("SEGREDO-123"));
        assertStatus(422, post("/api/produtos", "[1,2]"));
        assertStatus(422, post("/api/produtos", "\"texto\""));
    }

    @Test
    void ciclosLimitesEOverflow() {
        long p = post("/api/produtos", "{\"nome\":\"R\",\"preco\":\"1\"}").json().get("id").asLong();
        long a = post("/api/componentes", "{\"nome\":\"A\",\"parent_id\":" + p + ",\"preco\":\"1\"}").json().get("id").asLong();
        long b = post("/api/componentes", "{\"nome\":\"B\",\"parent_id\":" + a + ",\"preco\":\"1\"}").json().get("id").asLong();
        assertStatus(409, put("/api/componentes/" + a, "{\"parent_id\":" + a + "}"));
        assertStatus(409, put("/api/componentes/" + a, "{\"parent_id\":" + b + "}"));
        assertStatus(409, put("/api/componentes/" + p, "{\"parent_id\":" + b + "}"));
        assertStatus(422, put("/api/componentes/" + a, "{\"parent_id\":999999}"));
        long g = post("/api/componentes", "{\"nome\":\"G\",\"parent_id\":" + b + ",\"quantidade\":1000000,\"preco\":\"1\"}").json().get("id").asLong();
        Resp r = post("/api/componentes", "{\"nome\":\"H\",\"parent_id\":" + g + ",\"quantidade\":1000000,\"preco\":\"1\"}");
        assertStatus(422, r);
        assertTrue(r.json().get("detail").asText().contains("limite"));
        // a escrita recusada foi desfeita
        assertEquals(4, get("/api/produtos/" + p + "/componentes").json().get("total").asInt());
    }

    @Test
    void moverNoEPromoverAProduto() {
        long p = post("/api/produtos", "{\"nome\":\"R\"}").json().get("id").asLong();
        long a = post("/api/componentes", "{\"nome\":\"A\",\"parent_id\":" + p + ",\"preco\":\"1\"}").json().get("id").asLong();
        long b = post("/api/componentes", "{\"nome\":\"B\",\"parent_id\":" + p + ",\"preco\":\"1\"}").json().get("id").asLong();
        JsonNode movido = put("/api/componentes/" + b, "{\"parent_id\":" + a + "}").json();
        assertEquals(a, movido.get("parent_id").asLong());
        assertEquals(List.of("R", "A", "B"), java.util.stream.StreamSupport
            .stream(get("/api/componentes/" + b).json().get("caminho").spliterator(), false).map(JsonNode::asText).toList());
        JsonNode promovido = put("/api/componentes/" + b, "{\"parent_id\":null}").json();
        assertTrue(promovido.get("parent_id").isNull());
        assertEquals(2, get("/api/produtos").json().get("total").asInt());
    }

    @Test
    void simulacaoAbsurdaE422() {
        long p = post("/api/produtos", "{\"nome\":\"R\",\"preco\":\"1\"}").json().get("id").asLong();
        long a = post("/api/componentes", "{\"nome\":\"A\",\"parent_id\":" + p + ",\"quantidade\":1000,\"preco\":\"0.01\"}").json().get("id").asLong();
        post("/api/componentes", "{\"nome\":\"B\",\"parent_id\":" + a + ",\"quantidade\":1001,\"preco\":\"0.01\"}");
        assertStatus(422, post("/api/produtos/" + p + "/simulacao", "{\"quantidade\": 1000000}"));
        assertStatus(200, post("/api/produtos/" + p + "/simulacao", "{\"quantidade\": 1}"));
    }

    @Test
    void trilhaDeAuditoriaRegistraAlteracoes() {
        JsonNode p = seed("caneta");
        long pid = p.get("id").asLong();
        JsonNode raiz = get("/api/produtos/" + pid + "/explosao").json().get("raiz");
        long plastico = achar(raiz, "Plástico", "Corpo").get("id").asLong();
        patch("/api/componentes/" + plastico + "/preco", "{\"preco\":\"1.70\"}");
        delete("/api/componentes/" + plastico);
        JsonNode itens = get("/api/auditoria").json().get("itens");
        JsonNode preco = null, excluir = null;
        for (JsonNode i : itens) {
            if (i.get("acao").asText().equals("preco")) preco = i;
            if (i.get("acao").asText().equals("excluir")) excluir = i;
        }
        assertEquals("anon", preco.get("ator").asText());
        assertEquals("0.70", preco.get("antes").asText());
        assertEquals("1.70", preco.get("depois").asText());
        assertTrue(preco.get("detalhe").asText().contains("7.50 -> 8.50"));
        assertEquals("Plástico", excluir.get("nome").asText());
    }
}
