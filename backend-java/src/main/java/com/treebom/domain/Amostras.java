package com.treebom.domain;

import static com.treebom.domain.Modelos.ItemFormula.de;

import com.treebom.domain.Modelos.ItemFormula;
import java.util.List;
import java.util.Map;

/** Dados de exemplo (testes 1 e 2 do enunciado). */
public final class Amostras {
    private Amostras() {}

    public static final List<ItemFormula> CANETA = List.of(
        de("Caneta", null, 1, "2.50"),
        de("Tinta", "Caneta", 1, "0.80"),
        de("Pigmento", "Tinta", 1, "0.30"),
        de("Solvente", "Tinta", 1, "0.50"),
        de("Corpo", "Caneta", 1, "1.00"),
        de("Plástico", "Corpo", 1, "0.70"),
        de("Clip", "Corpo", 1, "0.30"),
        de("Tampa", "Caneta", 1, "0.70"),
        de("Plástico", "Tampa", 1, "0.50"),
        de("Vedação", "Tampa", 1, "0.20"));

    public static final List<ItemFormula> COMPUTADOR = List.of(
        de("Computador", null, 1, "2500.00"),
        de("Gabinete", "Computador", 1, "800.00"),
        de("Placa-Mãe", "Gabinete", 1, "500.00"),
        de("CPU", "Placa-Mãe", 1, "1000.00"),
        de("Memória RAM", "Placa-Mãe", 2, "200.00"),
        de("Chipset", "Placa-Mãe", 1, "150.00"),
        de("Fonte de Alimentação", "Gabinete", 1, "300.00"),
        de("Armazenamento", "Gabinete", 1, "0.00"),
        de("HDD", "Armazenamento", 1, "250.00"),
        de("SSD", "Armazenamento", 1, "400.00"),
        de("Monitor", "Computador", 1, "900.00"),
        de("Tela LCD", "Monitor", 1, "600.00"),
        de("Carcaça", "Monitor", 1, "100.00"),
        de("Periféricos", "Computador", 1, "0.00"),
        de("Teclado", "Periféricos", 1, "100.00"),
        de("Mouse", "Periféricos", 1, "80.00"));

    public static final Map<String, List<ItemFormula>> EXEMPLOS = Map.of("caneta", CANETA, "computador", COMPUTADOR);
}
