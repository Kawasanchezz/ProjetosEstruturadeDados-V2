import { useParams } from "react-router-dom";
import { useProdutos } from "@/api/hooks";

/** Produto da URL (:id) ou, na falta dele, o primeiro cadastrado. */
export function useSelectedProduct() {
  const { id } = useParams();
  const query = useProdutos();
  const lista = query.data?.itens ?? [];
  const selecionado = lista.find((p) => p.id === Number(id)) ?? lista[0];
  return { query, lista, selecionado };
}
