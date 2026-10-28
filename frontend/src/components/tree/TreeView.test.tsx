import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { caneta } from "@/test/fixtures";
import { TreeView } from "./TreeView";

// Animações mockadas: o teste verifica estrutura e comportamento, não movimento.
vi.mock("@/lib/animations/tree", () => ({ playTreeIntro: vi.fn(() => null), desenharLinhas: vi.fn() }));
vi.mock("@/lib/animations/countUp", async () => {
  const { formatBRL } = await import("@/lib/money");
  return { animateNumber: (el: HTMLElement, _de: string, para: string) => ((el.textContent = formatBRL(para)), null) };
});

const renderizar = (props: Partial<React.ComponentProps<typeof TreeView>> = {}) =>
  render(<TreeView raiz={caneta()} selectedId={null} onSelect={() => {}} {...props} />);

describe("TreeView", () => {
  it("renderiza a árvore acessível com todos os nós", () => {
    renderizar();
    const arvore = screen.getByRole("tree", { name: /árvore de componentes/i });
    expect(within(arvore).getAllByRole("treeitem")).toHaveLength(10);
    expect(within(arvore).getAllByText("Plástico")).toHaveLength(2);
    const raiz = screen.getByRole("treeitem", { name: /^Caneta/ });
    expect(raiz).toHaveAttribute("aria-expanded", "true");
    expect(within(raiz).getByText("R$ 7,50")).toBeInTheDocument();
  });

  it("recolhe, expande e seleciona", async () => {
    const user = userEvent.setup();
    const onSelect = vi.fn();
    renderizar({ onSelect });
    await user.click(screen.getByRole("button", { name: "Recolher Tinta" }));
    await waitFor(() => expect(screen.queryByText("Pigmento")).not.toBeInTheDocument());
    await user.click(screen.getByRole("button", { name: "Expandir Tinta" }));
    expect(await screen.findByText("Pigmento")).toBeInTheDocument();
    await user.click(screen.getByRole("treeitem", { name: /^Corpo/ }));
    expect(onSelect).toHaveBeenCalledWith(5);
  });

  it("navega por teclado", async () => {
    const user = userEvent.setup();
    const onSelect = vi.fn();
    renderizar({ onSelect });
    screen.getByRole("treeitem", { name: /^Caneta/ }).focus();
    await user.keyboard("{ArrowDown}");
    await waitFor(() => expect(screen.getByRole("treeitem", { name: /^Tinta/ })).toHaveFocus());
    await user.keyboard("{Enter}");
    expect(onSelect).toHaveBeenCalledWith(2);
  });

  it("nomes maliciosos são renderizados como texto puro (XSS)", () => {
    const raiz = caneta();
    raiz.nome = '<img src=x onerror="alert(1)">';
    const { container } = renderizar({ raiz });
    expect(container.querySelector("img")).toBeNull();
    expect(screen.getByText(raiz.nome)).toBeInTheDocument();
  });
});
