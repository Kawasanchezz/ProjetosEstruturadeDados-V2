import { Plus, Search } from "lucide-react";
import { lazy, Suspense, useMemo, useRef, useState } from "react";
import { useProdutos } from "@/api/hooks";
import { useAuth } from "@/auth/AuthContext";
import type { ProdutoResumo } from "@/api/types";
import { ButtonLink } from "@/components/ui/Button";
import { EmptyState, ErrorState, Skeleton } from "@/components/ui/Card";
import { Money } from "@/components/ui/Money";
import { ProductCarousel } from "@/features/products/ProductCarousel";
import { SeedButtons } from "@/features/products/SeedButtons";
import { useGSAP } from "@/lib/animations/gsap";
import { playHeroIntro, setupScrollEffects } from "@/lib/animations/landing";
import { normalizar } from "@/lib/tree";

// O gráfico (Recharts) carrega sob demanda.
const ProductCostChart = lazy(() => import("@/components/charts/ProductCostChart").then((m) => ({ default: m.ProductCostChart })));

function HeroArt() {
  return (
    <svg viewBox="0 0 320 240" className="h-full w-full" aria-hidden>
      <g fill="none" stroke="rgb(var(--accent))" strokeWidth="2.5" strokeLinecap="round" opacity="0.9">
        <path data-hero-line d="M160 52 C160 80 70 80 70 112" />
        <path data-hero-line d="M160 52 C160 80 160 80 160 112" />
        <path data-hero-line d="M160 52 C160 80 250 80 250 112" />
        <path data-hero-line d="M70 140 C70 165 40 165 40 190" />
        <path data-hero-line d="M70 140 C70 165 100 165 100 190" />
        <path data-hero-line d="M250 140 C250 165 220 165 220 190" />
        <path data-hero-line d="M250 140 C250 165 280 165 280 190" />
      </g>
      {[[160, 40, 18], [70, 126, 14], [160, 126, 14], [250, 126, 14], [40, 200, 10], [100, 200, 10], [220, 200, 10], [280, 200, 10]].map(([x, y, r], i) => (
        <circle key={i} data-hero-dot cx={x} cy={y} r={r} fill={i === 0 ? "rgb(var(--accent))" : i < 4 ? "#ffffff" : "rgb(255 255 255 / 0.45)"} />
      ))}
    </svg>
  );
}

function Hero() {
  const { podeEscrever } = useAuth();
  return (
    <section aria-label="Apresentação" className="relative overflow-hidden rounded-3xl bg-sidebar p-6 text-white">
      <div aria-hidden className="pointer-events-none absolute -right-10 -top-10" data-parallax>
        <div data-orb className="h-40 w-40 rounded-full bg-accent/25 blur-3xl" />
      </div>
      <p data-hero-item className="mb-3 inline-block rounded-full border border-white/20 px-3 py-1 text-xs font-semibold text-accent">
        Estrutura de Dados II · Árvore genérica
      </p>
      <h2 data-hero-item className="text-2xl font-extrabold leading-tight tracking-tight">
        Lista de materiais que <span className="hl">explode</span> e <span className="hl">implode</span> custos.
      </h2>
      <p data-hero-item className="mt-3 text-sm text-white/65">
        Cadastre a fórmula de um produto, explore a árvore completa, simule a produção e veja o impacto de cada
        mudança de preço subindo até a raiz.
      </p>
      <div data-hero-item className="mx-auto my-4 h-36 w-full max-w-[220px]">
        <HeroArt />
      </div>
      <div data-hero-item className="flex flex-col gap-2">
        <SeedButtons irParaExplosao onDark />
        {podeEscrever && (
          <ButtonLink to="/cadastro" variant="onDark">
            <Plus size={16} aria-hidden /> Novo produto
          </ButtonLink>
        )}
      </div>
    </section>
  );
}

