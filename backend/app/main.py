"""Fábrica da aplicação FastAPI."""
from __future__ import annotations

from contextlib import asynccontextmanager
from pathlib import Path
from typing import Optional

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from starlette.exceptions import HTTPException as StarletteHTTPException

from app import migrations
from app.api.auth_routes import router as auth_router
from app.api.errors import registrar_handlers
from app.api.routes import router
from app.config import Settings
from app.database import make_engine, make_sessionmaker
from app.ratelimit import RateLimitMiddleware
from app.security.auth import AuthService, LoginThrottle, RedisLoginThrottle
from app.security.middleware import BodyLimitMiddleware, SecurityMiddleware, TimeoutMiddleware, configurar_logs


class SPAStaticFiles(StaticFiles):
    """Serve o build do Vite; rotas desconhecidas caem no index.html (React Router)."""

    async def get_response(self, path: str, scope):
        try:
            return await super().get_response(path, scope)
        except StarletteHTTPException as exc:
            if exc.status_code == 404:
                return await super().get_response("index.html", scope)
            raise


def create_app(settings: Optional[Settings] = None, redis_async=None, redis_sync=None) -> FastAPI:
    settings = settings or Settings.from_env()
    settings.validar()  # fail-closed: configuração insegura impede a inicialização
    configurar_logs()
    engine = make_engine(settings.database_url, settings.request_timeout_s * 1000)
    if settings.redis_url and redis_async is None:
        import redis
        import redis.asyncio

        opcoes = dict(decode_responses=True, socket_timeout=1, socket_connect_timeout=1, protocol=2)  # RESP2: compatível com Redis antigos e novos
        redis_async = redis.asyncio.from_url(settings.redis_url, **opcoes)
        redis_sync = redis.from_url(settings.redis_url, **opcoes)

    @asynccontextmanager
    async def lifespan(_: FastAPI):
        if settings.auto_migrate:
            migrations.upgrade(settings.database_url)
        if settings.auth_enabled:
            with app.state.sessionmaker() as sessao:
                AuthService(sessao, settings, app.state.login_throttle).garantir_admin_inicial()
        yield
        engine.dispose()

    docs = settings.docs_ativos
    app = FastAPI(
        title="TreeBOM - Lista de Materiais",
        description="Árvore genérica de produtos e componentes: explosão, simulação e implosão de custos.",
        version="1.0.0",
        docs_url="/api/docs" if docs else None,
        openapi_url="/api/openapi.json" if docs else None,
        redoc_url=None,
        lifespan=lifespan,
    )
    app.state.settings = settings
    app.state.engine = engine
    app.state.sessionmaker = make_sessionmaker(engine)
    app.state.login_throttle = RedisLoginThrottle(redis_sync) if redis_sync is not None else LoginThrottle()

    # Ordem: o último adicionado é o mais externo.
    #   Security (cabeçalhos/request_id/500) → CORS → RateLimit → Timeout → BodyLimit → rotas
    app.add_middleware(BodyLimitMiddleware, max_bytes=settings.max_body_bytes)
    app.add_middleware(TimeoutMiddleware, segundos=settings.request_timeout_s)
    app.add_middleware(RateLimitMiddleware, leitura=settings.rate_limit, escrita=settings.write_rate_limit, login=settings.login_rate_limit, redis=redis_async)
    app.add_middleware(
        CORSMiddleware,
        allow_origins=list(settings.cors_origins),
        allow_methods=["GET", "POST", "PUT", "PATCH", "DELETE"],
        allow_headers=["Content-Type", "Authorization"],
        allow_credentials=True,  # necessário para o cookie de refresh; origens são sempre explícitas
    )
    app.add_middleware(SecurityMiddleware, hsts=settings.hsts_ativo)
    registrar_handlers(app)
    @app.get("/api/saude", include_in_schema=False)  # público de propósito (verificação de saúde); não expõe dados
    def saude():
        return {"status": "ok"}

    app.include_router(router, prefix="/api")
    app.include_router(auth_router, prefix="/api")

    if settings.static_dir and Path(settings.static_dir).is_dir():
        app.mount("/", SPAStaticFiles(directory=settings.static_dir, html=True), name="front")
    return app


app = create_app()  # ponto de entrada do uvicorn: `uvicorn app.main:app`
