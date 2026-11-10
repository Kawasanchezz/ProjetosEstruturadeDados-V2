import { describe, expect, it } from "vitest";
import { caneta } from "@/test/fixtures";
import { applyOverrides, colapsoInicial, countNodes, pathTo, profundidade, searchMatches } from "./tree";
import { layoutTree, NODE_H, NODE_W } from "./treeLayout";

describe("helpers da árvore", () => {
  const raiz = caneta();

  it("conta nós, profundidade e custo", () => {
    expect([countNodes(raiz), profundidade(raiz), raiz.custo_total]).toEqual([10, 3, "7.50"]);
  });

  it("distingue os dois 'Plástico' pelo caminho", () => {
    expect(pathTo(raiz, 6).map((n) => n.nome)).toEqual(["Caneta", "Corpo", "Plástico"]);
    expect(pathTo(raiz, 9).map((n) => n.nome)).toEqual(["Caneta", "Tampa", "Plástico"]);
    expect(pathTo(raiz, 999)).toEqual([]);
  });

  it("busca ignora acentos e caixa; árvores pequenas começam expandidas", () => {
    expect([...searchMatches(raiz, "PLASTICO")].sort()).toEqual([6, 9]);
    expect(colapsoInicial(raiz).size).toBe(0);
  });

  it("overrides não mutam a árvore original", () => {
    const nova = applyOverrides(raiz, { costs: { 1: "8.50" }, prices: { 6: "1.70" } });
    expect([nova.custo_total, nova.filhos[1].filhos[0].preco, raiz.custo_total]).toEqual(["8.50", "1.70", "7.50"]);
  });
});

describe("layoutTree", () => {
  it("posiciona por nível, centraliza o pai e esconde descendentes recolhidos", () => {
    const l = layoutTree(caneta(), new Set());
    const por = new Map(l.nodes.map((n) => [n.node.id, n]));
    expect(l.nodes).toHaveLength(10);
    expect(por.get(2)!.x).toBeGreaterThan(NODE_W);
    expect(por.get(1)!.y).toBe((por.get(2)!.y + por.get(8)!.y) / 2);
    const ys = l.nodes.filter((n) => n.depth === 2).map((n) => n.y).sort((a, b) => a - b);
    ys.slice(1).forEach((y, i) => expect(y - ys[i]).toBeGreaterThanOrEqual(NODE_H));
    expect(layoutTree(caneta(), new Set([2, 5])).nodes.map((n) => n.node.id)).toEqual([1, 2, 5, 8, 9, 10]);
  });
});
