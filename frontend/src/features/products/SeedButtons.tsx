import { Sparkles } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { useSeed } from "@/api/hooks";
import { useAuth } from "@/auth/AuthContext";
import type { Exemplo } from "@/api/types";
import { Button } from "@/components/ui/Button";
import { useToast } from "@/components/ui/Toast";

const EXEMPLOS: { chave: Exemplo; rotulo: string }[] = [
  { chave: "caneta", rotulo: "Caneta" },
  { chave: "computador", rotulo: "Computador" },
];

/** Carrega um exemplo (recria o produto de demonstração). */
function useCarregarExemplo(irParaExplosao: boolean) {
  const seed = useSeed();
  const toast = useToast();
  const navigate = useNavigate();

  const carregar = (chave: Exemplo, rotulo: string) =>
    seed.mutate(chave, {
      onSuccess: (produto) => {
        toast.success(`Exemplo "${rotulo}" carregado`);
        if (irParaExplosao) navigate(`/explosao/${produto.id}`);
      },
      onError: (e) => toast.error(e.message),
    });
  return { carregar, pendente: seed.isPending };
}

/** "Carregar exemplo": botões completos. `onDark` ajusta o contraste sobre fundos escuros. */
export function SeedButtons({ irParaExplosao = false, onDark = false }: { irParaExplosao?: boolean; onDark?: boolean }) {
  const { carregar, pendente } = useCarregarExemplo(irParaExplosao);
  const { podeEscrever } = useAuth();
  if (!podeEscrever) return null; // viewer não vê o seed (a API também responde 403)
  return (
    <>
      {EXEMPLOS.map((e, i) => (
        <Button
          key={e.chave}
          variant={i === 0 ? "primary" : onDark ? "onDark" : "ghost"}
          disabled={pendente}
          onClick={() => carregar(e.chave, e.rotulo)}
        >
          <Sparkles size={16} aria-hidden />
          Carregar exemplo: {e.rotulo}
        </Button>
      ))}
    </>
  );
}
