"""Rotas. Finas: validam entrada (Pydantic), delegam ao serviço, serializam saída."""
from __future__ import annotations

from typing import Annotated, Iterator, Literal

from fastapi import APIRouter, Depends, HTTPException, Path, Query, Request
from sqlalchemy.orm import Session

from app.api import schemas as s
from app.security.auth import Principal, exigir, get_principal
from app.services.formula_service import FormulaService

# Negar por padrão: TODA rota deste router exige ao menos 'viewer'; escritas exigem 'admin' (ver abaixo).
router = APIRouter(dependencies=[Depends(exigir("viewer"))])
ADMIN = [Depends(exigir("admin"))]
IdPath = Annotated[int, Path(gt=0, le=s.MAX_ID)]
Limite = Query(20, ge=1, le=100)
Deslocamento = Query(0, ge=0)


def get_service(request: Request, principal: Principal = Depends(get_principal)) -> Iterator[FormulaService]:
    with request.app.state.sessionmaker() as session:  # type: Session
        yield FormulaService(session, request.app.state.settings, ator=principal.username)


Servico = Depends(get_service)


# ------------------------------------------------------------------ produtos
@router.get("/produtos", response_model=s.PaginaProdutos, tags=["produtos"])
def listar_produtos(limit: int = Limite, offset: int = Deslocamento, svc: FormulaService = Servico):
    return svc.listar_produtos(limit, offset)


@router.post("/produtos", response_model=s.ProdutoResumo, status_code=201, tags=["produtos"], dependencies=ADMIN)
def criar_produto(dados: s.ProdutoCriar, svc: FormulaService = Servico):
    raiz = svc.criar_no(dados.nome, None, dados.quantidade, dados.preco)
    return svc.resumo_produto(raiz)


@router.get("/produtos/{produto_id}", response_model=s.ProdutoResumo, tags=["produtos"])
def obter_produto(produto_id: IdPath, svc: FormulaService = Servico):
    return svc.resumo_produto(svc.obter_produto(produto_id))


@router.put("/produtos/{produto_id}", response_model=s.ProdutoResumo, tags=["produtos"], dependencies=ADMIN)
def editar_produto(produto_id: IdPath, dados: s.ProdutoAtualizar, svc: FormulaService = Servico):
    campos = dados.model_dump(exclude_unset=True, exclude_none=True)
    return svc.resumo_produto(svc.editar_no(produto_id, campos, produto=True))


@router.delete("/produtos/{produto_id}", response_model=s.Remocao, tags=["produtos"], dependencies=ADMIN)
def excluir_produto(produto_id: IdPath, svc: FormulaService = Servico):
    """Exclusão em cascata: remove o produto e todos os seus componentes."""
    return {"removidos": svc.excluir_no(produto_id, produto=True)}


@router.get("/produtos/{produto_id}/explosao", response_model=s.Explosao, tags=["cálculos"])
def explosao(produto_id: IdPath, svc: FormulaService = Servico):
    return svc.explosao(produto_id)


@router.post("/produtos/{produto_id}/simulacao", response_model=s.Simulacao, tags=["cálculos"])
def simulacao(produto_id: IdPath, dados: s.SimulacaoEntrada, svc: FormulaService = Servico):
    return svc.simulacao(produto_id, dados.quantidade)


@router.get("/produtos/{produto_id}/componentes", response_model=s.PaginaComponentes, tags=["produtos"])
def listar_componentes(
    produto_id: IdPath, limit: int = Query(100, ge=1, le=500), offset: int = Deslocamento,
    svc: FormulaService = Servico,
):
    """Lista plana (nó raiz incluído), usada para popular o select de pai."""
    return svc.listar_componentes(produto_id, limit, offset)


# --------------------------------------------------------------- componentes
@router.post("/componentes", response_model=s.NoOut, status_code=201, tags=["componentes"], dependencies=ADMIN)
def criar_componente(dados: s.ComponenteCriar, svc: FormulaService = Servico):
    return svc.criar_no(dados.nome, dados.parent_id, dados.quantidade, dados.preco)


@router.get("/componentes/{componente_id}", response_model=s.ComponenteDetalhe, tags=["componentes"])
def obter_componente(componente_id: IdPath, svc: FormulaService = Servico):
    return svc.detalhe_componente(componente_id)


@router.put("/componentes/{componente_id}", response_model=s.NoOut, tags=["componentes"], dependencies=ADMIN)
def editar_componente(componente_id: IdPath, dados: s.ComponenteAtualizar, svc: FormulaService = Servico):
    campos = dados.model_dump(exclude_unset=True)
    campos = {k: v for k, v in campos.items() if v is not None or k == "parent_id"}
    return svc.editar_no(componente_id, campos)


@router.delete("/componentes/{componente_id}", response_model=s.Remocao, tags=["componentes"], dependencies=ADMIN)
def excluir_componente(componente_id: IdPath, svc: FormulaService = Servico):
    """Exclusão em cascata: remove o componente e toda a sua subárvore."""
    return {"removidos": svc.excluir_no(componente_id)}


@router.patch("/componentes/{componente_id}/preco", response_model=s.Implosao, tags=["cálculos"], dependencies=ADMIN)
def atualizar_preco(componente_id: IdPath, dados: s.PrecoAtualizar, svc: FormulaService = Servico):
    """Atualiza o preço e devolve a implosão: ids do nó até a raiz e custos antes/depois."""
    return svc.atualizar_preco(componente_id, dados.preco)


# ---------------------------------------------------------------------- seed
def seed_habilitado(request: Request) -> None:
    if not request.app.state.settings.seed_ativo:
        raise HTTPException(status_code=404, detail="Não encontrado")


@router.post(
    "/seed/{exemplo}", response_model=s.ProdutoResumo, status_code=201, tags=["seed"],
    dependencies=[*ADMIN, Depends(seed_habilitado)],
)
def seed(exemplo: Literal["caneta", "computador"], svc: FormulaService = Servico):
    """Carrega um exemplo (admin; desligado por padrão em produção). Recria um produto de mesmo nome."""
    return svc.resumo_produto(svc.carregar_exemplo(exemplo))


@router.get("/auditoria", response_model=s.PaginaAuditoria, tags=["auditoria"], dependencies=ADMIN)
def auditoria(limit: int = Limite, offset: int = Deslocamento, svc: FormulaService = Servico):
    """Trilha de auditoria: quem alterou preços, criou, moveu ou excluiu (somente admin)."""
    return svc.listar_auditoria(limit, offset)