function Resumo({ produtos }: { produtos: ProdutoResumo[] }) {
  const totalComp = produtos.reduce((a, p) => a + p.total_componentes, 0);
  const custo = produtos.reduce((a, p) => a + Number(p.custo_total), 0).toFixed(2);
  const prof = Math.max(0, ...produtos.map((p) => p.profundidade));

  const itens = [
    { rotulo: "Produtos", valor: <span className="num">{produtos.length}</span>, tile: "bg-accent text-ink" },
    { rotulo: "Componentes", valor: <span className="num">{totalComp}</span>, tile: "bg-tile3" },
    { rotulo: "Custo somado", valor: <Money value={custo} countOnMount />, tile: "bg-tile1" },
    { rotulo: "Maior profundidade", valor: <span className="num">{prof} níveis</span>, tile: "bg-tile2" },
  ];
  return (
    <section aria-labelledby="resumo-titulo" className="card rounded-3xl p-5">
      <h2 id="resumo-titulo" className="mb-3 text-lg font-bold">
        Resumo
      </h2>
      <dl className="grid grid-cols-2 gap-2.5">
        {itens.map((i) => (
          <div key={i.rotulo} className={`rounded-2xl p-3 ${i.tile}`}>
            <dt className="text-xs opacity-70">{i.rotulo}</dt>
            <dd className="mt-1 whitespace-nowrap text-base font-bold">{i.valor}</dd>
          </div>
        ))}
      </dl>
    </section>
  );
}

export default function Dashboard() {
  const { data, isLoading, isError, error, refetch } = useProdutos();
  const raiz = useRef<HTMLDivElement>(null);
  const [busca, setBusca] = useState("");

  // GSAP: intro do hero (timeline + stagger) e ScrollTrigger. O escopo garante limpeza no unmount.
  useGSAP(
    () => {
      if (!raiz.current) return;
      playHeroIntro(raiz.current);
      return setupScrollEffects(raiz.current);
    },
    { scope: raiz },
  );

  const produtos = useMemo(() => data?.itens ?? [], [data]);
  const filtrados = useMemo(() => {
    const q = normalizar(busca);
    return q ? produtos.filter((p) => normalizar(p.nome).includes(q)) : produtos;
  }, [produtos, busca]);

  return (
    <div ref={raiz}>
      <header className="mb-6 flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-extrabold tracking-tight sm:text-3xl">Visão geral</h1>
          <p className="mt-1 text-sm text-muted">Fórmulas cadastradas e custos de cada produto.</p>
        </div>
        <div className="relative w-full sm:w-72">
          <Search size={16} className="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-muted" aria-hidden />
          <input
            type="search"
            value={busca}
            onChange={(e) => setBusca(e.target.value)}
            placeholder="Buscar produtos"
            aria-label="Buscar produtos"
            className="field rounded-full bg-surface pl-10"
          />
        </div>
      </header>

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_340px]">
        <div className="min-w-0 space-y-6">
          <section aria-labelledby="produtos-titulo">
            <h2 id="produtos-titulo" className="mb-3 text-xl font-bold">
              Produtos cadastrados
            </h2>
            {isLoading ? (
              <div className="grid gap-4 sm:grid-cols-2 2xl:grid-cols-3" aria-busy="true" aria-label="Carregando produtos">
                {[0, 1, 2].map((i) => (
                  <Skeleton key={i} className="h-44 rounded-3xl" />
                ))}
              </div>
            ) : isError ? (
              <ErrorState message={error.message} onRetry={() => refetch()} />
            ) : produtos.length === 0 ? (
              <EmptyState
                title="Nenhum produto ainda"
                description="Carregue o exemplo da caneta ou do computador para ver a árvore em ação."
                action={<SeedButtons irParaExplosao />}
              />
            ) : filtrados.length === 0 ? (
              <p className="card rounded-3xl p-6 text-center text-sm text-muted">Nenhum produto encontrado para “{busca}”.</p>
            ) : (
              <ProductCarousel produtos={filtrados} />
            )}
          </section>

          <div className="grid gap-6 lg:grid-cols-2">
            <section aria-labelledby="grafico-titulo" className="card rounded-3xl p-5">
              <h2 id="grafico-titulo" className="text-lg font-bold">
                Custo por produto
              </h2>
              <p className="mb-2 text-xs text-muted">Custo total de cada fórmula cadastrada</p>
              {produtos.length === 0 ? (
                <p className="py-16 text-center text-sm text-muted">Sem dados para exibir.</p>
              ) : (
                <Suspense fallback={<Skeleton className="h-56" />}>
                  <ProductCostChart produtos={produtos} />
                </Suspense>
              )}
            </section>

            <Resumo produtos={produtos} />
          </div>
        </div>

        <div className="min-w-0 space-y-6">
          <Hero />
        </div>
      </div>
    </div>
  );
}
