import type { NoExplosao } from "@/api/types";

export const NODE_W = 236;
export const NODE_H = 88;
export const GAP_X = 80;
export const GAP_Y = 24;

export interface LayoutNode {
  node: NoExplosao;
  x: number;
  y: number;
  depth: number;
  parentId: number | null;
  posinset: number;
  setsize: number;
  collapsed: boolean;
}

export interface LayoutEdge {
  id: number; // id do filho
  d: string;
}

export interface TreeLayout {
  nodes: LayoutNode[]; // pré-ordem: serve também para a navegação por teclado
  edges: LayoutEdge[];
  width: number;
  height: number;
}

/** Layout em camadas (esquerda → direita): folhas ocupam linhas, o pai é centralizado nos filhos. */
export function layoutTree(raiz: NoExplosao, collapsed: ReadonlySet<number>): TreeLayout {
  const nodes: LayoutNode[] = [];
  const edges: LayoutEdge[] = [];
  const pos = new Map<number, { x: number; y: number }>();
  let linha = 0;
  let profundidadeMax = 0;

  const colocar = (
    n: NoExplosao,
    depth: number,
    parentId: number | null,
    posinset: number,
    setsize: number,
  ): number => {
    profundidadeMax = Math.max(profundidadeMax, depth);
    const recolhido = collapsed.has(n.id) && n.filhos.length > 0;
    const indice = nodes.length;
    nodes.push({ node: n, x: 0, y: 0, depth, parentId, posinset, setsize, collapsed: recolhido });

    let y: number;
    if (recolhido || n.filhos.length === 0) {
      y = linha++ * (NODE_H + GAP_Y);
    } else {
      const ys = n.filhos.map((f, i) => colocar(f, depth + 1, n.id, i + 1, n.filhos.length));
      y = (ys[0] + ys[ys.length - 1]) / 2;
    }
    const x = depth * (NODE_W + GAP_X);
    nodes[indice].x = x;
    nodes[indice].y = y;
    pos.set(n.id, { x, y });
    return y;
  };
  colocar(raiz, 0, null, 1, 1);

  for (const ln of nodes) {
    if (ln.parentId === null) continue;
    const p = pos.get(ln.parentId)!;
    const x1 = p.x + NODE_W;
    const y1 = p.y + NODE_H / 2;
    const x2 = ln.x;
    const y2 = ln.y + NODE_H / 2;
    const xm = (x1 + x2) / 2;
    edges.push({ id: ln.node.id, d: `M ${x1} ${y1} C ${xm} ${y1}, ${xm} ${y2}, ${x2} ${y2}` });
  }

  return {
    nodes,
    edges,
    width: (profundidadeMax + 1) * (NODE_W + GAP_X) - GAP_X,
    height: Math.max(linha, 1) * (NODE_H + GAP_Y) - GAP_Y,
  };
}
