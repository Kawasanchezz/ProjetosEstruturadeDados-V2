import { AnimatePresence, motion } from "motion/react";
import { Pencil, Plus, Trash2 } from "lucide-react";
import { useMemo, useState } from "react";
import { useComponentes } from "@/api/hooks";
import type { No, ProdutoResumo } from "@/api/types";
import { Button, IconButton } from "@/components/ui/Button";
import { Card, EmptyState, ErrorState, PageHeader, Skeleton } from "@/components/ui/Card";
import { Modal } from "@/components/ui/Modal";
import { ComponentForm } from "@/features/products/ComponentForm";
import { DeleteComponentDialog, DeleteProductDialog } from "@/features/products/DeleteDialogs";
import { ProductForm } from "@/features/products/ProductForm";
import { ProductPicker } from "@/features/products/ProductPicker";
import { SeedButtons } from "@/features/products/SeedButtons";
import { useSelectedProduct } from "@/features/products/useSelectedProduct";
import { rowVariants } from "@/lib/animations/variants";
import { ordenarHierarquico } from "@/lib/hierarchy";
import { formatBRL } from "@/lib/money";

type Dialogo =
  | { tipo: "produto"; editar?: ProdutoResumo }
  | { tipo: "componente"; editar?: No; pai?: number }
  | null;

