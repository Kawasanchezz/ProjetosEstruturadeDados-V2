"""Autenticação e autorização: Argon2id, JWT de acesso curto, refresh opaco com rotação e papéis.

Decisões:
- Access token JWT (HS256 fixado; "none" e outros algoritmos são rejeitados) com exp/iat/sub/jti, 10 min.
- Refresh token opaco (secrets) guardado só como SHA-256, rotacionado a cada uso, com detecção de reuso
  (token já usado ⇒ toda a "família" é revogada).
- O papel (admin/viewer) é lido do banco a cada requisição: rebaixar ou desativar um usuário vale na hora.
"""
from __future__ import annotations

import hashlib
import secrets
import threading
import time
import uuid
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from typing import Optional

import jwt
from argon2 import PasswordHasher
from argon2.exceptions import InvalidHashError, VerificationError
from fastapi import Depends, HTTPException, Request
from sqlalchemy import select, update
from sqlalchemy.orm import Session

from app.config import Settings
from app.orm import RefreshTokenORM, UsuarioORM, agora

ALGORITMO = "HS256"
RANK = {"viewer": 1, "admin": 2}
SENHA_MINIMA = 12

_hasher = PasswordHasher()  # Argon2id com parâmetros padrão da biblioteca (RFC 9106, perfil baixo)
_HASH_FALSO = _hasher.hash("senha-que-nunca-sera-usada")  # igualar o tempo de resposta p/ usuário inexistente


class ErroAutenticacao(Exception):
    """Credencial inválida (mensagem única: não revela se o usuário existe)."""


class ContaBloqueada(Exception):
    def __init__(self, segundos: int) -> None:
        super().__init__("Muitas tentativas")
        self.segundos = segundos


@dataclass(frozen=True)
class Principal:
    user_id: int
    username: str
    role: str


# ------------------------------------------------------------------------ senhas
def hash_senha(senha: str) -> str:
    return _hasher.hash(senha)


def verificar_senha(hash_: str, senha: str) -> bool:
    try:
        return _hasher.verify(hash_, senha)
    except (VerificationError, InvalidHashError):
        return False


def validar_forca_senha(senha: str) -> None:
    if len(senha) < SENHA_MINIMA or len(senha) > 128:
        raise ValueError(f"A senha deve ter entre {SENHA_MINIMA} e 128 caracteres")


# ------------------------------------------------------------------------- tokens
def emitir_access(settings: Settings, user: UsuarioORM) -> tuple[str, int]:
    ttl = settings.access_ttl_minutos * 60
    agora_ts = int(time.time())
    claims = {"sub": str(user.id), "iat": agora_ts, "exp": agora_ts + ttl, "jti": uuid.uuid4().hex, "typ": "access", "tv": user.token_version}
    return jwt.encode(claims, settings.jwt_secret, algorithm=ALGORITMO), ttl


def decodificar_access(settings: Settings, token: str) -> tuple[int, int]:
    """Retorna (id do usuário, token_version) ou levanta jwt.InvalidTokenError (expirado, assinatura ruim, alg errado...).

    Rotação de segredo: tenta o JWT_SECRET atual e, se definido, o JWT_SECRET_ANTERIOR (só para verificar;
    todo token novo é assinado com o atual). Depois de 1 TTL de access token, remova o anterior.
    """
    segredos = [s for s in (settings.jwt_secret, settings.jwt_secret_anterior) if s]
    dados = None
    for segredo in segredos:
        try:
            dados = jwt.decode(
                token,
                segredo,
                algorithms=[ALGORITMO],  # lista fixa: rejeita "none" e confusão de algoritmos
                options={"require": ["exp", "iat", "sub", "jti"]},
            )
            break
        except jwt.InvalidSignatureError:
            continue
    if dados is None:
        raise jwt.InvalidTokenError("assinatura inválida")
    if dados.get("typ") != "access":
        raise jwt.InvalidTokenError("tipo de token inválido")
    try:
        return int(dados["sub"]), int(dados.get("tv", 0))
    except (TypeError, ValueError) as exc:
        raise jwt.InvalidTokenError("claims inválidas") from exc


def _hash_token(token: str) -> str:
    return hashlib.sha256(token.encode()).hexdigest()


