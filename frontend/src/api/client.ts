const BASE = import.meta.env.VITE_API_URL ?? "/api";

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}

/**
 * Sessão: o access token vive SÓ em memória (nunca em localStorage/sessionStorage), então um XSS não consegue lê-lo
 * de um armazenamento. O refresh token fica em cookie HttpOnly, invisível para o JavaScript.
 */
let accessToken: string | null = null;
let aoExpirar: (() => void) | null = null;
export const sessao = {
  definirToken: (t: string | null) => {
    accessToken = t;
  },
  temToken: () => accessToken !== null,
  aoExpirar: (cb: (() => void) | null) => {
    aoExpirar = cb;
  },
};

/** Extrai uma mensagem legível do corpo de erro (string ou lista de erros do Pydantic). */
function mensagem(corpo: unknown, status: number): string {
  const detail = (corpo as { detail?: unknown } | null)?.detail;
  if (typeof detail === "string") return detail;
  if (Array.isArray(detail) && detail.length > 0) {
    const primeiro = detail[0] as { msg?: string; loc?: unknown[] };
    const campo = primeiro.loc?.slice(-1)[0];
    return campo ? `${String(campo)}: ${primeiro.msg ?? "valor inválido"}` : (primeiro.msg ?? "Dados inválidos");
  }
  if (status === 401) return "Sessão expirada. Entre novamente.";
  if (status === 403) return "Você não tem permissão para esta ação.";
  if (status === 429) return "Muitas requisições. Aguarde um instante.";
  return status >= 500 ? "Erro interno do servidor" : "Requisição inválida";
}

async function enviar(method: string, path: string, body?: unknown): Promise<Response> {
  const headers: Record<string, string> = {};
  if (body !== undefined) headers["Content-Type"] = "application/json";
  if (accessToken) headers["Authorization"] = `Bearer ${accessToken}`;
  try {
    return await fetch(`${BASE}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      credentials: "same-origin", // envia o cookie HttpOnly de refresh apenas ao mesmo site
    });
  } catch {
    throw new ApiError(0, "Não foi possível conectar à API");
  }
}

let renovacao: Promise<boolean> | null = null;

/** Troca o cookie de refresh por um novo access token. Chamadas simultâneas compartilham a mesma renovação. */
export function renovarSessao(): Promise<boolean> {
  renovacao ??= (async () => {
    try {
      const r = await enviar("POST", "/auth/refresh", {});
      if (!r.ok) return false;
      const dados = (await r.json()) as { access_token: string };
      accessToken = dados.access_token;
      return true;
    } catch {
      return false;
    } finally {
      renovacao = null;
    }
  })();
  return renovacao;
}

export async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  let resposta = await enviar(method, path, body);
  // Access token expirado/revogado: tenta UMA renovação e repete a chamada.
  if (resposta.status === 401 && !path.startsWith("/auth/")) {
    if (await renovarSessao()) {
      resposta = await enviar(method, path, body);
    } else {
      accessToken = null;
      aoExpirar?.();
    }
  }
  const corpo = await resposta.json().catch(() => null);
  if (!resposta.ok) {
    // Resposta de erro sem JSON = não veio da nossa API (ex.: o proxy do Vite sem backend no ar).
    if (corpo === null && resposta.status >= 500) {
      throw new ApiError(resposta.status, "API indisponível. Inicie o backend: cd backend && py -m uvicorn app.main:app --port 8000");
    }
    throw new ApiError(resposta.status, mensagem(corpo, resposta.status));
  }
  return corpo as T;
}

export const api = {
  get: <T>(path: string) => request<T>("GET", path),
  post: <T>(path: string, body?: unknown) => request<T>("POST", path, body ?? {}),
  put: <T>(path: string, body: unknown) => request<T>("PUT", path, body),
  patch: <T>(path: string, body: unknown) => request<T>("PATCH", path, body),
  delete: <T>(path: string) => request<T>("DELETE", path),
};
