package com.treebom.service;

import com.treebom.config.Settings;
import com.treebom.domain.Amostras;
import com.treebom.domain.Arvore;
import com.treebom.domain.Dinheiro;
import com.treebom.domain.ErroFormula.Ciclo;
import com.treebom.domain.ErroFormula.LimiteExcedido;
import com.treebom.domain.ErroFormula.NoNaoEncontrado;
import com.treebom.domain.ErroFormula.PaiInexistente;
import com.treebom.domain.Modelos.LinhaExplosao;
import com.treebom.domain.Modelos.LinhaSimulacao;
import com.treebom.domain.Modelos.PassoImplosao;
import com.treebom.domain.Modelos.RegistroNo;
import com.treebom.domain.Modelos.TreeNode;
import com.treebom.persistence.AuditoriaRepository;
import com.treebom.persistence.NoRepository;
import com.treebom.persistence.NoRepository.NoRow;
import com.treebom.service.Resultados.AuditoriaOut;
import com.treebom.service.Resultados.ComponenteDetalhe;
import com.treebom.service.Resultados.Edicao;
import com.treebom.service.Resultados.Explosao;
import com.treebom.service.Resultados.Implosao;
import com.treebom.service.Resultados.LinhaSimulacaoOut;
import com.treebom.service.Resultados.NoExplosao;
import com.treebom.service.Resultados.NoOut;
import com.treebom.service.Resultados.Pagina;
import com.treebom.service.Resultados.PassoImplosaoOut;
import com.treebom.service.Resultados.ProdutoResumo;
import com.treebom.service.Resultados.Simulacao;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso do TreeBOM. Orquestra repositório + domínio; cada método público é uma transação.
 *
 * <p>Segurança aplicada aqui (o servidor é a fonte da verdade):
 * <ul>
 *   <li>limites de nós, filhos por nó, profundidade e valor total revalidados após CADA escrita (antes do commit);
 *   <li>trilha de auditoria gravada na mesma transação da alteração;
 *   <li>SELECT ... FOR UPDATE (PostgreSQL) + bloqueio otimista por coluna {@code version} (todos os bancos).
 * </ul>
 */
@Service
public class FormulaService {
    private static final int LIMITE_ANCESTRAIS = 10_000;

    private final NoRepository repo;
    private final AuditoriaRepository auditoria;
    private final Settings cfg;

    public FormulaService(NoRepository repo, AuditoriaRepository auditoria, Settings cfg) {
        this.repo = repo;
        this.auditoria = auditoria;
        this.cfg = cfg;
    }

    // ------------------------------------------------------------------ helpers
    private static String dinheiro(BigDecimal v) {
        return Dinheiro.formatar(v);
    }

    private static String rep(Long v) {
        return v == null ? "None" : v.toString();
    }

    private NoRow no(long id, String rotulo, boolean bloquear) {
        return repo.get(id, bloquear).orElseThrow(() -> new NoNaoEncontrado(rotulo + " id=" + id + " não encontrado"));
    }

    private NoRow produto(long id, boolean bloquear) {
        NoRow no = repo.get(id, bloquear).orElse(null);
        if (no == null || no.parentId() != null) throw new NoNaoEncontrado("Produto id=" + id + " não encontrado");
        return no;
    }

    private NoRow raizDe(NoRow no) {
        List<NoRow> ancestrais = repo.ancestrais(no, LIMITE_ANCESTRAIS);
        return ancestrais.isEmpty() ? no : ancestrais.get(ancestrais.size() - 1);
    }

    private static TreeNode arvoreDe(long raizId, Collection<NoRow> rows) {
        List<RegistroNo> registros = new ArrayList<>(rows.size());
        for (NoRow r : rows)
            registros.add(new RegistroNo(r.id(), r.nome(), r.id() == raizId ? null : r.parentId(), r.quantidade(), r.preco()));
        return Arvore.construirPorId(registros);
    }

    private TreeNode arvore(long raizId) {
        return arvoreDe(raizId, repo.subarvore(raizId));
    }

    private NoRow paiValido(long parentId) {
        return repo.get(parentId, false).orElseThrow(() -> new PaiInexistente("Pai id=" + parentId + " não existe"));
    }

