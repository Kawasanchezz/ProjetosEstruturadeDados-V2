package com.treebom.security;

import com.treebom.config.Settings;
import com.treebom.domain.ErroEntrada;
import com.treebom.persistence.AuthRepository;
import com.treebom.persistence.AuthRepository.RefreshToken;
import com.treebom.persistence.AuthRepository.Usuario;
import com.treebom.security.TokenService.Emitido;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/**
 * Autenticação: Argon2id, JWT de acesso curto, refresh opaco com rotação e detecção de reuso.
 *
 * <ul>
 *   <li>Refresh token opaco (SecureRandom) guardado só como SHA-256, rotacionado a cada uso; token já usado ⇒ toda a
 *       "família" é revogada.
 *   <li>O papel (admin/viewer) é lido do banco a cada requisição: rebaixar ou desativar um usuário vale na hora.
 * </ul>
 */
@Service
public class AuthService {
    public static final int SENHA_MINIMA = 12;
    public static final int SENHA_MAXIMA = 128;

    /** Credencial inválida (mensagem única: não revela se o usuário existe). */
    public static class ErroAutenticacao extends RuntimeException {
        public ErroAutenticacao() {
            super("Credenciais inválidas");
        }
    }

    public static class ContaBloqueada extends RuntimeException {
        public final int segundos;

        public ContaBloqueada(int segundos) {
            super("Muitas tentativas");
            this.segundos = segundos;
        }
    }

    /** Resultado de login/renovação. */
    public record Sessao(Usuario usuario, String accessToken, int ttlSegundos, String refreshToken) {}

    // Argon2id com parâmetros padrão do Spring Security (RFC 9106, perfil baixo).
    private static final Argon2PasswordEncoder HASHER = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    // Igualar o tempo de resposta quando o usuário não existe. Criado sob demanda (1ª tentativa de login): o hash do Argon2
    // usa 16 MB e ~0,3 s, e sem login ligado ele nunca é necessário.
    private static final class HashFalso {
        static final String VALOR = HASHER.encode("senha-que-nunca-sera-usada");
    }
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuthRepository repo;
    private final Settings settings;
    private final LoginThrottle throttle;
    private final TokenService tokens;
    private final TransactionTemplate tx;

    public AuthService(AuthRepository repo, Settings settings, LoginThrottle throttle, TokenService tokens,
                       PlatformTransactionManager txManager) {
        this.repo = repo;
        this.settings = settings;
        this.throttle = throttle;
        this.tokens = tokens;
        this.tx = new TransactionTemplate(txManager);
    }

    // ------------------------------------------------------------------ senhas
    public static String hashSenha(String senha) {
        return HASHER.encode(senha);
    }