export default function CrudPage() {
  const { query, lista, selecionado } = useSelectedProduct();
  const componentes = useComponentes(selecionado?.id);
  const [dialogo, setDialogo] = useState<Dialogo>(null);
  const [excluirProduto, setExcluirProduto] = useState<ProdutoResumo | null>(null);
  const [excluirNo, setExcluirNo] = useState<No | null>(null);

  const nos = useMemo(() => componentes.data?.itens ?? [], [componentes.data]);
  const ordenados = useMemo(() => ordenarHierarquico(nos), [nos]);
  const nomes = useMemo(() => new Map(nos.map((n) => [n.id, n.nome])), [nos]);
  const fechar = () => setDialogo(null);

  return (
    <>
      <PageHeader
        title="Cadastro"
        subtitle="Crie, edite e exclua produtos e componentes. Excluir um nó remove também toda a sua subárvore (cascata), com confirmação."
        actions={
          <Button variant="primary" onClick={() => setDialogo({ tipo: "produto" })}>
            <Plus size={16} aria-hidden /> Novo produto
          </Button>
        }
      />

      {query.isLoading ? (
        <Skeleton className="h-64 w-full" />
      ) : query.isError ? (
        <ErrorState message={query.error.message} onRetry={() => query.refetch()} />
      ) : !selecionado ? (
        <EmptyState
          title="Nenhum produto ainda"
          description="Crie um produto do zero ou carregue um dos exemplos."
          action={
            <>
              <Button variant="primary" onClick={() => setDialogo({ tipo: "produto" })}>
                <Plus size={16} aria-hidden /> Novo produto
              </Button>
              <SeedButtons />
            </>
          }
        />
      ) : (
        <>
          <ProductPicker produtos={lista} selecionadoId={selecionado.id} base="/cadastro" />

          <Card className="mb-6 flex flex-wrap items-center justify-between gap-4">
            <div>
              <p className="text-xs uppercase tracking-wide text-muted">Produto selecionado</p>
              <p className="text-lg font-bold">{selecionado.nome}</p>
              <p className="num text-sm text-muted">
                {formatBRL(selecionado.custo_total)} · {selecionado.total_componentes} componentes
              </p>
            </div>
            <div className="flex gap-1">
              <Button onClick={() => setDialogo({ tipo: "produto", editar: selecionado })}>
                <Pencil size={15} aria-hidden /> Editar
              </Button>
              <Button variant="danger" onClick={() => setExcluirProduto(selecionado)}>
                <Trash2 size={15} aria-hidden /> Excluir
              </Button>
            </div>
          </Card>

          <Card className="overflow-hidden p-0!">
            <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border/10 px-4 py-4 sm:px-5">
              <h2 className="font-semibold">Componentes</h2>
              <Button className="whitespace-nowrap" onClick={() => setDialogo({ tipo: "componente", pai: selecionado.id })}>
                <Plus size={15} aria-hidden /> Novo componente
              </Button>
            </div>
            {componentes.isLoading ? (
              <Skeleton className="m-5 h-40" />
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <caption className="sr-only">Componentes de {selecionado.nome}</caption>
                  <thead>
                    <tr className="text-left text-xs uppercase tracking-wide text-muted max-sm:text-[10px] max-sm:tracking-normal">
                      <th scope="col" className="px-5 py-3">Nome</th>
                      <th scope="col" className="hidden px-3 py-3 sm:table-cell">Pai</th>
                      <th scope="col" className="px-2 py-3 text-right sm:px-3">Qtd.</th>
                      <th scope="col" className="px-3 py-3 text-right">Preço</th>
                      <th scope="col" className="px-5 py-3 text-right"><span className="sr-only">Ações</span></th>
                    </tr>
                  </thead>
                  <tbody>
                    <AnimatePresence initial={false}>
                      {ordenados.map((n, i) => (
                        <motion.tr
                          key={n.id}
                          layout
                          custom={i}
                          variants={rowVariants}
                          initial="hidden"
                          animate="show"
                          exit="exit"
                          className="border-t border-border/5"
                        >
                          <th scope="row" className="px-5 py-2.5 text-left font-medium" style={{ paddingLeft: 8 + n.nivel * 10 }}>
                            {n.nome}
                          </th>
                          <td className="hidden px-3 py-2.5 text-muted sm:table-cell">{n.parent_id === null ? "—" : nomes.get(n.parent_id)}</td>
                          <td className="num px-1 py-2.5 text-right sm:px-3">{n.quantidade}</td>
                          <td className="num whitespace-nowrap px-1 py-2.5 text-right max-sm:text-[13px] sm:px-3">{formatBRL(n.preco)}</td>
                          <td className="px-1 py-1.5 sm:px-5">
                            <div className="flex flex-nowrap justify-end gap-0 max-sm:[&>button]:h-8 max-sm:[&>button]:w-8 sm:gap-0.5">
                              <span className="hidden sm:inline-flex">
                                <IconButton label={`Adicionar filho em ${n.nome}`} onClick={() => setDialogo({ tipo: "componente", pai: n.id })}>
                                <Plus size={16} />
                              </IconButton>
                              </span>
                              {n.parent_id !== null && (
                                <>
                                  <IconButton label={`Editar ${n.nome}`} onClick={() => setDialogo({ tipo: "componente", editar: n })}>
                                    <Pencil size={16} />
                                  </IconButton>
                                  <IconButton label={`Excluir ${n.nome}`} className="hover:text-bad" onClick={() => setExcluirNo(n)}>
                                    <Trash2 size={16} />
                                  </IconButton>
                                </>
                              )}
                            </div>
                          </td>
                        </motion.tr>
                      ))}
                    </AnimatePresence>
                  </tbody>
                </table>
              </div>
            )}
          </Card>
        </>
      )}

      <Modal
        open={dialogo?.tipo === "produto"}
        onClose={fechar}
        title={dialogo?.tipo === "produto" && dialogo.editar ? "Editar produto" : "Novo produto"}
      >
        {dialogo?.tipo === "produto" && <ProductForm inicial={dialogo.editar} onDone={fechar} />}
      </Modal>
      <Modal
        open={dialogo?.tipo === "componente"}
        onClose={fechar}
        title={dialogo?.tipo === "componente" && dialogo.editar ? "Editar componente" : "Novo componente"}
        description="O pai define onde o componente entra na árvore."
      >
        {dialogo?.tipo === "componente" && (
          <ComponentForm nos={nos} inicial={dialogo.editar} paiPadrao={dialogo.pai} onDone={fechar} />
        )}
      </Modal>
      <DeleteProductDialog produto={excluirProduto} onClose={() => setExcluirProduto(null)} />
      <DeleteComponentDialog no={excluirNo} onClose={() => setExcluirNo(null)} />
    </>
  );
}