    /** Revalida limites e integridade da árvore do produto. Chamada antes do commit de toda escrita. */
    private TreeNode validarArvore(long raizId) {
        List<NoRow> rows = repo.subarvore(raizId);
        if (rows.size() > cfg.getMaxNosPorProduto())
            throw new LimiteExcedido("Um produto pode ter no máximo " + cfg.getMaxNosPorProduto() + " nós");
        Map<Long, Integer> filhos = new HashMap<>();
        for (NoRow r : rows) if (r.parentId() != null && r.id() != raizId) filhos.merge(r.parentId(), 1, Integer::sum);
        if (!filhos.isEmpty() && java.util.Collections.max(filhos.values()) > cfg.getMaxFilhosPorNo())
            throw new LimiteExcedido("Um nó pode ter no máximo " + cfg.getMaxFilhosPorNo() + " filhos");
        TreeNode arvore = arvoreDe(raizId, rows);
        if (Arvore.profundidade(arvore) > cfg.getMaxProfundidade())
            throw new LimiteExcedido("Profundidade máxima da árvore é " + cfg.getMaxProfundidade() + " níveis");
        if (Arvore.calcularTotal(arvore).compareTo(cfg.getMaxValorTotal()) > 0)
            throw new LimiteExcedido("O custo total do produto excede o limite permitido");
        return arvore;
    }

    private void auditar(String ator, String acao, Long noId, String nome, String antes, String depois, String detalhe) {
        auditoria.registrar(ator, acao, noId, nome, antes, depois, detalhe);
    }

    private static NoOut saida(NoRow n) {
        return new NoOut(n.id(), n.nome(), n.parentId(), n.quantidade(), n.preco());
    }

    // ---------------------------------------------------------------------- CRUD
    @Transactional
    public NoOut criarNo(String ator, String nome, Long parentId, int quantidade, BigDecimal preco) {
        if (parentId != null) paiValido(parentId);
        NoRow no = repo.inserir(nome, parentId, quantidade, preco);
        validarArvore(raizDe(no).id());
        auditar(ator, "criar", no.id(), nome, null, dinheiro(preco), "pai=" + rep(parentId) + " qtd=" + quantidade);
        return saida(no);
    }

    @Transactional
    public NoOut editarNo(String ator, long id, Edicao e, boolean ehProduto) {
        NoRow no = ehProduto ? produto(id, true) : no(id, "Componente", true);
        long raizAntiga = raizDe(no).id();
        BigDecimal precoAntes = no.preco();
        Long paiAntes = no.parentId();

        String nome = e.nome() != null ? e.nome() : no.nome();
        int quantidade = e.quantidade() != null ? e.quantidade() : no.quantidade();
        BigDecimal preco = e.preco() != null ? e.preco() : no.preco();
        boolean moveu = e.temParent() && !Objects.equals(e.parentId(), no.parentId());
        Long novoPai = no.parentId();
        if (moveu) {
            mover(no, e.parentId());
            novoPai = e.parentId();
        }
        NoRow atualizado = repo.atualizar(no, nome, quantidade, preco, novoPai);

        long raizNova = raizDe(atualizado).id();
        for (long raiz : new HashSet<>(List.of(raizAntiga, raizNova))) {
            if (repo.get(raiz, false).isPresent()) validarArvore(raiz);
        }
        if (preco.compareTo(precoAntes) != 0)
            auditar(ator, "preco", atualizado.id(), nome, dinheiro(precoAntes), dinheiro(preco), null);
        if (moveu)
            auditar(ator, "mover", atualizado.id(), nome, null, null, "pai " + rep(paiAntes) + " -> " + rep(novoPai));
        List<String> mudados = new ArrayList<>();
        if (e.nome() != null) mudados.add("nome");
        if (e.quantidade() != null) mudados.add("quantidade");
        if (!mudados.isEmpty())
            auditar(ator, "editar", atualizado.id(), nome, null, null, "campos=" + mudados);
        return saida(atualizado);
    }

    private void mover(NoRow no, Long novoPaiId) {
        if (novoPaiId != null) {
            paiValido(novoPaiId);
            Set<Long> descendentes = repo.subarvore(no.id()).stream().map(NoRow::id).collect(Collectors.toSet());
            if (descendentes.contains(novoPaiId))
                throw new Ciclo("Um nó não pode ser movido para dentro da própria subárvore");
        }
    }

    /** Exclusão em CASCATA: remove o nó e toda a subárvore. Retorna quantos nós saíram. */
    @Transactional
    public int excluirNo(String ator, long id, boolean ehProduto) {
        NoRow no = ehProduto ? produto(id, true) : no(id, "Componente", true);
        int removidos = repo.subarvore(no.id()).size();
        repo.excluir(no.id());
        auditar(ator, "excluir", no.id(), no.nome(), null, null, removidos + " nó(s) removido(s) em cascata");
        return removidos;
    }

