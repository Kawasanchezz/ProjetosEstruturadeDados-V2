import { Search } from "lucide-react";
import { useMemo, useState } from "react";
import { useExplosao } from "@/api/hooks";
import { NodeDetails } from "@/components/tree/NodeDetails";
import { TreeView } from "@/components/tree/TreeView";
import { ErrorState, Skeleton } from "@/components/ui/Card";
import { searchMatches } from "@/lib/tree";

/** Árvore interativa + busca + painel de detalhes. */
export function ExplosionView({ produtoId }: { produtoId: number }) {
  const { data, isLoading, isError, error, refetch } = useExplosao(produtoId);
  const [selecionado, setSelecionado] = useState<number | null>(null);
  const [consulta, setConsulta] = useState("");
  const raiz = data?.raiz;
  const matches = useMemo(() => (raiz ? searchMatches(raiz, consulta) : new Set<number>()), [raiz, consulta]);

  if (isError) return <ErrorState message={error.message} onRetry={() => refetch()} />;
  if (isLoading || !raiz) return <Skeleton className="h-[68vh] min-h-[420px] w-full" />;

  return (
    <div>
      <div className="relative mb-4 max-w-md">
        <Search size={16} className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-muted" aria-hidden />
        <input
          type="search"
          value={consulta}
          onChange={(e) => setConsulta(e.target.value)}
          placeholder="Buscar componente…"
          aria-label="Buscar componente"
          className="field pl-10"
        />
        {consulta && (
          <span className="absolute right-3 top-1/2 -translate-y-1/2 text-xs text-muted" aria-live="polite">
            {matches.size} {matches.size === 1 ? "resultado" : "resultados"}
          </span>
        )}
      </div>

      <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_340px]">
        <TreeView
          key={raiz.id}
          raiz={raiz}
          selectedId={selecionado}
          onSelect={setSelecionado}
          matches={matches}
          query={consulta}
        />
        <NodeDetails raiz={raiz} nodeId={selecionado} onSelect={setSelecionado} />
      </div>
    </div>
  );
}
