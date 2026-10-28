"""Domínio puro do TreeBOM: árvore genérica e algoritmos, sem dependência de framework."""
from app.domain.errors import (
    CicloError,
    ErroFormula,
    LimiteExcedidoError,
    NoNaoEncontradoError,
    PaiAmbiguoError,
    PaiInexistenteError,
    RaizInvalidaError,
)
from app.domain.models import ItemFormula, LinhaSimulacao, PassoImplosao, RegistroNo, TreeNode
from app.domain.tree import (
    atualizar_preco,
    build_tree,
    build_tree_por_id,
    calcular_total,
    calcular_totais,
    custo_unitario,
    explodir,
    localizar,
    print_tree,
    profundidade,
    simular_producao,
)

__all__ = [
    "CicloError", "ErroFormula", "LimiteExcedidoError", "NoNaoEncontradoError", "PaiAmbiguoError",
    "PaiInexistenteError", "RaizInvalidaError", "ItemFormula", "LinhaSimulacao",
    "PassoImplosao", "RegistroNo", "TreeNode", "atualizar_preco", "build_tree",
    "build_tree_por_id", "calcular_total", "calcular_totais", "custo_unitario", "explodir", "localizar",
    "print_tree", "profundidade", "simular_producao",
]