    // ------------------------------------------------------------------- leitura
    @Transactional
    public NoOut obterNo(long id) {
        return saida(no(id, "Componente", false));
    }

    @Transactional
    public ProdutoResumo obterProduto(long id) {
        return resumoProduto(produto(id, false), null);
    }

    private ProdutoResumo resumoProduto(NoRow raiz, List<NoRow> linhas) {
        TreeNode arvore = linhas == null ? arvore(raiz.id()) : arvoreDe(raiz.id(), linhas);
        return new ProdutoResumo(raiz.id(), raiz.nome(), raiz.quantidade(), raiz.preco(), Arvore.calcularTotal(arvore),
            Arvore.explodir(arvore).size() - 1, Arvore.profundidade(arvore));
    }

    @Transactional
    public Pagina<ProdutoResumo> listarProdutos(int limit, int offset) {
        NoRepository.Pagina pagina = repo.listarRaizes(limit, offset);
        Map<Long, List<NoRow>> porRaiz = repo.subarvores(pagina.itens().stream().map(NoRow::id).toList()); // 1 consulta
        List<ProdutoResumo> itens = new ArrayList<>();
        for (NoRow r : pagina.itens()) itens.add(resumoProduto(r, porRaiz.get(r.id())));
        return new Pagina<>(pagina.total(), itens);
    }

    @Transactional
    public Pagina<NoOut> listarComponentes(long produtoId, int limit, int offset) {
        produto(produtoId, false);
        List<NoRow> rows = repo.subarvore(produtoId);
        int de = Math.min(offset, rows.size());
        int ate = Math.min(offset + limit, rows.size());
        return new Pagina<>(rows.size(), rows.subList(de, ate).stream().map(FormulaService::saida).toList());
    }

    @Transactional
    public ComponenteDetalhe detalheComponente(long id) {
        NoRow no = no(id, "Componente", false);
        List<NoRow> ancestrais = repo.ancestrais(no, LIMITE_ANCESTRAIS);
        NoRow raiz = ancestrais.isEmpty() ? no : ancestrais.get(ancestrais.size() - 1);
        TreeNode node = Arvore.localizar(arvore(raiz.id()), id);
        List<String> caminho = new ArrayList<>();
        for (int i = ancestrais.size() - 1; i >= 0; i--) caminho.add(ancestrais.get(i).nome());
        caminho.add(no.nome());
        return new ComponenteDetalhe(no.id(), no.nome(), no.parentId(), no.quantidade(), no.preco(), raiz.id(), caminho,
            Arvore.calcularTotal(node), Arvore.explodir(node).size() - 1);
    }

    // ------------------------------------------------------------------ explosão
    @Transactional
    public Explosao explosao(long produtoId) {
        produto(produtoId, false);
        TreeNode arvore = arvore(produtoId);
        if (Arvore.profundidade(arvore) > cfg.getMaxProfundidade())
            throw new LimiteExcedido("Profundidade máxima da árvore é " + cfg.getMaxProfundidade() + " níveis");
        Map<TreeNode, BigDecimal> totais = Arvore.calcularTotais(arvore);
        if (totais.get(arvore).compareTo(cfg.getMaxValorTotal()) > 0)
            throw new LimiteExcedido("O custo total do produto excede o limite permitido");

        // Montagem iterativa (sem recursão): a profundidade já é limitada, mas o código não depende disso.
        record Par(TreeNode no, List<NoExplosao> destino) {}
        List<NoExplosao> raizLista = new ArrayList<>(1);
        Deque<Par> pilha = new ArrayDeque<>();
        pilha.push(new Par(arvore, raizLista));
        while (!pilha.isEmpty()) {
            Par par = pilha.pop();
            TreeNode no = par.no();
            BigDecimal total = totais.get(no);
            List<NoExplosao> filhos = new ArrayList<>(no.filhos.size());
            par.destino().add(new NoExplosao(no.id, no.nome, no.pai == null ? null : no.pai.id, no.quantidade, no.preco,
                total.divide(BigDecimal.valueOf(no.quantidade)), total, filhos));
            for (int i = no.filhos.size() - 1; i >= 0; i--) pilha.push(new Par(no.filhos.get(i), filhos));
        }
        return new Explosao(produtoId, arvore.nome, totais.get(arvore), raizLista.get(0));
    }

