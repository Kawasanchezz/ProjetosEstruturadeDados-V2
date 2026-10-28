import { gsap, prefersReducedMotion, ScrollTrigger } from "./gsap";

/** Intro do hero: stagger dos textos, desenho da árvore decorativa e flutuação dos orbs. */
export function playHeroIntro(scope: Element) {
  if (prefersReducedMotion()) return;
  const tl = gsap.timeline({ defaults: { ease: "power3.out" } });
  tl.from("[data-hero-item]", { y: 28, opacity: 0, duration: 0.8, stagger: 0.12 });

  const linhas = scope.querySelectorAll<SVGPathElement>("[data-hero-line]");
  linhas.forEach((l) => {
    const len = l.getTotalLength();
    gsap.set(l, { strokeDasharray: len, strokeDashoffset: len });
  });
  tl.to(linhas, { strokeDashoffset: 0, duration: 0.9, stagger: 0.08, ease: "power2.inOut" }, 0.3);
  tl.from("[data-hero-dot]", { scale: 0, transformOrigin: "50% 50%", duration: 0.5, stagger: 0.07, ease: "back.out(2)" }, 0.5);

  gsap.to("[data-orb]", { y: "+=24", duration: 5, ease: "sine.inOut", yoyo: true, repeat: -1, stagger: 1.2 });
}

/** ScrollTrigger: parallax dos orbs (scrub). Recalcula os gatilhos quando o layout muda após o carregamento. */
export function setupScrollEffects(scope: Element): (() => void) | undefined {
  if (prefersReducedMotion()) return;
  gsap.to("[data-parallax]", {
    yPercent: -35,
    ease: "none",
    scrollTrigger: { trigger: scope, start: "top top", end: "bottom top", scrub: true },
  });

  let timer: ReturnType<typeof setTimeout>;
  const observer = new ResizeObserver(() => {
    clearTimeout(timer);
    timer = setTimeout(() => ScrollTrigger.refresh(), 150);
  });
  observer.observe(scope);
  return () => {
    clearTimeout(timer);
    observer.disconnect();
  };
}
