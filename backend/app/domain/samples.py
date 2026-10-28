"""Dados de exemplo (testes 1 e 2 do enunciado, convertidos do Java)."""
from app.domain.models import ItemFormula as I

CANETA = [
    I("Caneta", None, 1, "2.50"),
    I("Tinta", "Caneta", 1, "0.80"),
    I("Pigmento", "Tinta", 1, "0.30"),
    I("Solvente", "Tinta", 1, "0.50"),
    I("Corpo", "Caneta", 1, "1.00"),
    I("Plástico", "Corpo", 1, "0.70"),
    I("Clip", "Corpo", 1, "0.30"),
    I("Tampa", "Caneta", 1, "0.70"),
    I("Plástico", "Tampa", 1, "0.50"),
    I("Vedação", "Tampa", 1, "0.20"),
]

COMPUTADOR = [
    I("Computador", None, 1, "2500.00"),
    I("Gabinete", "Computador", 1, "800.00"),
    I("Placa-Mãe", "Gabinete", 1, "500.00"),
    I("CPU", "Placa-Mãe", 1, "1000.00"),
    I("Memória RAM", "Placa-Mãe", 2, "200.00"),
    I("Chipset", "Placa-Mãe", 1, "150.00"),
    I("Fonte de Alimentação", "Gabinete", 1, "300.00"),
    I("Armazenamento", "Gabinete", 1, "0.00"),
    I("HDD", "Armazenamento", 1, "250.00"),
    I("SSD", "Armazenamento", 1, "400.00"),
    I("Monitor", "Computador", 1, "900.00"),
    I("Tela LCD", "Monitor", 1, "600.00"),
    I("Carcaça", "Monitor", 1, "100.00"),
    I("Periféricos", "Computador", 1, "0.00"),
    I("Teclado", "Periféricos", 1, "100.00"),
    I("Mouse", "Periféricos", 1, "80.00"),
]

EXEMPLOS = {"caneta": CANETA, "computador": COMPUTADOR}
