import { useCallback, useEffect, useRef, useState, type PointerEvent as ReactPointerEvent, type RefObject } from "react";

export interface View {
  x: number;
  y: number;
  k: number;
}

const K_MIN = 0.25;
const K_MAX = 1.8;
const clamp = (v: number, a: number, b: number) => Math.min(b, Math.max(a, v));

/**
 * Zoom/pan do canvas da árvore. A transformação é aplicada por CSS em um único
 * elemento que nenhuma biblioteca de animação toca.
 * - arrastar o fundo: pan · roda: pan · ctrl+roda (ou pinça): zoom no cursor
 */
export function usePanZoom(viewport: RefObject<HTMLElement | null>) {
  const [view, setView] = useState<View>({ x: 0, y: 0, k: 1 });
  const arrasto = useRef<{ x: number; y: number } | null>(null);

  const zoomPara = useCallback((fator: number, cx?: number, cy?: number) => {
    const el = viewport.current;
    const px = cx ?? (el ? el.clientWidth / 2 : 0);
    const py = cy ?? (el ? el.clientHeight / 2 : 0);
    setView((v) => {
      const k = clamp(v.k * fator, K_MIN, K_MAX);
      const r = k / v.k;
      return { k, x: px - (px - v.x) * r, y: py - (py - v.y) * r };
    });
  }, [viewport]);

  const fit = useCallback(
    (largura: number, altura: number) => {
      const el = viewport.current;
      if (!el || el.clientWidth === 0 || el.clientHeight === 0) return;
      const margem = 48;
      const topo = 56; // reserva espaço para a barra de ferramentas sobreposta
      const movel = el.clientWidth < 640;
      // No celular não encolhe até ficar ilegível: mantém zoom mínimo de 0,6 e começa na raiz (arraste para o resto).
      const kMin = movel ? 0.6 : K_MIN;
      const k = clamp(Math.min((el.clientWidth - margem) / largura, (el.clientHeight - margem - topo) / altura), kMin, 1);
      const x = movel && largura * k > el.clientWidth ? 16 : (el.clientWidth - largura * k) / 2;
      setView({ k, x, y: topo + (el.clientHeight - topo - altura * k) / 2 });
    },
    [viewport],
  );

  /** Garante que um retângulo (em coordenadas do mundo) esteja visível, sem alterar o zoom. */
  const revelar = useCallback(
    (x: number, y: number, w: number, h: number) => {
      const el = viewport.current;
      if (!el || el.clientWidth === 0) return;
      setView((v) => {
        const m = 24;
        let nx = v.x;
        let ny = v.y;
        if (x * v.k + v.x < m) nx = m - x * v.k;
        else if ((x + w) * v.k + v.x > el.clientWidth - m) nx = el.clientWidth - m - (x + w) * v.k;
        if (y * v.k + v.y < m) ny = m - y * v.k;
        else if ((y + h) * v.k + v.y > el.clientHeight - m) ny = el.clientHeight - m - (y + h) * v.k;
        return nx === v.x && ny === v.y ? v : { ...v, x: nx, y: ny };
      });
    },
    [viewport],
  );

  useEffect(() => {
    const el = viewport.current;
    if (!el) return;
    const onWheel = (e: WheelEvent) => {
      e.preventDefault();
      if (e.ctrlKey || e.metaKey) {
        const r = el.getBoundingClientRect();
        zoomPara(Math.exp(-e.deltaY * 0.01), e.clientX - r.left, e.clientY - r.top);
      } else {
        setView((v) => ({ ...v, x: v.x - e.deltaX, y: v.y - e.deltaY }));
      }
    };
    el.addEventListener("wheel", onWheel, { passive: false });
    return () => el.removeEventListener("wheel", onWheel);
  }, [viewport, zoomPara]);

  const handlers = {
    onPointerDown: (e: ReactPointerEvent<HTMLElement>) => {
      if (e.button !== 0 || (e.target as HTMLElement).closest("[data-node-id],[data-no-pan]")) return;
      arrasto.current = { x: e.clientX, y: e.clientY };
      e.currentTarget.setPointerCapture?.(e.pointerId);
    },
    onPointerMove: (e: ReactPointerEvent<HTMLElement>) => {
      const a = arrasto.current;
      if (!a) return;
      const dx = e.clientX - a.x;
      const dy = e.clientY - a.y;
      arrasto.current = { x: e.clientX, y: e.clientY };
      setView((v) => ({ ...v, x: v.x + dx, y: v.y + dy }));
    },
    onPointerUp: () => {
      arrasto.current = null;
    },
  };

  return { view, zoomPara, fit, revelar, handlers };
}