# ------------------------------------------------------------ anti força bruta (login)
class LoginThrottle:
    """Atraso progressivo por (IP, usuário): 3 falhas livres, depois bloqueio 2^n s (máx. 15 min)."""

    LIVRES = 3
    TETO = 900
    MAX_CHAVES = 10_000

    def __init__(self) -> None:
        self._estado: dict[str, tuple[int, float]] = {}  # chave -> (falhas, bloqueado_ate)
        self._lock = threading.Lock()

    def segundos_restantes(self, chave: str) -> int:
        with self._lock:
            _, ate = self._estado.get(chave, (0, 0.0))
        return max(0, int(ate - time.monotonic() + 0.999))

    def falha(self, chave: str) -> None:
        with self._lock:
            if len(self._estado) >= self.MAX_CHAVES:  # limite de memória: descarta as entradas já liberadas
                agora_m = time.monotonic()
                self._estado = {k: v for k, v in self._estado.items() if v[1] > agora_m}
            falhas, _ = self._estado.get(chave, (0, 0.0))
            falhas += 1
            espera = 0 if falhas <= self.LIVRES else min(2 ** (falhas - self.LIVRES), self.TETO)
            self._estado[chave] = (falhas, time.monotonic() + espera)

    def sucesso(self, chave: str) -> None:
        with self._lock:
            self._estado.pop(chave, None)


class RedisLoginThrottle:
    """Mesma regra do LoginThrottle, mas com estado no Redis (compartilhado entre réplicas). Cai para memória se o Redis falhar."""

    TTL = 3600

    def __init__(self, cliente) -> None:
        self.r = cliente  # redis.Redis síncrono (as rotas de login rodam em threadpool)
        self._local = LoginThrottle()

    @staticmethod
    def _k(chave: str) -> str:
        return "lt:" + hashlib.sha256(chave.encode()).hexdigest()[:32]

    def segundos_restantes(self, chave: str) -> int:
        try:
            ate = float(self.r.hget(self._k(chave), "ate") or 0)
            return max(0, int(ate - time.time() + 0.999))
        except Exception:  # noqa: BLE001
            return self._local.segundos_restantes(chave)

    def falha(self, chave: str) -> None:
        try:
            k = self._k(chave)
            falhas = int(self.r.hincrby(k, "falhas", 1))
            espera = 0 if falhas <= LoginThrottle.LIVRES else min(2 ** (falhas - LoginThrottle.LIVRES), LoginThrottle.TETO)
            self.r.hset(k, "ate", time.time() + espera)
            self.r.expire(k, self.TTL)
        except Exception:  # noqa: BLE001
            self._local.falha(chave)

    def sucesso(self, chave: str) -> None:
        try:
            self.r.delete(self._k(chave))
        except Exception:  # noqa: BLE001
            self._local.sucesso(chave)


