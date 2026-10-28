"""Erros de domínio. Mensagens são seguras para exibir ao usuário."""


class ErroFormula(ValueError):
    """Erro base de qualquer inconsistência na fórmula do produto."""


class PaiInexistenteError(ErroFormula):
    """O item referencia um pai que não existe na lista."""


class PaiAmbiguoError(ErroFormula):
    """O nome do pai aparece mais de uma vez; não dá para resolver só pelo nome."""


class CicloError(ErroFormula):
    """A hierarquia contém um ciclo (um nó seria ancestral de si mesmo)."""


class RaizInvalidaError(ErroFormula):
    """A fórmula não possui exatamente uma raiz."""


class NoNaoEncontradoError(ErroFormula):
    """Nenhum nó com o id informado."""


class LimiteExcedidoError(ErroFormula):
    """Um limite de tamanho, profundidade ou valor da fórmula foi excedido."""
