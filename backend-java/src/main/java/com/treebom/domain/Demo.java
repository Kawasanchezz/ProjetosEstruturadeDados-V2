package com.treebom.domain;

import com.treebom.domain.Modelos.ItemFormula;
import com.treebom.domain.Modelos.LinhaSimulacao;
import com.treebom.domain.Modelos.TreeNode;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Execução dos testes 1 e 2 do enunciado com saída formatada: {@code java -cp target/classes com.treebom.domain.Demo}. */
public final class Demo {
    private Demo() {}

    record Caso(String titulo, List<ItemFormula> itens, BigDecimal esperado, int unidades) {}

    public static boolean executar(PrintStream out) {
        List<Caso> casos = List.of(
            new Caso("Teste 1: Caneta", Amostras.CANETA, new BigDecimal("7.50"), 10),
            new Caso("Teste 2: Computador", Amostras.COMPUTADOR, new BigDecimal("8080.00"), 5));
        boolean ok = true;
        for (Caso c : casos) {
            out.println("\n" + "=".repeat(70) + "\n" + c.titulo() + "\n" + "=".repeat(70));
            TreeNode raiz = Arvore.construir(c.itens());
            Arvore.imprimir(raiz, out::println);
            BigDecimal total = Arvore.calcularTotal(raiz);
            boolean passou = total.compareTo(c.esperado()) == 0;
            ok &= passou;
            out.println("\nTotal: " + Dinheiro.formatarBrl(total) + " (esperado " + Dinheiro.formatarBrl(c.esperado())
                + ") [" + (passou ? "OK" : "FALHOU") + "]");

            out.println("\nSimulação: " + c.unidades() + " unidades");
            out.printf("%-28s%10s%18s%n", "Componente", "Qtd total", "Custo");
            BigDecimal soma = BigDecimal.ZERO;
            for (LinhaSimulacao l : Arvore.simularProducao(raiz, c.unidades())) {
                String nome = "  ".repeat(l.caminho().size() - 1) + l.nome();
                out.printf("%-28s%10d%18s%n", nome, l.quantidadeTotal(), Dinheiro.formatarBrl(l.custo()));
                soma = soma.add(l.custo());
            }
            out.printf("%-38s%18s%n", "TOTAL", Dinheiro.formatarBrl(soma));
        }
        return ok;
    }

    public static void main(String[] args) {
        System.exit(executar(new PrintStream(System.out, true, StandardCharsets.UTF_8)) ? 0 : 1);
    }
}