# ------------------------------------------------------------------------ serviço
class AuthService:
    def __init__(self, session: Session, settings: Settings, throttle: LoginThrottle) -> None:
        self.session = session
        self.settings = settings
        self.throttle = throttle

    def criar_usuario(self, username: str, senha: str, role: str) -> UsuarioORM:
        validar_forca_senha(senha)
        if role not in RANK:
            raise ValueError("Papel inválido")
        user = UsuarioORM(username=username.strip().lower(), password_hash=hash_senha(senha), role=role)
        self.session.add(user)
        self.session.commit()
        return user

    def redefinir_senha(self, username: str, senha: str) -> UsuarioORM:
        """Recuperação sem e-mail (uso do administrador do servidor, via CLI): troca a senha e derruba TODAS as sessões."""
        validar_forca_senha(senha)
        user = self.session.scalar(select(UsuarioORM).where(UsuarioORM.username == username.strip().lower()))
        if user is None:
            raise ErroAutenticacao()
        user.password_hash = hash_senha(senha)
        user.ativo = True
        user.token_version += 1  # access tokens em circulação deixam de valer
        self.session.execute(update(RefreshTokenORM).where(RefreshTokenORM.user_id == user.id).values(revogado=True))
        self.session.commit()
        return user

    def garantir_admin_inicial(self) -> None:
        s = self.settings
        if not (s.admin_username and s.admin_password):
            return
        if self.session.scalar(select(UsuarioORM.id).limit(1)) is None:
            self.criar_usuario(s.admin_username, s.admin_password, "admin")

    def _emitir_refresh(self, user: UsuarioORM, familia: Optional[str] = None) -> str:
        token = secrets.token_urlsafe(48)
        self.session.add(
            RefreshTokenORM(
                token_hash=_hash_token(token),
                user_id=user.id,
                familia=familia or uuid.uuid4().hex,
                expira_em=agora() + timedelta(days=self.settings.refresh_ttl_dias),
            )
        )
        return token

    def login(self, username: str, senha: str, ip: str) -> tuple[UsuarioORM, str, int, str]:
        chave = f"{ip}|{username.strip().lower()}"
        espera = self.throttle.segundos_restantes(chave)
        if espera:
            raise ContaBloqueada(espera)
        user = self.session.scalar(select(UsuarioORM).where(UsuarioORM.username == username.strip().lower()))
        # Sempre executa uma verificação Argon2 (mesmo sem usuário) para não vazar existência pelo tempo.
        ok = verificar_senha(user.password_hash if user else _HASH_FALSO, senha)
        if not (user and user.ativo and ok):
            self.throttle.falha(chave)
            raise ErroAutenticacao()
        self.throttle.sucesso(chave)
        access, ttl = emitir_access(self.settings, user)
        refresh = self._emitir_refresh(user)
        self.session.commit()
        return user, access, ttl, refresh

    def renovar(self, token: str) -> tuple[UsuarioORM, str, int, str]:
        registro = self.session.get(RefreshTokenORM, _hash_token(token))
        if registro is None:
            raise ErroAutenticacao()
        if registro.revogado:
            # Reuso de um token já rotacionado: possível roubo → derruba a família inteira.
            self.session.execute(update(RefreshTokenORM).where(RefreshTokenORM.familia == registro.familia).values(revogado=True))
            self.session.commit()
            raise ErroAutenticacao()
        expira = registro.expira_em if registro.expira_em.tzinfo else registro.expira_em.replace(tzinfo=timezone.utc)
        user = self.session.get(UsuarioORM, registro.user_id)
        if expira <= agora() or not (user and user.ativo):
            registro.revogado = True
            self.session.commit()
            raise ErroAutenticacao()
        registro.revogado = True  # rotação: o token usado deixa de valer
        novo = self._emitir_refresh(user, registro.familia)
        access, ttl = emitir_access(self.settings, user)
        self.session.commit()
        return user, access, ttl, novo

    def logout(self, token: Optional[str]) -> None:
        """Revoga a família do refresh E invalida os access tokens em circulação (token_version + 1)."""
        if not token:
            return
        registro = self.session.get(RefreshTokenORM, _hash_token(token))
        if registro:
            self.session.execute(update(RefreshTokenORM).where(RefreshTokenORM.familia == registro.familia).values(revogado=True))
            user = self.session.get(UsuarioORM, registro.user_id)
            if user:
                user.token_version += 1
            self.session.commit()


# ------------------------------------------------------------- dependências FastAPI
def _nao_autenticado() -> HTTPException:
    return HTTPException(status_code=401, detail="Autenticação necessária", headers={"WWW-Authenticate": "Bearer"})


def get_principal(request: Request) -> Principal:
    """Resolve quem está chamando. Com AUTH_ENABLED=false (apenas dev) todos são 'anon' com papel admin."""
    settings: Settings = request.app.state.settings
    if not settings.auth_enabled:
        return Principal(user_id=0, username="anon", role="admin")

    esquema, _, token = request.headers.get("authorization", "").partition(" ")
    if esquema.lower() != "bearer" or not token:
        raise _nao_autenticado()
    try:
        user_id, versao = decodificar_access(settings, token.strip())
    except jwt.InvalidTokenError:
        raise _nao_autenticado() from None

    with request.app.state.sessionmaker() as sessao:
        user = sessao.get(UsuarioORM, user_id)
        if user is None or not user.ativo or user.token_version != versao:  # versão antiga = sessão revogada
            raise _nao_autenticado()
        return Principal(user_id=user.id, username=user.username, role=user.role)


def exigir(papel: str):
    """Dependência: nega por padrão. 401 sem credencial válida, 403 com papel insuficiente."""

    def verificar(principal: Principal = Depends(get_principal)) -> Principal:
        if RANK[principal.role] < RANK[papel]:
            raise HTTPException(status_code=403, detail="Permissão insuficiente")
        return principal

    verificar.__name__ = f"exigir_{papel}"
    return verificar
