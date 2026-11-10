import { LogIn, Network } from "lucide-react";
import { useState, type FormEvent } from "react";
import { ApiError } from "@/api/client";
import { useAuth } from "@/auth/AuthContext";
import { Button } from "@/components/ui/Button";
import { Field } from "@/components/ui/Field";
import { ThemeToggle } from "@/components/ui/ThemeToggle";

export default function LoginPage() {
  const { entrar } = useAuth();
  const [usuario, setUsuario] = useState("");
  const [senha, setSenha] = useState("");
  const [erro, setErro] = useState<string>();
  const [enviando, setEnviando] = useState(false);

  const enviar = async (e: FormEvent) => {
    e.preventDefault();
    if (!usuario.trim() || !senha) return setErro("Informe usuário e senha.");
    setErro(undefined);
    setEnviando(true);
    try {
      await entrar(usuario.trim(), senha);
    } catch (err) {
      // Mensagem genérica para credenciais; o servidor também não revela se o usuário existe.
      setErro(err instanceof ApiError && (err.status === 429 || err.status === 0) ? err.message : err instanceof ApiError && err.status === 401 ? "Usuário ou senha inválidos." : "Não foi possível entrar. Tente novamente.");
    } finally {
      setEnviando(false);
      setSenha("");
    }
  };

  return (
    <main className="grid min-h-screen place-items-center bg-backdrop p-4">
      <div className="absolute right-4 top-4">
        <ThemeToggle />
      </div>
      <form onSubmit={enviar} noValidate className="card w-full max-w-sm space-y-5 rounded-3xl p-7" aria-labelledby="login-titulo">
        <div className="flex items-center gap-3">
          <span className="grid h-11 w-11 place-items-center rounded-full bg-accent text-ink">
            <Network size={20} aria-hidden />
          </span>
          <div>
            <h1 id="login-titulo" className="text-xl font-bold leading-tight">
              Entrar
            </h1>
            <p className="text-xs text-muted">TreeBOM · Lista de materiais</p>
          </div>
        </div>

        <Field label="Usuário" htmlFor="login-usuario">
          <input
            id="login-usuario"
            className="field"
            autoComplete="username"
            autoFocus
            maxLength={50}
            value={usuario}
            onChange={(e) => setUsuario(e.target.value)}
          />
        </Field>
        <Field label="Senha" htmlFor="login-senha" error={erro}>
          <input
            id="login-senha"
            type="password"
            className="field"
            autoComplete="current-password"
            maxLength={128}
            value={senha}
            aria-invalid={!!erro}
            aria-describedby={erro ? "login-senha-erro" : undefined}
            onChange={(e) => setSenha(e.target.value)}
          />
        </Field>

        <Button type="submit" variant="primary" className="w-full" disabled={enviando}>
          <LogIn size={16} aria-hidden />
          {enviando ? "Entrando…" : "Entrar"}
        </Button>
      </form>
    </main>
  );
}
