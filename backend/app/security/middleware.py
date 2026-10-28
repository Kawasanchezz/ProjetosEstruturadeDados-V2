"""Middlewares ASGI de segurança: cabeçalhos, request_id, erro genérico e limites de corpo."""
from __future__ import annotations

import asyncio
import contextvars
import json
import logging
import re
import uuid

from starlette.types import ASGIApp, Message, Receive, Scope, Send

log = logging.getLogger("bom")
request_id_ctx: contextvars.ContextVar[str] = contextvars.ContextVar("request_id", default="-")

# CSP compatível com o build do Vite: scripts e estilos só do próprio domínio (sem 'unsafe-inline'/'unsafe-eval').
# Framer Motion, GSAP e React alteram estilos via CSSOM (element.style), que a CSP não bloqueia.
CSP = (
    "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; "
    "connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'"
)
CABECALHOS_BASE = [
    (b"x-content-type-options", b"nosniff"),
    (b"referrer-policy", b"no-referrer"),
    (b"permissions-policy", b"camera=(), microphone=(), geolocation=(), payment=()"),
    (b"x-frame-options", b"DENY"),
    (b"cross-origin-opener-policy", b"same-origin"),
    (b"cross-origin-resource-policy", b"same-origin"),
]
HSTS = (b"strict-transport-security", b"max-age=31536000; includeSubDomains")
CONTROLE = re.compile(r"[\x00-\x1f\x7f]")


def limpar_log(valor: object) -> str:
    """Neutraliza quebras de linha e controles (log injection) e limita o tamanho."""
    return CONTROLE.sub("?", str(valor))[:200]


class JsonFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        dados = {
            "ts": self.formatTime(record, "%Y-%m-%dT%H:%M:%S%z"),
            "nivel": record.levelname,
            "logger": record.name,
            "request_id": request_id_ctx.get(),
            "msg": record.getMessage(),
        }
        if record.exc_info:
            dados["excecao"] = self.formatException(record.exc_info)
        return json.dumps(dados, ensure_ascii=False)


def configurar_logs() -> None:
    logger = logging.getLogger("bom")
    if any(isinstance(h.formatter, JsonFormatter) for h in logger.handlers):
        return
    handler = logging.StreamHandler()
    handler.setFormatter(JsonFormatter())
    logger.addHandler(handler)
    logger.setLevel(logging.INFO)
    logger.propagate = False


class SecurityMiddleware:
    """Camada mais externa: request_id, cabeçalhos de segurança e resposta 500 sem detalhes internos."""

    def __init__(self, app: ASGIApp, hsts: bool) -> None:
        self.app = app
        self.hsts = hsts

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] != "http":
            return await self.app(scope, receive, send)

        request_id = uuid.uuid4().hex
        token = request_id_ctx.set(request_id)
        caminho = scope.get("path", "")
        iniciou = False

        async def enviar(msg: Message) -> None:
            nonlocal iniciou
            if msg["type"] == "http.response.start":
                iniciou = True
                cabecalhos = [(k, v) for k, v in msg.get("headers", []) if k.lower() not in {b"server", b"x-powered-by"}]
                cabecalhos += CABECALHOS_BASE + [(b"x-request-id", request_id.encode())]
                if not caminho.startswith("/api/docs"):  # o Swagger UI usa CDN; só existe com ENABLE_DOCS
                    cabecalhos.append((b"content-security-policy", CSP.encode()))
                if caminho.startswith("/api/"):
                    cabecalhos.append((b"cache-control", b"no-store"))
                if self.hsts:
                    cabecalhos.append(HSTS)
                msg = {**msg, "headers": cabecalhos}
            await send(msg)

        try:
            await self.app(scope, receive, enviar)
        except Exception:  # noqa: BLE001 - última barreira: nada de stack trace para o cliente
            log.exception("Erro não tratado em %s %s", limpar_log(scope.get("method")), limpar_log(caminho))
            if not iniciou:
                corpo = json.dumps({"detail": "Erro interno do servidor", "request_id": request_id}).encode()
                await enviar({"type": "http.response.start", "status": 500, "headers": [(b"content-type", b"application/json"), (b"content-length", str(len(corpo)).encode())]})
                await send({"type": "http.response.body", "body": corpo})
        finally:
            request_id_ctx.reset(token)


class BodyLimitMiddleware:
    """Rejeita corpos grandes (413), sem Content-Length em chunked (411) e tipos não-JSON (415)."""

    def __init__(self, app: ASGIApp, max_bytes: int) -> None:
        self.app = app
        self.max_bytes = max_bytes

    @staticmethod
    async def _responder(scope: Scope, receive: Receive, send: Send, status: int, detalhe: str) -> None:
        corpo = json.dumps({"detail": detalhe}).encode()
        await send({"type": "http.response.start", "status": status, "headers": [(b"content-type", b"application/json"), (b"content-length", str(len(corpo)).encode())]})
        await send({"type": "http.response.body", "body": corpo})

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] == "http" and scope.get("method") in {"POST", "PUT", "PATCH"}:
            cab = {k.lower(): v for k, v in scope.get("headers", [])}
            tamanho = cab.get(b"content-length")
            if tamanho is None and b"transfer-encoding" in cab:
                return await self._responder(scope, receive, send, 411, "Content-Length é obrigatório")
            try:
                n = int(tamanho) if tamanho is not None else 0
            except ValueError:
                return await self._responder(scope, receive, send, 400, "Content-Length inválido")
            if n > self.max_bytes:
                return await self._responder(scope, receive, send, 413, "Corpo da requisição grande demais")
            if n > 0 and not cab.get(b"content-type", b"").lower().startswith(b"application/json"):
                return await self._responder(scope, receive, send, 415, "Use Content-Type: application/json")
        await self.app(scope, receive, send)


class TimeoutMiddleware:
    """Responde 504 se a requisição passar de `segundos`.

    Limite honesto: handlers síncronos rodam numa thread que o asyncio não consegue interromper; o cliente recebe o 504 e a
    conexão é liberada, mas a thread termina o trabalho. Por isso os tetos de tamanho da árvore continuam sendo a defesa principal.
    """

    def __init__(self, app: ASGIApp, segundos: int) -> None:
        self.app = app
        self.segundos = segundos

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] != "http" or self.segundos <= 0:
            return await self.app(scope, receive, send)
        iniciou = False

        async def enviar(msg: Message) -> None:
            nonlocal iniciou
            if msg["type"] == "http.response.start":
                iniciou = True
            await send(msg)

        try:
            await asyncio.wait_for(self.app(scope, receive, enviar), timeout=self.segundos)
        except asyncio.TimeoutError:
            log.warning("Timeout de %ss em %s %s", self.segundos, limpar_log(scope.get("method")), limpar_log(scope.get("path")))
            if not iniciou:
                corpo = json.dumps({"detail": "Tempo limite da requisição excedido"}).encode()
                await send({"type": "http.response.start", "status": 504, "headers": [(b"content-type", b"application/json"), (b"content-length", str(len(corpo)).encode())]})
                await send({"type": "http.response.body", "body": corpo})
