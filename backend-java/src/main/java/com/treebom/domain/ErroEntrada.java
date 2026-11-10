package com.treebom.domain;

/**
 * Entrada inválida com mensagem escrita por nós, segura para mostrar ao usuário (vira 422 com o texto).
 * Qualquer outra {@link IllegalArgumentException} (de bibliotecas, por exemplo) NÃO tem a mensagem exposta.
 */
public class ErroEntrada extends IllegalArgumentException {
    public ErroEntrada(String mensagem) {
        super(mensagem);
    }
}
