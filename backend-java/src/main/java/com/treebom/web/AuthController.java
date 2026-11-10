package com.treebom.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.treebom.config.Settings;
import com.treebom.persistence.AuthRepository.Usuario;
import com.treebom.security.AuthService;
import com.treebom.security.AuthService.ContaBloqueada;
import com.treebom.security.AuthService.ErroAutenticacao;
import com.treebom.security.AuthService.Sessao;
import com.treebom.security.Principal;
import com.treebom.web.Validacao.Corpo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import com.treebom.persistence.Integridade;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Rotas de autenticação. Só existem com AUTH_ENABLED=true; sem isso respondem 404. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    public static final String COOKIE = "bom_refresh";
    public static final String COOKIE_PATH = "/api/auth";

    public record TokenOut(String accessToken, String tokenType, int expiresIn, String role) {}

    public record UsuarioOut(long id, String username, String role, boolean ativo) {}

    private final AuthService auth;
    private final Settings settings;

    public AuthController(AuthService auth, Settings settings) {
        this.auth = auth;
        this.settings = settings;
    }

    private void authHabilitada() {
        if (!settings.isAuthEnabled()) throw ApiException.naoEncontrado();
    }

    /** Defesa extra de CSRF no refresh por cookie (além de SameSite=Strict): Origin, se enviada, deve ser conhecida. */
    private void origemConfiavel(HttpServletRequest req) {
        String origem = req.getHeader("Origin");
        if (origem == null || origem.isEmpty()) return;
        String netloc;
        try {
            URI uri = URI.create(origem);
            netloc = uri.getPort() >= 0 ? uri.getHost() + ":" + uri.getPort() : uri.getHost();
        } catch (IllegalArgumentException e) {
            throw new ApiException(403, "Origem não permitida");
        }
        boolean mesmaOrigem = netloc != null && netloc.equals(req.getHeader("Host"));
        if (!mesmaOrigem && !settings.getCorsOrigins().contains(origem)) throw new ApiException(403, "Origem não permitida");
    }

    private void definirCookie(HttpServletResponse res, String token) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE, token)
            .maxAge(Duration.ofDays(settings.getRefreshTtlDias())).path(COOKIE_PATH)
            .httpOnly(true).secure(settings.cookieSeguro()).sameSite("Strict").build();
        res.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void apagarCookie(HttpServletResponse res) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE, "").maxAge(0).path(COOKIE_PATH)
            .httpOnly(true).secure(settings.cookieSeguro()).sameSite("Strict").build();
        res.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private static TokenOut tokenOut(Sessao s) {
        return new TokenOut(s.accessToken(), "bearer", s.ttlSegundos(), s.usuario().role());
    }

    /** Público: diz ao front se há login (não revela mais nada). */
    @GetMapping("/config")
    @Publico
    public Map<String, Boolean> config() {
        return Map.of("auth_enabled", settings.isAuthEnabled());
    }

    @PostMapping("/login")
    @Publico
    public TokenOut login(HttpServletRequest req, HttpServletResponse res, @RequestBody JsonNode json) {
        authHabilitada();
        Corpo c = Corpo.de(json, Set.of("username", "password"));
        String username = c.usernameLogin("username");
        String senha = c.senha("password", 1, AuthService.SENHA_MAXIMA);
        c.concluir();
        try {
            Sessao sessao = auth.login(username, senha, req.getRemoteAddr());
            definirCookie(res, sessao.refreshToken());
            return tokenOut(sessao);
        } catch (ContaBloqueada e) {
            throw new ApiException(429, "Muitas tentativas. Aguarde para tentar novamente.", Map.of("Retry-After", String.valueOf(e.segundos)));
        } catch (ErroAutenticacao e) {
            throw new ApiException(401, "Credenciais inválidas", Map.of("WWW-Authenticate", "Bearer"));
        }
    }

    @PostMapping("/refresh")
    @Publico
    public TokenOut refresh(HttpServletRequest req, HttpServletResponse res,
                            @CookieValue(name = COOKIE, required = false) String refresh) {
        authHabilitada();
        origemConfiavel(req);
        if (refresh == null || refresh.isEmpty())
            throw new ApiException(401, "Sessão expirada", Map.of("WWW-Authenticate", "Bearer"));
        try {
            Sessao sessao = auth.renovar(refresh);
            definirCookie(res, sessao.refreshToken());
            return tokenOut(sessao);
        } catch (ErroAutenticacao e) {
            apagarCookie(res);
            // A resposta de erro é montada pelo advice; o cabeçalho Set-Cookie já está no objeto de resposta.
            throw new ApiException(401, "Sessão expirada", Map.of("WWW-Authenticate", "Bearer"));
        }
    }

    @PostMapping("/logout")
    @Publico
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest req, HttpServletResponse res,
                       @CookieValue(name = COOKIE, required = false) String refresh) {
        authHabilitada();
        origemConfiavel(req);
        auth.logout(refresh);
        apagarCookie(res);
    }

    @GetMapping("/me")
    public UsuarioOut me(@RequestAttribute(AuthInterceptor.ATRIBUTO) Principal quem) {
        authHabilitada();
        Usuario u = auth.usuario(quem.userId()).orElseThrow(ApiException::naoAutenticado);
        return new UsuarioOut(u.id(), u.username(), u.role(), u.ativo());
    }

    @PostMapping("/users")
    @Admin
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioOut criarUsuario(@RequestBody JsonNode json) {
        authHabilitada();
        Corpo c = Corpo.de(json, Set.of("username", "password", "role"));
        String username = c.usernameNovo("username");
        String senha = c.senha("password", AuthService.SENHA_MINIMA, AuthService.SENHA_MAXIMA);
        String role = c.opcao("role", Set.of(Principal.ADMIN, Principal.VIEWER));
        c.concluir();
        try {
            Usuario u = auth.criarUsuario(username, senha, role);
            return new UsuarioOut(u.id(), u.username(), u.role(), u.ativo());
        } catch (DataAccessException e) {
            if (!Integridade.violacao(e)) throw e;
            throw new ApiException(409, "Nome de usuário indisponível");
        }
    }
}
