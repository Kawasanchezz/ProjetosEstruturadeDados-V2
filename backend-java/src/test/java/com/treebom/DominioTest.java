package com.treebom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.treebom.domain.Amostras;
import com.treebom.domain.Arvore;
import com.treebom.domain.Demo;
import com.treebom.domain.Dinheiro;
import com.treebom.domain.ErroFormula.Ciclo;
import com.treebom.domain.ErroFormula.NoNaoEncontrado;
import com.treebom.domain.ErroFormula.PaiAmbiguo;
import com.treebom.domain.ErroFormula.PaiInexistente;
import com.treebom.domain.ErroFormula.RaizInvalida;
import com.treebom.domain.Modelos.ItemFormula;
import com.treebom.domain.Modelos.LinhaSimulacao;
import com.treebom.domain.Modelos.PassoImplosao;
import com.treebom.domain.Modelos.RegistroNo;
import com.treebom.domain.Modelos.TreeNode;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Domínio puro: Testes 1 e 2 do enunciado, simulação, implosão e erros de construção. */
class DominioTest {
    static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    static ItemFormula i(String nome, String pai, int q, String preco) {
        return ItemFormula.de(nome, pai, q, preco);
    }

    @Test
    void totalCaneta() {
        assertEquals(0, Arvore.calcularTotal(Arvore.construir(Amostras.CANETA)).compareTo(d("7.50")));
    }

    @Test
    void totalComputador() {
        assertEquals(0, Arvore.calcularTotal(Arvore.construir(Amostras.COMPUTADOR)).compareTo(d("8080.00")));
    }

    @Test
    void profundidadeESubtotal() {
        TreeNode pc = Arvore.construir(Amostras.COMPUTADOR);
        assertEquals(3, Arvore.profundidade(Arvore.construir(Amostras.CANETA)));
        assertEquals(4, Arvore.profundidade(pc));
        assertEquals(0, Arvore.calcularTotal(Arvore.localizar(pc, 2)).compareTo(d("2050.00"))); // Placa-Mãe, com 2 pentes de RAM
    }

    @Test
    void imprimirArvoreEFormatacao() {
        List<String> saida = new ArrayList<>();
        Arvore.imprimir(Arvore.construir(Amostras.CANETA), saida::add);
        assertEquals(10, saida.size());
        assertTrue(saida.get(0).contains("R$ 7,50"));
        assertTrue(saida.get(2).startsWith("        └── Pigmento"));
        assertEquals("R$ 8.080,00", Dinheiro.formatarBrl(d("8080")));
        assertEquals("R$ 0,05", Dinheiro.formatarBrl(d("0.05")));
        assertEquals("R$ 1.234.567,89", Dinheiro.formatarBrl(d("1234567.89")));
    }

    @Test
    void simulacao10CanetasE5Computadores() {
        List<LinhaSimulacao> linhas = Arvore.simularProducao(Arvore.construir(Amostras.CANETA), 10);
        assertTrue(linhas.stream().allMatch(l -> l.quantidadeTotal() == 10));
        assertEquals(0, linhas.stream().map(LinhaSimulacao::custo).reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(d("75.00")));
        List<LinhaSimulacao> pcs = Arvore.simularProducao(Arvore.construir(Amostras.COMPUTADOR), 5);
        LinhaSimulacao ram = pcs.stream().filter(l -> l.nome().equals("Memória RAM")).findFirst().orElseThrow();
        assertEquals(10, ram.quantidadeTotal());
        assertEquals(0, ram.custo().compareTo(d("2000.00")));
        assertEquals(0, pcs.stream().map(LinhaSimulacao::custo).reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(d("40400.00")));
    }

    @Test
    void implosaoMudarPlastico() {
        TreeNode caneta = Arvore.construir(Amostras.CANETA);
        List<PassoImplosao> passos = Arvore.atualizarPreco(caneta, 5, d("1.70")); // Plástico do Corpo
        assertEquals(List.of(5L, 4L, 0L), passos.stream().map(PassoImplosao::id).toList());
        assertTrue(passos.stream().allMatch(p -> p.diferenca().compareTo(d("1.00")) == 0));
        assertEquals(0, Arvore.calcularTotal(caneta).compareTo(d("8.50")));
        assertEquals(0, Arvore.localizar(caneta, 8).preco.compareTo(d("0.50")));
    }

