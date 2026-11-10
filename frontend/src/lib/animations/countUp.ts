import { formatBRL } from "@/lib/money";
import { gsap, prefersReducedMotion } from "./gsap";

/**
 * Count-up de valor monetário (GSAP). Escreve o texto direto no elemento
 * e termina sempre no valor exato (string), sem erro de ponto flutuante.
 */
export function animateNumber(el: HTMLElement, from: string, to: string, duration = 0.9): gsap.core.Tween | null {
  if (prefersReducedMotion() || from === to) {
    el.textContent = formatBRL(to);
    return null;
  }
  const proxy = { v: Number(from) };
  return gsap.to(proxy, {
    v: Number(to),
    duration,
    ease: "power2.out",
    onUpdate: () => {
      el.textContent = formatBRL(proxy.v);
    },
    onComplete: () => {
      el.textContent = formatBRL(to);
    },
  });
}
