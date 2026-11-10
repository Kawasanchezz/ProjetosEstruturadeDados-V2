package com.treebom.web;

import com.treebom.config.Settings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final AuthInterceptor autenticacao;
    private final Settings settings;

    public WebConfig(AuthInterceptor autenticacao, Settings settings) {
        this.autenticacao = autenticacao;
        this.settings = settings;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(autenticacao).addPathPatterns("/api/**");
    }

    /** A raiz "/" não passa pelo resolvedor de recursos (caminho vazio): encaminha para o index.html. */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        if (staticAtivo()) registry.addViewController("/").setViewName("forward:/index.html");
    }

    private boolean staticAtivo() {
        return StringUtils.hasText(settings.getStaticDir()) && Files.isDirectory(Path.of(settings.getStaticDir()));
    }

    /** Serve o build do Vite; rotas desconhecidas (fora de /api) caem no index.html (React Router). */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (!StringUtils.hasText(settings.getStaticDir())) return;
        Path raiz = Path.of(settings.getStaticDir()).toAbsolutePath().normalize();
        if (!Files.isDirectory(raiz)) return;
        String local = raiz.toUri().toString();
        registry.addResourceHandler("/**").addResourceLocations(local).resourceChain(true)
            .addResolver(new PathResourceResolver() {
                @Override
                protected Resource getResource(String caminho, Resource location) throws IOException {
                    Resource r = super.getResource(caminho, location);
                    if (r != null && r.exists() && r.isReadable() && r.isFile()) return r;
                    if (caminho.equals("api") || caminho.startsWith("api/")) return null; // API desconhecida = 404, não HTML
                    Resource index = new FileSystemResource(raiz.resolve("index.html"));
                    return index.exists() ? index : null;
                }
            });
    }
}