    @Test
    void nomesRepetidosSaoNosDistintos() {
        TreeNode caneta = Arvore.construir(Amostras.CANETA);
        Set<String> pais = Arvore.preOrdem(caneta).stream().filter(n -> n.nome.equals("Plástico")).map(n -> n.pai.nome).collect(Collectors.toSet());
        assertEquals(Set.of("Corpo", "Tampa"), pais);
    }

    @Test
    void errosDeConstrucao() {
        assertThrows(PaiInexistente.class, () -> Arvore.construir(List.of(i("A", null, 1, "1"), i("B", "X", 1, "1"))));
        assertThrows(PaiAmbiguo.class, () -> Arvore.construir(List.of(i("A", null, 1, "1"), i("X", "A", 1, "1"), i("X", "A", 1, "1"), i("Y", "X", 1, "1"))));
        assertThrows(Ciclo.class, () -> Arvore.construir(List.of(i("A", "B", 1, "1"), i("B", "A", 1, "1"))));
        assertThrows(Ciclo.class, () -> Arvore.construir(List.of(i("R", null, 1, "1"), i("A", "A", 1, "1"))));
        assertThrows(RaizInvalida.class, () -> Arvore.construir(List.of(i("A", null, 1, "1"), i("B", null, 1, "1"))));
        assertThrows(RaizInvalida.class, () -> Arvore.construir(List.of()));
        assertThrows(NoNaoEncontrado.class, () -> Arvore.atualizarPreco(Arvore.construir(Amostras.CANETA), 999, d("1")));
    }

    @ParameterizedTest
    @CsvSource({"'',1,1", "A,0,1", "A,1,-0.01"})
    void itemFormulaValidaEntrada(String nome, int qtd, String preco) {
        assertThrows(IllegalArgumentException.class, () -> ItemFormula.de(nome, null, qtd, preco));
    }

    @Test
    void precoInvalidoEmTexto() {
        assertThrows(IllegalArgumentException.class, () -> ItemFormula.de("A", null, 1, "abc"));
    }

    @Test
    void buildPorIdResolvePeloId() {
        TreeNode raiz = Arvore.construirPorId(List.of(
            new RegistroNo(10, "Raiz", null, 1, d("1")), new RegistroNo(11, "Igual", 10L, 1, d("1")),
            new RegistroNo(12, "Outro", 10L, 1, d("1")), new RegistroNo(13, "Igual", 12L, 1, d("1"))));
        assertEquals(12, Arvore.localizar(raiz, 13).pai.id);
        assertEquals(0, Arvore.calcularTotal(raiz).compareTo(d("4")));
    }

    @Test
    void arvoreProfundissimaNaoEstouraAPilha() {
        int n = 5_000;
        List<RegistroNo> regs = new ArrayList<>();
        regs.add(new RegistroNo(1, "n1", null, 1, d("1")));
        for (int k = 2; k <= n; k++) regs.add(new RegistroNo(k, "n" + k, (long) (k - 1), 1, d("1")));
        TreeNode arvore = Arvore.construirPorId(regs);
        assertEquals(n, Arvore.profundidade(arvore));
        assertEquals(0, Arvore.calcularTotal(arvore).compareTo(d("5000")));
        assertEquals(n, Arvore.explodir(arvore).size());
        assertEquals(n, Arvore.simularProducao(arvore, 3).size());
    }

    @Test
    void demoDoEnunciadoPassa() {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        assertTrue(Demo.executar(new PrintStream(buf, true, StandardCharsets.UTF_8)));
        String saida = buf.toString(StandardCharsets.UTF_8);
        assertTrue(saida.contains("R$ 7,50") && saida.contains("R$ 8.080,00") && saida.contains("R$ 40.400,00"));
    }
}
