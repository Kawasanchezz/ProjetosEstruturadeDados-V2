import { useComponente, useExcluirComponente, useExcluirProduto } from "@/api/hooks";
import type { No, ProdutoResumo } from "@/api/types";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { useToast } from "@/components/ui/Toast";

const plural = (n: number, um: string, varios: string) => `${n} ${n === 1 ? um : varios}`;

/** Exclusão em cascata de um produto: remove o produto e todos os componentes. */
export function DeleteProductDialog({ produto, onClose }: { produto: ProdutoResumo | null; onClose: () => void }) {
  const excluir = useExcluirProduto();
  const toast = useToast();
  const confirmar = () =>
    produto &&
    excluir.mutate(produto.id, {
      onSuccess: (r) => {
        toast.success(`Produto excluído (${plural(r.removidos, "item removido", "itens removidos")})`);
        onClose();
      },
      onError: (e) => toast.error(e.message),
    });

  return (
    <ConfirmDialog open={!!produto} title="Excluir produto?" onClose={onClose} onConfirm={confirmar} loading={excluir.isPending}>
      <p>
        <strong className="text-fg">{produto?.nome}</strong> e{" "}
        <strong className="text-fg">{plural(produto?.total_componentes ?? 0, "componente", "componentes")}</strong> serão
        excluídos em cascata. Esta ação não pode ser desfeita.
      </p>
    </ConfirmDialog>
  );
}

/** Exclusão em cascata de um componente: remove o nó e toda a sua subárvore. */
export function DeleteComponentDialog({ no, onClose }: { no: No | null; onClose: () => void }) {
  const excluir = useExcluirComponente();
  const detalhe = useComponente(no?.id);
  const toast = useToast();
  const descendentes = detalhe.data?.total_descendentes;

  const confirmar = () =>
    no &&
    excluir.mutate(no.id, {
      onSuccess: (r) => {
        toast.success(`Componente excluído (${plural(r.removidos, "item removido", "itens removidos")})`);
        onClose();
      },
      onError: (e) => toast.error(e.message),
    });

  return (
    <ConfirmDialog
      open={!!no}
      title="Excluir componente?"
      onClose={onClose}
      onConfirm={confirmar}
      loading={excluir.isPending || detalhe.isLoading}
    >
      <p>
        <strong className="text-fg">{no?.nome}</strong>
        {descendentes === undefined
          ? " será excluído."
          : descendentes > 0
            ? ` e seus ${plural(descendentes, "descendente", "descendentes")} serão excluídos em cascata.`
            : " será excluído (não possui filhos)."}{" "}
        Esta ação não pode ser desfeita.
      </p>
    </ConfirmDialog>
  );
}
