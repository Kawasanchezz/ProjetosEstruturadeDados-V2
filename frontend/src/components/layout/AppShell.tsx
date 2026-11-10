import { motion } from "motion/react";
import { Calculator, Database, LayoutGrid, LogOut, Network, Zap, type LucideIcon } from "lucide-react";
import type { ReactNode } from "react";
import { Link, NavLink, useLocation } from "react-router-dom";
import { useAuth } from "@/auth/AuthContext";
import { IconButton } from "@/components/ui/Button";
import { ThemeToggle } from "@/components/ui/ThemeToggle";
import { cn } from "@/lib/cn";

interface Item {
  to: string;
  label: string;
  curto: string;
  icon: LucideIcon;
  end?: boolean;
  /** Só administradores veem (a API também bloqueia). */
  admin?: boolean;
}

const LINKS: Item[] = [
  { to: "/", label: "Dashboard", curto: "Início", icon: LayoutGrid, end: true },
  { to: "/explosao", label: "Explosão", curto: "Explosão", icon: Network },
  { to: "/simulacao", label: "Simulação", curto: "Simulação", icon: Calculator },
  { to: "/preco", label: "Preço e implosão", curto: "Preço", icon: Zap, admin: true },
  { to: "/cadastro", label: "Cadastro", curto: "Cadastro", icon: Database, admin: true },
];

function Logo() {
  return (
    <Link to="/" className="flex items-center gap-2.5 font-bold tracking-tight text-white">
      <span className="grid h-9 w-9 place-items-center rounded-full bg-accent text-ink">
        <Network size={18} aria-hidden />
      </span>
      <span className="text-lg">TreeBOM</span>
    </Link>
  );
}

function NavItems() {
  const { pathname } = useLocation();
  const { podeEscrever } = useAuth();
  const ativo = (l: Item) => (l.end ? pathname === l.to : pathname.startsWith(l.to));

  return (
    <nav aria-label="Principal" className="flex flex-col gap-1">
      {LINKS.filter((l) => !l.admin || podeEscrever).map((l) => {
        const Icone = l.icon;
        return (
          <NavLink
            key={l.to}
            to={l.to}
            end={l.end}
            className={cn(
              "relative flex items-center gap-3 whitespace-nowrap rounded-full px-4 py-2.5 text-sm font-semibold transition-colors",
              ativo(l) ? "text-ink" : "text-white/70 hover:text-white",
            )}
          >
            {ativo(l) && (
              <motion.span
                layoutId="nav-pill-side"
                className="absolute inset-0 -z-10 rounded-full bg-accent"
                transition={{ type: "spring", stiffness: 380, damping: 32 }}
              />
            )}
            <Icone size={18} aria-hidden />
            {l.label}
          </NavLink>
        );
      })}
    </nav>
  );
}

/** Celular/tablet: barra de abas fixa embaixo, ao alcance do polegar. */
function BottomNav() {
  const { pathname } = useLocation();
  const { podeEscrever } = useAuth();
  const ativo = (l: Item) => (l.end ? pathname === l.to : pathname.startsWith(l.to));

  return (
    <nav
      aria-label="Principal"
      className="fixed inset-x-0 bottom-0 z-40 border-t border-white/10 bg-sidebar pb-[env(safe-area-inset-bottom)] lg:hidden"
    >
      <ul className="mx-auto flex max-w-lg justify-around px-1">
        {LINKS.filter((l) => !l.admin || podeEscrever).map((l) => {
          const Icone = l.icon;
          const atual = ativo(l);
          return (
            <li key={l.to} className="min-w-0 flex-1">
              <NavLink
                to={l.to}
                end={l.end}
                aria-label={l.label}
                className={cn(
                  "flex flex-col items-center gap-0.5 rounded-xl px-1 py-2 text-[11px] font-semibold transition-colors",
                  atual ? "text-accent" : "text-white/60 hover:text-white",
                )}
              >
                <Icone size={20} aria-hidden />
                <span className="max-w-full truncate">{l.curto}</span>
              </NavLink>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}

function Sessao({ compacto }: { compacto?: boolean }) {
  const { estado, usuario, sair } = useAuth();
  if (estado !== "autenticado" || !usuario) return null;
  return (
    <div className="flex items-center gap-2 text-sm text-white/70">
      {!compacto && (
        <span className="min-w-0 flex-1 truncate">
          <span className="block truncate font-semibold text-white">{usuario.username}</span>
          <span className="block text-xs">{usuario.role === "admin" ? "Administrador" : "Somente leitura"}</span>
        </span>
      )}
      <IconButton label="Sair" onClick={() => void sair()} className="text-white/70 hover:bg-white/10 hover:text-white">
        <LogOut size={17} />
      </IconButton>
    </div>
  );
}

export function AppShell({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-screen bg-backdrop lg:p-5">
      <div className="mx-auto flex min-h-screen max-w-[1600px] flex-col gap-4 bg-bg p-3 sm:p-4 lg:min-h-[calc(100vh-2.5rem)] lg:flex-row lg:gap-6 lg:rounded-[2rem] lg:p-5 lg:shadow-card">
        {/* Desktop: sidebar escura fixa */}
        <aside className="sticky top-10 hidden h-[calc(100vh-5rem)] w-60 shrink-0 flex-col rounded-3xl bg-sidebar p-5 lg:flex">
          <Logo />
          <div className="mt-8">
            <NavItems />
          </div>
          <div className="mt-auto space-y-3">
            <Sessao />
            <div className="flex items-center justify-between text-sm text-white/70">
              Tema
              <ThemeToggle onDark />
            </div>
          </div>
        </aside>

        {/* Mobile/tablet: cabeçalho enxuto + abas fixas embaixo */}
        <header className="flex items-center justify-between gap-3 rounded-3xl bg-sidebar px-4 py-3 lg:hidden">
          <Logo />
          <div className="flex items-center gap-1">
            <Sessao compacto />
            <ThemeToggle onDark />
          </div>
        </header>
        <BottomNav />

        <div className="min-w-0 flex-1 pb-24 lg:pb-0">{children}</div>
      </div>
    </div>
  );
}
