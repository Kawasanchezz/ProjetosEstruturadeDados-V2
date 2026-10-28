import { motion } from "motion/react";
import { ChevronRight, MousePointerClick } from "lucide-react";
import type { NoExplosao } from "@/api/types";
import { Money } from "@/components/ui/Money";
import { drawerVariants } from "@/lib/animations/variants";
import { formatBRL } from "@/lib/money";
import { flatten, pathTo } from "@/lib/tree";

interface Props {
  raiz: NoExplosao;
  nodeId: number | null;
  onSelect: (id: number) => void;
}

function Linha({ rotulo, children }: { rotulo: string; children: React.ReactNode }) {
  return (
    <div className="flex items-baseline justify-between gap-3 border-b border-border/10 py-2.5 last:border-0">
      <dt className="text-xs uppercase tracking-wide text-muted">{rotulo}</dt>
      <dd className="num text-right text-sm font-medium">{children}</dd>
    </div>
  );
}

/** Painel lateral com os detalhes do nó selecionado (tudo calculado a partir da árvore já carregada). */
export function NodeDetails({ raiz, nodeId, onSelect }: Props) {
  const caminho = nodeId === null ? [] : pathTo(raiz, nodeId);
  const no = caminho[caminho.length - 1];

  if (!no) {
    return (
      <aside className="glass flex flex-col items-center justify-center rounded-2xl p-8 text-center text-sm text-muted lg:min-h-[420px]">
        <MousePointerClick className="mb-3 text-accent2" aria-hidden />
        Selecione um nó da árvore para ver quantidade, preço, custo da subárvore e caminho até a raiz.
      </aside>
    );
  }

  const participacao = Number(raiz.custo_total) > 0 ? (Number(no.custo_total) / Number(raiz.custo_total)) * 100 : 0;

  return (
    <motion.aside
      key={no.id}
      variants={drawerVariants}
      initial="hidden"
      animate="show"
      className="glass rounded-2xl p-5"
      aria-label={`Detalhes de ${no.nome}`}
    >
      <p className="text-xs uppercase tracking-wide text-muted">{no.parent_id === null ? "Produto" : "Componente"}</p>
      <h2 className="mt-1 break-words text-xl font-bold">{no.nome}</h2>

      <div className="my-4 rounded-xl bg-accent/30 p-4">
        <p className="text-xs uppercase tracking-wide text-muted">Custo da subárvore</p>
        <p className="text-2xl font-bold">
          <Money value={no.custo_total} />
        </p>
        <div className="mt-3 h-1.5 overflow-hidden rounded-full bg-fg/10" aria-hidden>
          <motion.div
            className="h-full origin-left rounded-full bg-accent2"
            initial={{ scaleX: 0 }}
            animate={{ scaleX: Math.min(participacao, 100) / 100 }}
            transition={{ duration: 0.6 }}
            style={{ width: "100%" }}
          />
        </div>
        <p className="mt-1 text-xs text-muted">{participacao.toFixed(1).replace(".", ",")}% do custo total do produto</p>
      </div>

      <dl>
        <Linha rotulo="Quantidade">{no.quantidade}</Linha>
        <Linha rotulo="Preço próprio">{formatBRL(no.preco)}</Linha>
        <Linha rotulo="Custo unitário">{formatBRL(no.custo_unitario)}</Linha>
        <Linha rotulo="Filhos diretos">{no.filhos.length}</Linha>
        <Linha rotulo="Descendentes">{flatten(no).length - 1}</Linha>
        <Linha rotulo="Nível">{caminho.length}</Linha>
      </dl>

      <nav aria-label="Caminho até a raiz" className="mt-4">
        <p className="mb-2 text-xs uppercase tracking-wide text-muted">Caminho até a raiz</p>
        <ol className="flex flex-wrap items-center gap-1 text-sm">
          {[...caminho].reverse().map((n, i, lista) => (
            <li key={n.id} className="flex items-center gap-1">
              <button
                onClick={() => onSelect(n.id)}
                className={`rounded-lg px-2 py-1 transition-colors hover:bg-fg/10 ${n.id === no.id ? "font-semibold text-accent2" : "text-muted"}`}
              >
                {n.nome}
              </button>
              {i < lista.length - 1 && <ChevronRight size={14} className="text-muted" aria-hidden />}
            </li>
          ))}
        </ol>
      </nav>
    </motion.aside>
  );
}
