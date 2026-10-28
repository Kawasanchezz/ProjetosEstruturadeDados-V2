"""Tradução de erros de domínio/infra em respostas HTTP sem expor detalhes internos."""
from __future__ import annotations

import logging

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from sqlalchemy.exc import IntegrityError, SQLAlchemyError
from sqlalchemy.orm.exc import StaleDataError

from app.domain import (
    CicloError,
    ErroFormula,
    NoNaoEncontradoError,
    PaiAmbiguoError,
    PaiInexistenteError,
    RaizInvalidaError,
)
from app.security.middleware import request_id_ctx

log = logging.getLogger("bom")

STATUS = {
    NoNaoEncontradoError: 404,
    PaiInexistenteError: 422,
    CicloError: 409,
    PaiAmbiguoError: 409,
    RaizInvalidaError: 409,
}


def _resp(status: int, detail: str, **extra: object) -> JSONResponse:
    return JSONResponse(status_code=status, content={"detail": detail, **extra})


def registrar_handlers(app: FastAPI) -> None:
    @app.exception_handler(ErroFormula)
    async def _formula(_: Request, exc: ErroFormula):
        return _resp(STATUS.get(type(exc), 422), str(exc))

    @app.exception_handler(RequestValidationError)
    async def _validacao(_: Request, exc: RequestValidationError):
        # O handler padrão ecoa o valor enviado ("input") e o contexto; devolvemos só local e mensagem.
        erros = [{"loc": [str(p) for p in e.get("loc", [])], "msg": str(e.get("msg", "valor inválido"))} for e in exc.errors()[:10]]
        return JSONResponse(status_code=422, content={"detail": erros})

    @app.exception_handler(StaleDataError)
    async def _concorrencia(_: Request, __: StaleDataError):
        return _resp(409, "Este registro foi alterado por outra operação. Recarregue e tente novamente.")

    @app.exception_handler(IntegrityError)
    async def _integridade(_: Request, exc: IntegrityError):
        log.warning("Violação de integridade: %s", type(exc.orig).__name__)
        return _resp(409, "Operação viola uma regra de integridade dos dados")

    @app.exception_handler(SQLAlchemyError)
    async def _banco(_: Request, exc: SQLAlchemyError):
        log.exception("Erro de banco")
        return _resp(500, "Erro interno do servidor", request_id=request_id_ctx.get())
