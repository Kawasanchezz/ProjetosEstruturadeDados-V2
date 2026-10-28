"""Contratos da API. Valores monetários saem como string ("8080.00")."""
from __future__ import annotations

import unicodedata
from datetime import datetime
from decimal import Decimal
from typing import Annotated, Literal, Optional

from pydantic import AfterValidator, BaseModel, ConfigDict, Field, PlainSerializer, StringConstraints

CENT = Decimal("0.01")


def _fmt(valor: Decimal) -> str:
    return f"{valor.quantize(CENT):.2f}"


Dinheiro = Annotated[Decimal, PlainSerializer(_fmt, return_type=str, when_used="json")]
PrecoEntrada = Annotated[Decimal, Field(ge=0, max_digits=12, decimal_places=2)]
MAX_ID = 2_147_483_647  # cabe em INTEGER de 32 bits: ids maiores nem chegam ao banco


def _sem_controle(valor: str) -> str:
    """Rejeita caracteres de controle e de formatação (inclui NUL, quebras de linha e marcas bidi)."""
    if any(unicodedata.category(c).startswith("C") for c in valor):
        raise ValueError("O nome contém caracteres de controle não permitidos")
    return valor


# Nomes são texto livre (podem conter < > ' "), sempre tratados como dado: ORM parametrizado no banco
# e renderização como texto no front. Controles/bidi são barrados para evitar log injection e spoofing visual.
Nome = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=120), AfterValidator(_sem_controle)]
Quantidade = Annotated[int, Field(gt=0, le=1_000_000, strict=True)]


class _Entrada(BaseModel):
    model_config = ConfigDict(extra="forbid")


# ---- entrada
class ProdutoCriar(_Entrada):
    nome: Nome
    preco: PrecoEntrada = Decimal("0")
    quantidade: Quantidade = 1


class ProdutoAtualizar(_Entrada):
    nome: Optional[Nome] = None
    preco: Optional[PrecoEntrada] = None
    quantidade: Optional[Quantidade] = None


class ComponenteCriar(_Entrada):
    nome: Nome
    parent_id: int = Field(gt=0, le=MAX_ID, strict=True)
    quantidade: Quantidade = 1
    preco: PrecoEntrada


class ComponenteAtualizar(_Entrada):
    """`parent_id` presente no corpo move o nó (null promove a produto)."""

    nome: Optional[Nome] = None
    quantidade: Optional[Quantidade] = None
    preco: Optional[PrecoEntrada] = None
    parent_id: Optional[int] = Field(default=None, gt=0, le=MAX_ID, strict=True)


class PrecoAtualizar(_Entrada):
    preco: PrecoEntrada


class SimulacaoEntrada(_Entrada):
    quantidade: int = Field(gt=0, le=1_000_000, strict=True)


class LoginEntrada(_Entrada):
    username: Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=50)]
    password: Annotated[str, StringConstraints(min_length=1, max_length=128)]


class UsuarioCriar(_Entrada):
    username: Annotated[str, StringConstraints(strip_whitespace=True, to_lower=True, pattern=r"^[a-z0-9_.-]{3,50}$")]
    password: Annotated[str, StringConstraints(min_length=12, max_length=128)]
    role: Literal["admin", "viewer"]


# ---- saída
class NoOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    nome: str
    parent_id: Optional[int]
    quantidade: int
    preco: Dinheiro


class ComponenteDetalhe(NoOut):
    produto_id: int
    caminho: list[str]
    custo_total: Dinheiro
    total_descendentes: int


class ProdutoResumo(BaseModel):
    id: int
    nome: str
    quantidade: int
    preco: Dinheiro
    custo_total: Dinheiro
    total_componentes: int
    profundidade: int


class PaginaProdutos(BaseModel):
    total: int
    itens: list[ProdutoResumo]


class PaginaComponentes(BaseModel):
    total: int
    itens: list[NoOut]


class NoExplosao(BaseModel):
    id: int
    nome: str
    parent_id: Optional[int]
    quantidade: int
    preco: Dinheiro
    custo_unitario: Dinheiro
    custo_total: Dinheiro
    filhos: list["NoExplosao"]


class Explosao(BaseModel):
    produto_id: int
    nome: str
    total: Dinheiro
    raiz: NoExplosao


class LinhaSimulacaoOut(BaseModel):
    id: int
    nome: str
    caminho: list[str]
    quantidade_total: int
    custo: Dinheiro


class Simulacao(BaseModel):
    produto_id: int
    unidades: int
    total: Dinheiro
    linhas: list[LinhaSimulacaoOut]


class PassoImplosaoOut(BaseModel):
    id: int
    nome: str
    antes: Dinheiro
    depois: Dinheiro
    diferenca: Dinheiro


class Implosao(BaseModel):
    produto_id: int
    ids_afetados: list[int]
    total_antes: Dinheiro
    total_depois: Dinheiro
    passos: list[PassoImplosaoOut]


class Remocao(BaseModel):
    removidos: int


class ErroOut(BaseModel):
    detail: str


class TokenOut(BaseModel):
    access_token: str
    token_type: Literal["bearer"] = "bearer"
    expires_in: int
    role: str


class UsuarioOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    username: str
    role: str
    ativo: bool


class AuditoriaOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    ocorrido_em: datetime
    ator: str
    acao: str
    no_id: Optional[int]
    nome: Optional[str]
    antes: Optional[str]
    depois: Optional[str]
    detalhe: Optional[str]


class PaginaAuditoria(BaseModel):
    total: int
    itens: list[AuditoriaOut]
