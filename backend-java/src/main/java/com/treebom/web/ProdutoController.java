package com.treebom.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.treebom.security.Principal;
import com.treebom.service.FormulaService;
import com.treebom.service.Resultados.Edicao;
import com.treebom.service.Resultados.Explosao;
import com.treebom.service.Resultados.NoOut;
import com.treebom.service.Resultados.Pagina;
import com.treebom.service.Resultados.ProdutoResumo;
import com.treebom.service.Resultados.Remocao;
import com.treebom.service.Resultados.Simulacao;
import com.treebom.web.Validacao.Corpo;
import java.math.BigDecimal;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Rotas finas: validam a entrada, delegam ao serviço e serializam a saída. */
@RestController
@RequestMapping("/api/produtos")
public class ProdutoController {
    private final FormulaService svc;

    public ProdutoController(FormulaService svc) {
        this.svc = svc;
    }

    @GetMapping
    public Pagina<ProdutoResumo> listar(@RequestParam(required = false) String limit,
                                        @RequestParam(required = false) String offset) {
        return svc.listarProdutos(Validacao.queryInt(limit, "limit", 20, 1, 100), Validacao.queryInt(offset, "offset", 0, 0, Integer.MAX_VALUE));
    }

    @PostMapping
    @Admin
    @ResponseStatus(HttpStatus.CREATED)
    public ProdutoResumo criar(@RequestAttribute(AuthInterceptor.ATRIBUTO) Principal quem, @RequestBody JsonNode json) {
        Corpo c = Corpo.de(json, Set.of("nome", "preco", "quantidade"));
        String nome = c.nome("nome", true, false);
        BigDecimal preco = c.preco("preco", false, false);
        Integer quantidade = c.quantidade("quantidade", false, false);
        c.concluir();
        NoOut criado = svc.criarNo(quem.username(), nome, null, quantidade != null ? quantidade : 1, preco != null ? preco : BigDecimal.ZERO);
        return svc.resumoDe(criado.id());
    }

    @GetMapping("/{id}")
    public ProdutoResumo obter(@PathVariable String id) {
        return svc.obterProduto(Validacao.id(id, "produto_id"));
    }

    @PutMapping("/{id}")
    @Admin
    public ProdutoResumo editar(@RequestAttribute(AuthInterceptor.ATRIBUTO) Principal quem, @PathVariable String id,
                                @RequestBody JsonNode json) {
        long produtoId = Validacao.id(id, "produto_id");
        Corpo c = Corpo.de(json, Set.of("nome", "preco", "quantidade"));
        Edicao edicao = new Edicao(c.nome("nome", false, true), c.quantidade("quantidade", false, true),
            c.preco("preco", false, true), false, null);
        c.concluir();
        svc.editarNo(quem.username(), produtoId, edicao, true);
        return svc.resumoDe(produtoId);
    }

    /** Exclusão em cascata: remove o produto e todos os seus componentes. */
    @DeleteMapping("/{id}")
    @Admin
    public Remocao excluir(@RequestAttribute(AuthInterceptor.ATRIBUTO) Principal quem, @PathVariable String id) {
        return new Remocao(svc.excluirNo(quem.username(), Validacao.id(id, "produto_id"), true));
    }

    @GetMapping("/{id}/explosao")
    public Explosao explosao(@PathVariable String id) {
        return svc.explosao(Validacao.id(id, "produto_id"));
    }

    @PostMapping("/{id}/simulacao")
    public Simulacao simulacao(@PathVariable String id, @RequestBody JsonNode json) {
        long produtoId = Validacao.id(id, "produto_id");
        Corpo c = Corpo.de(json, Set.of("quantidade"));
        Integer quantidade = c.quantidade("quantidade", true, false);
        c.concluir();
        return svc.simulacao(produtoId, quantidade);
    }

    /** Lista plana (nó raiz incluído), usada para popular o select de pai. */
    @GetMapping("/{id}/componentes")
    public Pagina<NoOut> componentes(@PathVariable String id, @RequestParam(required = false) String limit,
                                     @RequestParam(required = false) String offset) {
        long produtoId = Validacao.id(id, "produto_id");
        return svc.listarComponentes(produtoId, Validacao.queryInt(limit, "limit", 100, 1, 500),
            Validacao.queryInt(offset, "offset", 0, 0, Integer.MAX_VALUE));
    }
}
