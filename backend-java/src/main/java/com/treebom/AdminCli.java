package com.treebom;

import com.treebom.persistence.AuthRepository.Usuario;
import com.treebom.security.AuthService;
import com.treebom.security.AuthService.ErroAutenticacao;
import java.io.Console;
import java.io.PrintStream;
import java.util.function.Supplier;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Administração fora da API (sem e-mail): criar usuário e redefinir senha.
 *
 * <pre>
 * java -Dloader.main=com.treebom.AdminCli -cp target/treebom.jar org.springframework.boot.loader.launch.PropertiesLauncher criar-usuario NOME admin|viewer
 * java -Dloader.main=com.treebom.AdminCli -cp target/treebom.jar org.springframework.boot.loader.launch.PropertiesLauncher redefinir-senha NOME
 * </pre>
 *
 * Use as mesmas variáveis de ambiente da API (como DATABASE_URL). A senha é pedida no terminal (ou lida de
 * BOM_NOVA_SENHA); nunca é aceita como argumento, para não ir ao histórico do shell.
 */
@Component
public class AdminCli {
    static final String USO = """
        Uso:
          criar-usuario NOME admin|viewer
          redefinir-senha NOME
        A senha é pedida no terminal (ou lida de BOM_NOVA_SENHA).""";

    private final AuthService auth;

    public AdminCli(AuthService auth) {
        this.auth = auth;
    }

    /** Executa o comando e devolve o código de saída. */
    public int executar(String[] args, Supplier<String> senha, PrintStream out) {
        if (args.length < 2 || !(args[0].equals("criar-usuario") || args[0].equals("redefinir-senha"))) {
            out.println(USO);
            return 2;
        }
        try {
            if (args[0].equals("criar-usuario")) {
                if (args.length != 3) {
                    out.println(USO);
                    return 2;
                }
                Usuario u = auth.criarUsuario(args[1], senha.get(), args[2]);
                out.println("Usuário criado: " + u.username() + " (" + u.role() + ").");
            } else {
                Usuario u = auth.redefinirSenha(args[1], senha.get());
                out.println("Senha redefinida para " + u.username() + "; todas as sessões foram encerradas.");
            }
            return 0;
        } catch (ErroAutenticacao e) {
            out.println("Usuário não encontrado.");
            return 1;
        } catch (IllegalArgumentException e) {
            out.println("Erro: " + e.getMessage());
            return 1;
        }
    }

    private static String lerSenha() {
        String env = System.getenv("BOM_NOVA_SENHA");
        if (env != null && !env.isEmpty()) return env;
        Console console = System.console();
        if (console == null) throw new IllegalArgumentException("Sem terminal interativo: defina BOM_NOVA_SENHA.");
        String senha = new String(console.readPassword("Nova senha (12+ caracteres): "));
        if (!senha.equals(new String(console.readPassword("Repita a senha: "))))
            throw new IllegalArgumentException("As senhas não conferem.");
        return senha;
    }

    public static void main(String[] args) {
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(TreeBomApplication.class)
                .web(WebApplicationType.NONE).bannerMode(org.springframework.boot.Banner.Mode.OFF)
                .logStartupInfo(false).run()) {
            int codigo = ctx.getBean(AdminCli.class).executar(args, AdminCli::lerSenha, System.out);
            ctx.close();
            System.exit(codigo);
        }
    }
}
