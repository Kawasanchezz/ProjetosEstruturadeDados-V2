package com.treebom.web;

import com.treebom.config.Settings;
import com.treebom.security.Principal;
import com.treebom.service.FormulaService;
import com.treebom.service.Resultados.AuditoriaOut;
import com.treebom.service.Resultados.Pagina;
import com.treebom.service.Resultados.ProdutoResumo;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Saúde, seed de exemplos e trilha de auditoria. */
@RestController
@RequestMapping("/api")
public class OutrasRotasController {
    private final FormulaService svc;
    private final Settings settings;

    public OutrasRotasController(FormulaService svc, Settings settings) {
        this.svc = svc;
        this.settings = settings;
    }

    /** Público de propósito (verificação de saúde); não expõe dados. */
    @GetMapping("/saude")
    @Publico
    public Map<String, String> saude() {
        return Map.of("status", "ok");
    }

    /** Carrega um exemplo (admin; desligado por padrão em produção). Recria um produto de mesmo nome. */
    @PostMapping("/seed/{exemplo}")
    @Admin
    @ResponseStatus(HttpStatus.CREATED)
    public ProdutoResumo seed(@RequestAttribute(AuthInterceptor.ATRIBUTO) Principal quem, @PathVariable String exemplo) {
        if (!settings.seedAtivo()) throw ApiException.naoEncontrado();
        if (!Set.of("caneta", "computador").contains(exemplo))
            throw Validacao.erro("path", "exemplo", "Use 'caneta' ou 'computador'");
        return svc.carregarExemplo(quem.username(), exemplo);
    }

    /** Trilha de auditoria: quem alterou preços, criou, moveu ou excluiu (somente admin). */
    @GetMapping("/auditoria")
    @Admin
    public Pagina<AuditoriaOut> auditoria(@RequestParam(required = false) String limit,
                                          @RequestParam(required = false) String offset) {
        return svc.listarAuditoria(Validacao.queryInt(limit, "limit", 20, 1, 100),
            Validacao.queryInt(offset, "offset", 0, 0, Integer.MAX_VALUE));
    }
}
