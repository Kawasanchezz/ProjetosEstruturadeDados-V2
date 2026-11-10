package com.treebom.web.teste;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Rotas que só existem nos testes: uma que falha com detalhes internos e outra lenta. */
@RestController
@RequestMapping("/api/_teste")
public class RotasDeTeste {
    @GetMapping("/quebra")
    public String quebra() {
        throw new IllegalStateException("segredo-interno SELECT * FROM usuarios");
    }

    @GetMapping("/lento")
    public String lento() throws InterruptedException {
        Thread.sleep(3_000);
        return "tarde demais";
    }
}
