import { AnimatePresence, motion } from "motion/react";
import { CheckCircle2, Info, XCircle } from "lucide-react";
import { createContext, useCallback, useContext, useMemo, useRef, useState, type ReactNode } from "react";
import { toastVariants } from "@/lib/animations/variants";
import { cn } from "@/lib/cn";

type Tipo = "success" | "error" | "info";
interface ToastItem {
  id: number;
  tipo: Tipo;
  texto: string;
}

interface ToastApi {
  success: (texto: string) => void;
  error: (texto: string) => void;
  info: (texto: string) => void;
}

const Ctx = createContext<ToastApi | null>(null);

export function useToast(): ToastApi {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error("useToast fora do ToastProvider");
  return ctx;
}

const icones = { success: CheckCircle2, error: XCircle, info: Info } as const;
const cores = { success: "text-good", error: "text-bad", info: "text-accent2" } as const;

export function ToastProvider({ children }: { children: ReactNode }) {
  const [itens, setItens] = useState<ToastItem[]>([]);
  const proximo = useRef(1);

  const adicionar = useCallback((tipo: Tipo, texto: string) => {
    const id = proximo.current++;
    setItens((atual) => [...atual.slice(-3), { id, tipo, texto }]);
    setTimeout(() => setItens((atual) => atual.filter((t) => t.id !== id)), 4500);
  }, []);

  const api = useMemo<ToastApi>(
    () => ({
      success: (t) => adicionar("success", t),
      error: (t) => adicionar("error", t),
      info: (t) => adicionar("info", t),
    }),
    [adicionar],
  );

  return (
    <Ctx.Provider value={api}>
      {children}
      <div
        role="status"
        aria-live="polite"
        className="pointer-events-none fixed bottom-4 right-4 z-[60] flex w-[min(92vw,22rem)] flex-col gap-2"
      >
        <AnimatePresence initial={false}>
          {itens.map((t) => {
            const Icone = icones[t.tipo];
            return (
              <motion.div
                key={t.id}
                layout
                variants={toastVariants}
                initial="hidden"
                animate="show"
                exit="exit"
                className="glass-strong pointer-events-auto flex items-start gap-3 rounded-xl px-4 py-3 text-sm"
              >
                <Icone size={18} className={cn("mt-0.5 shrink-0", cores[t.tipo])} aria-hidden />
                <span>{t.texto}</span>
              </motion.div>
            );
          })}
        </AnimatePresence>
      </div>
    </Ctx.Provider>
  );
}
