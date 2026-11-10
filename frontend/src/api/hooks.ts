import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "./client";
import type {
  ComponenteAtualizacao,
  ComponenteDetalhe,
  ComponenteEntrada,
  Exemplo,
  Explosao,
  Implosao,
  No,
  Pagina,
  ProdutoEntrada,
  ProdutoResumo,
  Simulacao,
} from "./types";

export const keys = {
  produtos: ["produtos"] as const,
  explosao: (id: number) => ["explosao", id] as const,
  componentes: (id: number) => ["componentes", id] as const,
  componente: (id: number) => ["componente", id] as const,
};

export const useProdutos = () =>
  useQuery({
    queryKey: keys.produtos,
    queryFn: () => api.get<Pagina<ProdutoResumo>>("/produtos?limit=100"),
  });

export const useExplosao = (id: number | undefined) =>
  useQuery({
    queryKey: keys.explosao(id ?? 0),
    queryFn: () => api.get<Explosao>(`/produtos/${id}/explosao`),
    enabled: id !== undefined,
  });

export const useComponentes = (produtoId: number | undefined) =>
  useQuery({
    queryKey: keys.componentes(produtoId ?? 0),
    queryFn: () => api.get<Pagina<No>>(`/produtos/${produtoId}/componentes?limit=500`),
    enabled: produtoId !== undefined,
  });

export const useComponente = (id: number | undefined) =>
  useQuery({
    queryKey: keys.componente(id ?? 0),
    queryFn: () => api.get<ComponenteDetalhe>(`/componentes/${id}`),
    enabled: id !== undefined,
  });

/** Qualquer escrita invalida tudo que depende da árvore. */
export function useInvalidarArvore() {
  const qc = useQueryClient();
  return () =>
    Promise.all(
      ["produtos", "explosao", "componentes", "componente"].map((k) => qc.invalidateQueries({ queryKey: [k] })),
    );
}

export function useSeed() {
  const invalidar = useInvalidarArvore();
  return useMutation({
    mutationFn: (exemplo: Exemplo) => api.post<ProdutoResumo>(`/seed/${exemplo}`),
    onSuccess: invalidar,
  });
}

export function useCriarProduto() {
  const invalidar = useInvalidarArvore();
  return useMutation({
    mutationFn: (dados: ProdutoEntrada) => api.post<ProdutoResumo>("/produtos", dados),
    onSuccess: invalidar,
  });
}

export function useEditarProduto() {
  const invalidar = useInvalidarArvore();
  return useMutation({
    mutationFn: ({ id, dados }: { id: number; dados: Partial<ProdutoEntrada> }) =>
      api.put<ProdutoResumo>(`/produtos/${id}`, dados),
    onSuccess: invalidar,
  });
}

export function useExcluirProduto() {
  const invalidar = useInvalidarArvore();
  return useMutation({
    mutationFn: (id: number) => api.delete<{ removidos: number }>(`/produtos/${id}`),
    onSuccess: invalidar,
  });
}

export function useCriarComponente() {
  const invalidar = useInvalidarArvore();
  return useMutation({
    mutationFn: (dados: ComponenteEntrada) => api.post<No>("/componentes", dados),
    onSuccess: invalidar,
  });
}

export function useEditarComponente() {
  const invalidar = useInvalidarArvore();
  return useMutation({
    mutationFn: ({ id, dados }: { id: number; dados: ComponenteAtualizacao }) =>
      api.put<No>(`/componentes/${id}`, dados),
    onSuccess: invalidar,
  });
}

export function useExcluirComponente() {
  const invalidar = useInvalidarArvore();
  return useMutation({
    mutationFn: (id: number) => api.delete<{ removidos: number }>(`/componentes/${id}`),
    onSuccess: invalidar,
  });
}

export const useSimular = () =>
  useMutation({
    mutationFn: ({ produtoId, quantidade }: { produtoId: number; quantidade: number }) =>
      api.post<Simulacao>(`/produtos/${produtoId}/simulacao`, { quantidade }),
  });

/** Não invalida sozinho: a página de implosão invalida ao fim da animação. */
export const useAtualizarPreco = () =>
  useMutation({
    mutationFn: ({ id, preco }: { id: number; preco: string }) =>
      api.patch<Implosao>(`/componentes/${id}/preco`, { preco }),
  });
