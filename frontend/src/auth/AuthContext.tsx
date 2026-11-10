import { useQueryClient } from "@tanstack/react-query";
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { ApiError, api, renovarSessao, sessao } from "@/api/client";

export type Papel = "admin" | "viewer";
type Estado = "carregando" | "aberto" | "anonimo" | "autenticado";

interface Usuario {
  username: string;
  role: Papel;
}

interface AuthApi {
  estado: Estado;
  usuario: Usuario | null;
  /** Admin (ou API sem login): pode criar, editar, excluir e alterar preços. A API valida de novo no servidor. */
  podeEscrever: boolean;
  entrar: (username: string, password: string) => Promise<void>;
  sair: () => Promise<void>;
}

const Ctx = createContext<AuthApi | null>(null);

export function useAuth(): AuthApi {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error("useAuth fora do AuthProvider");
  return ctx;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const qc = useQueryClient();
  const [estado, setEstado] = useState<Estado>("carregando");
  const [usuario, setUsuario] = useState<Usuario | null>(null);

  const carregarUsuario = useCallback(async () => {
    const eu = await api.get<Usuario>("/auth/me");
    setUsuario({ username: eu.username, role: eu.role });
    setEstado("autenticado");
  }, []);

  const encerrarLocal = useCallback(() => {
    sessao.definirToken(null);
    setUsuario(null);
    setEstado("anonimo");
    qc.clear(); // nenhum dado da sessão anterior fica em cache
  }, [qc]);

  // Descobre se a API exige login e tenta retomar a sessão pelo cookie de refresh (recarregar a página não desloga).
  useEffect(() => {
    sessao.aoExpirar(encerrarLocal);
    let ativo = true;
    (async () => {
      // A API pode ainda estar subindo (a JVM leva alguns segundos): tenta de novo em vez de cair na tela de login.
      const pausa = (ms: number) => new Promise((r) => setTimeout(r, ms));
      for (let tentativa = 0; ativo; tentativa++) {
        try {
          const cfg = await api.get<{ auth_enabled: boolean }>("/auth/config");
          if (!ativo) return;
          if (!cfg.auth_enabled) return setEstado("aberto");
          if (await renovarSessao()) await carregarUsuario();
          else setEstado("anonimo");
          return;
        } catch (e) {
          const indisponivel = e instanceof ApiError && (e.status === 0 || e.status >= 500);
          if (!indisponivel || tentativa >= 40) {
            if (ativo) setEstado("anonimo");
            return;
          }
          await pausa(1000);
        }
      }
    })();
    return () => {
      ativo = false;
      sessao.aoExpirar(null);
    };
  }, [carregarUsuario, encerrarLocal]);

  const entrar = useCallback(
    async (username: string, password: string) => {
      const r = await api.post<{ access_token: string }>("/auth/login", { username, password });
      sessao.definirToken(r.access_token);
      await carregarUsuario();
    },
    [carregarUsuario],
  );

  const sair = useCallback(async () => {
    try {
      await api.post("/auth/logout");
    } finally {
      encerrarLocal();
    }
  }, [encerrarLocal]);

  const valor = useMemo<AuthApi>(
    () => ({ estado, usuario, podeEscrever: estado === "aberto" || usuario?.role === "admin", entrar, sair }),
    [estado, usuario, entrar, sair],
  );
  return <Ctx.Provider value={valor}>{children}</Ctx.Provider>;
}
