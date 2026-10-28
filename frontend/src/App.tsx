import { AnimatePresence, MotionConfig } from "motion/react";
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

/** Sem login (API aberta) mostra o app; com login exige sessão. A API valida a autorização de novo a cada chamada. */
function Gate() {
  const { estado } = useAuth();
  if (estado === "carregando") {
    return (
      <div className="grid min-h-screen place-items-center bg-backdrop" aria-busy="true" aria-label="Carregando">
        <Skeleton className="h-40 w-72" />
      </div>
    );
  }
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
