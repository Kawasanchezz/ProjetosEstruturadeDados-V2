import { motion } from "motion/react";
import { Link } from "react-router-dom";
import type { ProdutoResumo } from "@/api/types";
import { cn } from "@/lib/cn";

interface Props {
  produtos: ProdutoResumo[];
  selecionadoId: number | undefined;
  /** Rota-base, ex.: "/explosao" → "/explosao/3". */
  base: string;
}

/** Seletor de produto em "pílulas"; o destaque desliza entre elas via layoutId. */
export function ProductPicker({ produtos, selecionadoId, base }: Props) {
  return (
    <nav aria-label="Produto" className="mb-6 flex flex-wrap gap-1.5 rounded-2xl bg-fg/5 p-1.5">
      {produtos.map((p) => {
        const ativo = p.id === selecionadoId;
        return (
          <Link
            key={p.id}
            to={`${base}/${p.id}`}
            aria-current={ativo ? "page" : undefined}
            className={cn(
              "relative rounded-xl px-4 py-2 text-sm font-semibold transition-colors",
              ativo ? "text-ink" : "text-muted hover:text-fg",
            )}
          >
            {ativo && (
              <motion.span
                layoutId={`picker-${base}`}
                className="absolute inset-0 rounded-xl bg-accent"
                transition={{ type: "spring", stiffness: 400, damping: 32 }}
              />
            )}
            <span className="relative">{p.nome}</span>
          </Link>
        );
      })}
    </nav>
  );
}
