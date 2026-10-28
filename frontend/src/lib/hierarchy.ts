import type { No } from "@/api/types";

export interface NoNivel extends No {
  nivel: number;
}

/** Ordena a lista plana em pré-ordem (pai antes dos filhos) e informa o nível de cada item. */
export function ordenarHierarquico(itens: No[]): NoNivel[] {
  const filhos = new Map<number | null, No[]>();
  for (const n of itens) filhos.set(n.parent_id, [...(filhos.get(n.parent_id) ?? []), n]);
  const saida: NoNivel[] = [];
  const visitar = (pai: number | null, nivel: number) => {
    for (const n of filhos.get(pai) ?? []) {
      saida.push({ ...n, nivel });
      visitar(n.id, nivel + 1);
    }
  };
  visitar(null, 0);
  return saida;
}

/** Ids do nó e de todos os seus descendentes (não podem ser escolhidos como novo pai). */
export function idsDaSubarvore(itens: No[], id: number): Set<number> {
  const ids = new Set<number>([id]);
  let mudou = true;
  while (mudou) {
    mudou = false;
    for (const n of itens) {
      if (n.parent_id !== null && ids.has(n.parent_id) && !ids.has(n.id)) {
        ids.add(n.id);
        mudou = true;
      }
    }
  }
  return ids;
}
