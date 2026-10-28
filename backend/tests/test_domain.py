"""Domínio puro: Testes 1 e 2 do enunciado, simulação, implosão e erros de construção."""
from decimal import Decimal

import pytest

from app.domain import (
    CicloError, ItemFormula, NoNaoEncontradoError, PaiAmbiguoError, PaiInexistenteError, RaizInvalidaError,
    RegistroNo, atualizar_preco, build_tree, build_tree_por_id, calcular_total, explodir, localizar,
    print_tree, profundidade, simular_producao,
)
from app.domain.samples import CANETA, COMPUTADOR
from app.domain.tree import formatar_brl

D = Decimal


def test_total_caneta():
    assert calcular_total(build_tree(CANETA)) == D("7.50")


def test_total_computador():
    assert calcular_total(build_tree(COMPUTADOR)) == D("8080.00")


def test_profundidade_e_subtotal():
    pc = build_tree(COMPUTADOR)
    assert profundidade(build_tree(CANETA)) == 3 and profundidade(pc) == 4
    assert calcular_total(localizar(pc, 2)) == D("2050.00")  # Placa-Mãe, com 2 pentes de RAM


def test_print_tree_e_formatacao():
    saida = []
    print_tree(build_tree(CANETA), saida.append)
    assert len(saida) == 10 and "R$ 7,50" in saida[0] and saida[2].startswith("        └── Pigmento")
    assert formatar_brl(D("8080")) == "R$ 8.080,00"


def test_simulacao_10_canetas_e_5_computadores():
    linhas = simular_producao(build_tree(CANETA), 10)
    assert all(l.quantidade_total == 10 for l in linhas) and sum(l.custo for l in linhas) == D("75.00")
    pcs = simular_producao(build_tree(COMPUTADOR), 5)
    ram = next(l for l in pcs if l.nome == "Memória RAM")
    assert ram.quantidade_total == 10 and ram.custo == D("2000.00")
    assert sum(l.custo for l in pcs) == D("40400.00")


def test_implosao_mudar_plastico():
    caneta = build_tree(CANETA)
    passos = atualizar_preco(caneta, 5, "1.70")  # Plástico do Corpo
    assert [p.id for p in passos] == [5, 4, 0] and all(p.diferenca == D("1.00") for p in passos)
    assert calcular_total(caneta) == D("8.50") and localizar(caneta, 8).preco == D("0.50")


def test_nomes_repetidos_sao_nos_distintos():
    caneta = build_tree(CANETA)
    plasticos = [localizar(caneta, i) for i in range(10) if localizar(caneta, i).nome == "Plástico"]
    assert {p.pai.nome for p in plasticos} == {"Corpo", "Tampa"}


def test_erros_de_construcao():
    with pytest.raises(PaiInexistenteError):
        build_tree([ItemFormula("A", None, 1, "1"), ItemFormula("B", "X", 1, "1")])
    with pytest.raises(PaiAmbiguoError):
        build_tree([ItemFormula("A", None, 1, "1"), ItemFormula("X", "A", 1, "1"), ItemFormula("X", "A", 1, "1"), ItemFormula("Y", "X", 1, "1")])
    with pytest.raises(CicloError):
        build_tree([ItemFormula("A", "B", 1, "1"), ItemFormula("B", "A", 1, "1")])
    with pytest.raises(CicloError):
        build_tree([ItemFormula("R", None, 1, "1"), ItemFormula("A", "A", 1, "1")])
    with pytest.raises(RaizInvalidaError):
        build_tree([ItemFormula("A", None, 1, "1"), ItemFormula("B", None, 1, "1")])
    with pytest.raises(NoNaoEncontradoError):
        atualizar_preco(build_tree(CANETA), 999, "1")


@pytest.mark.parametrize("nome,qtd,preco", [("", 1, "1"), ("A", 0, "1"), ("A", 1, "-0.01"), ("A", 1, "abc")])
def test_item_formula_valida_entrada(nome, qtd, preco):
    with pytest.raises(ValueError):
        ItemFormula(nome, None, qtd, preco)


def test_build_por_id_resolve_pelo_id():
    raiz = build_tree_por_id([
        RegistroNo(10, "Raiz", None, 1, "1"), RegistroNo(11, "Igual", 10, 1, "1"),
        RegistroNo(12, "Outro", 10, 1, "1"), RegistroNo(13, "Igual", 12, 1, "1"),
    ])
    assert localizar(raiz, 13).pai.id == 12 and calcular_total(raiz) == D("4")


def test_arvore_profundissima_nao_estoura_a_recursao():
    """5.000 níveis: a recursão ingênua passaria do limite do Python (~1.000)."""
    n = 5_000
    regs = [RegistroNo(1, "n1", None, 1, D("1"))] + [RegistroNo(i, f"n{i}", i - 1, 1, D("1")) for i in range(2, n + 1)]
    arvore = build_tree_por_id(regs)
    assert profundidade(arvore) == n and calcular_total(arvore) == n
    assert len(explodir(arvore)) == n and len(simular_producao(arvore, 3)) == n
