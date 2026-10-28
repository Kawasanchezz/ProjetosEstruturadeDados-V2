import { AnimatePresence, motion } from "motion/react";
import { ChevronsDownUp, ChevronsUpDown, Maximize2, Sparkles, ZoomIn, ZoomOut } from "lucide-react";
import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState, type KeyboardEvent } from "react";
import type { NoExplosao } from "@/api/types";
import { IconButton } from "@/components/ui/Button";
import { usePanZoom } from "@/hooks/usePanZoom";
import { useGSAP } from "@/lib/animations/gsap";
import { playTreeIntro } from "@/lib/animations/tree";
import { cn } from "@/lib/cn";
import { colapsoInicial, flatten, pathTo } from "@/lib/tree";
import { layoutTree, NODE_H, NODE_W } from "@/lib/treeLayout";
import { TreeNodeCard } from "./TreeNodeCard";

interface Props {
  raiz: NoExplosao;
  selectedId: number | null;
  onSelect: (id: number) => void;
  /** Ids que casam com a busca; `query` ativa o destaque e o esmaecimento dos demais. */
  matches?: ReadonlySet<number>;
  query?: string;
  /** Ids destacados (ex.: caminho da implosão). */
  highlightIds?: ReadonlySet<number>;
  className?: string;
  label?: string;
}

/**
 * Árvore interativa.
 *  Framer Motion: posição dos cards (x/y), entrada/saída ao expandir/recolher, chevrons, linhas (opacity/d).
 *  GSAP: explosão inicial dos nós (elemento interno), desenho das linhas (stroke-dashoffset).
 */
