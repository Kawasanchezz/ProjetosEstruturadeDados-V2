"""usuarios.token_version: revogação imediata de access tokens

Revision ID: 0003
Revises: 0002
Create Date: 2026-10-22
"""
from alembic import op
import sqlalchemy as sa

revision = "0003"
down_revision = "0002"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column("usuarios", sa.Column("token_version", sa.Integer(), nullable=False, server_default="1"))


def downgrade() -> None:
    with op.batch_alter_table("usuarios") as lote:
        lote.drop_column("token_version")
