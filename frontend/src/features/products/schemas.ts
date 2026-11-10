import { z } from "zod";
import { normalizarPreco } from "@/lib/money";

// Os campos do formulário são strings (como digitados); a conversão acontece no envio.
const nome = z.string().trim().min(1, "Informe o nome").max(120, "Máximo de 120 caracteres");
const preco = z.string().refine((v) => normalizarPreco(v) !== null, "Use até 2 casas decimais, ex.: 12,50");
const quantidade = z
  .string()
  .refine((v) => /^\d+$/.test(v.trim()) && Number(v) >= 1 && Number(v) <= 1_000_000, "Inteiro entre 1 e 1.000.000");

export const produtoSchema = z.object({ nome, preco, quantidade });
export const componenteSchema = produtoSchema.extend({ parent_id: z.string().min(1, "Escolha o pai") });

export type ProdutoForm = z.infer<typeof produtoSchema>;
export type ComponenteForm = z.infer<typeof componenteSchema>;
