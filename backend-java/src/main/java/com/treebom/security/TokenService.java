package com.treebom.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.SignatureVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.treebom.config.Settings;
import com.treebom.persistence.AuthRepository.Usuario;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Access token JWT (HS256 fixado; "none" e outros algoritmos são rejeitados) com exp/iat/sub/jti e 10 min de vida.
 * O claim {@code tv} carrega a token_version do usuário: incrementá-la derruba todos os tokens já emitidos.
 */
@Component
public class TokenService {
    /** Token inválido (expirado, assinatura ruim, algoritmo errado, claims faltando...). */
    public static class TokenInvalido extends Exception {
        public TokenInvalido(String m) {
            super(m);
        }
    }

    public record Emitido(String token, int ttlSegundos) {}

    public record Claims(long userId, int tokenVersion) {}

    private final Settings settings;

    public TokenService(Settings settings) {
        this.settings = settings;
    }

    public Emitido emitirAccess(Usuario user) {
        int ttl = settings.getAccessTtlMinutos() * 60;
        Instant agora = Instant.now();
        String token = JWT.create()
            .withSubject(Long.toString(user.id()))
            .withIssuedAt(agora)
            .withExpiresAt(agora.plusSeconds(ttl))
            .withJWTId(UUID.randomUUID().toString().replace("-", ""))
            .withClaim("typ", "access")
            .withClaim("tv", user.tokenVersion())
            .sign(Algorithm.HMAC256(settings.getJwtSecret()));
        return new Emitido(token, ttl);
    }

    /**
     * Rotação de segredo: tenta o JWT_SECRET atual e, se definido, o JWT_SECRET_ANTERIOR (só para verificar; todo
     * token novo é assinado com o atual). Depois de 1 TTL de access token, remova o anterior.
     */
    public Claims decodificar(String token) throws TokenInvalido {
        List<String> segredos = new ArrayList<>();
        if (StringUtils.hasText(settings.getJwtSecret())) segredos.add(settings.getJwtSecret());
        if (StringUtils.hasText(settings.getJwtSecretAnterior())) segredos.add(settings.getJwtSecretAnterior());
        DecodedJWT dados = null;
        for (String segredo : segredos) {
            try {
                // Algoritmo fixo: rejeita "none" e confusão de algoritmos.
                dados = JWT.require(Algorithm.HMAC256(segredo)).build().verify(token);
                break;
            } catch (SignatureVerificationException e) {
                // tenta o próximo segredo
            } catch (JWTVerificationException e) {
                throw new TokenInvalido(e.getMessage());
            }
        }
        if (dados == null) throw new TokenInvalido("assinatura inválida");
        if (dados.getExpiresAt() == null || dados.getIssuedAt() == null || dados.getSubject() == null || dados.getId() == null)
            throw new TokenInvalido("claims obrigatórias ausentes");
        if (!"access".equals(dados.getClaim("typ").asString())) throw new TokenInvalido("tipo de token inválido");
        try {
            Integer tv = dados.getClaim("tv").asInt();
            return new Claims(Long.parseLong(dados.getSubject()), tv == null ? 0 : tv);
        } catch (NumberFormatException e) {
            throw new TokenInvalido("claims inválidas");
        }
    }
}
