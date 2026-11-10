import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { sessao } from "@/api/client";
import { AuthProvider, useAuth } from "@/auth/AuthContext";
import LoginPage from "./LoginPage";

const json = (corpo: unknown, status = 200) => Promise.resolve(new Response(JSON.stringify(corpo), { status }));

function Estado() {
  const { estado, usuario, podeEscrever } = useAuth();
  return <p data-testid="estado">{`${estado}|${usuario?.role ?? "-"}|${podeEscrever}`}</p>;
}

describe("login", () => {
  const fetchMock = vi.fn<(url: string, init?: RequestInit) => Promise<Response>>();
  beforeEach(() => {
    fetchMock.mockReset();
    sessao.definirToken(null);
    vi.stubGlobal("fetch", fetchMock);
  });
  afterEach(() => vi.unstubAllGlobals());

  const montar = () =>
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <AuthProvider>
            <Estado />
            <LoginPage />
          </AuthProvider>
        </MemoryRouter>
      </QueryClientProvider>,
    );

  it("senha errada mostra mensagem genérica e limpa o campo de senha", async () => {
    fetchMock.mockImplementation((url) => {
      if (url.endsWith("/auth/config")) return json({ auth_enabled: true });
      if (url.endsWith("/auth/refresh")) return json({ detail: "Sessão expirada" }, 401);
      return json({ detail: "Credenciais inválidas" }, 401);
    });
    const user = userEvent.setup();
    montar();
    await user.type(await screen.findByLabelText("Usuário"), "admin");
    await user.type(screen.getByLabelText("Senha"), "errada-errada");
    await user.click(screen.getByRole("button", { name: /entrar/i }));
    expect(await screen.findByText("Usuário ou senha inválidos.")).toBeInTheDocument();
    expect(screen.getByLabelText("Senha")).toHaveValue("");
  });

  it("login válido guarda o token só em memória e libera a escrita para admin", async () => {
    fetchMock.mockImplementation((url, init) => {
      if (url.endsWith("/auth/config")) return json({ auth_enabled: true });
      if (url.endsWith("/auth/refresh")) return json({ detail: "x" }, 401);
      if (url.endsWith("/auth/login")) return json({ access_token: "tok-teste" });
      if (url.endsWith("/auth/me")) {
        expect((init?.headers as Record<string, string>)["Authorization"]).toBe("Bearer tok-teste");
        return json({ username: "admin", role: "admin" });
      }
      return json({}, 404);
    });
    const user = userEvent.setup();
    montar();
    await user.type(await screen.findByLabelText("Usuário"), "admin");
    await user.type(screen.getByLabelText("Senha"), crypto.randomUUID());
    await user.click(screen.getByRole("button", { name: /entrar/i }));
    await waitFor(() => expect(screen.getByTestId("estado")).toHaveTextContent("autenticado|admin|true"));
    expect(JSON.stringify({ ...localStorage, ...sessionStorage })).not.toContain("tok-teste");
  });

  it("API sem login: abre direto e permite escrever", async () => {
    fetchMock.mockImplementation(() => json({ auth_enabled: false }));
    montar();
    await waitFor(() => expect(screen.getByTestId("estado")).toHaveTextContent("aberto|-|true"));
  });
});
