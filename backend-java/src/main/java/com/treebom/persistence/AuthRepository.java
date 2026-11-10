package com.treebom.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Usuários e refresh tokens (só o SHA-256 do token é guardado). */
@Repository
public class AuthRepository {

    public record Usuario(long id, String username, String passwordHash, String role, boolean ativo, int tokenVersion) {}

    public record RefreshToken(String tokenHash, long userId, String familia, Instant expiraEm, boolean revogado) {}

    private static final String COLUNAS_USUARIO = "id, username, password_hash, role, ativo, token_version";

    private final JdbcClient jdbc;

    public AuthRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static Usuario usuario(ResultSet rs) throws SQLException {
        return new Usuario(rs.getLong("id"), rs.getString("username"), rs.getString("password_hash"),
            rs.getString("role"), rs.getBoolean("ativo"), rs.getInt("token_version"));
    }

    private static RefreshToken refresh(ResultSet rs) throws SQLException {
        return new RefreshToken(rs.getString("token_hash"), rs.getLong("user_id"), rs.getString("familia"),
            Instant.ofEpochMilli(rs.getLong("expira_em")), rs.getBoolean("revogado"));
    }

    // ------------------------------------------------------------------ usuários
    public Optional<Usuario> usuarioPorId(long id) {
        return jdbc.sql("SELECT " + COLUNAS_USUARIO + " FROM usuarios WHERE id = :id").param("id", id)
            .query((rs, n) -> usuario(rs)).optional();
    }

    public Optional<Usuario> usuarioPorUsername(String username) {
        return jdbc.sql("SELECT " + COLUNAS_USUARIO + " FROM usuarios WHERE username = :u").param("u", username)
            .query((rs, n) -> usuario(rs)).optional();
    }

    public boolean existeUsuario() {
        return jdbc.sql("SELECT COUNT(*) FROM (SELECT id FROM usuarios LIMIT 1) t").query(Long.class).single() > 0;
    }

    /** Lança DataIntegrityViolationException se o nome de usuário já existir. */
    public Usuario criarUsuario(String username, String passwordHash, String role) {
        long id = jdbc.sql("INSERT INTO usuarios (username, password_hash, role, ativo, token_version, criado_em) "
                + "VALUES (:u, :h, :r, :ativo, 1, :agora) RETURNING id")
            .param("u", username).param("h", passwordHash).param("r", role).param("ativo", true)
            .param("agora", Instant.now().toEpochMilli())
            .query(Long.class).single();
        return new Usuario(id, username, passwordHash, role, true, 1);
    }

    public void redefinirSenha(long userId, String passwordHash) {
        jdbc.sql("UPDATE usuarios SET password_hash = :h, ativo = :ativo, token_version = token_version + 1 WHERE id = :id")
            .param("h", passwordHash).param("ativo", true).param("id", userId).update();
    }

    public void incrementarTokenVersion(long userId) {
        jdbc.sql("UPDATE usuarios SET token_version = token_version + 1 WHERE id = :id").param("id", userId).update();
    }

    // ------------------------------------------------------------ refresh tokens
    public void salvarRefresh(String tokenHash, long userId, String familia, Instant expiraEm) {
        jdbc.sql("INSERT INTO refresh_tokens (token_hash, user_id, familia, expira_em, revogado) VALUES (:h, :u, :f, :e, :r)")
            .param("h", tokenHash).param("u", userId).param("f", familia).param("e", expiraEm.toEpochMilli())
            .param("r", false).update();
    }

    public Optional<RefreshToken> refreshPorHash(String tokenHash) {
        return jdbc.sql("SELECT token_hash, user_id, familia, expira_em, revogado FROM refresh_tokens WHERE token_hash = :h")
            .param("h", tokenHash).query((rs, n) -> refresh(rs)).optional();
    }

    public void revogar(String tokenHash) {
        jdbc.sql("UPDATE refresh_tokens SET revogado = :r WHERE token_hash = :h").param("r", true).param("h", tokenHash).update();
    }

    public void revogarFamilia(String familia) {
        jdbc.sql("UPDATE refresh_tokens SET revogado = :r WHERE familia = :f").param("r", true).param("f", familia).update();
    }

    public void revogarTodosDoUsuario(long userId) {
        jdbc.sql("UPDATE refresh_tokens SET revogado = :r WHERE user_id = :u").param("r", true).param("u", userId).update();
    }
}
