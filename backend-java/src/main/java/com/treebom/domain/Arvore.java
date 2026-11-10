package com.treebom.domain;

import com.treebom.domain.ErroFormula.Ciclo;
import com.treebom.domain.ErroFormula.LimiteExcedido;
import com.treebom.domain.ErroFormula.NoNaoEncontrado;
import com.treebom.domain.ErroFormula.PaiAmbiguo;
import com.treebom.domain.ErroFormula.PaiInexistente;
import com.treebom.domain.ErroFormula.RaizInvalida;
import com.treebom.domain.Modelos.ItemFormula;
import com.treebom.domain.Modelos.LinhaExplosao;
import com.treebom.domain.Modelos.LinhaSimulacao;
import com.treebom.domain.Modelos.PassoImplosao;
import com.treebom.domain.Modelos.RegistroNo;
import com.treebom.domain.Modelos.TreeNode;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Algoritmos sobre a árvore genérica do TreeBOM (domínio puro, sem framework). Todos iterativos: nenhum depende
 * da pilha de chamadas, então árvores profundas não estouram a pilha.
 *
 * <pre>
 * custo_unitario(n) = preco(n) + soma(custo_total(filho))
 * custo_total(n)    = quantidade(n) × custo_unitario(n)
 * </pre>
 */
public final class Arvore {
    private Arvore() {}

    // ------------------------------------------------------------------ construção
    /** Liga filhos aos pais em O(n), valida raiz única e ausência de ciclos. */
    private static TreeNode ligar(Map<Long, TreeNode> nos, Map<Long, Long> paiDe) {
        TreeNode raiz = null;
        for (Map.Entry<Long, Long> e : paiDe.entrySet()) {
            if (e.getValue() == null) {
                if (raiz != null) throw new RaizInvalida("A fórmula possui mais de uma raiz");
                raiz = nos.get(e.getKey());
            } else {
                nos.get(e.getValue()).adicionarFilho(nos.get(e.getKey()));
            }
        }
        if (raiz == null) throw new Ciclo("Nenhuma raiz encontrada: a hierarquia contém um ciclo");
        // Nós em ciclo nunca são alcançados a partir da raiz.
        if (preOrdem(raiz).size() != nos.size()) throw new Ciclo("A hierarquia contém um ciclo");
        return raiz;
    }

    /**
     * Constrói a árvore a partir de itens que referenciam o pai pelo nome. Cada item recebe um id único (sua
     * posição). Nomes repetidos entre nós são permitidos (ex.: "Plástico"); só o nome do <i>pai</i> precisa ser
     * inequívoco.
     */
    public static TreeNode construir(List<ItemFormula> itens) {
        if (itens.isEmpty()) throw new RaizInvalida("A fórmula está vazia");
        Map<Long, TreeNode> nos = new LinkedHashMap<>();
        Map<String, List<Long>> idsPorNome = new HashMap<>();
        for (int i = 0; i < itens.size(); i++) {
            ItemFormula it = itens.get(i);
            nos.put((long) i, new TreeNode(i, it.nome(), it.quantidade(), it.preco()));
            idsPorNome.computeIfAbsent(it.nome(), k -> new ArrayList<>()).add((long) i);
        }
        Map<Long, Long> paiDe = new LinkedHashMap<>();
        for (int i = 0; i < itens.size(); i++) {
            ItemFormula it = itens.get(i);
            if (it.pai() == null) {
                paiDe.put((long) i, null);
                continue;
            }
            List<Long> candidatos = idsPorNome.getOrDefault(it.pai(), List.of());
            if (candidatos.isEmpty())
                throw new PaiInexistente("Pai '" + it.pai() + "' do item '" + it.nome() + "' não existe");
            if (candidatos.size() > 1)
                throw new PaiAmbiguo("Pai '" + it.pai() + "' do item '" + it.nome() + "' é ambíguo ("
                    + candidatos.size() + " nós com esse nome)");
            if (candidatos.get(0) == i) throw new Ciclo("O item '" + it.nome() + "' não pode ser pai de si mesmo");
            paiDe.put((long) i, candidatos.get(0));
        }
        return ligar(nos, paiDe);
    }

