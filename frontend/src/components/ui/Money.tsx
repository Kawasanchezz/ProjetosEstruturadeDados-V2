import { useLayoutEffect, useRef } from "react";
import { animateNumber } from "@/lib/animations/countUp";
import { cn } from "@/lib/cn";
import { formatBRL } from "@/lib/money";

interface Props {
  value: string;
  className?: string;
  /** Faz o count-up a partir de zero na primeira renderização. */
  countOnMount?: boolean;
  duration?: number;
}

/** Valor monetário com count-up (GSAP) sempre que `value` muda. O texto é escrito via ref (nunca HTML). */
export function Money({ value, className, countOnMount = false, duration }: Props) {
  const ref = useRef<HTMLSpanElement>(null);
  const anterior = useRef<string | null>(countOnMount ? "0.00" : null);

  useLayoutEffect(() => {
    const el = ref.current;
    if (!el) return;
    const de = anterior.current;
    anterior.current = value;
    if (de === null || de === value) {
      el.textContent = formatBRL(value);
      return;
    }
    const tween = animateNumber(el, de, value, duration);
    return () => {
      tween?.kill();
    };
  }, [value, duration]);

  return <span ref={ref} className={cn("num", className)} />;
}
