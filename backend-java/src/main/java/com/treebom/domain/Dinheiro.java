package com.treebom.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Utilidades de dinheiro: sempre {@link BigDecimal}, nunca ponto flutuante. */
public final class Dinheiro {
    private Dinheiro() {}

    /** Converte texto em decimal finito; lança {@link IllegalArgumentException} se inválido. */
    public static BigDecimal paraDecimal(String valor, String campo) {
        try {
            return new BigDecimal(valor.trim());
        } catch (NumberFormatException | NullPointerException e) {
            throw new ErroEntrada(campo + " inválido");
        }
    }

    /** Texto com 2 casas ("8080.00"), como a API devolve. */
    public static String formatar(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_EVEN).toPlainString();
    }

    /** Centavos exatos; falha se houver mais de 2 casas decimais. */
    public static long emCentavos(BigDecimal valor) {
        try {
            return valor.movePointRight(2).longValueExact();
        } catch (ArithmeticException e) {
            throw new ErroEntrada("valor monetário aceita no máximo 2 casas decimais");
        }
    }

    public static BigDecimal deCentavos(long centavos) {
        return BigDecimal.valueOf(centavos, 2);
    }

    /** Formata como moeda brasileira: 8080 -> "R$ 8.080,00". */
    public static String formatarBrl(BigDecimal valor) {
        String plano = valor.setScale(2, RoundingMode.HALF_EVEN).toPlainString();
        String[] partes = plano.split("\\.");
        String inteiro = partes[0];
        boolean negativo = inteiro.startsWith("-");
        if (negativo) inteiro = inteiro.substring(1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < inteiro.length(); i++) {
            if (i > 0 && (inteiro.length() - i) % 3 == 0) sb.append('.');
            sb.append(inteiro.charAt(i));
        }
        return "R$ " + (negativo ? "-" : "") + sb + "," + partes[1];
    }
}
