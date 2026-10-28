import { ArrowDown, ArrowRight, RotateCcw, TrendingDown, TrendingUp, Zap } from "lucide-react";
import { useMemo, useRef, useState, type FormEvent } from "react";
import { useAtualizarPreco, useComponentes, useExplosao, useInvalidarArvore } from "@/api/hooks";
import type { Implosao, ProdutoResumo } from "@/api/types";
import { TreeView } from "@/components/tree/TreeView";
import { Button } from "@/components/ui/Button";
import { Card, ErrorState, Skeleton } from "@/components/ui/Card";
import { Field } from "@/components/ui/Field";
import { Money } from "@/components/ui/Money";
import { useToast } from "@/components/ui/Toast";
import { useGSAP } from "@/lib/animations/gsap";
import { playImplosion } from "@/lib/animations/implosion";
import { cn } from "@/lib/cn";
import { formatBRL, formatDiferenca, normalizarPreco, sinal } from "@/lib/money";
import { applyOverrides, findNode } from "@/lib/tree";

interface Execucao {
  token: number;
  resultado: Implosao;
  alteradoId: number;
  precoAntes: string;
  precoDepois: string;
  revelados: number;
  ativa: boolean;
}

/** Subir o custo é ruim (vermelho); baixar é bom (verde). */
const corDiferenca = (d: string) => (sinal(d) > 0 ? "text-bad" : sinal(d) < 0 ? "text-good" : "text-muted");

