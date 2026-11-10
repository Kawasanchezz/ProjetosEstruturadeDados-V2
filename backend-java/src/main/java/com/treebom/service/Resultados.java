package com.treebom.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Contratos de saída da API. Valores monetários saem como string ("8080.00") pelo serializador global. */
public final class Resultados {
    private Resultados() {}

    public record Pagina<T>(long total, List<T> itens) {}

    public record NoOut(long id, String nome, Long parentId, int quantidade, BigDecimal preco) {}

    public record ComponenteDetalhe(long id, String nome, Long parentId, int quantidade, BigDecimal preco, long produtoId,
                                    List<String> caminho, BigDecimal custoTotal, int totalDescendentes) {}

    public record ProdutoResumo(long id, String nome, int quantidade, BigDecimal preco, BigDecimal custoTotal,
                                int totalComponentes, int profundidade) {}

    public record NoExplosao(long id, String nome, Long parentId, int quantidade, BigDecimal preco,
                             BigDecimal custoUnitario, BigDecimal custoTotal, List<NoExplosao> filhos) {}

    public record Explosao(long produtoId, String nome, BigDecimal total, NoExplosao raiz) {}

    public record LinhaSimulacaoOut(long id, String nome, List<String> caminho, long quantidadeTotal, BigDecimal custo) {}

    public record Simulacao(long produtoId, long unidades, BigDecimal total, List<LinhaSimulacaoOut> linhas) {}

    public record PassoImplosaoOut(long id, String nome, BigDecimal antes, BigDecimal depois, BigDecimal diferenca) {}

    public record Implosao(long produtoId, List<Long> idsAfetados, BigDecimal totalAntes, BigDecimal totalDepois,
                           List<PassoImplosaoOut> passos) {}

    public record Remocao(int removidos) {}

    public record AuditoriaOut(long id, Instant ocorridoEm, String ator, String acao, Long noId, String nome,
                               String antes, String depois, String detalhe) {}

    /**
     * Campos a editar. {@code nome}/{@code quantidade}/{@code preco} nulos = não alterar. {@code temParent} indica que
     * {@code parent_id} veio no corpo (null promove o nó a produto).
     */
    public record Edicao(String nome, Integer quantidade, BigDecimal preco, boolean temParent, Long parentId) {}
}
