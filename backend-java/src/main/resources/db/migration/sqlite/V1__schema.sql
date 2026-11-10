-- Árvore (auto-relacionamento), usuários, refresh tokens e trilha de auditoria.
-- Dinheiro em centavos (BIGINT); datas em milissegundos desde a época (BIGINT): idêntico em SQLite e PostgreSQL.
CREATE TABLE componentes (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    nome       VARCHAR(120) NOT NULL,
    parent_id  INTEGER REFERENCES componentes (id) ON DELETE CASCADE,
    quantidade INTEGER NOT NULL,
    preco      BIGINT  NOT NULL,
    version    INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT ck_componentes_quantidade_positiva CHECK (quantidade > 0),
    CONSTRAINT ck_componentes_preco_nao_negativo CHECK (preco >= 0),
    CONSTRAINT ck_componentes_sem_autoreferencia CHECK (parent_id IS NULL OR parent_id <> id)
);
CREATE INDEX ix_componentes_parent_id ON componentes (parent_id);

CREATE TABLE usuarios (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    username      VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(16)  NOT NULL,
    ativo         BOOLEAN      NOT NULL DEFAULT 1,
    token_version INTEGER      NOT NULL DEFAULT 1,
    criado_em     BIGINT       NOT NULL,
    CONSTRAINT ck_usuarios_role CHECK (role IN ('admin', 'viewer'))
);
CREATE UNIQUE INDEX ix_usuarios_username ON usuarios (username);

CREATE TABLE refresh_tokens (
    token_hash VARCHAR(64) PRIMARY KEY,
    user_id    INTEGER     NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    familia    VARCHAR(32) NOT NULL,
    expira_em  BIGINT      NOT NULL,
    revogado   BOOLEAN     NOT NULL DEFAULT 0
);
CREATE INDEX ix_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX ix_refresh_tokens_familia ON refresh_tokens (familia);

CREATE TABLE auditoria (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    ocorrido_em BIGINT      NOT NULL,
    ator        VARCHAR(80) NOT NULL,
    acao        VARCHAR(32) NOT NULL,
    no_id       INTEGER,
    nome        VARCHAR(120),
    antes       VARCHAR(64),
    depois      VARCHAR(64),
    detalhe     VARCHAR(255)
);
CREATE INDEX ix_auditoria_ocorrido_em ON auditoria (ocorrido_em);
