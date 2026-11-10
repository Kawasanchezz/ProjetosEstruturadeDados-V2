package com.treebom.persistence;

import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Trilha de auditoria. Sem FK para o nó: o registro sobrevive à exclusão. */
@Repository
public class AuditoriaRepository {

    public record Registro(long id, Instant ocorridoEm, String ator, String acao, Long noId, String nome,
                           String antes, String depois, String detalhe) {}

    private final JdbcClient jdbc;

    public AuditoriaRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void registrar(String ator, String acao, Long noId, String nome, String antes, String depois, String detalhe) {
        jdbc.sql("INSERT INTO auditoria (ocorrido_em, ator, acao, no_id, nome, antes, depois, detalhe) "
                + "VALUES (:quando, :ator, :acao, :no, :nome, :antes, :depois, :detalhe)")
            .param("quando", Instant.now().toEpochMilli()).param("ator", cortar(ator, 80)).param("acao", acao)
            .param("no", noId).param("nome", nome).param("antes", antes).param("depois", depois)
            .param("detalhe", cortar(detalhe, 255)).update();
    }

    private static String cortar(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    public long contar() {
        return jdbc.sql("SELECT COUNT(*) FROM auditoria").query(Long.class).single();
    }

    public List<Registro> listar(int limit, int offset) {
        return jdbc.sql("SELECT id, ocorrido_em, ator, acao, no_id, nome, antes, depois, detalhe FROM auditoria "
                + "ORDER BY id DESC LIMIT :l OFFSET :o")
            .param("l", limit).param("o", offset)
            .query((rs, n) -> {
                long no = rs.getLong("no_id");
                Long noId = rs.wasNull() ? null : no;
                return new Registro(rs.getLong("id"), Instant.ofEpochMilli(rs.getLong("ocorrido_em")), rs.getString("ator"),
                    rs.getString("acao"), noId, rs.getString("nome"), rs.getString("antes"), rs.getString("depois"),
                    rs.getString("detalhe"));
            }).list();
    }
}
