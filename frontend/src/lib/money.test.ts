import { describe, expect, it } from "vitest";
import { formatBRL, formatDiferenca, normalizarPreco, sinal } from "./money";

describe("formatBRL", () => {
  it.each([
    ["8080.00", "R$ 8.080,00"],
    ["7.5", "R$ 7,50"],
    ["0", "R$ 0,00"],
    ["1234567.89", "R$ 1.234.567,89"],
    ["-2.5", "-R$ 2,50"],
    ["-0.00", "R$ 0,00"],
    ["abc", "R$ 0,00"],
  ])("formata %s como %s", (entrada, esperado) => expect(formatBRL(entrada)).toBe(esperado));

  it("aceita number", () => expect(formatBRL(40400)).toBe("R$ 40.400,00"));
});

describe("diferença e preço digitado", () => {
  it("mostra sinal explícito", () => {
    expect(formatDiferenca("1.00")).toBe("+R$ 1,00");
    expect(formatDiferenca("-2.50")).toBe("−R$ 2,50");
    expect([sinal("1"), sinal("-1"), sinal("0")]).toEqual([1, -1, 0]);
  });

  it.each([["1,7", "1.70"], ["12.50", "12.50"], [" 3 ", "3.00"]])("normaliza %s", (e, s) => expect(normalizarPreco(e)).toBe(s));
  it.each(["", "abc", "-1", "1.234", "1,2,3", "12345678901"])("rejeita %j", (e) => expect(normalizarPreco(e)).toBeNull());
});
