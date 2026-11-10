package com.treebom.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.treebom.security.Principal;
import com.treebom.service.FormulaService;
import com.treebom.service.Resultados.ComponenteDetalhe;
import com.treebom.service.Resultados.Edicao;
import com.treebom.service.Resultados.Implosao;
import com.treebom.service.Resultados.NoOut;
import com.treebom.service.Resultados.Remocao;
import com.treebom.web.Validacao.Corpo;
import java.math.BigDecimal;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/componentes")
public class ComponenteController {
    private final FormulaService svc;

    public ComponenteController(FormulaService svc) {
        this.svc = svc;
    }

    @PostMapping
    @Admin
    @ResponseStatus(HttpStatus.CREATED)
    public NoOut criar(@RequestAttribute(AuthInterceptor.ATRIBUTO) Principal quem, @RequestBody JsonNode json) {
        Corpo c = Corpo.de(json, Set.of("nome", "parent_id", "quantidade", "preco"));
        String nome = c.nome("nome", true, false);
        Long parentId = c.idPai("parent_id", true, false);
        Integer quantidade = c.quantidade("quantidade", false, false);
        BigDecimal preco = c.preco("preco", true, false);
        c.concluir();
        return svc.criarNo(quem.username(), nome, parentId, quantidade != null ? quantidade : 1, preco);
    }

    @GetMapping("/{id}")
    public ComponenteDetalhe obter(@PathVariable String id) {
        return svc.detalheComponente(Validacao.id(id, "componente_id"));
    }

    /** {@code parent_id} presente no corpo move o nó (null promove a produto). */
    @PutMapping("/{id}")
    @Admin
    public NoOut editar(@RequestAttribute(AuthInterceptor.ATRIBUTO) Principal quem, @PathVariable String id,
                        @RequestBody JsonNode json) {
        long componenteId = Validacao.id(id, "componente_id");
        Corpo c = Corpo.de(json, Set.of("nome", "quantidade", "preco", "parent_id"));
        Edicao edicao = new Edicao(c.nome("nome", false, true), c.quantidade("quantidade", false, true),
            c.preco("preco", false, true), c.tem("parent_id"), c.idPai("parent_id", false, true));
        c.concluir();
        return svc.editarNo(quem.username(), componenteId, edicao, false);
    }

    /** Exclusão em cascata: remove o componente e toda a sua subárvore. */
    @DeleteMapping("/{id}")
    @Admin
    public Remocao excluir(@RequestAttribute(AuthInterceptor.ATRIBUTO) Principal quem, @PathVariable String id) {
        return new Remocao(svc.excluirNo(quem.username(), Validacao.id(id, "componente_id"), false));
    }

    /** Atualiza o preço e devolve a implosão: ids do nó até a raiz e custos antes/depois. */
    @PatchMapping("/{id}/preco")
    @Admin
    public Implosao atualizarPreco(@RequestAttribute(AuthInterceptor.ATRIBUTO) Principal quem, @PathVariable String id,
                                   @RequestBody JsonNode json) {
        long componenteId = Validacao.id(id, "componente_id");
        Corpo c = Corpo.de(json, Set.of("preco"));
        BigDecimal preco = c.preco("preco", true, false);
        c.concluir();
        return svc.atualizarPreco(quem.username(), componenteId, preco);
    }
}
