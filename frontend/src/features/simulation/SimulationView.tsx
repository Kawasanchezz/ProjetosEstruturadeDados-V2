import { AnimatePresence, motion } from "motion/react";
import { Calculator } from "lucide-react";
import { useMemo, useState, type FormEvent } from "react";
import { useSimular } from "@/api/hooks";
import type { ProdutoResumo } from "@/api/types";
import { CostCompositionChart } from "@/components/charts/CostCompositionChart";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { Field } from "@/components/ui/Field";
import { Money } from "@/components/ui/Money";
import { useToast } from "@/components/ui/Toast";
import { rowVariants } from "@/lib/animations/variants";
import { composicaoDeCusto } from "@/lib/composition";
import { formatBRL } from "@/lib/money";

export function SimulationView({ produto }: { produto: ProdutoResumo }) {
  const [texto, setTexto] = useState("10");
  const [erro, setErro] = useState<string>();
  const simular = useSimular();
  const toast = useToast();
  const resultado = simular.data?.produto_id === produto.id ? simular.data : undefined;
  const fatias = useMemo(() => (resultado ? composicaoDeCusto(resultado.linhas) : []), [resultado]);

  const enviar = (e: FormEvent) => {
    e.preventDefault();
    const n = Number(texto);
    if (!Number.isInteger(n) || n < 1 || n > 1_000_000) {
      setErro("Informe um número inteiro entre 1 e 1.000.000");
      return;
    }
    setErro(undefined);
    simular.mutate({ produtoId: produto.id, quantidade: n }, { onError: (err) => toast.error(err.message) });
  };

  return (
    <div className="space-y-6">
      <Card>
        <form onSubmit={enviar} className="flex flex-wrap items-end gap-4" noValidate>
          <div className="w-full sm:w-56">
            <Field label="Quantas unidades?" htmlFor="unidades" error={erro}>
              <input
                id="unidades"
                inputMode="numeric"
                value={texto}
                onChange={(e) => setTexto(e.target.value)}
                aria-invalid={!!erro}
                aria-describedby={erro ? "unidades-erro" : undefined}
                className="field num"
              />
            </Field>
          </div>
          <Button type="submit" variant="primary" disabled={simular.isPending}>
            <Calculator size={16} aria-hidden />
            {simular.isPending ? "Calculando…" : `Simular produção de ${produto.nome}`}
          </Button>
        </form>
      </Card>

      <AnimatePresence mode="wait">
        {resultado && (
          <motion.div
            key={`${resultado.produto_id}-${resultado.unidades}`}
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0 }}
            className="space-y-6"
          >
            <Card className="bg-accent/25">
              <p className="text-xs uppercase tracking-wide text-muted">
                Valor total de aquisição · {resultado.unidades.toLocaleString("pt-BR")} un.
              </p>
              <p className="mt-1 text-4xl font-bold sm:text-5xl" aria-live="polite">
                <Money value={resultado.total} countOnMount duration={1.4} className="text-gradient" />
              </p>
            </Card>

            <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_380px]">
              <Card className="overflow-hidden p-0!">
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <caption className="sr-only">Componentes necessários e custo de aquisição</caption>
                    <thead>
                      <tr className="border-b border-border/10 text-left text-xs uppercase tracking-wide text-muted">
                        <th scope="col" className="px-3 py-3 sm:px-5">Componente</th>
                        <th scope="col" className="px-2 py-3 text-right sm:px-3">Qtd.</th>
                        <th scope="col" className="px-3 py-3 text-right sm:px-5">Custo</th>
                      </tr>
                    </thead>
                    <tbody>
                      {resultado.linhas.map((l, i) => (
                        <motion.tr
                          key={l.id}
                          custom={i}
                          variants={rowVariants}
                          initial="hidden"
                          animate="show"
                          className="border-b border-border/5 last:border-0"
                        >
                          <th scope="row" className="py-2.5 pr-2 text-left font-medium" style={{ paddingLeft: 8 + (l.caminho.length - 1) * 10 }}>
                            {l.nome}
                          </th>
                          <td className="num px-2 py-2.5 text-right sm:px-3">{l.quantidade_total.toLocaleString("pt-BR")}</td>
                          <td className="num whitespace-nowrap px-3 py-2.5 text-right sm:px-5">{formatBRL(l.custo)}</td>
                        </motion.tr>
                      ))}
                    </tbody>
                    <tfoot>
                      <tr className="bg-fg/5 font-semibold">
                        <th scope="row" className="px-3 py-3 text-left sm:px-5">Total</th>
                        <td />
                        <td className="num whitespace-nowrap px-3 py-3 text-right sm:px-5">{formatBRL(resultado.total)}</td>
                      </tr>
                    </tfoot>
                  </table>
                </div>
              </Card>

              <Card>
                <h2 className="mb-2 font-semibold">Composição do custo</h2>
                {fatias.length ? <CostCompositionChart dados={fatias} /> : <p className="text-sm text-muted">Sem custos a exibir.</p>}
              </Card>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
