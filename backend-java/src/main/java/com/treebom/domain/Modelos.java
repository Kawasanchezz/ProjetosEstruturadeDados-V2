package com.treebom.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Modelos do domínio: entrada (ItemFormula/RegistroNo), o nó da árvore genérica e os resultados. */
public final class Modelos {
    private Modelos() {}

    static void validar(String nome, int quantidade, BigDecimal preco) {
        if (nome == null || nome.isBlank()) throw new ErroEntrada("nome não pode ser vazio");
        if (quantidade <= 0) throw new ErroEntrada("quantidade deve ser maior que zero");
        if (preco.signum() < 0) throw new ErroEntrada("preço não pode ser negativo");
    }

    /** Linha de entrada: o pai é referenciado pelo nome. */
    public record ItemFormula(String nome, String pai, int quantidade, BigDecimal preco) {
        public ItemFormula {
            validar(nome, quantidade, preco);
        }

        public static ItemFormula de(String nome, String pai, int quantidade, String preco) {
            return new ItemFormula(nome, pai, quantidade, Dinheiro.paraDecimal(preco, "preço"));
        }
    }

    /** Linha de entrada por id (usada pelo banco): o pai é resolvido por id único. */
    public record RegistroNo(long id, String nome, Long parentId, int quantidade, BigDecimal preco) {
        public RegistroNo {
            validar(nome, quantidade, preco);
        }
    }

    /** Nó de uma árvore genérica: N filhos e referência ao pai (para a implosão). Igualdade por identidade. */
    public static final class TreeNode {
        public final long id;
        public final String nome;
        public final int quantidade;
        public BigDecimal preco;
        public TreeNode pai;
        public final List<TreeNode> filhos = new ArrayList<>();

        public TreeNode(long id, String nome, int quantidade, BigDecimal preco) {
            this.id = id;
            this.nome = nome;
            this.quantidade = quantidade;
            this.preco = preco;
        }

        public void adicionarFilho(TreeNode filho) {
            filho.pai = this;
            filhos.add(filho);
        }

        @Override
        public String toString() {
            return "TreeNode(" + id + ", " + nome + ")";
        }
    }

    /** Resultado da simulação para um nó. */
    public record LinhaSimulacao(long id, String nome, List<String> caminho, long quantidadeTotal, BigDecimal custo) {}

    /** Custo total de um nó do caminho (nó alterado → raiz) antes e depois da mudança. */
    public record PassoImplosao(long id, String nome, BigDecimal antes, BigDecimal depois) {
        public BigDecimal diferenca() {
            return depois.subtract(antes);
        }
    }

    /** Linha da explosão em pré-ordem. */
    public record LinhaExplosao(int nivel, TreeNode no, BigDecimal custoTotal) {}
}
