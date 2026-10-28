"""Rate limiting por IP e por classe de rota. Sem REDIS_URL: janela deslizante em memória (1 processo).
Com REDIS_URL: contadores compartilhados entre réplicas (janela fixa); se o Redis cair, volta para a memória.

Classes: "login" (/api/auth/login), "escrita" (POST/PUT/PATCH/DELETE) e "leitura" (o resto).
Em produção, atrás de proxy, rode o uvicorn com --proxy-headers e --forwarded-allow-ips restrito
para que `scope["client"]` seja o IP real e não o do proxy.
"""
from __future__ import annotations

import logging
import threading
import time
from collections import defaultdict, deque

from starlette.responses import JSONResponse
from starlette.types import ASGIApp, Receive, Scope, Send

log = logging.getLogger("bom")
JANELAS = {"second": 1, "minute": 60, "hour": 3600}
METODOS_ESCRITA = {"POST", "PUT", "PATCH", "DELETE"}
LIMPEZA_A_CADA = 2_000  # requisições


def parse_limite(texto: str) -> tuple[int, int]:
    """'120/minute' -> (120, 60)."""
    quantidade, _, unidade = texto.partition("/")
    return int(quantidade), JANELAS[unidade.strip().lower()]


class RateLimitMiddleware:
    def __init__(self, app: ASGIApp, leitura: str, escrita: str, login: str, redis=None) -> None:
        self.app = app
        self.redis = redis  # cliente redis.asyncio opcional: contadores compartilhados entre réplicas
        self.limites = {"leitura": parse_limite(leitura), "escrita": parse_limite(escrita), "login": parse_limite(login)}
        self._acessos: dict[tuple[str, str], deque[float]] = defaultdict(deque)
        self._lock = threading.Lock()
        self._contador = 0

    @staticmethod
    def _classe(metodo: str, caminho: str) -> str:
        if caminho.rstrip("/") == "/api/auth/login":
            return "login"
        return "escrita" if metodo in METODOS_ESCRITA else "leitura"

    def _limpar(self, agora: float) -> None:
        """Remove IPs inativos: sem isso o dicionário cresceria sem limite (DoS de memória)."""
        janela_maxima = max(j for _, j in self.limites.values())
        for chave in [k for k, fila in self._acessos.items() if not fila or agora - fila[-1] >= janela_maxima]:
            del self._acessos[chave]

    def _permitido(self, cliente: str, classe: str) -> bool:
        maximo, janela = self.limites[classe]
        agora = time.monotonic()
        with self._lock:
            self._contador += 1
            if self._contador % LIMPEZA_A_CADA == 0:
                self._limpar(agora)
            fila = self._acessos[(cliente, classe)]
            while fila and agora - fila[0] >= janela:
                fila.popleft()
            if len(fila) >= maximo:
                return False
            fila.append(agora)
            return True

    async def _permitido_redis(self, cliente: str, classe: str) -> bool:
        """Janela fixa por (classe, IP). Simples e atômica; permite rajada de até 2x o limite na virada da janela."""
        maximo, janela = self.limites[classe]
        chave = f"rl:{classe}:{cliente}:{int(time.time() // janela)}"
        async with self.redis.pipeline(transaction=True) as pipe:
            pipe.incr(chave)
            pipe.expire(chave, janela + 1)
            contagem, _ = await pipe.execute()
        return int(contagem) <= maximo

    async def _checar(self, cliente: str, classe: str) -> bool:
        if self.redis is not None:
            try:
                return await self._permitido_redis(cliente, classe)
            except Exception:  # noqa: BLE001 - Redis fora do ar não pode derrubar a API: cai para o limite local
                log.warning("Redis indisponível no rate limit; usando contador em memória")
        return self._permitido(cliente, classe)

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] == "http" and scope.get("method") != "OPTIONS":
            cliente = (scope.get("client") or ("desconhecido", 0))[0]
            classe = self._classe(scope.get("method", "GET"), scope.get("path", ""))
            if not await self._checar(cliente, classe):
                resposta = JSONResponse(
                    {"detail": "Muitas requisições. Tente novamente em instantes."},
                    status_code=429,
                    headers={"Retry-After": str(self.limites[classe][1])},
                )
                return await resposta(scope, receive, send)
        await self.app(scope, receive, send)
