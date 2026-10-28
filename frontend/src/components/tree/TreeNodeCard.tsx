import { motion } from "motion/react";
import { ChevronRight } from "lucide-react";
import { memo, type Ref } from "react";
import { Money } from "@/components/ui/Money";
import { spring } from "@/lib/animations/variants";
import { cn } from "@/lib/cn";
import { formatBRL } from "@/lib/money";
import { normalizar } from "@/lib/tree";
import { NODE_H, NODE_W, type LayoutNode } from "@/lib/treeLayout";

interface Props {
  ln: LayoutNode;
  selected: boolean;
  focusable: boolean;
  matched: boolean;
  dimmed: boolean;
  highlighted: boolean;
  /** Posição do pai: nós novos "nascem" dele ao expandir. null na carga inicial. */
  origin: { x: number; y: number } | null;
  query: string;
  elRef: Ref<HTMLDivElement>;
  onSelect: (id: number) => void;
  onToggle: (id: number) => void;
  onFocusItem: (id: number) => void;
}

/** Texto com o trecho buscado destacado — tudo renderizado como texto React (sem HTML injetado). */
function HighlightText({ text, query }: { text: string; query: string }) {
  const q = normalizar(query);
  if (!q) return <>{text}</>;
  const chars = Array.from(text);
  const norm = chars.map((c) => normalizar(c));
  if (norm.some((c) => c.length !== 1)) return <>{text}</>;
  const i = norm.join("").indexOf(q);
  if (i < 0) return <>{text}</>;
  return (
    <>
      {chars.slice(0, i).join("")}
      <mark className="rounded bg-accent2/30 px-0.5 text-fg">{chars.slice(i, i + q.length).join("")}</mark>
      {chars.slice(i + q.length).join("")}
    </>
  );
}

function TreeNodeCardBase({
  ln, selected, focusable, matched, dimmed, highlighted, origin, query, elRef, onSelect, onToggle, onFocusItem,
}: Props) {
  const { node, depth, collapsed } = ln;
  const temFilhos = node.filhos.length > 0;
  const hue = 262 - depth * 30;

  return (
    <motion.div
      ref={elRef}
      role="treeitem"
      aria-level={depth + 1}
      aria-posinset={ln.posinset}
      aria-setsize={ln.setsize}
      aria-expanded={temFilhos ? !collapsed : undefined}
      aria-selected={selected}
      aria-label={`${node.nome}, quantidade ${node.quantidade}, custo ${formatBRL(node.custo_total)}`}
      tabIndex={focusable ? 0 : -1}
      data-node-id={node.id}
      className="group absolute left-0 top-0 cursor-pointer outline-none"
      style={{ width: NODE_W, height: NODE_H }}
      initial={origin ? { x: origin.x, y: origin.y, opacity: 0, scale: 0.6 } : false}
      animate={{ x: ln.x, y: ln.y, opacity: dimmed ? 0.3 : 1, scale: 1 }}
      exit={{ opacity: 0, scale: 0.6, transition: { duration: 0.18 } }}
      transition={spring}
      onClick={() => onSelect(node.id)}
      onFocus={() => onFocusItem(node.id)}
    >
      {/* Elemento interno: pertence ao GSAP (explosão, pop da implosão). Nada aqui é animado pelo Framer. */}
      <div data-node-inner data-x={ln.x} data-y={ln.y} className="relative h-full w-full">
        <span
          data-pulse-ring
          aria-hidden
          className="pointer-events-none absolute inset-0 rounded-2xl opacity-0 ring-2 ring-accent2"
        />
        <div
          className={cn(
            "glass relative flex h-full flex-col justify-between rounded-2xl border-l-4 px-3.5 py-2.5 transition-shadow",
            "group-focus-visible:ring-2 group-focus-visible:ring-accent2",
            selected && "shadow-glow",
            highlighted && "ring-2 ring-accent2",
            matched && "ring-2 ring-accent2",
          )}
          style={{ borderLeftColor: `hsl(${hue} 85% 66%)` }}
        >
          <div className="flex items-start justify-between gap-2">
            <p className="truncate text-sm font-semibold" title={node.nome}>
              <HighlightText text={node.nome} query={query} />
            </p>
            {temFilhos && (
              <motion.button
                type="button"
                tabIndex={-1}
                aria-label={`${collapsed ? "Expandir" : "Recolher"} ${node.nome}`}
                className="-mr-1 -mt-0.5 flex shrink-0 items-center gap-0.5 rounded-lg px-1 py-0.5 text-xs text-muted hover:bg-fg/10 hover:text-fg"
                whileHover={{ scale: 1.1 }}
                whileTap={{ scale: 0.9 }}
                onClick={(e) => {
                  e.stopPropagation();
                  onToggle(node.id);
                }}
              >
                <span className="num">{node.filhos.length}</span>
                <motion.span animate={{ rotate: collapsed ? 0 : 90 }} transition={{ duration: 0.2 }} className="grid">
                  <ChevronRight size={14} />
                </motion.span>
              </motion.button>
            )}
          </div>
          <p className="num truncate text-xs text-muted">
            {node.quantidade} × {formatBRL(node.preco)}
          </p>
          <p className="text-sm font-semibold">
            <Money value={node.custo_total} />
          </p>
        </div>
      </div>
    </motion.div>
  );
}

export const TreeNodeCard = memo(TreeNodeCardBase);
