"""cria a tabela componentes (auto-relacionamento)

Revision ID: 0001
Revises:
Create Date: 2026-10-18
"""
from alembic import op
import sqlalchemy as sa

revision = "0001"
down_revision = None
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "componentes",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("nome", sa.String(120), nullable=False),
        sa.Column("parent_id", sa.Integer(), sa.ForeignKey("componentes.id", ondelete="CASCADE")),
        sa.Column("quantidade", sa.Integer(), nullable=False),
        sa.Column("preco", sa.BigInteger(), nullable=False),  # centavos
        sa.CheckConstraint("quantidade > 0", name="ck_componentes_quantidade_positiva"),
        sa.CheckConstraint("preco >= 0", name="ck_componentes_preco_nao_negativo"),
        sa.CheckConstraint("parent_id IS NULL OR parent_id <> id", name="ck_componentes_sem_autoreferencia"),
    )
    op.create_index("ix_componentes_parent_id", "componentes", ["parent_id"])


def downgrade() -> None:
    op.drop_index("ix_componentes_parent_id", table_name="componentes")
    op.drop_table("componentes")
