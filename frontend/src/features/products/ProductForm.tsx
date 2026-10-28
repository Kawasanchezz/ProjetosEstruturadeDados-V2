import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { useCriarProduto, useEditarProduto } from "@/api/hooks";
import type { ProdutoResumo } from "@/api/types";
import { Button } from "@/components/ui/Button";
import { Field } from "@/components/ui/Field";
import { useToast } from "@/components/ui/Toast";
import { normalizarPreco } from "@/lib/money";
import { produtoSchema, type ProdutoForm } from "./schemas";

interface Props {
  inicial?: ProdutoResumo;
  onDone: (produto: ProdutoResumo) => void;
}

export function ProductForm({ inicial, onDone }: Props) {
  const criar = useCriarProduto();
  const editar = useEditarProduto();
  const toast = useToast();
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ProdutoForm>({
    resolver: zodResolver(produtoSchema),
    defaultValues: {
      nome: inicial?.nome ?? "",
      preco: inicial?.preco ?? "0.00",
      quantidade: String(inicial?.quantidade ?? 1),
    },
  });
  const pendente = criar.isPending || editar.isPending;

  const enviar = handleSubmit((v) => {
    const dados = { nome: v.nome.trim(), preco: normalizarPreco(v.preco)!, quantidade: Number(v.quantidade) };
    const opcoes = {
      onSuccess: (p: ProdutoResumo) => {
        toast.success(inicial ? "Produto atualizado" : "Produto criado");
        onDone(p);
      },
      onError: (e: Error) => toast.error(e.message),
    };
    if (inicial) editar.mutate({ id: inicial.id, dados }, opcoes);
    else criar.mutate(dados, opcoes);
  });

  return (
    <form onSubmit={enviar} className="space-y-4" noValidate>
      <Field label="Nome" htmlFor="p-nome" error={errors.nome?.message}>
        <input id="p-nome" className="field" aria-invalid={!!errors.nome} {...register("nome")} />
      </Field>
      <div className="grid grid-cols-2 gap-3">
        <Field label="Preço próprio (R$)" htmlFor="p-preco" error={errors.preco?.message} hint="Montagem / mão de obra">
          <input id="p-preco" inputMode="decimal" className="field num" aria-invalid={!!errors.preco} {...register("preco")} />
        </Field>
        <Field label="Quantidade" htmlFor="p-qtd" error={errors.quantidade?.message}>
          <input id="p-qtd" inputMode="numeric" className="field num" aria-invalid={!!errors.quantidade} {...register("quantidade")} />
        </Field>
      </div>
      <div className="flex justify-end pt-2">
        <Button type="submit" variant="primary" disabled={pendente}>
          {pendente ? "Salvando…" : inicial ? "Salvar alterações" : "Criar produto"}
        </Button>
      </div>
    </form>
  );
}
