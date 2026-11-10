import { zodResolver } from "@hookform/resolvers/zod";
import { useMemo } from "react";
import { useForm } from "react-hook-form";
import { useCriarComponente, useEditarComponente } from "@/api/hooks";
import type { No } from "@/api/types";
import { Button } from "@/components/ui/Button";
import { Field } from "@/components/ui/Field";
import { useToast } from "@/components/ui/Toast";
import { idsDaSubarvore, ordenarHierarquico } from "@/lib/hierarchy";
import { normalizarPreco } from "@/lib/money";
import { componenteSchema, type ComponenteForm } from "./schemas";

interface Props {
  /** Todos os nós do produto (inclui a raiz): alimenta o select de pai. */
  nos: No[];
  inicial?: No;
  /** Pai pré-selecionado ao criar. */
  paiPadrao?: number;
  onDone: () => void;
}

export function ComponentForm({ nos, inicial, paiPadrao, onDone }: Props) {
  const criar = useCriarComponente();
  const editar = useEditarComponente();
  const toast = useToast();

  // Ao editar, o próprio nó e seus descendentes não podem ser o novo pai (evita ciclos).
  const opcoes = useMemo(() => {
    const proibidos = inicial ? idsDaSubarvore(nos, inicial.id) : new Set<number>();
    return ordenarHierarquico(nos).filter((n) => !proibidos.has(n.id));
  }, [nos, inicial]);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ComponenteForm>({
    resolver: zodResolver(componenteSchema),
    defaultValues: {
      nome: inicial?.nome ?? "",
      preco: inicial?.preco ?? "",
      quantidade: String(inicial?.quantidade ?? 1),
      parent_id: String(inicial?.parent_id ?? paiPadrao ?? ""),
    },
  });
  const pendente = criar.isPending || editar.isPending;

  const enviar = handleSubmit((v) => {
    const dados = {
      nome: v.nome.trim(),
      preco: normalizarPreco(v.preco)!,
      quantidade: Number(v.quantidade),
      parent_id: Number(v.parent_id),
    };
    const callbacks = {
      onSuccess: () => {
        toast.success(inicial ? "Componente atualizado" : "Componente criado");
        onDone();
      },
      onError: (e: Error) => toast.error(e.message),
    };
    if (inicial) editar.mutate({ id: inicial.id, dados }, callbacks);
    else criar.mutate(dados, callbacks);
  });

  return (
    <form onSubmit={enviar} className="space-y-4" noValidate>
      <Field label="Nome" htmlFor="c-nome" error={errors.nome?.message}>
        <input id="c-nome" className="field" aria-invalid={!!errors.nome} {...register("nome")} />
      </Field>
      <Field label="Pai" htmlFor="c-pai" error={errors.parent_id?.message}>
        <select id="c-pai" className="field" aria-invalid={!!errors.parent_id} {...register("parent_id")}>
          <option value="">Selecione…</option>
          {opcoes.map((n) => (
            <option key={n.id} value={n.id}>
              {"— ".repeat(n.nivel)}
              {n.nome}
            </option>
          ))}
        </select>
      </Field>
      <div className="grid grid-cols-2 gap-3">
        <Field label="Quantidade" htmlFor="c-qtd" error={errors.quantidade?.message}>
          <input id="c-qtd" inputMode="numeric" className="field num" aria-invalid={!!errors.quantidade} {...register("quantidade")} />
        </Field>
        <Field label="Preço (R$)" htmlFor="c-preco" error={errors.preco?.message}>
          <input id="c-preco" inputMode="decimal" className="field num" aria-invalid={!!errors.preco} {...register("preco")} />
        </Field>
      </div>
      <div className="flex justify-end pt-2">
        <Button type="submit" variant="primary" disabled={pendente}>
          {pendente ? "Salvando…" : inicial ? "Salvar alterações" : "Criar componente"}
        </Button>
      </div>
    </form>
  );
}
