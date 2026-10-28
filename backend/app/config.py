"""Configuração via variáveis de ambiente (12-factor), com padrões seguros e validação na partida."""
from __future__ import annotations

import os
from dataclasses import dataclass
from decimal import Decimal
from typing import Optional

SEGREDO_MINIMO = 32


class ConfiguracaoInsegura(RuntimeError):
    """A configuração viola uma regra de segurança e a aplicação se recusa a iniciar."""


def _bool(valor: Optional[str], padrao: Optional[bool]) -> Optional[bool]:
    if valor is None or valor.strip() == "":
        return padrao
    return valor.strip().lower() in {"1", "true", "yes", "sim"}


@dataclass(frozen=True)
class Settings:
    # --- ambiente
    app_env: str = "development"  # "production" liga os padrões restritivos
    database_url: str = "sqlite:///./bom.db"
    auto_migrate: bool = True
    static_dir: Optional[str] = None

    # --- HTTP
    cors_origins: tuple[str, ...] = ("http://localhost:5173",)
    rate_limit: str = "120/minute"  # leituras
    write_rate_limit: str = "30/minute"  # POST/PUT/PATCH/DELETE (inclui seed)
    login_rate_limit: str = "10/minute"
    max_body_bytes: int = 65_536
    request_timeout_s: int = 15  # 504 se a requisição passar disso
    enable_docs: Optional[bool] = None  # None = ligado só fora de produção
    enable_seed: Optional[bool] = None  # None = ligado só fora de produção
    hsts: Optional[bool] = None  # None = ligado só em produção

    # --- limites da árvore (anti-DoS e anti-overflow)
    max_profundidade: int = 50
    max_nos_por_produto: int = 2_000
    max_filhos_por_no: int = 100
    max_quantidade_total: int = 10**12
    max_valor_total: Decimal = Decimal("1000000000000")  # R$ 1 trilhão

    # --- autenticação (opcional)
    auth_enabled: bool = False
    jwt_secret: Optional[str] = None
    redis_url: Optional[str] = None  # limites compartilhados entre réplicas (opcional)
    jwt_secret_anterior: Optional[str] = None  # só para VERIFICAR tokens durante a rotação do segredo
    access_ttl_minutos: int = 10
    refresh_ttl_dias: int = 7
    cookie_secure: Optional[bool] = None  # None = ligado em produção
    admin_username: Optional[str] = None
    admin_password: Optional[str] = None

    # ------------------------------------------------------------------ derivados
    @property
    def producao(self) -> bool:
        return self.app_env.lower() == "production"

    @property
    def docs_ativos(self) -> bool:
        return self.enable_docs if self.enable_docs is not None else not self.producao

    @property
    def seed_ativo(self) -> bool:
        return self.enable_seed if self.enable_seed is not None else not self.producao

    @property
    def hsts_ativo(self) -> bool:
        return self.hsts if self.hsts is not None else self.producao

    @property
    def cookie_seguro(self) -> bool:
        return self.cookie_secure if self.cookie_secure is not None else self.producao

    def validar(self) -> None:
        """Falha cedo (fail-closed) quando a configuração é perigosa."""
        if "*" in self.cors_origins:
            raise ConfiguracaoInsegura("CORS_ORIGINS não pode conter '*'. Liste as origens explicitamente.")
        if self.jwt_secret_anterior and len(self.jwt_secret_anterior) < SEGREDO_MINIMO:
            raise ConfiguracaoInsegura(f"JWT_SECRET_ANTERIOR precisa ter {SEGREDO_MINIMO}+ caracteres.")
        if self.auth_enabled:
            if not self.jwt_secret or len(self.jwt_secret) < SEGREDO_MINIMO:
                raise ConfiguracaoInsegura(f"JWT_SECRET é obrigatório com AUTH_ENABLED=true e precisa ter {SEGREDO_MINIMO}+ caracteres.")
        if self.producao:
            if not self.auth_enabled:
                raise ConfiguracaoInsegura("APP_ENV=production exige AUTH_ENABLED=true.")
            if not self.cookie_seguro:
                raise ConfiguracaoInsegura("Em produção o cookie de refresh precisa ser Secure (COOKIE_SECURE=true).")
            if self.admin_password and len(self.admin_password) < 12:
                raise ConfiguracaoInsegura("ADMIN_PASSWORD precisa ter 12+ caracteres.")

    @classmethod
    def from_env(cls) -> "Settings":
        env = os.environ
        origens = env.get("CORS_ORIGINS")
        padrao = cls()
        return cls(
            app_env=env.get("APP_ENV", padrao.app_env),
            database_url=env.get("DATABASE_URL", padrao.database_url),
            auto_migrate=bool(_bool(env.get("AUTO_MIGRATE"), True)),
            static_dir=env.get("STATIC_DIR") or None,
            cors_origins=tuple(o.strip() for o in origens.split(",") if o.strip()) if origens is not None else padrao.cors_origins,
            rate_limit=env.get("RATE_LIMIT", padrao.rate_limit),
            write_rate_limit=env.get("WRITE_RATE_LIMIT", padrao.write_rate_limit),
            login_rate_limit=env.get("LOGIN_RATE_LIMIT", padrao.login_rate_limit),
            max_body_bytes=int(env.get("MAX_BODY_BYTES", padrao.max_body_bytes)),
            request_timeout_s=int(env.get("REQUEST_TIMEOUT_S", padrao.request_timeout_s)),
            enable_docs=_bool(env.get("ENABLE_DOCS"), None),
            enable_seed=_bool(env.get("ENABLE_SEED"), None),
            hsts=_bool(env.get("HSTS"), None),
            max_profundidade=int(env.get("MAX_PROFUNDIDADE", padrao.max_profundidade)),
            max_nos_por_produto=int(env.get("MAX_NOS_POR_PRODUTO", padrao.max_nos_por_produto)),
            max_filhos_por_no=int(env.get("MAX_FILHOS_POR_NO", padrao.max_filhos_por_no)),
            auth_enabled=bool(_bool(env.get("AUTH_ENABLED"), False)),
            jwt_secret=env.get("JWT_SECRET") or None,
            jwt_secret_anterior=env.get("JWT_SECRET_ANTERIOR") or None,
            redis_url=env.get("REDIS_URL") or None,
            access_ttl_minutos=int(env.get("ACCESS_TTL_MINUTOS", padrao.access_ttl_minutos)),
            refresh_ttl_dias=int(env.get("REFRESH_TTL_DIAS", padrao.refresh_ttl_dias)),
            cookie_secure=_bool(env.get("COOKIE_SECURE"), None),
            admin_username=env.get("ADMIN_USERNAME") or None,
            admin_password=env.get("ADMIN_PASSWORD") or None,
        )