    /** Constrói a árvore a partir de registros (id, parentId), como no banco. */
    public static TreeNode construirPorId(Collection<RegistroNo> registros) {
        if (registros.isEmpty()) throw new RaizInvalida("A fórmula está vazia");
        Map<Long, TreeNode> nos = new LinkedHashMap<>();
        for (RegistroNo r : registros) {
            if (nos.containsKey(r.id())) throw new ErroFormula("Id duplicado: " + r.id());
            nos.put(r.id(), new TreeNode(r.id(), r.nome(), r.quantidade(), r.preco()));
        }
        Map<Long, Long> paiDe = new LinkedHashMap<>();
        for (RegistroNo r : registros) {
            if (r.parentId() != null && !nos.containsKey(r.parentId()))
                throw new PaiInexistente("Pai id=" + r.parentId() + " do nó id=" + r.id() + " não existe");
            if (r.parentId() != null && r.parentId() == r.id())
                throw new Ciclo("O nó id=" + r.id() + " não pode ser pai de si mesmo");
            paiDe.put(r.id(), r.parentId());
        }
        return ligar(nos, paiDe);
    }

    // --------------------------------------------------------------------- percurso
    /** Pré-ordem iterativa: pai antes dos filhos, filhos na ordem original. */
    public static List<TreeNode> preOrdem(TreeNode raiz) {
        List<TreeNode> saida = new ArrayList<>();
        Deque<TreeNode> pilha = new ArrayDeque<>();
        pilha.push(raiz);
        while (!pilha.isEmpty()) {
            TreeNode no = pilha.pop();
            saida.add(no);
            for (int i = no.filhos.size() - 1; i >= 0; i--) pilha.push(no.filhos.get(i));
        }
        return saida;
    }

    public static TreeNode localizar(TreeNode raiz, long id) {
        for (TreeNode no : preOrdem(raiz)) if (no.id == id) return no;
        throw new NoNaoEncontrado("Nó id=" + id + " não encontrado");
    }

    /** Número de níveis da subárvore (folha = 1). */
    public static int profundidade(TreeNode no) {
        int maior = 0;
        Deque<Object[]> pilha = new ArrayDeque<>();
        pilha.push(new Object[] {no, 1});
        while (!pilha.isEmpty()) {
            Object[] topo = pilha.pop();
            TreeNode atual = (TreeNode) topo[0];
            int nivel = (Integer) topo[1];
            maior = Math.max(maior, nivel);
            for (TreeNode f : atual.filhos) pilha.push(new Object[] {f, nivel + 1});
        }
        return maior;
    }

    // --------------------------------------------------------------------- cálculos
    /**
     * Custo total de TODOS os nós da subárvore em uma passada O(n), sem recursão. Percorre a pré-ordem do fim ao
     * início, garantindo que os filhos já foram calculados. A chave é a identidade do objeto (ids podem se repetir
     * em árvores de teste).
     */
    public static Map<TreeNode, BigDecimal> calcularTotais(TreeNode raiz) {
        Map<TreeNode, BigDecimal> totais = new IdentityHashMap<>();
        List<TreeNode> ordem = preOrdem(raiz);
        for (int i = ordem.size() - 1; i >= 0; i--) {
            TreeNode no = ordem.get(i);
            BigDecimal unitario = no.preco;
            for (TreeNode f : no.filhos) unitario = unitario.add(totais.get(f));
            totais.put(no, unitario.multiply(BigDecimal.valueOf(no.quantidade)));
        }
        return totais;
    }

    /** Custo da subárvore considerando a quantidade do próprio nó. */
    public static BigDecimal calcularTotal(TreeNode no) {
        return calcularTotais(no).get(no);
    }

    /** Explosão em pré-ordem: (nível, nó, custo total da subárvore). O(n), iterativa. */
    public static List<LinhaExplosao> explodir(TreeNode no) {
        Map<TreeNode, BigDecimal> totais = calcularTotais(no);
        List<LinhaExplosao> linhas = new ArrayList<>();
        Deque<Object[]> pilha = new ArrayDeque<>();
        pilha.push(new Object[] {no, 0});
        while (!pilha.isEmpty()) {
            Object[] topo = pilha.pop();
            TreeNode atual = (TreeNode) topo[0];
            int nivel = (Integer) topo[1];
            linhas.add(new LinhaExplosao(nivel, atual, totais.get(atual)));
            for (int i = atual.filhos.size() - 1; i >= 0; i--) pilha.push(new Object[] {atual.filhos.get(i), nivel + 1});
        }
        return linhas;
    }