    public static boolean verificarSenha(String hash, String senha) {
        try {
            return HASHER.matches(senha, hash);
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static void validarForcaSenha(String senha) {
        if (senha.length() < SENHA_MINIMA || senha.length() > SENHA_MAXIMA)
            throw new ErroEntrada("A senha deve ter entre " + SENHA_MINIMA + " e " + SENHA_MAXIMA + " caracteres");
    }

    static String hashToken(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String tokenAleatorio() {
        byte[] b = new byte[48];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    // ---------------------------------------------------------------- usuários
    /** Lança DataIntegrityViolationException se o nome já existir. */
    @Transactional
    public Usuario criarUsuario(String username, String senha, String role) {
        validarForcaSenha(senha);
        if (Principal.rank(role) == 0) throw new ErroEntrada("Papel inválido");
        return repo.criarUsuario(username.strip().toLowerCase(Locale.ROOT), hashSenha(senha), role);
    }

    /** Recuperação sem e-mail (administrador do servidor, via CLI): troca a senha e derruba TODAS as sessões. */
    @Transactional
    public Usuario redefinirSenha(String username, String senha) {
        validarForcaSenha(senha);
        Usuario user = repo.usuarioPorUsername(username.strip().toLowerCase(Locale.ROOT)).orElseThrow(ErroAutenticacao::new);
        repo.redefinirSenha(user.id(), hashSenha(senha)); // também incrementa token_version
        repo.revogarTodosDoUsuario(user.id());
        return repo.usuarioPorId(user.id()).orElseThrow();
    }

    @Transactional
    public void garantirAdminInicial() {
        if (!StringUtils.hasText(settings.getAdminUsername()) || !StringUtils.hasText(settings.getAdminPassword())) return;
        if (!repo.existeUsuario()) criarUsuario(settings.getAdminUsername(), settings.getAdminPassword(), Principal.ADMIN);
    }

    @Transactional
    public Optional<Usuario> usuario(long id) {
        return repo.usuarioPorId(id);
    }

    /** Resolve o dono de um access token válido; vazio se o usuário sumiu, foi desativado ou a sessão foi revogada. */
    @Transactional
    public Optional<Principal> principalDe(TokenService.Claims claims) {
        return repo.usuarioPorId(claims.userId())
            .filter(u -> u.ativo() && u.tokenVersion() == claims.tokenVersion()) // versão antiga = sessão revogada
            .map(u -> new Principal(u.id(), u.username(), u.role()));
    }

    // ------------------------------------------------------------------ sessões
    private String emitirRefresh(Usuario user, String familia) {
        String token = tokenAleatorio();
        repo.salvarRefresh(hashToken(token), user.id(), familia != null ? familia : UUID.randomUUID().toString().replace("-", ""),
            Instant.now().plus(Duration.ofDays(settings.getRefreshTtlDias())));
        return token;
    }

    /** Não é transacional por inteiro: as falhas precisam alimentar o throttle mesmo sem escrita no banco. */
    public Sessao login(String username, String senha, String ip) {
        String normalizado = username.strip().toLowerCase(Locale.ROOT);
        String chave = ip + "|" + normalizado;
        int espera = throttle.segundosRestantes(chave);
        if (espera > 0) throw new ContaBloqueada(espera);
        Usuario user = tx.execute(st -> repo.usuarioPorUsername(normalizado).orElse(null));
        // Sempre executa uma verificação Argon2 (mesmo sem usuário) para não vazar existência pelo tempo.
        boolean ok = verificarSenha(user != null ? user.passwordHash() : HashFalso.VALOR, senha);
        if (user == null || !user.ativo() || !ok) {
            throttle.falha(chave);
            throw new ErroAutenticacao();
        }
        throttle.sucesso(chave);
        return tx.execute(st -> {
            Emitido access = tokens.emitirAccess(user);
            String refresh = emitirRefresh(user, null);
            return new Sessao(user, access.token(), access.ttlSegundos(), refresh);
        });
    }

    /**
     * Rotação do refresh. Na falha de reuso é preciso PERSISTIR a revogação da família e ainda assim sinalizar o erro,
     * por isso a transação devolve null (commit) em vez de lançar exceção (rollback).
     */
    public Sessao renovar(String token) {
        Sessao sessao = tx.execute(st -> {
            RefreshToken registro = repo.refreshPorHash(hashToken(token)).orElse(null);
            if (registro == null) return null;
            if (registro.revogado()) {
                // Reuso de um token já rotacionado: possível roubo → derruba a família inteira.
                repo.revogarFamilia(registro.familia());
                return null;
            }
            Usuario user = repo.usuarioPorId(registro.userId()).orElse(null);
            if (!registro.expiraEm().isAfter(Instant.now()) || user == null || !user.ativo()) {
                repo.revogar(registro.tokenHash());
                return null;
            }
            repo.revogar(registro.tokenHash()); // rotação: o token usado deixa de valer
            String novo = emitirRefresh(user, registro.familia());
            Emitido access = tokens.emitirAccess(user);
            return new Sessao(user, access.token(), access.ttlSegundos(), novo);
        });
        if (sessao == null) throw new ErroAutenticacao();
        return sessao;
    }

    /** Revoga a família do refresh E invalida os access tokens em circulação (token_version + 1). */
    public void logout(String token) {
        if (!StringUtils.hasText(token)) return;
        tx.executeWithoutResult(st -> repo.refreshPorHash(hashToken(token)).ifPresent(registro -> {
            repo.revogarFamilia(registro.familia());
            repo.incrementarTokenVersion(registro.userId());
        }));
    }
}
