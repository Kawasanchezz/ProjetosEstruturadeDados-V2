package com.treebom.config;

import com.treebom.security.AuthService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Na partida com login ligado, cria o admin inicial (ADMIN_USERNAME/ADMIN_PASSWORD) se ainda não há usuários. */
@Component
public class Inicializacao implements ApplicationRunner {
    private final Settings settings;
    private final AuthService auth;

    public Inicializacao(Settings settings, AuthService auth) {
        this.settings = settings;
        this.auth = auth;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (settings.isAuthEnabled()) auth.garantirAdminInicial();
    }
}
