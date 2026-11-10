package com.treebom.web;

import com.treebom.domain.ErroEntrada;
import com.treebom.domain.ErroFormula;
import com.treebom.domain.ErroFormula.Ciclo;
import com.treebom.domain.ErroFormula.NoNaoEncontrado;
import com.treebom.domain.ErroFormula.PaiAmbiguo;
import com.treebom.domain.ErroFormula.RaizInvalida;
import com.treebom.persistence.NoRepository.ConflitoConcorrencia;
import com.treebom.web.Validacao.ValidacaoException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import com.treebom.persistence.Integridade;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Tradução de erros de domínio/infra em respostas HTTP sem expor detalhes internos. */
@RestControllerAdvice
public class ErrosAdvice {
    private static final Logger log = LoggerFactory.getLogger("bom");

    private static ResponseEntity<Map<String, Object>> resp(int status, Object detail) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("detail", detail);
        return ResponseEntity.status(status).body(corpo);
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> api(ApiException e) {
        HttpHeaders h = new HttpHeaders();
        e.cabecalhos().forEach(h::add);
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("detail", e.getMessage());
        return ResponseEntity.status(e.status()).headers(h).body(corpo);
    }

    @ExceptionHandler(ErroFormula.class)
    public ResponseEntity<Map<String, Object>> formula(ErroFormula e) {
        int status = switch (e) {
            case NoNaoEncontrado x -> 404;
            case Ciclo x -> 409;
            case PaiAmbiguo x -> 409;
            case RaizInvalida x -> 409;
            default -> 422; // inclui PaiInexistente e LimiteExcedido
        };
        return resp(status, e.getMessage());
    }

    @ExceptionHandler(ValidacaoException.class)
    public ResponseEntity<Map<String, Object>> validacao(ValidacaoException e) {
        // Devolvemos só local e mensagem: o valor enviado nunca é ecoado.
        return resp(422, e.erros().stream().map(x -> Map.of("loc", x.loc(), "msg", x.msg())).toList());
    }

    @ExceptionHandler(ErroEntrada.class)
    public ResponseEntity<Map<String, Object>> entrada(ErroEntrada e) {
        return resp(422, e.getMessage());
    }

    /** IllegalArgumentException "de fora" (bibliotecas): a mensagem pode ter detalhes internos, então não é exposta. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> argumento(IllegalArgumentException e) {
        log.warn("Argumento inválido: {}", e.getClass().getSimpleName());
        return resp(422, "Entrada inválida");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> jsonInvalido(HttpMessageNotReadableException e) {
        return resp(422, List.of(Map.of("loc", List.of("body"), "msg", "JSON inválido ou ausente")));
    }

    @ExceptionHandler(ConflitoConcorrencia.class)
    public ResponseEntity<Map<String, Object>> concorrencia(ConflitoConcorrencia e) {
        return resp(409, e.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> integridade(DataAccessException e) {
        log.warn("Violação de integridade: {}", e.getClass().getSimpleName());
        return resp(409, "Operação viola uma regra de integridade dos dados");
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> banco(DataAccessException e) {
        if (Integridade.violacao(e)) return integridade(e);
        log.error("Erro de banco", e);
        return interno();
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<Map<String, Object>> semRota(Exception e) {
        return resp(404, "Não encontrado");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> metodo(HttpRequestMethodNotSupportedException e) {
        return resp(405, "Método não permitido");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> tipo(HttpMediaTypeNotSupportedException e) {
        return resp(415, "Use Content-Type: application/json");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> inesperado(Exception e) {
        if (e instanceof org.springframework.web.ErrorResponse er) // erros do próprio Spring MVC (4xx): mantém o status
            return resp(er.getStatusCode().value(), "Requisição inválida");
        log.error("Erro não tratado", e);
        return interno();
    }

    private static ResponseEntity<Map<String, Object>> interno() {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("detail", "Erro interno do servidor");
        corpo.put("request_id", MDC.get("request_id"));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(corpo);
    }
}
