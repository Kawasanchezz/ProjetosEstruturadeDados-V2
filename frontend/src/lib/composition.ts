import type { LinhaSimulacao } from "@/api/types";

export interface FatiaCusto {
  nome: string;
  valor: number;
  percentual: number;
}

/**
 * Agrupa o custo da simulação pelos filhos diretos da raiz. O que o próprio produto
 * custa (montagem) aparece como "<produto> (montagem)". Itens sem custo ficam de fora.
 */
export function composicaoDeCusto(linhas: LinhaSimulacao[]): FatiaCusto[] {
  const grupos = new Map<string, number>();
  for (const l of linhas) {
    const grupo = l.caminho.length === 1 ? `${l.caminho[0]} (montagem)` : l.caminho[1];
    grupos.set(grupo, (grupos.get(grupo) ?? 0) + Number(l.custo));
  }
  const total = [...grupos.values()].reduce((a, b) => a + b, 0);
  return [...grupos.entries()]
    .filter(([, valor]) => valor > 0)
    .map(([nome, valor]) => ({ nome, valor, percentual: total > 0 ? (valor / total) * 100 : 0 }))
    .sort((a, b) => b.valor - a.valor);
}
