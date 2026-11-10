import { gsap, prefersReducedMotion } from "./gsap";

/** Prepara os conectores SVG para serem "desenhados" (stroke-dashoffset). */
function prepararLinhas(scope: Element): SVGPathElement[] {
  const paths = Array.from(scope.querySelectorAll<SVGPathElement>("[data-edge]"));
  paths.forEach((p) => {
    const len = p.getTotalLength?.() ?? 0;
    gsap.set(p, { strokeDasharray: len, strokeDashoffset: len });
  });
  return paths;
}

export function desenharLinhas(paths: SVGPathElement[], tl: gsap.core.Timeline, at: number | string = 0) {
  if (!paths.length) return;
  tl.to(
    paths,
    {
      strokeDashoffset: 0,
      duration: 0.7,
      ease: "power2.out",
      stagger: { amount: 0.6 },
      clearProps: "strokeDasharray,strokeDashoffset", // o `d` volta a ser livre para o Framer Motion
    },
    at,
  );
}

/**
 * Intro da árvore (GSAP).
 *  - explode=true: os nós partem do centro, se afastam e se organizam no layout (efeito de explosão)
 *  - em seguida as linhas conectoras são desenhadas
 * Anima apenas o elemento interno [data-node-inner]; o posicionamento fica com o Framer Motion.
 */
export function playTreeIntro(scope: Element, center: { x: number; y: number }, explode: boolean) {
  if (prefersReducedMotion()) return null;
  const inners = Array.from(scope.querySelectorAll<HTMLElement>("[data-node-inner]"));
  const paths = prepararLinhas(scope);
  const tl = gsap.timeline();

  if (explode && inners.length) {
    inners.forEach((el) => {
      const x = Number(el.dataset.x);
      const y = Number(el.dataset.y);
      gsap.set(el, {
        x: center.x - x,
        y: center.y - y,
        scale: 0.2,
        opacity: 0,
        rotate: gsap.utils.random(-25, 25),
      });
    });
    tl.to(inners, {
      x: 0,
      y: 0,
      scale: 1,
      opacity: 1,
      rotate: 0,
      duration: 1.1,
      ease: "power3.out",
      stagger: { amount: 0.5, from: "start" },
      clearProps: "transform,opacity",
    });
    desenharLinhas(paths, tl, "-=0.5");
  } else {
    desenharLinhas(paths, tl);
  }
  return tl;
}
