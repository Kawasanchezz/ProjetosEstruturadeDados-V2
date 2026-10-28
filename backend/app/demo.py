"""Execução dos testes 1 e 2 com saída formatada: `python -m app.demo`."""
from decimal import Decimal

from app.domain import build_tree, calcular_total, print_tree, simular_producao
from app.domain.samples import CANETA, COMPUTADOR
from app.domain.tree import formatar_brl

CASOS = [
    ("Teste 1: Caneta", CANETA, Decimal("7.50"), 10),
    ("Teste 2: Computador", COMPUTADOR, Decimal("8080.00"), 5),
]


def executar() -> bool:
    ok = True
    for titulo, itens, esperado, unidades in CASOS:
        print(f"\n{'=' * 70}\n{titulo}\n{'=' * 70}")
        raiz = build_tree(itens)
        print_tree(raiz)
        total = calcular_total(raiz)
        passou = total == esperado
        ok &= passou
        print(f"\nTotal: {formatar_brl(total)} (esperado {formatar_brl(esperado)}) "
              f"[{'OK' if passou else 'FALHOU'}]")

        print(f"\nSimulação: {unidades} unidades")
        linhas = simular_producao(raiz, unidades)
        print(f"{'Componente':<28}{'Qtd total':>10}{'Custo':>18}")
        for linha in linhas:
            print(f"{'  ' * (len(linha.caminho) - 1) + linha.nome:<28}"
                  f"{linha.quantidade_total:>10}{formatar_brl(linha.custo):>18}")
        print(f"{'TOTAL':<38}{formatar_brl(sum((l.custo for l in linhas), Decimal(0))):>18}")
    return ok


if __name__ == "__main__":
    raise SystemExit(0 if executar() else 1)