export function TreeView({ raiz, selectedId, onSelect, matches, query = "", highlightIds, className, label = "Árvore de componentes" }: Props) {
  const viewport = useRef<HTMLDivElement>(null);
  const mundo = useRef<HTMLDivElement>(null);
  const itens = useRef(new Map<number, HTMLDivElement>());
  const [collapsed, setCollapsed] = useState<Set<number>>(() => colapsoInicial(raiz));
  const [focusedId, setFocusedId] = useState<number>(raiz.id);
  const [pronto, setPronto] = useState(false);
  const precisaEnquadrar = useRef(false);
  const { view, zoomPara, fit, revelar, handlers } = usePanZoom(viewport);

  const layout = useMemo(() => layoutTree(raiz, collapsed), [raiz, collapsed]);
  const porId = useMemo(() => new Map(layout.nodes.map((n) => [n.node.id, n])), [layout]);
  const buscando = query.trim().length > 0;

  // Busca: expande os ancestrais dos resultados para que fiquem visíveis.
  useEffect(() => {
    if (!matches?.size) return;
    setCollapsed((atual) => {
      const novo = new Set(atual);
      matches.forEach((id) => pathTo(raiz, id).slice(0, -1).forEach((n) => novo.delete(n.id)));
      return novo.size === atual.size ? atual : novo;
    });
  }, [matches, raiz]);

  useLayoutEffect(() => {
    fit(layout.width, layout.height);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [raiz.id]);

  useLayoutEffect(() => {
    if (!precisaEnquadrar.current) return;
    precisaEnquadrar.current = false;
    fit(layout.width, layout.height);
  }, [layout, fit]);

  useEffect(() => setPronto(true), []);

  const centro = { x: layout.width / 2 - NODE_W / 2, y: layout.height / 2 - NODE_H / 2 };
  const { contextSafe } = useGSAP(
    () => {
      if (mundo.current) playTreeIntro(mundo.current, centro, true);
    },
    { scope: mundo, dependencies: [raiz.id], revertOnUpdate: true },
  );
  const explodirNovamente = contextSafe(() => {
    if (mundo.current) playTreeIntro(mundo.current, centro, true);
  });

  const alternar = useCallback((id: number) => {
    setCollapsed((atual) => {
      const novo = new Set(atual);
      if (!novo.delete(id)) novo.add(id);
      return novo;
    });
  }, []);

  const focar = useCallback(
    (id: number) => {
      setFocusedId(id);
      const ln = porId.get(id);
      if (ln) revelar(ln.x, ln.y, NODE_W, NODE_H);
      requestAnimationFrame(() => itens.current.get(id)?.focus({ preventScroll: true }));
    },
    [porId, revelar],
  );

  const aoTeclar = (e: KeyboardEvent<HTMLDivElement>) => {
    const alvo = (e.target as HTMLElement).closest<HTMLElement>("[data-node-id]");
    if (!alvo) return;
    const id = Number(alvo.dataset.nodeId);
    const i = layout.nodes.findIndex((n) => n.node.id === id);
    const ln = layout.nodes[i];
    if (!ln) return;
    const irPara = (idx: number) => {
      const destino = layout.nodes[Math.min(Math.max(idx, 0), layout.nodes.length - 1)];
      focar(destino.node.id);
    };
    const temFilhos = ln.node.filhos.length > 0;
    switch (e.key) {
      case "ArrowDown": irPara(i + 1); break;
      case "ArrowUp": irPara(i - 1); break;
      case "Home": irPara(0); break;
      case "End": irPara(layout.nodes.length - 1); break;
      case "ArrowRight":
        if (temFilhos && ln.collapsed) alternar(id);
        else if (temFilhos) irPara(i + 1);
        break;
      case "ArrowLeft":
        if (temFilhos && !ln.collapsed) alternar(id);
        else if (ln.parentId !== null) focar(ln.parentId);
        break;
      case "Enter":
      case " ":
        onSelect(id);
        break;
      default:
        return;
    }
    e.preventDefault();
  };

  const expandirTudo = () => setCollapsed(new Set());
  const recolherTudo = () =>
    setCollapsed(new Set(flatten(raiz).filter((n) => n.filhos.length && n.id !== raiz.id).map((n) => n.id)));
  // Após expandir/recolher tudo, reenquadra assim que o novo layout existir.
  const reenquadrar = (fn: () => void) => () => {
    precisaEnquadrar.current = true;
    fn();
  };
  return (
    <div className={cn("glass relative overflow-hidden rounded-2xl", className)}>
      <div
        ref={viewport}
        role="tree"
        aria-label={label}
        className="relative h-[68vh] min-h-[420px] cursor-grab touch-none overflow-hidden active:cursor-grabbing"
        onKeyDown={aoTeclar}
        {...handlers}
      >
        <div
          ref={mundo}
          className="absolute left-0 top-0 origin-top-left"
          style={{ width: layout.width, height: layout.height, transform: `translate(${view.x}px, ${view.y}px) scale(${view.k})` }}
        >
          <svg className="pointer-events-none absolute left-0 top-0 overflow-visible" width={layout.width} height={layout.height} aria-hidden>
            <AnimatePresence initial={false}>
              {layout.edges.map((edge) => {
                const ligado = highlightIds?.has(edge.id) && highlightIds.has(porId.get(edge.id)?.parentId ?? -1);
                return (
                  <motion.path
                    key={edge.id}
                    data-edge={edge.id}
                    fill="none"
                    strokeWidth={ligado ? 3 : 2}
                    strokeLinecap="round"
                    stroke={ligado ? "rgb(var(--accent2))" : "rgb(var(--accent2) / 0.55)"}
                    initial={{ opacity: 0, d: edge.d }}
                    animate={{ opacity: buscando ? 0.35 : 1, d: edge.d }}
                    exit={{ opacity: 0 }}
                    transition={{ duration: 0.35 }}
                  />
                );
              })}
            </AnimatePresence>
          </svg>

          <AnimatePresence initial={false}>
            {layout.nodes.map((ln) => {
              const pai = ln.parentId === null ? null : porId.get(ln.parentId);
              const id = ln.node.id;
              return (
                <TreeNodeCard
                  key={id}
                  ln={ln}
                  selected={selectedId === id}
                  focusable={focusedId === id}
                  matched={matches?.has(id) ?? false}
                  dimmed={buscando && !(matches?.has(id) ?? false)}
                  highlighted={highlightIds?.has(id) ?? false}
                  origin={pronto && pai ? { x: pai.x, y: pai.y } : null}
                  query={query}
                  elRef={(el) => {
                    if (el) itens.current.set(id, el);
                    else itens.current.delete(id);
                  }}
                  onSelect={onSelect}
                  onToggle={alternar}
                  onFocusItem={setFocusedId}
                />
              );
            })}
          </AnimatePresence>
        </div>
      </div>

      <div data-no-pan className="absolute right-3 top-3 flex items-center gap-1 rounded-2xl bg-bg/60 p-1 backdrop-blur-md">
        <IconButton label="Explodir novamente" onClick={explodirNovamente}><Sparkles size={17} /></IconButton>
        <IconButton label="Expandir tudo" onClick={reenquadrar(expandirTudo)}><ChevronsUpDown size={17} /></IconButton>
        <IconButton label="Recolher tudo" onClick={reenquadrar(recolherTudo)}><ChevronsDownUp size={17} /></IconButton>
        <IconButton label="Aumentar zoom" onClick={() => zoomPara(1.25)}><ZoomIn size={17} /></IconButton>
        <IconButton label="Diminuir zoom" onClick={() => zoomPara(0.8)}><ZoomOut size={17} /></IconButton>
        <IconButton label="Ajustar à tela" onClick={() => fit(layout.width, layout.height)}><Maximize2 size={17} /></IconButton>
      </div>
      <p className="pointer-events-none absolute bottom-3 left-4 hidden text-xs text-muted sm:block">
        Arraste para mover · Ctrl + roda para zoom · setas para navegar
      </p>
    </div>
  );
}
