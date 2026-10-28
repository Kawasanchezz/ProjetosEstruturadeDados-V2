"""Modelos do domínio: entrada (ItemFormula/RegistroNo) e o nó da árvore genérica."""
from __future__ import annotations

from dataclasses import dataclass, field
from decimal import Decimal, InvalidOperation
from typing import Optional, Union

Dinheiro = Union[Decimal, str, int]


def _para_decimal(valor: Dinheiro, campo: str) -> Decimal:
    try:
        resultado = Decimal(str(valor))
    except InvalidOperation as exc:
        raise ValueError(f"{campo} inválido: {valor!r}") from exc
    if not resultado.is_finite():
        raise ValueError(f"{campo} deve ser finito")
    return resultado


def _validar(nome: str, quantidade: int, preco: Decimal) -> None:
    if not nome or not nome.strip():
        raise ValueError("nome não pode ser vazio")
    if quantidade <= 0:
        raise ValueError("quantidade deve ser maior que zero")
    if preco < 0:
        raise ValueError("preço não pode ser negativo")


@dataclass(frozen=True)
class ItemFormula:
    """Linha de entrada: o pai é referenciado pelo nome (como nos testes em Java)."""

    nome: str
    pai: Optional[str]
    quantidade: int
    preco: Decimal

    def __post_init__(self) -> None:
        object.__setattr__(self, "preco", _para_decimal(self.preco, "preço"))
        _validar(self.nome, self.quantidade, self.preco)


@dataclass(frozen=True)
class RegistroNo:
    """Linha de entrada por id (usada pelo banco): o pai é resolvido por id único."""

    id: int
    nome: str
    parent_id: Optional[int]
    quantidade: int
    preco: Decimal

    def __post_init__(self) -> None:
        object.__setattr__(self, "preco", _para_decimal(self.preco, "preço"))
        _validar(self.nome, self.quantidade, self.preco)


@dataclass(eq=False)
class TreeNode:
    """Nó de uma árvore genérica: N filhos e referência ao pai (para a implosão)."""

    id: int
    nome: str
    quantidade: int
    preco: Decimal
    pai: Optional["TreeNode"] = field(default=None, repr=False)
    filhos: list["TreeNode"] = field(default_factory=list, repr=False)

    def adicionar_filho(self, filho: "TreeNode") -> None:
        filho.pai = self
        self.filhos.append(filho)

    @property
    def eh_folha(self) -> bool:
        return not self.filhos


@dataclass(frozen=True)
class LinhaSimulacao:
    """Resultado da simulação para um nó."""

    id: int
    nome: str
    caminho: tuple[str, ...]
    quantidade_total: int
    custo: Decimal


@dataclass(frozen=True)
class PassoImplosao:
    """Custo total de um nó do caminho (nó alterado → raiz) antes e depois da mudança."""

    id: int
    nome: str
    antes: Decimal
    depois: Decimal

    @property
    def diferenca(self) -> Decimal:
        return self.depois - self.antes
