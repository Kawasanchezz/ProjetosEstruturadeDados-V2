package com.treebom.web;

import com.treebom.config.Settings;
import com.treebom.security.AuthService;
import com.treebom.security.Principal;
import com.treebom.security.TokenService;
import com.treebom.security.TokenService.Claims;
import com.treebom.security.TokenService.TokenInvalido;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Autorização no servidor (fonte da verdade). Nega por padrão: toda rota mapeada exige {@code viewer}, salvo as
 * marcadas {@link Publico}; as marcadas {@link Admin} exigem {@code admin}. 401 sem credencial válida, 403 com papel
 * insuficiente. Com AUTH_ENABLED=false (apenas dev) todos são "anon" com papel admin.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {
    public static final String ATRIBUTO = "treebom.principal";

    private final Settings settings;
    private final TokenService tokens;
    private final AuthService auth;

    public AuthInterceptor(Settings settings, TokenService tokens, AuthService auth) {
        this.settings = settings;
        this.tokens = tokens;
        this.auth = auth;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
        if (!(handler instanceof HandlerMethod metodo)) return true;
        if (metodo.hasMethodAnnotation(Publico.class)) return true;

        Principal principal = resolver(req);
        if (metodo.hasMethodAnnotation(Admin.class) && !principal.atende(Principal.ADMIN))
            throw new ApiException(403, "Permissão insuficiente");
        req.setAttribute(ATRIBUTO, principal);
        return true;
    }

    private Principal resolver(HttpServletRequest req) {
        if (!settings.isAuthEnabled()) return new Principal(0, "anon", Principal.ADMIN);

        String cabecalho = req.getHeader("Authorization");
        if (cabecalho == null) throw ApiException.naoAutenticado();
        int espaco = cabecalho.indexOf(' ');
        String esquema = espaco < 0 ? cabecalho : cabecalho.substring(0, espaco);
        String token = espaco < 0 ? "" : cabecalho.substring(espaco + 1).strip();
        if (!esquema.toLowerCase(Locale.ROOT).equals("bearer") || token.isEmpty()) throw ApiException.naoAutenticado();
        Claims claims;
        try {
            claims = tokens.decodificar(token);
        } catch (TokenInvalido e) {
            throw ApiException.naoAutenticado();
        }
        return auth.principalDe(claims).orElseThrow(ApiException::naoAutenticado);
    }
}