    private record ItemSimulacao(TreeNode no, long multiplicador, List<String> caminho) {}

    /**
     * Quantidade necessária de cada nó para {@code unidades} do produto e seu custo de aquisição.
     * <pre>
     * quantidade_total(n) = unidades × produto das quantidades da raiz até n
     * custo(n)            = quantidade_total(n) × preço próprio de n
     * </pre>
     * Com {@code limiteQuantidade}, interrompe com {@link LimiteExcedido} antes de acumular números absurdos.
     */
    public static List<LinhaSimulacao> simularProducao(TreeNode raiz, long unidades, Long limiteQuantidade) {
        if (unidades <= 0) throw new ErroEntrada("unidades deve ser maior que zero");
        List<LinhaSimulacao> linhas = new ArrayList<>();
        Deque<ItemSimulacao> pilha = new ArrayDeque<>();
        pilha.push(new ItemSimulacao(raiz, unidades, List.of()));
        while (!pilha.isEmpty()) {
            ItemSimulacao item = pilha.pop();
            TreeNode no = item.no();
            long total = Math.multiplyExact(item.multiplicador(), (long) no.quantidade);
            if (limiteQuantidade != null && total > limiteQuantidade)
                throw new LimiteExcedido("A quantidade necessária de '" + no.nome + "' excede o limite de " + limiteQuantidade);
            List<String> caminho = new ArrayList<>(item.caminho());
            caminho.add(no.nome);
            List<String> imutavel = Collections.unmodifiableList(caminho);
            linhas.add(new LinhaSimulacao(no.id, no.nome, imutavel, total, BigDecimal.valueOf(total).multiply(no.preco)));
            for (int i = no.filhos.size() - 1; i >= 0; i--) pilha.push(new ItemSimulacao(no.filhos.get(i), total, imutavel));
        }
        return linhas;
    }

    public static List<LinhaSimulacao> simularProducao(TreeNode raiz, long unidades) {
        return simularProducao(raiz, unidades, null);
    }

    /**
     * Atualiza o preço do nó e executa a implosão (do nó até a raiz). Retorna um passo por nó afetado, na ordem
     * nó → raiz, com custo antes/depois.
     */
    public static List<PassoImplosao> atualizarPreco(TreeNode raiz, long id, BigDecimal novoPreco) {
        if (novoPreco.signum() < 0) throw new ErroEntrada("preço não pode ser negativo");
        TreeNode no = localizar(raiz, id);
        List<TreeNode> caminho = new ArrayList<>();
        for (TreeNode atual = no; atual != null; atual = atual.pai) caminho.add(atual);
        Map<TreeNode, BigDecimal> antes = calcularTotais(raiz);
        no.preco = novoPreco;
        Map<TreeNode, BigDecimal> depois = calcularTotais(raiz);
        List<PassoImplosao> passos = new ArrayList<>();
        for (TreeNode n : caminho) passos.add(new PassoImplosao(n.id, n.nome, antes.get(n), depois.get(n)));
        return passos;
    }

    // ----------------------------------------------------------------------- saída
    public static List<String> linhasArvore(TreeNode no) {
        List<String> saida = new ArrayList<>();
        for (LinhaExplosao l : explodir(no)) {
            saida.add("    ".repeat(l.nivel()) + (l.nivel() > 0 ? "└── " : "") + l.no().nome
                + " (qtd: " + l.no().quantidade + ", preço: " + Dinheiro.formatarBrl(l.no().preco)
                + ", subtotal: " + Dinheiro.formatarBrl(l.custoTotal()) + ")");
        }
        return saida;
    }

    /** Imprime a árvore indentada com quantidade, preço e custo da subárvore. */
    public static void imprimir(TreeNode no, Consumer<String> saida) {
        linhasArvore(no).forEach(saida);
    }
}
