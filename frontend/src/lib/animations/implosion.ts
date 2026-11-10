import { gsap, prefersReducedMotion } from "./gsap";

export interface ImplosionOptions {
  /** Contêiner que tem a árvore ([data-node-id]) e a escada de resultados ([data-ladder-row]). */
  scope: Element;
  /** Ids do nó alterado até a raiz. */
  ids: number[];
  /** Chamado quando cada degrau "recebe" o novo valor (o React atualiza o valor e o count-up roda). */
  onStep: (indice: number) => void;
  onComplete: () => void;
}

const PASSO = 0.85;

/**
 * Implosão: um pulso sobe do nó alterado até a raiz, nó a nó.
 * Em cada degrau: anel de pulso na árvore, "pop" do card, entrada da linha na escada
 * e atualização do valor. Usa só transform/opacity.
 */
export function playImplosion({ scope, ids, onStep, onComplete }: ImplosionOptions): gsap.core.Timeline | null {
  const linhas = scope.querySelectorAll<HTMLElement>("[data-ladder-row]");
  if (prefersReducedMotion()) {
    gsap.set(linhas, { opacity: 1, y: 0 });
    ids.forEach((_, i) => onStep(i));
    onComplete();
    return null;
  }

  const tl = gsap.timeline({ onComplete });
  gsap.set(linhas, { opacity: 0, y: 14 });
  gsap.set(scope.querySelectorAll("[data-ladder-link]"), { scaleY: 0, transformOrigin: "50% 100%" });

  ids.forEach((id, i) => {
    const t = i * PASSO;
    const inner = scope.querySelector<HTMLElement>(`[data-node-id="${id}"] [data-node-inner]`);
    const anel = scope.querySelector<HTMLElement>(`[data-node-id="${id}"] [data-pulse-ring]`);
    const linha = scope.querySelector<HTMLElement>(`[data-ladder-row="${i}"]`);
    const ligacao = scope.querySelector<HTMLElement>(`[data-ladder-link="${i}"]`);

    if (ligacao) tl.to(ligacao, { scaleY: 1, duration: 0.25, ease: "none" }, t);
    if (anel) {
      tl.fromTo(anel, { scale: 1, opacity: 0.9 }, { scale: 1.35, opacity: 0, duration: 0.7, ease: "power2.out" }, t);
    }
    if (inner) {
      tl.to(inner, { scale: 1.07, duration: 0.2, yoyo: true, repeat: 1, ease: "power2.inOut" }, t);
    }
    tl.call(() => onStep(i), [], t + 0.2);
    if (linha) tl.to(linha, { opacity: 1, y: 0, duration: 0.4, ease: "power3.out" }, t + 0.1);
  });
  tl.to({}, { duration: 0.4 }); // respiro final
  return tl;
}
