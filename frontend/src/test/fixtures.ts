import type { NoExplosao } from "@/api/types";

type Spec = [id: number, nome: string, qtd: number, preco: string, filhos?: Spec[]];
const centavos = (v: string) => Math.round(Number(v) * 100);
const texto = (c: number) => (c / 100).toFixed(2);

/** Monta uma árvore de explosão com os mesmos custos do backend (centavos inteiros). */
function montar(spec: Spec, parent: number | null): { no: NoExplosao; total: number } {
  const [id, nome, quantidade, preco, filhosSpec = []] = spec;
  const filhos = filhosSpec.map((s) => montar(s, id));
  const unitario = centavos(preco) + filhos.reduce((a, f) => a + f.total, 0);
  const total = quantidade * unitario;
  return {
    total,
    no: { id, nome, parent_id: parent, quantidade, preco, custo_unitario: texto(unitario), custo_total: texto(total), filhos: filhos.map((f) => f.no) },
  };
}

const CANETA_SPEC: Spec = [
  1, "Caneta", 1, "2.50",
  [
    [2, "Tinta", 1, "0.80", [[3, "Pigmento", 1, "0.30"], [4, "Solvente", 1, "0.50"]]],
    [5, "Corpo", 1, "1.00", [[6, "Plástico", 1, "0.70"], [7, "Clip", 1, "0.30"]]],
    [8, "Tampa", 1, "0.70", [[9, "Plástico", 1, "0.50"], [10, "Vedação", 1, "0.20"]]],
  ],
];

export const caneta = (): NoExplosao => montar(CANETA_SPEC, null).no;