export function ImplosionView({ produto }: { produto: ProdutoResumo }) {
  const { data, isLoading, isError, error, refetch } = useExplosao(produto.id);
  const componentes = useComponentes(produto.id);
  const atualizar = useAtualizarPreco();
  const invalidar = useInvalidarArvore();
  const toast = useToast();

  const escopo = useRef<HTMLDivElement>(null);
  const [alvoId, setAlvoId] = useState<number | null>(null);
  const [precoTxt, setPrecoTxt] = useState("");
  const [erro, setErro] = useState<string>();
  const [exec, setExec] = useState<Execucao | null>(null);

  const raiz = data?.raiz;

  // Animação da implosão (GSAP). Só reinicia quando o token da execução muda.
  useGSAP(
    () => {
      if (!exec?.ativa || !escopo.current) return;
      const ids = exec.resultado.ids_afetados;
      playImplosion({
        scope: escopo.current,
        ids,
        onStep: (i) => setExec((e) => (e ? { ...e, revelados: Math.max(e.revelados, i + 1) } : e)),
        onComplete: () => {
          // Só devolve o controle aos dados reais depois de recarregá-los.
          void invalidar().then(() => setExec((e) => (e ? { ...e, ativa: false } : e)));
        },
      });
    },
    { scope: escopo, dependencies: [exec?.token], revertOnUpdate: false },
  );

  const rotulos = useMemo(() => {
    const itens = componentes.data?.itens ?? [];
    const porId = new Map(itens.map((n) => [n.id, n]));
    const nivel = (id: number): number => {
      const pai = porId.get(id)?.parent_id;
      return pai == null ? 0 : 1 + nivel(pai);
    };
    return itens.map((n) => {
      const pai = n.parent_id == null ? null : porId.get(n.parent_id)?.nome;
      return { id: n.id, texto: `${"— ".repeat(nivel(n.id))}${n.nome}${pai ? ` (em ${pai})` : ""}` };
    });
  }, [componentes.data]);

  const arvoreExibida = useMemo(() => {
    if (!raiz) return undefined;
    if (!exec?.ativa) return raiz;
    const { passos } = exec.resultado;
    const costs: Record<number, string> = {};
    passos.forEach((p, i) => {
      costs[p.id] = i < exec.revelados ? p.depois : p.antes;
    });
    return applyOverrides(raiz, {
      costs,
      prices: { [exec.alteradoId]: exec.revelados > 0 ? exec.precoDepois : exec.precoAntes },
    });
  }, [raiz, exec]);

  const selecionar = (id: number) => {
    setAlvoId(id);
    setErro(undefined);
    if (raiz) setPrecoTxt(findNode(raiz, id)?.preco ?? "");
  };

  const aplicar = (ev: FormEvent) => {
    ev.preventDefault();
    if (alvoId === null || !raiz) return setErro("Escolha o componente a atualizar");
    const novo = normalizarPreco(precoTxt);
    if (novo === null) return setErro("Preço inválido: use até 2 casas decimais (ex.: 1,70)");
    setErro(undefined);
    const precoAntes = findNode(raiz, alvoId)?.preco ?? "0.00";
    atualizar.mutate(
      { id: alvoId, preco: novo },
      {
        onSuccess: (resultado) => {
          setExec({ token: Date.now(), resultado, alteradoId: alvoId, precoAntes, precoDepois: novo, revelados: 0, ativa: true });
          toast.success("Preço atualizado. Propagando até a raiz…");
        },
        onError: (e) => toast.error(e.message),
      },
    );
  };

  const reproduzir = () => setExec((e) => (e ? { ...e, token: Date.now(), revelados: 0, ativa: true } : e));

  if (isError) return <ErrorState message={error.message} onRetry={() => refetch()} />;
  if (isLoading || !raiz || !arvoreExibida) return <Skeleton className="h-[68vh] min-h-[420px] w-full" />;

  const passos = exec?.resultado.passos ?? [];
  const caminhoIds = new Set(exec?.resultado.ids_afetados ?? []);
  const concluida = !!exec && !exec.ativa;

  return (
    <div ref={escopo} className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_380px]">
      <TreeView
        key={raiz.id}
        raiz={arvoreExibida}
        selectedId={alvoId}
        onSelect={selecionar}
        highlightIds={caminhoIds}
        label="Árvore do produto (clique em um nó para escolhê-lo)"
      />

      <div className="space-y-4">
        <Card>
          <form onSubmit={aplicar} className="space-y-4" noValidate>
            <Field label="Componente" htmlFor="componente">
              <select
                id="componente"
                className="field"
                value={alvoId ?? ""}
                onChange={(e) => (e.target.value ? selecionar(Number(e.target.value)) : setAlvoId(null))}
              >
                <option value="">Selecione…</option>
                {rotulos.map((r) => (
                  <option key={r.id} value={r.id}>
                    {r.texto}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Novo preço (R$)" htmlFor="novo-preco" error={erro} hint="Ou clique em um nó da árvore.">
              <input
                id="novo-preco"
                inputMode="decimal"
                className="field num"
                value={precoTxt}
                onChange={(e) => setPrecoTxt(e.target.value)}
                aria-invalid={!!erro}
              />
            </Field>
            <div className="flex flex-wrap gap-2">
              <Button type="submit" variant="primary" disabled={atualizar.isPending || exec?.ativa}>
                <Zap size={16} aria-hidden />
                {atualizar.isPending ? "Aplicando…" : "Aplicar e implodir"}
              </Button>
              {concluida && (
                <Button onClick={reproduzir}>
                  <RotateCcw size={16} aria-hidden />
                  Reproduzir
                </Button>
              )}
            </div>
          </form>
        </Card>

        <Card aria-live="polite">
          <h2 className="mb-1 font-semibold">Propagação até a raiz</h2>
          {!exec ? (
            <p className="text-sm text-muted">
              Atualize o preço de um componente: o novo custo sobe pela árvore, nó a nó, até o produto.
            </p>
          ) : (
            <>
              <p className="mb-4 text-xs text-muted">
                <span className="text-bad">Vermelho</span>: o custo subiu · <span className="text-good">verde</span>: o custo caiu
              </p>
              <ol>
                {passos.map((p, i) => {
                  const revelado = exec.revelados > i || !exec.ativa;
                  return (
                    <li key={`${exec.token}-${p.id}`} className="relative">
                      {i > 0 && (
                        <span
                          data-ladder-link={i}
                          aria-hidden
                          className="absolute -top-3 left-[15px] h-3 w-0.5 origin-bottom bg-accent2"
                        />
                      )}
                      <div
                        data-ladder-row={i}
                        style={{ opacity: 0 }}
                        className={cn("mb-3 flex items-start gap-3 rounded-xl bg-fg/5 p-3", i === passos.length - 1 && "mb-0")}
                      >
                        <span className="grid h-8 w-8 shrink-0 place-items-center rounded-full bg-sidebar text-xs font-bold text-white">
                          {i === 0 ? <ArrowDown size={14} className="rotate-180" aria-hidden /> : i + 1}
                        </span>
                        <div className="min-w-0 flex-1">
                          <p className="truncate text-sm font-semibold">
                            {p.nome}
                            {i === passos.length - 1 && <span className="ml-2 text-xs font-normal text-muted">raiz</span>}
                          </p>
                          <p className="num flex flex-wrap items-center gap-x-1.5 text-xs text-muted">
                            <span>{formatBRL(p.antes)}</span>
                            <ArrowRight size={12} aria-hidden />
                            <Money value={revelado ? p.depois : p.antes} className="font-semibold text-fg" />
                          </p>
                        </div>
                        <span className={cn("num flex shrink-0 items-center gap-1 text-sm font-semibold", corDiferenca(p.diferenca))}>
                          {sinal(p.diferenca) < 0 ? <TrendingDown size={14} aria-hidden /> : <TrendingUp size={14} aria-hidden />}
                          {formatDiferenca(p.diferenca)}
                        </span>
                      </div>
                    </li>
                  );
                })}
              </ol>

              {concluida && (
                <div className="mt-4 rounded-xl border border-border/10 p-3 text-sm">
                  <p className="text-xs uppercase tracking-wide text-muted">Total do produto</p>
                  <p className="num mt-1 flex flex-wrap items-center gap-2">
                    <span className="text-muted">{formatBRL(exec.resultado.total_antes)}</span>
                    <ArrowRight size={14} aria-hidden />
                    <span className="font-bold">{formatBRL(exec.resultado.total_depois)}</span>
                    <span className={cn("font-semibold", corDiferenca(passos[passos.length - 1]?.diferenca ?? "0"))}>
                      ({formatDiferenca(passos[passos.length - 1]?.diferenca ?? "0")})
                    </span>
                  </p>
                </div>
              )}
            </>
          )}
        </Card>
      </div>
    </div>
  );
}
