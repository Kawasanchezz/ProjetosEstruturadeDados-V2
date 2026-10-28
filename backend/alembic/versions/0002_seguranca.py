"""segurança: bloqueio otimista, usuários, refresh tokens e auditoria

Revision ID: 0002
Revises: 0001
Create Date: 2026-10-19
"""
from alembic import op
import sqlalchemy as sa

revision = "0002"
down_revision = "0001"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column("componentes", sa.Column("version", sa.Integer(), nullable=False, server_default="1"))

    op.create_table(
        "usuarios",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("username", sa.String(50), nullable=False),
        sa.Column("password_hash", sa.String(255), nullable=False),
        sa.Column("role", sa.String(16), nullable=False),
        sa.Column("ativo", sa.Boolean(), nullable=False, server_default=sa.true()),
        sa.Column("criado_em", sa.DateTime(timezone=True), nullable=False),
        sa.CheckConstraint("role IN ('admin', 'viewer')", name="ck_usuarios_role"),
    )
    op.create_index("ix_usuarios_username", "usuarios", ["username"], unique=True)

    op.create_table(
        "refresh_tokens",
        sa.Column("token_hash", sa.String(64), primary_key=True),
        sa.Column("user_id", sa.Integer(), sa.ForeignKey("usuarios.id", ondelete="CASCADE"), nullable=False),
        sa.Column("familia", sa.String(32), nullable=False),
        sa.Column("expira_em", sa.DateTime(timezone=True), nullable=False),
        sa.Column("revogado", sa.Boolean(), nullable=False, server_default=sa.false()),
    )
    op.create_index("ix_refresh_tokens_user_id", "refresh_tokens", ["user_id"])
    op.create_index("ix_refresh_tokens_familia", "refresh_tokens", ["familia"])

    op.create_table(
        "auditoria",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("ocorrido_em", sa.DateTime(timezone=True), nullable=False),
        sa.Column("ator", sa.String(80), nullable=False),
        sa.Column("acao", sa.String(32), nullable=False),
        sa.Column("no_id", sa.Integer()),
        sa.Column("nome", sa.String(120)),
        sa.Column("antes", sa.String(64)),
        sa.Column("depois", sa.String(64)),
        sa.Column("detalhe", sa.String(255)),
    )
    op.create_index("ix_auditoria_ocorrido_em", "auditoria", ["ocorrido_em"])


def downgrade() -> None:
    op.drop_index("ix_auditoria_ocorrido_em", table_name="auditoria")
    op.drop_table("auditoria")
    op.drop_index("ix_refresh_tokens_familia", table_name="refresh_tokens")
    op.drop_index("ix_refresh_tokens_user_id", table_name="refresh_tokens")
    op.drop_table("refresh_tokens")
    op.drop_index("ix_usuarios_username", table_name="usuarios")
    op.drop_table("usuarios")
    with op.batch_alter_table("componentes") as lote:
        lote.drop_column("version")
