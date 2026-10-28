/**
 * Formata dinheiro em pt-BR sem passar por float quando a entrada é string.
 * "8080.00" -> "R$ 8.080,00"
 */
export function formatBRL(valor: string | number): string {
  const texto = typeof valor === "number" ? valor.toFixed(2) : valor.trim();
  const m = /^(-?)(\d+)(?:\.(\d+))?$/.exec(texto);
  if (!m) return "R$ 0,00";
  const [, sinalTexto, inteiro, frac = ""] = m;
  const centavos = (frac + "00").slice(0, 2);
  const milhar = inteiro.replace(/^0+(?=\d)/, "").replace(/\B(?=(\d{3})+(?!\d))/g, ".");
  const negativo = sinalTexto === "-" && (Number(inteiro) !== 0 || Number(centavos) !== 0);
  return `${negativo ? "-" : ""}R$ ${milhar},${centavos}`;
}

/** Diferença com sinal explícito: "+R$ 1,00" / "−R$ 2,50" / "R$ 0,00". */
export function formatDiferenca(valor: string): string {
  const base = formatBRL(valor);
  if (base === "R$ 0,00") return base;
  if (base.startsWith("-")) return base.replace("-R$", "−R$");
  return `+${base}`;
}

export const sinal = (valor: string): -1 | 0 | 1 => {
  const n = Number(valor);
  return n > 0 ? 1 : n < 0 ? -1 : 0;
};

/** Converte "12,5" / "12.50" digitado pelo usuário para "12.50"; null se inválido. */
export function normalizarPreco(entrada: string): string | null {
  const t = entrada.trim().replace(",", ".");
  if (!/^\d{1,10}(\.\d{1,2})?$/.test(t)) return null;
  const [i, f = ""] = t.split(".");
  return `${i}.${(f + "00").slice(0, 2)}`;
}
