package com.treebom.web.filtros;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.util.Locale;

/**
 * Resposta que passa a ignorar qualquer alteração depois de {@link #descartar()}. Usada pelo filtro de timeout: o
 * trabalho que estourou o tempo continua rodando em segundo plano e não pode mais mexer no status, nos cabeçalhos nem
 * nos cookies da resposta (que o filtro já preencheu com o 504).
 */
final class RespostaDescartavel extends HttpServletResponseWrapper {
    private volatile boolean descartada;

    RespostaDescartavel(HttpServletResponse res) {
        super(res);
    }

    void descartar() {
        descartada = true;
    }

    @Override public void setStatus(int sc) { if (!descartada) super.setStatus(sc); }
    @Override public void sendError(int sc) throws IOException { if (!descartada) super.sendError(sc); }
    @Override public void sendError(int sc, String msg) throws IOException { if (!descartada) super.sendError(sc, msg); }
    @Override public void sendRedirect(String location) throws IOException { if (!descartada) super.sendRedirect(location); }
    @Override public void setHeader(String n, String v) { if (!descartada) super.setHeader(n, v); }
    @Override public void addHeader(String n, String v) { if (!descartada) super.addHeader(n, v); }
    @Override public void setIntHeader(String n, int v) { if (!descartada) super.setIntHeader(n, v); }
    @Override public void addIntHeader(String n, int v) { if (!descartada) super.addIntHeader(n, v); }
    @Override public void setDateHeader(String n, long v) { if (!descartada) super.setDateHeader(n, v); }
    @Override public void addDateHeader(String n, long v) { if (!descartada) super.addDateHeader(n, v); }
    @Override public void addCookie(Cookie c) { if (!descartada) super.addCookie(c); }
    @Override public void setContentType(String t) { if (!descartada) super.setContentType(t); }
    @Override public void setContentLength(int l) { if (!descartada) super.setContentLength(l); }
    @Override public void setContentLengthLong(long l) { if (!descartada) super.setContentLengthLong(l); }
    @Override public void setCharacterEncoding(String c) { if (!descartada) super.setCharacterEncoding(c); }
    @Override public void setLocale(Locale l) { if (!descartada) super.setLocale(l); }
    @Override public void reset() { if (!descartada) super.reset(); }
    @Override public void resetBuffer() { if (!descartada) super.resetBuffer(); }
    @Override public void flushBuffer() throws IOException { if (!descartada) super.flushBuffer(); }
}
