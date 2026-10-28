import { useReducedMotion } from "motion/react";
import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from "recharts";
import type { FatiaCusto } from "@/lib/composition";
import { formatBRL } from "@/lib/money";

// Paleta categórica com bom contraste nos dois temas.
export const PALETA = ["#8b6cff", "#22d3ee", "#f472b6", "#fbbf24", "#34d399", "#60a5fa", "#fb923c", "#a3e635"];

const pct = (n: number) => `${n.toFixed(1).replace(".", ",")}%`;

export function CostCompositionChart({ dados }: { dados: FatiaCusto[] }) {
  const reduzir = useReducedMotion();
  const resumo = dados.map((d) => `${d.nome} ${pct(d.percentual)}`).join(", ");

  return (
    <div>
      <div role="img" aria-label={`Composição do custo: ${resumo}`} className="h-64">
        <ResponsiveContainer width="100%" height="100%">
          <PieChart>
            <Pie
              data={dados}
              dataKey="valor"
              nameKey="nome"
              innerRadius="58%"
              outerRadius="92%"
              paddingAngle={2}
              stroke="none"
              isAnimationActive={!reduzir}
              animationDuration={900}
            >
              {dados.map((d, i) => (
                <Cell key={d.nome} fill={PALETA[i % PALETA.length]} />
              ))}
            </Pie>
            <Tooltip
              formatter={(valor, nome) => [formatBRL(Number(valor)), String(nome)]}
              contentStyle={{
                background: "rgb(var(--surface))",
                border: "1px solid rgb(var(--border) / 0.15)",
                borderRadius: 12,
                color: "rgb(var(--fg))",
              }}
              itemStyle={{ color: "rgb(var(--fg))" }}
            />
          </PieChart>
        </ResponsiveContainer>
      </div>
      <ul className="mt-2 space-y-1.5">
        {dados.map((d, i) => (
          <li key={d.nome} className="flex items-center justify-between gap-3 text-sm">
            <span className="flex min-w-0 items-center gap-2">
              <span className="h-2.5 w-2.5 shrink-0 rounded-full" style={{ background: PALETA[i % PALETA.length] }} aria-hidden />
              <span className="truncate">{d.nome}</span>
            </span>
            <span className="num shrink-0 text-muted">
              {formatBRL(d.valor)} · {pct(d.percentual)}
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}
