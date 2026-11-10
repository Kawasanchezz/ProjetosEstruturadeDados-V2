import { AnimatePresence, MotionConfig } from "motion/react";
import { Network } from "lucide-react";
import { lazy, Suspense } from "react";
import { Route, Routes, useLocation } from "react-router-dom";
import { RequireAdmin } from "@/auth/RequireAdmin";
import { useAuth } from "@/auth/AuthContext";
import { AppShell } from "@/components/layout/AppShell";
import { PageTransition } from "@/components/layout/PageTransition";
import { Skeleton } from "@/components/ui/Card";
import { ToastProvider } from "@/components/ui/Toast";
import Dashboard from "@/pages/Dashboard";
import LoginPage from "@/pages/LoginPage";
import NotFound from "@/pages/NotFound";

// Telas pesadas (gráficos, árvore) carregam sob demanda.
const CrudPage = lazy(() => import("@/pages/CrudPage"));
const ExplosionPage = lazy(() => import("@/pages/ExplosionPage"));
const ImplosionPage = lazy(() => import("@/pages/ImplosionPage"));
const SimulationPage = lazy(() => import("@/pages/SimulationPage"));

function AnimatedRoutes() {
  const location = useLocation();
  // A chave usa só o 1º segmento: trocar de produto (/explosao/1 → /explosao/2) não refaz a transição de página.
  const chave = location.pathname.split("/")[1] ?? "";

  return (
    <AnimatePresence mode="wait" initial={false}>
      <PageTransition key={chave}>
        <Suspense fallback={<Skeleton className="h-72 w-full" />}>
        <Routes location={location}>
          <Route path="/" element={<Dashboard />} />
          <Route path="/explosao/:id?" element={<ExplosionPage />} />
          <Route path="/simulacao/:id?" element={<SimulationPage />} />
          <Route path="/preco/:id?" element={<RequireAdmin><ImplosionPage /></RequireAdmin>} />
          <Route path="/cadastro/:id?" element={<RequireAdmin><CrudPage /></RequireAdmin>} />
          <Route path="*" element={<NotFound />} />
        </Routes>
        </Suspense>
      </PageTransition>
    </AnimatePresence>
  );
}

/** Enquanto a API sobe (a JVM leva alguns segundos), mostra uma tela de carregamento em vez de um bloco vazio. */
function TelaCarregando() {
  return (
    <main className="grid min-h-screen place-items-center bg-backdrop p-4" aria-busy="true" role="status">
      <div className="card w-full max-w-sm space-y-4 rounded-3xl p-7 text-center">
        <span className="mx-auto grid h-12 w-12 place-items-center rounded-full bg-accent text-ink">
          <Network size={22} aria-hidden />
        </span>
        <div>
          <h1 className="text-xl font-bold leading-tight">TreeBOM</h1>
          <p className="mt-1 text-sm text-muted">Iniciando o servidor… isso leva alguns segundos.</p>
        </div>
        <div className="h-1.5 overflow-hidden rounded-full bg-black/10">
          <div className="h-full w-1/3 animate-[carregando_1.2s_ease-in-out_infinite] rounded-full bg-accent" />
        </div>
        <p className="text-xs text-muted">Se demorar, confira se o terminal mostra “PRONTO”.</p>
      </div>
    </main>
  );
}

/** Sem login (API aberta) mostra o app; com login exige sessão. A API valida a autorização de novo a cada chamada. */
function Gate() {
  const { estado } = useAuth();
  if (estado === "carregando") return <TelaCarregando />;
  if (estado === "anonimo") return <LoginPage />;
  return (
    <AppShell>
      <AnimatedRoutes />
    </AppShell>
  );
}

export default function App() {
  return (
    // reducedMotion="user": o Framer Motion desliga animações de transform quando o SO pede menos movimento.
    <MotionConfig reducedMotion="user">
      <ToastProvider>
        <Gate />
      </ToastProvider>
    </MotionConfig>
  );
}
