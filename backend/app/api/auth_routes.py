"""Rotas de autenticação. Só existem com AUTH_ENABLED=true; sem isso respondem 404."""
from __future__ import annotations

from typing import Iterator, Optional
from urllib.parse import urlparse

from fastapi import APIRouter, Cookie, Depends, HTTPException, Request, Response
from sqlalchemy.exc import IntegrityError

from app.api import schemas as s
from app.security.auth import AuthService, ContaBloqueada, ErroAutenticacao, Principal, exigir

COOKIE = "bom_refresh"
COOKIE_PATH = "/api/auth"

router = APIRouter(prefix="/auth", tags=["autenticação"])


def auth_habilitada(request: Request) -> None:
    if not request.app.state.settings.auth_enabled:
        raise HTTPException(status_code=404, detail="Não encontrado")


def get_auth(request: Request) -> Iterator[AuthService]:
    with request.app.state.sessionmaker() as session:
        yield AuthService(session, request.app.state.settings, request.app.state.login_throttle)


def _ip(request: Request) -> str:
    return request.client.host if request.client else "desconhecido"


def _origem_confiavel(request: Request) -> None:
    """Defesa extra de CSRF no refresh por cookie (além de SameSite=Strict): Origin, se enviada, deve ser conhecida."""
    origem = request.headers.get("origin")
    if not origem:
        return
    settings = request.app.state.settings
    mesma_origem = urlparse(origem).netloc == request.headers.get("host")
    if not mesma_origem and origem not in settings.cors_origins:
        raise HTTPException(status_code=403, detail="Origem não permitida")


def _definir_cookie(request: Request, resposta: Response, token: str) -> None:
    settings = request.app.state.settings
    resposta.set_cookie(
        COOKIE, token, max_age=settings.refresh_ttl_dias * 86400, path=COOKIE_PATH,
        httponly=True, secure=settings.cookie_seguro, samesite="strict",
    )


def _token_out(user, access: str, ttl: int) -> s.TokenOut:
    return s.TokenOut(access_token=access, expires_in=ttl, role=user.role)


@router.get("/config")
def config(request: Request) -> dict:
    """Público: diz ao front se há login (não revela mais nada)."""
    return {"auth_enabled": request.app.state.settings.auth_enabled}


@router.post("/login", response_model=s.TokenOut, dependencies=[Depends(auth_habilitada)])
def login(dados: s.LoginEntrada, request: Request, resposta: Response, auth: AuthService = Depends(get_auth)):
    try:
        user, access, ttl, refresh = auth.login(dados.username, dados.password, _ip(request))
    except ContaBloqueada as exc:
        raise HTTPException(status_code=429, detail="Muitas tentativas. Aguarde para tentar novamente.", headers={"Retry-After": str(exc.segundos)}) from None
    except ErroAutenticacao:
        raise HTTPException(status_code=401, detail="Credenciais inválidas", headers={"WWW-Authenticate": "Bearer"}) from None
    _definir_cookie(request, resposta, refresh)
    return _token_out(user, access, ttl)


@router.post("/refresh", response_model=s.TokenOut, dependencies=[Depends(auth_habilitada)])
def refresh(request: Request, resposta: Response, bom_refresh: Optional[str] = Cookie(default=None), auth: AuthService = Depends(get_auth)):
    _origem_confiavel(request)
    if not bom_refresh:
        raise HTTPException(status_code=401, detail="Sessão expirada", headers={"WWW-Authenticate": "Bearer"})
    try:
        user, access, ttl, novo = auth.renovar(bom_refresh)
    except ErroAutenticacao:
        resposta.delete_cookie(COOKIE, path=COOKIE_PATH)
        raise HTTPException(status_code=401, detail="Sessão expirada", headers={"WWW-Authenticate": "Bearer"}) from None
    _definir_cookie(request, resposta, novo)
    return _token_out(user, access, ttl)


@router.post("/logout", status_code=204, dependencies=[Depends(auth_habilitada)])
def logout(request: Request, resposta: Response, bom_refresh: Optional[str] = Cookie(default=None), auth: AuthService = Depends(get_auth)):
    _origem_confiavel(request)
    auth.logout(bom_refresh)
    resposta.delete_cookie(COOKIE, path=COOKIE_PATH)
    resposta.status_code = 204


@router.get("/me", response_model=s.UsuarioOut, dependencies=[Depends(auth_habilitada)])
def me(principal: Principal = Depends(exigir("viewer")), auth: AuthService = Depends(get_auth)):
    from app.orm import UsuarioORM

    return auth.session.get(UsuarioORM, principal.user_id)


@router.post("/users", response_model=s.UsuarioOut, status_code=201, dependencies=[Depends(auth_habilitada), Depends(exigir("admin"))])
def criar_usuario(dados: s.UsuarioCriar, auth: AuthService = Depends(get_auth)):
    try:
        return auth.criar_usuario(dados.username, dados.password, dados.role)
    except IntegrityError:
        raise HTTPException(status_code=409, detail="Nome de usuário indisponível") from None
