"""Infraestrutura de banco: Base declarativa, tipo monetário e fábrica de engine."""
from __future__ import annotations

from decimal import Decimal
from typing import Optional

from sqlalchemy import BigInteger, Engine, create_engine, event
from sqlalchemy.orm import DeclarativeBase, sessionmaker
from sqlalchemy.pool import StaticPool
from sqlalchemy.types import TypeDecorator


class Base(DeclarativeBase):
    pass


class MoneyCents(TypeDecorator):
    """Guarda Decimal como inteiro em centavos: exato e idêntico em SQLite e PostgreSQL."""

    impl = BigInteger
    cache_ok = True

    def process_bind_param(self, value: Optional[Decimal], dialect) -> Optional[int]:
        if value is None:
            return None
        centavos = Decimal(value) * 100
        if centavos != centavos.to_integral_value():
            raise ValueError("valor monetário aceita no máximo 2 casas decimais")
        return int(centavos)

    def process_result_value(self, value: Optional[int], dialect) -> Optional[Decimal]:
        return None if value is None else Decimal(value) / 100


def make_engine(url: str, statement_timeout_ms: Optional[int] = None) -> Engine:
    if url.startswith("sqlite"):
        opcoes: dict = {"connect_args": {"check_same_thread": False}}
        if url in ("sqlite://", "sqlite:///:memory:"):
            opcoes["poolclass"] = StaticPool  # mesma conexão para o banco em memória
        engine = create_engine(url, **opcoes)

        @event.listens_for(engine, "connect")
        def _fk_on(dbapi_conn, _record):  # SQLite ignora FKs/ON DELETE CASCADE por padrão
            dbapi_conn.execute("PRAGMA foreign_keys=ON")

        return engine
    opcoes: dict = {"pool_pre_ping": True}
    if statement_timeout_ms:
        # PostgreSQL aborta a consulta no servidor: ao contrário do timeout da aplicação, isso INTERROMPE o trabalho.
        opcoes["connect_args"] = {"options": f"-c statement_timeout={int(statement_timeout_ms)}"}
    return create_engine(url, **opcoes)


def make_sessionmaker(engine: Engine) -> sessionmaker:
    return sessionmaker(engine, expire_on_commit=False)
