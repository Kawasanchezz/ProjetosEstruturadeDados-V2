package com.treebom.web;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validação de entrada estrita: campos extras são proibidos, inteiros não aceitam
 * texto/booleano/decimal, preços têm no máximo 12 dígitos e 2 casas. As respostas de erro nunca ecoam o valor enviado.
 */
public final class Validacao {
    private Validacao() {}

    public static final long MAX_ID = 2_147_483_647L; // cabe em INTEGER de 32 bits: ids maiores nem chegam ao banco
    public static final int MAX_QUANTIDADE = 1_000_000;

    public record Erro(List<String> loc, String msg) {}

    /** Entrada inválida → 422 com a lista de erros (local + mensagem). */
    public static class ValidacaoException extends RuntimeException {
        private final List<Erro> erros;

        public ValidacaoException(List<Erro> erros) {
            super("Entrada inválida");
            this.erros = erros.size() > 10 ? erros.subList(0, 10) : erros;
        }

        public List<Erro> erros() {
            return erros;
        }
    }

    public static ValidacaoException erro(String onde, String campo, String msg) {
        return new ValidacaoException(List.of(new Erro(List.of(onde, campo), msg)));
    }

    // ---------------------------------------------------------- path e query
    /** Id de caminho: inteiro de 1 a {@link #MAX_ID}. */
    public static long id(String bruto, String campo) {
        try {
            long v = Long.parseLong(bruto);
            if (v >= 1 && v <= MAX_ID) return v;
        } catch (NumberFormatException e) {
            // cai no erro abaixo
        }
        throw erro("path", campo, "O id deve ser um inteiro entre 1 e " + MAX_ID);
    }

    public static int queryInt(String bruto, String campo, int padrao, int min, int max) {
        if (bruto == null) return padrao;
        try {
            int v = Integer.parseInt(bruto);
            if (v >= min && v <= max) return v;
        } catch (NumberFormatException e) {
            // cai no erro abaixo
        }
        throw erro("query", campo, "Deve ser um inteiro entre " + min + " e " + max);
    }

    // --------------------------------------------------------------------- corpo
    /** Leitor de um corpo JSON objeto com acúmulo de erros. */
    public static final class Corpo {
        private static final Pattern USERNAME = Pattern.compile("^[a-z0-9_.-]{3,50}$");
        private final JsonNode json;
        private final List<Erro> erros = new ArrayList<>();

        private Corpo(JsonNode json) {
            this.json = json;
        }

        /** Exige um objeto JSON e proíbe campos que não estejam em {@code permitidos}. */
        public static Corpo de(JsonNode bruto, Set<String> permitidos) {
            if (bruto == null || !bruto.isObject()) throw erro("body", "", "O corpo deve ser um objeto JSON");
            Corpo c = new Corpo(bruto);
            bruto.fieldNames().forEachRemaining(nome -> {
                if (!permitidos.contains(nome)) c.erros.add(new Erro(List.of("body", nome), "Campo não permitido"));
            });
            return c;
        }

        public boolean tem(String campo) {
            return json.has(campo);
        }

        private void falha(String campo, String msg) {
            erros.add(new Erro(List.of("body", campo), msg));
        }

        /** null = ausente (ou nulo aceito). */
        private JsonNode valor(String campo, boolean obrigatorio, boolean aceitaNulo) {
            JsonNode n = json.get(campo);
            if (n == null) {
                if (obrigatorio) falha(campo, "Campo obrigatório");
                return null;
            }
            if (n.isNull()) {
                if (!aceitaNulo) falha(campo, "O valor não pode ser nulo");
                return null;
            }
            return n;
        }

        private String texto(String campo, JsonNode n, boolean strip, int min, int max) {
            if (!n.isTextual()) {
                falha(campo, "Deve ser um texto");
                return null;
            }
            String s = strip ? n.textValue().strip() : n.textValue();
            int tamanho = s.codePointCount(0, s.length());
            if (tamanho < min || tamanho > max) {
                falha(campo, "O texto deve ter entre " + min + " e " + max + " caracteres");
                return null;
            }
            return s;
        }

        /** Nome de produto/componente: texto aparado, 1–120 caracteres, sem caracteres de controle/formatação. */
        public String nome(String campo, boolean obrigatorio, boolean aceitaNulo) {
            JsonNode n = valor(campo, obrigatorio, aceitaNulo);
            if (n == null) return null;
            String s = texto(campo, n, true, 1, 120);
            if (s != null && semControle(s)) {
                falha(campo, "O nome contém caracteres de controle não permitidos");
                return null;
            }
            return s;
        }