    // ----------------------------------------------------------------- simulação
    @Transactional
    public Simulacao simulacao(long produtoId, long unidades) {
        produto(produtoId, false);
        TreeNode arvore = arvore(produtoId);
        if (BigDecimal.valueOf(unidades).multiply(Arvore.calcularTotal(arvore)).compareTo(cfg.getMaxValorTotal()) > 0)
            throw new LimiteExcedido("O valor total da simulação excede o limite permitido");
        List<LinhaSimulacao> linhas = Arvore.simularProducao(arvore, unidades, cfg.getMaxQuantidadeTotal());
        BigDecimal total = BigDecimal.ZERO;
        List<LinhaSimulacaoOut> saida = new ArrayList<>();
        for (LinhaSimulacao l : linhas) {
            total = total.add(l.custo());
            saida.add(new LinhaSimulacaoOut(l.id(), l.nome(), l.caminho(), l.quantidadeTotal(), l.custo()));
        }
        return new Simulacao(produtoId, unidades, total, saida);
    }

    // ------------------------------------------------------------------ implosão
    @Transactional
    public Implosao atualizarPreco(String ator, long id, BigDecimal novoPreco) {
        NoRow no = no(id, "Componente", true);
        NoRow raiz = raizDe(no);
        TreeNode arvore = arvore(raiz.id());
        BigDecimal precoAntes = no.preco();
        List<PassoImplosao> passos = Arvore.atualizarPreco(arvore, id, novoPreco);
        PassoImplosao ultimo = passos.get(passos.size() - 1);
        if (ultimo.depois().compareTo(cfg.getMaxValorTotal()) > 0)
            throw new LimiteExcedido("O custo total do produto excede o limite permitido");
        repo.atualizar(no, no.nome(), no.quantidade(), novoPreco, no.parentId()); // conflito = outra escrita no mesmo nó → 409
        auditar(ator, "preco", no.id(), no.nome(), dinheiro(precoAntes), dinheiro(novoPreco),
            "total do produto " + dinheiro(ultimo.antes()) + " -> " + dinheiro(ultimo.depois()));
        List<PassoImplosaoOut> saida = new ArrayList<>();
        for (PassoImplosao p : passos) saida.add(new PassoImplosaoOut(p.id(), p.nome(), p.antes(), p.depois(), p.diferenca()));
        return new Implosao(raiz.id(), passos.stream().map(PassoImplosao::id).toList(), ultimo.antes(), ultimo.depois(), saida);
    }

    // ---------------------------------------------------------------------- seed
    /** Cria o exemplo. Idempotente: substitui um produto de mesmo nome já existente. */
    @Transactional
    public ProdutoResumo carregarExemplo(String ator, String chave) {
        TreeNode raizDominio = Arvore.construir(Amostras.EXEMPLOS.get(chave));
        repo.raizPorNome(raizDominio.nome).ifPresent(existente -> repo.excluir(existente.id()));
        Map<Long, Long> ids = new HashMap<>();
        NoRow raizDb = null;
        for (LinhaExplosao linha : Arvore.explodir(raizDominio)) { // pré-ordem: pai sempre antes do filho
            TreeNode node = linha.no();
            Long parentId = node.pai == null ? null : ids.get(node.pai.id);
            NoRow criado = repo.inserir(node.nome, parentId, node.quantidade, node.preco);
            ids.put(node.id, criado.id());
            if (raizDb == null) raizDb = criado;
        }
        auditar(ator, "seed", raizDb.id(), raizDb.nome(), null, null, "exemplo=" + chave);
        return resumoProduto(raizDb, null);
    }

    // ----------------------------------------------------------------- auditoria
    @Transactional
    public Pagina<AuditoriaOut> listarAuditoria(int limit, int offset) {
        long total = auditoria.contar();
        List<AuditoriaOut> itens = auditoria.listar(limit, offset).stream()
            .map(r -> new AuditoriaOut(r.id(), r.ocorridoEm(), r.ator(), r.acao(), r.noId(), r.nome(), r.antes(), r.depois(), r.detalhe()))
            .toList();
        return new Pagina<>(total, itens);
    }

    /** Resumo de um produto já existente (usado após criar/editar). */
    @Transactional
    public ProdutoResumo resumoDe(long produtoId) {
        return resumoProduto(produto(produtoId, false), null);
    }
}
