package com.treebom.web;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.treebom.domain.Dinheiro;
import java.io.IOException;
import java.math.BigDecimal;
import org.springframework.boot.jackson.JsonComponent;

/** Todo valor monetário sai como texto com 2 casas ("8080.00"): nunca ponto flutuante na API. */
@JsonComponent
public class DinheiroSerializer extends JsonSerializer<BigDecimal> {
    @Override
    public void serialize(BigDecimal valor, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeString(Dinheiro.formatar(valor));
    }

    @Override
    public Class<BigDecimal> handledType() {
        return BigDecimal.class;
    }
}