        /** Rejeita caracteres de controle e de formatação (inclui NUL, quebras de linha e marcas bidi). */
        private static boolean semControle(String s) {
            return s.codePoints().anyMatch(cp -> {
                int t = Character.getType(cp);
                return t == Character.CONTROL || t == Character.FORMAT || t == Character.SURROGATE
                    || t == Character.PRIVATE_USE || t == Character.UNASSIGNED;
            });
        }

        /** Inteiro estrito: só número inteiro JSON (texto, booleano e decimal são rejeitados). */
        private Long inteiro(String campo, JsonNode n, long min, long max) {
            if (!n.isIntegralNumber() || !n.canConvertToLong()) {
                falha(campo, "Deve ser um número inteiro");
                return null;
            }
            long v = n.longValue();
            if (v < min || v > max) {
                falha(campo, "Deve estar entre " + min + " e " + max);
                return null;
            }
            return v;
        }

        public Integer quantidade(String campo, boolean obrigatorio, boolean aceitaNulo) {
            JsonNode n = valor(campo, obrigatorio, aceitaNulo);
            if (n == null) return null;
            Long v = inteiro(campo, n, 1, MAX_QUANTIDADE);
            return v == null ? null : v.intValue();
        }

        public Long idPai(String campo, boolean obrigatorio, boolean aceitaNulo) {
            JsonNode n = valor(campo, obrigatorio, aceitaNulo);
            return n == null ? null : inteiro(campo, n, 1, MAX_ID);
        }

        /** Preço: texto ou número, ≥ 0, no máximo 12 dígitos no total e 2 casas decimais. */
        public BigDecimal preco(String campo, boolean obrigatorio, boolean aceitaNulo) {
            JsonNode n = valor(campo, obrigatorio, aceitaNulo);
            if (n == null) return null;
            BigDecimal v;
            try {
                if (n.isNumber()) v = n.decimalValue();
                else if (n.isTextual()) v = new BigDecimal(n.textValue().strip());
                else throw new NumberFormatException();
            } catch (NumberFormatException e) {
                falha(campo, "Deve ser um valor decimal");
                return null;
            }
            if (v.signum() < 0) {
                falha(campo, "Não pode ser negativo");
                return null;
            }
            BigDecimal normal = v.signum() == 0 ? BigDecimal.ZERO : v.stripTrailingZeros();
            int casas = Math.max(normal.scale(), 0);
            int digitos = normal.scale() >= 0 ? Math.max(normal.precision(), normal.scale()) : normal.precision() - normal.scale();
            if (casas > 2) {
                falha(campo, "Aceita no máximo 2 casas decimais");
                return null;
            }
            if (digitos > 12) {
                falha(campo, "Aceita no máximo 12 dígitos");
                return null;
            }
            return normal.setScale(2);
        }

        /** Login: usuário aparado, 1–50 caracteres. */
        public String usernameLogin(String campo) {
            JsonNode n = valor(campo, true, false);
            return n == null ? null : texto(campo, n, true, 1, 50);
        }

        /** Senha: sem aparar, no tamanho dado. */
        public String senha(String campo, int min, int max) {
            JsonNode n = valor(campo, true, false);
            return n == null ? null : texto(campo, n, false, min, max);
        }

        /** Novo usuário: aparado, minúsculo, [a-z0-9_.-]{3,50}. */
        public String usernameNovo(String campo) {
            JsonNode n = valor(campo, true, false);
            if (n == null) return null;
            if (!n.isTextual()) {
                falha(campo, "Deve ser um texto");
                return null;
            }
            String s = n.textValue().strip().toLowerCase(Locale.ROOT);
            if (!USERNAME.matcher(s).matches()) {
                falha(campo, "Use 3 a 50 caracteres entre a-z, 0-9, _ . -");
                return null;
            }
            return s;
        }

        public String opcao(String campo, Set<String> validas) {
            JsonNode n = valor(campo, true, false);
            if (n == null) return null;
            if (!n.isTextual() || !validas.contains(n.textValue())) {
                falha(campo, "Valor inválido");
                return null;
            }
            return n.textValue();
        }

        /** Lança 422 se algum campo falhou. */
        public void concluir() {
            if (!erros.isEmpty()) throw new ValidacaoException(erros);
        }
    }
}
