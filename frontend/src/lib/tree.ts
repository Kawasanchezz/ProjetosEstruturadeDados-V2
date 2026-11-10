import type { NoExplosao } from "@/api/types";

export function flatten(raiz: NoExplosao): NoExplosao[] {
  const out: NoExplosao[] = [];
  const visitar = (n: NoExplosao) => {
    out.push(n);
    n.filhos.forEach(visitar);
  };
  visitar(raiz);
  return out;
}

export const countNodes = (raiz: NoExplosao) => flatten(raiz).length;

export function profundidade(raiz: NoExplosao): number {
  return 1 + Math.max(0, ...raiz.filhos.map(profundidade));
}

/** Caminho da raiz até o nó (inclusive); [] se não existir. */
export function pathTo(raiz: NoExplosao, id: number): NoExplosao[] {
  if (raiz.id === id) return [raiz];
  for (const filho of raiz.filhos) {
    const sub = pathTo(filho, id);
    if (sub.length) return [raiz, ...sub];
  }
  return [];
}

export const findNode = (raiz: NoExplosao, id: number) => flatten(raiz).find((n) => n.id === id);

export const normalizar = (s: string) =>
  s.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase().trim();

export function searchMatches(raiz: NoExplosao, consulta: string): Set<number> {
  const q = normalizar(consulta);
  if (!q) return new Set();
  return new Set(flatten(raiz).filter((n) => normalizar(n.nome).includes(q)).map((n) => n.id));
}

/** Árvores grandes começam recolhidas a partir do nível 2 (colapso inicial). */
export const LIMITE_COLAPSO = 60;
export function colapsoInicial(raiz: NoExplosao): Set<number> {
  if (countNodes(raiz) <= LIMITE_COLAPSO) return new Set();
  const ids = new Set<number>();
  const visitar = (n: NoExplosao, nivel: number) => {
    if (nivel >= 2 && n.filhos.length) ids.add(n.id);
    n.filhos.forEach((f) => visitar(f, nivel + 1));
  };
  visitar(raiz, 0);
  return ids;
}

export interface Overrides {
  costs?: Record<number, string>;
  prices?: Record<number, string>;
}

/** Cópia da árvore com custos/preços substituídos (usado para animar a implosão degrau a degrau). */
export function applyOverrides(raiz: NoExplosao, o: Overrides): NoExplosao {
  const visitar = (n: NoExplosao): NoExplosao => ({
    ...n,
    custo_total: o.costs?.[n.id] ?? n.custo_total,
    preco: o.prices?.[n.id] ?? n.preco,
    filhos: n.filhos.map(visitar),
  });
  return visitar(raiz);
}
