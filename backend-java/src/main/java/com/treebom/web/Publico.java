package com.treebom.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Libera uma rota da autenticação. A API NEGA POR PADRÃO: qualquer rota sem esta marca exige ao menos o papel
 * {@code viewer}; as escritas exigem {@link Admin}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Publico {}
