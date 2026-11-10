package com.treebom.domain;

/** Erro base de qualquer inconsistência na fórmula do produto. Mensagens são seguras para exibir ao usuário. */
public class ErroFormula extends RuntimeException {
    public ErroFormula(String mensagem) {
        super(mensagem);
    }

    /** O item referencia um pai que não existe na lista. */
    public static class PaiInexistente extends ErroFormula {
        public PaiInexistente(String m) { super(m); }
    }

    /** O nome do pai aparece mais de uma vez; não dá para resolver só pelo nome. */
    public static class PaiAmbiguo extends ErroFormula {
        public PaiAmbiguo(String m) { super(m); }
    }

    /** A hierarquia contém um ciclo (um nó seria ancestral de si mesmo). */
    public static class Ciclo extends ErroFormula {
        public Ciclo(String m) { super(m); }
    }

    /** A fórmula não possui exatamente uma raiz. */
    public static class RaizInvalida extends ErroFormula {
        public RaizInvalida(String m) { super(m); }
    }

    /** Nenhum nó com o id informado. */
    public static class NoNaoEncontrado extends ErroFormula {
        public NoNaoEncontrado(String m) { super(m); }
    }

    /** Um limite de tamanho, profundidade ou valor da fórmula foi excedido. */
    public static class LimiteExcedido extends ErroFormula {
        public LimiteExcedido(String m) { super(m); }
    }
}
