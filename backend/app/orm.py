"""Tabelas: árvore (auto-relacionamento), usuários, refresh tokens e trilha de auditoria."""
from __future__ import annotations

from datetime import datetime, timezone
from decimal import Decimal
from typing import Optional

from sqlalchemy import Boolean, CheckConstraint, DateTime, ForeignKey, Integer, String
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base, MoneyCents


def agora() -> datetime:
    return datetime.now(timezone.utc)


class NoORM(Base):
    """Produto = nó sem pai; componente = nó com pai."""

    __tablename__ = "componentes"
    __table_args__ = (
        CheckConstraint("quantidade > 0", name="ck_componentes_quantidade_positiva"),
        CheckConstraint("preco >= 0", name="ck_componentes_preco_nao_negativo"),
        CheckConstraint("parent_id IS NULL OR parent_id <> id", name="ck_componentes_sem_autoreferencia"),
    )

    id: Mapped[int] = mapped_column(primary_key=True)
    nome: Mapped[str] = mapped_column(String(120))
    parent_id: Mapped[Optional[int]] = mapped_column(
        ForeignKey("componentes.id", ondelete="CASCADE"), index=True
    )
    quantidade: Mapped[int]
    preco: Mapped[Decimal] = mapped_column(MoneyCents())
    # Bloqueio otimista: UPDATE ... WHERE version = <lida>. Escrita concorrente → StaleDataError → 409.
    version: Mapped[int] = mapped_column(Integer, default=1, server_default="1")

    pai: Mapped[Optional["NoORM"]] = relationship(back_populates="filhos", remote_side=[id])
    filhos: Mapped[list["NoORM"]] = relationship(
        back_populates="pai", cascade="all, delete-orphan", passive_deletes=True
    )

    __mapper_args__ = {"version_id_col": version}


class UsuarioORM(Base):
    __tablename__ = "usuarios"
    __table_args__ = (CheckConstraint("role IN ('admin', 'viewer')", name="ck_usuarios_role"),)

    id: Mapped[int] = mapped_column(primary_key=True)
    username: Mapped[str] = mapped_column(String(50), unique=True, index=True)
    password_hash: Mapped[str] = mapped_column(String(255))
    role: Mapped[str] = mapped_column(String(16))
    ativo: Mapped[bool] = mapped_column(Boolean, default=True, server_default="1")
    # Incrementado no logout/redefinição: invalida na hora TODOS os access tokens já emitidos (claim `tv`).
    token_version: Mapped[int] = mapped_column(Integer, default=1, server_default="1")
    criado_em: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=agora)


class RefreshTokenORM(Base):
    """Guarda só o SHA-256 do token: um vazamento do banco não entrega tokens utilizáveis."""

    __tablename__ = "refresh_tokens"

    token_hash: Mapped[str] = mapped_column(String(64), primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("usuarios.id", ondelete="CASCADE"), index=True)
    familia: Mapped[str] = mapped_column(String(32), index=True)
    expira_em: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    revogado: Mapped[bool] = mapped_column(Boolean, default=False, server_default="0")


class AuditoriaORM(Base):
    """Trilha de auditoria. Sem FK para o nó: o registro sobrevive à exclusão."""

    __tablename__ = "auditoria"

    id: Mapped[int] = mapped_column(primary_key=True)
    ocorrido_em: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=agora, index=True)
    ator: Mapped[str] = mapped_column(String(80))
    acao: Mapped[str] = mapped_column(String(32))
    no_id: Mapped[Optional[int]] = mapped_column(Integer)
    nome: Mapped[Optional[str]] = mapped_column(String(120))
    antes: Mapped[Optional[str]] = mapped_column(String(64))
    depois: Mapped[Optional[str]] = mapped_column(String(64))
    detalhe: Mapped[Optional[str]] = mapped_column(String(255))
