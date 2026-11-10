import type { ReactNode } from "react";
import { EmptyState } from "@/components/ui/Card";
import { useAuth } from "./AuthContext";

/** Só para a interface: esconde telas de escrita de quem não é admin. A API recusa a escrita de qualquer forma (403). */
export function RequireAdmin({ children }: { children: ReactNode }) {
  const { podeEscrever } = useAuth();
  if (!podeEscrever) {
    return <EmptyState title="Acesso restrito" description="Esta tela é exclusiva para administradores. Seu perfil tem acesso somente de leitura (explosão e simulação)." />;
  }
  return <>{children}</>;
}
