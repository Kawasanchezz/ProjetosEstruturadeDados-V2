import { AnimatePresence, motion } from "motion/react";
import { useEffect, useState } from "react";
import type { ProdutoResumo } from "@/api/types";
import { listContainer } from "@/lib/animations/variants";
import { cn } from "@/lib/cn";
import { ProductCard } from "./ProductCard";

/** Quantos cards cabem por "página" conforme a largura da tela. */
function usePageSize(): number {
  const calcular = () =>
    window.matchMedia("(min-width: 1536px)").matches ? 3 : window.matchMedia("(min-width: 640px)").matches ? 2 : 1;
  const [tamanho, setTamanho] = useState(calcular);
  useEffect(() => {
    const aoRedimensionar = () => setTamanho(calcular());
    window.addEventListener("resize", aoRedimensionar);
    return () => window.removeEventListener("resize", aoRedimensionar);
  }, []);
  return tamanho;
}

/** Cards de produto em páginas, com bolinhas de navegação embaixo. */
export function ProductCarousel({ produtos }: { produtos: ProdutoResumo[] }) {
  const porPagina = usePageSize();
  const [pedida, setPedida] = useState(0);
  const totalPaginas = Math.max(1, Math.ceil(produtos.length / porPagina));
  const pagina = Math.min(pedida, totalPaginas - 1); // a busca/redimensionamento pode reduzir o total
  const visiveis = produtos.slice(pagina * porPagina, (pagina + 1) * porPagina);

  return (
    <div>
      <AnimatePresence mode="wait" initial={false}>
        <motion.div
          key={`${pagina}-${porPagina}`}
          className="grid gap-4"
          style={{ gridTemplateColumns: `repeat(${porPagina}, minmax(0, 1fr))` }}
          variants={listContainer}
          initial="hidden"
          animate="show"
          exit="exit"
        >
          {visiveis.map((p, i) => (
            <ProductCard key={p.id} produto={p} indice={pagina * porPagina + i} />
          ))}
        </motion.div>
      </AnimatePresence>

      {totalPaginas > 1 && (
        <nav aria-label="Páginas de produtos" className="mt-4 flex items-center justify-center gap-2">
          {Array.from({ length: totalPaginas }, (_, i) => (
            <motion.button
              key={i}
              type="button"
              aria-label={`Página ${i + 1} de ${totalPaginas}`}
              aria-current={i === pagina ? "true" : undefined}
              onClick={() => setPedida(i)}
              animate={{ width: i === pagina ? 28 : 10 }}
              whileHover={{ scale: 1.15 }}
              whileTap={{ scale: 0.9 }}
              transition={{ type: "spring", stiffness: 420, damping: 30 }}
              className={cn("h-2.5 rounded-full transition-colors", i === pagina ? "bg-fg" : "bg-fg/20 hover:bg-fg/40")}
            />
          ))}
        </nav>
      )}
    </div>
  );
}
