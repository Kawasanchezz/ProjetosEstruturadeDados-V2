package com.treebom.persistence;

import com.treebom.config.DatabaseConfig;
import com.treebom.config.Settings;
import com.treebom.domain.Dinheiro;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Acesso a dados: só SQL, nenhuma regra de negócio. Dinheiro é guardado em centavos. */
@Repository
public class NoRepository {

    /** Linha da tabela {@code componentes}. */
    public record NoRow(long id, String nome, Long parentId, int quantidade, BigDecimal preco, int version) {}

    /** Outra escrita alterou o registro desde a leitura (bloqueio otimista). */
    public static class ConflitoConcorrencia extends RuntimeException {
        public ConflitoConcorrencia() {
            super("Este registro foi alterado por outra operação. Recarregue e tente novamente.");
        }
    }

    private static final String COLUNAS = "id, nome, parent_id, quantidade, preco, version";

    private final JdbcClient jdbc;
    private final boolean postgres;

    public NoRepository(JdbcClient jdbc, Settings settings) {
        this.jdbc = jdbc;
        this.postgres = DatabaseConfig.ehPostgres(settings.getDatabaseUrl());
    }

    static NoRow mapear(ResultSet rs) throws SQLException {
        long parent = rs.getLong("parent_id");
        Long parentId = rs.wasNull() ? null : parent;
        return new NoRow(rs.getLong("id"), rs.getString("nome"), parentId, rs.getInt("quantidade"),
            Dinheiro.deCentavos(rs.getLong("preco")), rs.getInt("version"));
    }

    /** {@code bloquear=true} emite SELECT ... FOR UPDATE (PostgreSQL); o SQLite usa só o bloqueio otimista. */
    public Optional<NoRow> get(long id, boolean bloquear) {
        String sql = "SELECT " + COLUNAS + " FROM componentes WHERE id = :id" + (bloquear && postgres ? " FOR UPDATE" : "");
        return jdbc.sql(sql).param("id", id).query((rs, n) -> mapear(rs)).optional();
    }

    public NoRow inserir(String nome, Long parentId, int quantidade, BigDecimal preco) {
        long id = jdbc.sql("INSERT INTO componentes (nome, parent_id, quantidade, preco, version) "
                + "VALUES (:nome, :pai, :qtd, :preco, 1) RETURNING id")
            .param("nome", nome).param("pai", parentId).param("qtd", quantidade).param("preco", Dinheiro.emCentavos(preco))
            .query(Long.class).single();
        return new NoRow(id, nome, parentId, quantidade, preco, 1);
    }

    /** UPDATE ... WHERE version = <lida>; zero linhas afetadas = escrita concorrente. */
    public NoRow atualizar(NoRow antigo, String nome, int quantidade, BigDecimal preco, Long parentId) {
        int linhas = jdbc.sql("UPDATE componentes SET nome = :nome, quantidade = :qtd, preco = :preco, parent_id = :pai, "
                + "version = version + 1 WHERE id = :id AND version = :v")
            .param("nome", nome).param("qtd", quantidade).param("preco", Dinheiro.emCentavos(preco))
            .param("pai", parentId).param("id", antigo.id()).param("v", antigo.version())
            .update();
        if (linhas == 0) throw new ConflitoConcorrencia();
        return new NoRow(antigo.id(), nome, parentId, quantidade, preco, antigo.version() + 1);
    }

    public void excluir(long id) {
        jdbc.sql("DELETE FROM componentes WHERE id = :id").param("id", id).update(); // filhos saem por ON DELETE CASCADE
    }

    public record Pagina(List<NoRow> itens, long total) {}

    public Pagina listarRaizes(int limit, int offset) {
        long total = jdbc.sql("SELECT COUNT(*) FROM componentes WHERE parent_id IS NULL").query(Long.class).single();
        List<NoRow> itens = jdbc.sql("SELECT " + COLUNAS + " FROM componentes WHERE parent_id IS NULL ORDER BY id LIMIT :l OFFSET :o")
            .param("l", limit).param("o", offset).query((rs, n) -> mapear(rs)).list();
        return new Pagina(itens, total);
    }

    public Optional<NoRow> raizPorNome(String nome) {
        return jdbc.sql("SELECT " + COLUNAS + " FROM componentes WHERE parent_id IS NULL AND nome = :nome ORDER BY id LIMIT 1")
            .param("nome", nome).query((rs, n) -> mapear(rs)).optional();
    }

    /** Nó e todos os descendentes numa única query (CTE recursiva; UNION descarta repetições e protege de ciclos). */
    public List<NoRow> subarvore(long raizId) {
        return jdbc.sql("WITH RECURSIVE sub(id) AS (SELECT id FROM componentes WHERE id = :raiz "
                + "UNION SELECT c.id FROM componentes c JOIN sub s ON c.parent_id = s.id) "
                + "SELECT " + COLUNAS + " FROM componentes WHERE id IN (SELECT id FROM sub) ORDER BY id")
            .param("raiz", raizId).query((rs, n) -> mapear(rs)).list();
    }

    /** Subárvores de VÁRIAS raízes numa única consulta (evita N consultas na listagem de produtos). */
    public Map<Long, List<NoRow>> subarvores(List<Long> raizIds) {
        Map<Long, List<NoRow>> agrupado = new LinkedHashMap<>();
        for (Long id : raizIds) agrupado.put(id, new ArrayList<>());
        if (raizIds.isEmpty()) return agrupado;
        jdbc.sql("WITH RECURSIVE sub(id, raiz) AS (SELECT id, id FROM componentes WHERE id IN (:ids) "
                + "UNION SELECT c.id, s.raiz FROM componentes c JOIN sub s ON c.parent_id = s.id) "
                + "SELECT c.id, c.nome, c.parent_id, c.quantidade, c.preco, c.version, sub.raiz "
                + "FROM componentes c JOIN sub ON c.id = sub.id ORDER BY c.id")
            .param("ids", raizIds)
            .query((rs, n) -> {
                agrupado.get(rs.getLong("raiz")).add(mapear(rs));
                return null;
            }).list();
        return agrupado;
    }

    /** Do pai imediato até a raiz. */
    public List<NoRow> ancestrais(NoRow no, int limite) {
        List<NoRow> cadeia = new ArrayList<>();
        NoRow atual = no;
        while (atual.parentId() != null && cadeia.size() < limite) {
            atual = get(atual.parentId(), false).orElseThrow();
            cadeia.add(atual);
        }
        return cadeia;
    }
}
