import pytest
from fastapi.testclient import TestClient

from app.config import Settings
from app.database import Base
from app.main import create_app

SEGREDO = "s" * 48
LIMITES_ALTOS = dict(rate_limit="100000/minute", write_rate_limit="100000/minute", login_rate_limit="100000/minute")


def make_client(redis_async=None, redis_sync=None, **extra) -> TestClient:
    """App com SQLite em memória e limites altos (cada teste sobrescreve o que quer provar)."""
    base = dict(database_url="sqlite://", auto_migrate=False, **LIMITES_ALTOS)
    app = create_app(Settings(**{**base, **extra}), redis_async=redis_async, redis_sync=redis_sync)
    Base.metadata.create_all(app.state.engine)
    return TestClient(app)


@pytest.fixture
def client():
    with make_client() as c:
        yield c


def achar(no: dict, nome: str, pai: str | None = None) -> dict | None:
    """Busca (pré-ordem) na árvore da explosão por nome e, opcionalmente, pelo nome do pai."""

    def rec(n, nome_pai):
        if n["nome"] == nome and (pai is None or nome_pai == pai):
            return n
        for f in n["filhos"]:
            achado = rec(f, n["nome"])
            if achado:
                return achado

    return rec(no, None)
