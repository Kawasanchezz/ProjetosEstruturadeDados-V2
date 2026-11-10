package com.treebom;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.treebom.config.Settings;
import com.treebom.config.Settings.ConfiguracaoInsegura;
import com.treebom.security.AuthService;
import com.treebom.security.LoginThrottle;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Configuração insegura impede a partida; throttle em memória tem limite de chaves. */
class ConfigETest {
    static final String SEG = BaseTest.SEGREDO;

    static Settings com(Consumer<Settings> ajuste) {
        Settings s = new Settings();
        ajuste.accept(s);
        return s;
    }

    static Stream<Settings> inseguras() {
        return Stream.of(
            com(s -> s.setAuthEnabled(true)),
            com(s -> { s.setAuthEnabled(true); s.setJwtSecret("curto"); }),
            com(s -> s.setCorsOrigins(List.of("*"))),
            com(s -> { s.setAppEnv("production"); s.setAuthEnabled(false); }),
            com(s -> { s.setAppEnv("production"); s.setAuthEnabled(true); s.setJwtSecret(SEG); s.setCookieSecure(false); }),
            com(s -> { s.setAuthEnabled(true); s.setJwtSecret(SEG); s.setJwtSecretAnterior("curto"); }),
            com(s -> { s.setAppEnv("production"); s.setAuthEnabled(true); s.setJwtSecret(SEG); s.setAdminPassword("curta"); }));
    }

    @ParameterizedTest
    @MethodSource("inseguras")
    void configuracaoInseguraImpedeAPartida(Settings s) {
        assertThrows(ConfiguracaoInsegura.class, s::validar);
    }

    @Test
    void padroesSaoSeguros() {
        Settings s = new Settings();
        assertDoesNotThrow(s::validar);
        assertTrue(s.seedAtivo());
        assertEquals(false, s.hstsAtivo());
        Settings p = com(x -> { x.setAppEnv("production"); x.setAuthEnabled(true); x.setJwtSecret(SEG); });
        assertDoesNotThrow(p::validar);
        assertEquals(false, p.seedAtivo());
        assertTrue(p.hstsAtivo() && p.cookieSeguro());
    }

    @Test
    void throttleEmMemoriaTemLimiteDeChaves() {
        LoginThrottle t = new LoginThrottle();
        for (int i = 0; i < LoginThrottle.MAX_CHAVES + 50; i++) t.falha("k" + i);
        assertTrue(t.tamanho() <= LoginThrottle.MAX_CHAVES + 1);
    }

    @Test
    void throttleBloqueiaAposTresFalhasELiberaNoSucesso() {
        LoginThrottle t = new LoginThrottle();
        for (int i = 0; i < 3; i++) t.falha("ip|u");
        assertEquals(0, t.segundosRestantes("ip|u"));
        t.falha("ip|u");
        assertTrue(t.segundosRestantes("ip|u") >= 1);
        t.sucesso("ip|u");
        assertEquals(0, t.segundosRestantes("ip|u"));
    }

    @Test
    void senhaPolitica() {
        assertThrows(IllegalArgumentException.class, () -> AuthService.validarForcaSenha("curta"));
        assertDoesNotThrow(() -> AuthService.validarForcaSenha("doze-chars-ok"));
        String hash = AuthService.hashSenha("minha-senha-forte-123");
        assertTrue(hash.startsWith("$argon2id$"));
        assertTrue(AuthService.verificarSenha(hash, "minha-senha-forte-123"));
        assertEquals(false, AuthService.verificarSenha(hash, "outra"));
        assertEquals(false, AuthService.verificarSenha("lixo", "x"));
    }
}
