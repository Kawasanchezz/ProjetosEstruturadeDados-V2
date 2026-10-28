import { useReducedMotion } from "motion/react";
import { useState } from "react";
import { Bar, BarChart, CartesianGrid, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { ProdutoResumo } from "@/api/types";
import { formatBRL } from "@/lib/money";

interface Props {
  produtos: ProdutoResumo[];
}

/** Barras escuras; a barra sob o cursor fica lima (como no layout de referência). */
export function ProductCostChart({ produtos }: Props) {
  const reduzir = useReducedMotion();
  const [ativo, setAtivo] = useState<number | null>(null);
  const dados = produtos.map((p) => ({ nome: p.nome, valor: Number(p.custo_total) }));
  const resumo = dados.map((d) => `${d.nome} ${formatBRL(d.valor)}`).join(", ");

  return (
    <div role="img" aria-label={`Custo total por produto: ${resumo}`} className="h-56">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={dados} margin={{ top: 28, right: 8, left: -8, bottom: 0 }} onMouseLeave={() => setAtivo(null)}>
          <CartesianGrid vertical={false} stroke="rgb(var(--border) / 0.1)" />
          <XAxis dataKey="nome" tickLine={false} axisLine={false} tick={{ fill: "rgb(var(--muted))", fontSize: 12 }} />
          <YAxis
            tickLine={false}
            axisLine={false}
            width={56}
            tick={{ fill: "rgb(var(--muted))", fontSize: 11 }}
            tickFormatter={(v: number) => (v >= 1000 ? `${(v / 1000).toFixed(0)}k` : String(v))}
          />
          <Tooltip
            cursor={false}
            formatter={(valor) => [formatBRL(Number(valor)), "Custo total"]}
            contentStyle={{ background: "rgb(var(--sidebar))", border: "none", borderRadius: 12, color: "#fff" }}
            itemStyle={{ color: "#fff" }}
            labelStyle={{ color: "rgb(var(--accent))", fontWeight: 700 }}
          />
          <Bar
            dataKey="valor"
            radius={[10, 10, 10, 10]}
            maxBarSize={28}
            isAnimationActive={!reduzir}
            animationDuration={800}
            onMouseEnter={(_, i) => setAtivo(i)}
          >
            {dados.map((d, i) => (
              <Cell key={d.nome} fill={ativo === i ? "rgb(var(--accent))" : "rgb(var(--fg) / 0.88)"} />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
