"""Algoritmos sobre a árvore genérica do TreeBOM.

Regra de cálculo (ver README):
    custo_unitario(n) = preco(n) + soma(custo_total(filho) for filho in n.filhos)
    custo_total(n)    = quantidade(n) × custo_unitario(n)
"""
from __future__ import annotations

from decimal import Decimal
from typing import Callable, Iterable, Iterator, Optional, Sequence

from app.domain.errors import (
    CicloError,
    ErroFormula,
    LimiteExcedidoError,
    NoNaoEncontradoError,
    PaiAmbiguoError,
    PaiInexistenteError,
    RaizInvalidaError,
)
from app.domain.models import (
    Dinheiro,
    ItemFormula,
    LinhaSimulacao,
    PassoImplosao,
    RegistroNo,
    TreeNode,
    _para_decimal,
)

CENTAVOS = Decimal("0.01")


# --------------------------------------------------------------------- construção
def _ligar(nos: dict[int, TreeNode], pai_de: dict[int, Optional[int]]) -> TreeNode:
    """Liga filhos aos pais em O(n), valida raiz única e ausência de ciclos."""
    raiz: Optional[TreeNode] = None
    for id_, pai_id in pai_de.items():
        if pai_id is None:
            if raiz is not None:
                raise RaizInvalidaError("A fórmula possui mais de uma raiz")
            raiz = nos[id_]
        else:
            nos[pai_id].adicionar_filho(nos[id_])
    if raiz is None:
        raise CicloError("Nenhuma raiz encontrada: a hierarquia contém um ciclo")
    # Nós em ciclo nunca são alcançados a partir da raiz.
    if sum(1 for _ in _percorrer(raiz)) != len(nos):
        raise CicloError("A hierarquia contém um ciclo")
    return raiz


def build_tree(itens: Sequence[ItemFormula]) -> TreeNode:
    """Constrói a árvore a partir de itens que referenciam o pai pelo nome.

    Cada item recebe um id único (sua posição). Nomes repetidos entre nós são
    permitidos (ex.: "Plástico"); só o nome do *pai* precisa ser inequívoco.
    """
    if not itens:
        raise RaizInvalidaError("A fórmula está vazia")
    nos = {i: TreeNode(i, it.nome, it.quantidade, it.preco) for i, it in enumerate(itens)}
    ids_por_nome: dict[str, list[int]] = {}
    for i, it in enumerate(itens):
        ids_por_nome.setdefault(it.nome, []).append(i)

    pai_de: dict[int, Optional[int]] = {}
    for i, it in enumerate(itens):
        if it.pai is None:
            pai_de[i] = None
            continue
        candidatos = ids_por_nome.get(it.pai, [])
        if not candidatos:
            raise PaiInexistenteError(f"Pai '{it.pai}' do item '{it.nome}' não existe")
        if len(candidatos) > 1:
            raise PaiAmbiguoError(
                f"Pai '{it.pai}' do item '{it.nome}' é ambíguo ({len(candidatos)} nós com esse nome)"
            )
        if candidatos[0] == i:
            raise CicloError(f"O item '{it.nome}' não pode ser pai de si mesmo")
        pai_de[i] = candidatos[0]
    return _ligar(nos, pai_de)


def build_tree_por_id(registros: Iterable[RegistroNo]) -> TreeNode:
    """Constrói a árvore a partir de registros (id, parent_id), como no banco."""
    registros = list(registros)
    if not registros:
        raise RaizInvalidaError("A fórmula está vazia")
    nos: dict[int, TreeNode] = {}
    for r in registros:
        if r.id in nos:
            raise ErroFormula(f"Id duplicado: {r.id}")
        nos[r.id] = TreeNode(r.id, r.nome, r.quantidade, r.preco)
    pai_de: dict[int, Optional[int]] = {}
    for r in registros:
        if r.parent_id is not None and r.parent_id not in nos:
            raise PaiInexistenteError(f"Pai id={r.parent_id} do nó id={r.id} não existe")
        if r.parent_id == r.id:
            raise CicloError(f"O nó id={r.id} não pode ser pai de si mesmo")
        pai_de[r.id] = r.parent_id
    return _ligar(nos, pai_de)


# ----------------------------------------------------------------------- percurso
def _percorrer(raiz: TreeNode) -> Iterator[TreeNode]:
    """Pré-ordem iterativa (usada só para contagem/busca; o resto é recursivo)."""
    pilha = [raiz]
    while pilha:
        no = pilha.pop()
        yield no
        pilha.extend(reversed(no.filhos))


def localizar(raiz: TreeNode, id_: int) -> TreeNode:
    for no in _percorrer(raiz):
        if no.id == id_:
            return no
    raise NoNaoEncontradoError(f"Nó id={id_} não encontrado")


def profundidade(no: TreeNode) -> int:
    """Número de níveis da subárvore (folha = 1). Iterativo: não depende do limite de recursão."""
    maior = 0
    pilha = [(no, 1)]
    while pilha:
        atual, nivel = pilha.pop()
        maior = max(maior, nivel)
        pilha.extend((f, nivel + 1) for f in atual.filhos)
    return maior


