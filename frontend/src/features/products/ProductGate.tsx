import type { ReactNode } from "react";
import type { ProdutoResumo } from "@/api/types";
import { EmptyState, ErrorState, PageHeader, Skeleton } from "@/components/ui/Card";
import { ProductPicker } from "./ProductPicker";
import { SeedButtons } from "./SeedButtons";
import { useSelectedProduct } from "./useSelectedProduct";

interface Props {
  title: string;
  subtitle: string;
  base: string;
  actions?: ReactNode;
  children: (produto: ProdutoResumo) => ReactNode;
}

/** Cabeçalho + seletor de produto + estados de carregamento/erro/vazio, comuns às telas por produto. */
export function ProductGate({ title, subtitle, base, actions, children }: Props) {
  const { query, lista, selecionado } = useSelectedProduct();

  return (
    <>
      <PageHeader title={title} subtitle={subtitle} actions={actions} />
      {query.isLoading ? (
        <div className="space-y-4" aria-busy="true" aria-label="Carregando">
          <Skeleton className="h-12 w-72" />
          <Skeleton className="h-[420px] w-full" />
        </div>
      ) : query.isError ? (
        <ErrorState message={query.error.message} onRetry={() => query.refetch()} />
      ) : !selecionado ? (
        <EmptyState
          title="Nenhum produto cadastrado"
          description="Carregue um exemplo para ver a árvore em ação ou crie seu primeiro produto na tela de cadastro."
          action={<SeedButtons />}
        />
      ) : (
        <>
          <ProductPicker produtos={lista} selecionadoId={selecionado.id} base={base} />
          {children(selecionado)}
        </>
      )}
    </>
  );
}
