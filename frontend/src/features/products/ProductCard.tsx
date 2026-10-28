import { motion } from "motion/react";
import { Calculator, Network, Package, Zap } from "lucide-react";
import { Link } from "react-router-dom";
import type { ProdutoResumo } from "@/api/types";
import { Money } from "@/components/ui/Money";
import { listItem } from "@/lib/animations/variants";

const TILES = ["bg-tile1", "bg-tile2", "bg-tile3"];

const acao =
  "inline-flex items-center gap-1.5 rounded-full bg-fg/5 px-3 py-1.5 text-xs font-semibold transition-colors hover:bg-accent hover:text-ink";

/** Card compacto de produto: custo total (count-up), componentes, níveis e atalhos. */
export function ProductCard({ produto, indice = 0 }: { produto: ProdutoResumo; indice?: number }) {
  return (
    <motion.article
      layout
      variants={listItem}
      whileHover={{ y: -4 }}
      className="card flex flex-col gap-4 rounded-3xl p-4"
      aria-label={produto.nome}
    >
      <div className="flex items-center gap-3">
        <span className={`grid h-11 w-11 shrink-0 place-items-center rounded-2xl ${TILES[indice % TILES.length]}`}>
          <Package size={20} aria-hidden />
        </span>
        <div className="min-w-0">
          <h3 className="truncate font-bold" title={produto.nome}>
            {produto.nome}
          </h3>
          <p className="text-xs text-muted">
            <span className="num">{produto.total_componentes}</span> componentes
          </p>
        </div>
      </div>

      <dl className="grid grid-cols-2 gap-3 text-xs">
        <div>
          <dt className="text-muted">Custo total</dt>
          <dd className="mt-0.5 text-base font-bold">
            <Money value={produto.custo_total} countOnMount />
          </dd>
        </div>
        <div>
          <dt className="text-muted">Profundidade</dt>
          <dd className="mt-0.5 text-base font-bold">
            <span className="num">{produto.profundidade}</span> níveis
          </dd>
        </div>
      </dl>

      <div className="mt-auto flex flex-wrap gap-1.5">
        <Link to={`/explosao/${produto.id}`} className={acao}>
          <Network size={13} aria-hidden /> Explosão
        </Link>
        <Link to={`/simulacao/${produto.id}`} className={acao}>
          <Calculator size={13} aria-hidden /> Simular
        </Link>
        <Link to={`/preco/${produto.id}`} className={acao}>
          <Zap size={13} aria-hidden /> Preço
        </Link>
      </div>
    </motion.article>
  );
}