# ----------------------------------------------------------------------- cálculos
def _pre_ordem(raiz: TreeNode) -> list[TreeNode]:
    """Pré-ordem iterativa (pai antes dos filhos, filhos na ordem original)."""
    return list(_percorrer(raiz))


def calcular_totais(raiz: TreeNode) -> dict[int, Decimal]:
    """Custo total de TODOS os nós da subárvore em uma passada O(n), sem recursão.

    Percorre do fim da pré-ordem para o início, garantindo que os filhos já foram calculados.
    A chave é `id(no)` (identidade do objeto), pois ids de domínio podem se repetir em árvores de teste.
    """
    totais: dict[int, Decimal] = {}
    for no in reversed(_pre_ordem(raiz)):
        unitario = no.preco + sum((totais[id(f)] for f in no.filhos), Decimal("0"))
        totais[id(no)] = no.quantidade * unitario
    return totais


def custo_unitario(no: TreeNode) -> Decimal:
    """Custo de UMA unidade do nó: preço próprio + custo total dos filhos."""
    totais = calcular_totais(no)
    return no.preco + sum((totais[id(f)] for f in no.filhos), Decimal("0"))


def calcular_total(no: TreeNode) -> Decimal:
    """Custo da subárvore considerando a quantidade do próprio nó."""
    return calcular_totais(no)[id(no)]


def explodir(no: TreeNode, nivel: int = 0) -> list[tuple[int, TreeNode, Decimal]]:
    """Explosão em pré-ordem: lista de (nível, nó, custo_total_da_subárvore). O(n), iterativa."""
    totais = calcular_totais(no)
    linhas: list[tuple[int, TreeNode, Decimal]] = []
    pilha = [(no, nivel)]
    while pilha:
        atual, n = pilha.pop()
        linhas.append((n, atual, totais[id(atual)]))
        pilha.extend((f, n + 1) for f in reversed(atual.filhos))
    return linhas


def simular_producao(raiz: TreeNode, unidades: int, limite_quantidade: int | None = None) -> list[LinhaSimulacao]:
    """Quantidade necessária de cada nó para `unidades` do produto e seu custo de aquisição.

    quantidade_total(n) = unidades × produto das quantidades da raiz até n.
    custo(n)            = quantidade_total(n) × preço próprio de n.
    A soma dos custos é igual a `unidades × calcular_total(raiz)`.
    Com `limite_quantidade`, interrompe com LimiteExcedidoError antes de acumular números absurdos.
    """
    if unidades <= 0:
        raise ValueError("unidades deve ser maior que zero")

    linhas: list[LinhaSimulacao] = []
    pilha: list[tuple[TreeNode, int, tuple[str, ...]]] = [(raiz, unidades, ())]
    while pilha:
        no, multiplicador, caminho = pilha.pop()
        total = multiplicador * no.quantidade
        if limite_quantidade is not None and total > limite_quantidade:
            raise LimiteExcedidoError(f"A quantidade necessária de '{no.nome}' excede o limite de {limite_quantidade}")
        caminho = caminho + (no.nome,)
        linhas.append(LinhaSimulacao(no.id, no.nome, caminho, total, total * no.preco))
        pilha.extend((f, total, caminho) for f in reversed(no.filhos))
    return linhas


def atualizar_preco(raiz: TreeNode, id_: int, novo_preco: Dinheiro) -> list[PassoImplosao]:
    """Atualiza o preço do nó e executa a implosão (do nó até a raiz).

    Retorna um passo por nó afetado, na ordem nó → raiz, com custo antes/depois.
    """
    preco = _para_decimal(novo_preco, "preço")
    if preco < 0:
        raise ValueError("preço não pode ser negativo")
    no = localizar(raiz, id_)

    caminho: list[TreeNode] = []
    atual: Optional[TreeNode] = no
    while atual is not None:
        caminho.append(atual)
        atual = atual.pai
    antes = calcular_totais(raiz)
    no.preco = preco
    depois = calcular_totais(raiz)
    return [PassoImplosao(n.id, n.nome, antes[id(n)], depois[id(n)]) for n in caminho]


# ------------------------------------------------------------------------ saída
def formatar_brl(valor: Decimal) -> str:
    """Formata como moeda brasileira: 8080 -> 'R$ 8.080,00'."""
    texto = f"{valor.quantize(CENTAVOS):,.2f}"
    return "R$ " + texto.replace(",", "X").replace(".", ",").replace("X", ".")


def linhas_arvore(no: TreeNode) -> list[str]:
    return [
        f"{'    ' * n}{'└── ' if n else ''}{item.nome} "
        f"(qtd: {item.quantidade}, preço: {formatar_brl(item.preco)}, "
        f"subtotal: {formatar_brl(sub)})"
        for n, item, sub in explodir(no)
    ]


def print_tree(no: TreeNode, saida: Callable[[str], None] = print) -> None:
    """Imprime a árvore indentada com quantidade, preço e custo da subárvore."""
    for linha in linhas_arvore(no):
        saida(linha)
