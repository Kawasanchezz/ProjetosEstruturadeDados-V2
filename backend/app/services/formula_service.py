"""Casos de uso do TreeBOM. Orquestra repositório + domínio; cada método público é uma transação.

Segurança aplicada aqui (servidor é a fonte da verdade):
- limites de nós, filhos por nó, profundidade e valor total revalidados após CADA escrita (antes do commit);
- trilha de auditoria gravada na mesma transação da alteração;
- SELECT ... FOR UPDATE (PostgreSQL) + bloqueio otimista por coluna `version` (todos os bancos).
"""
from __future__ import annotations

from collections import Counter
from decimal import Decimal
from typing import Any, Optional

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.config import Settings
from app.domain import (
    CicloError,
    LimiteExcedidoError,
    NoNaoEncontradoError,
    PaiInexistenteError,
    RegistroNo,
    TreeNode,
    atualizar_preco,
    build_tree,
    build_tree_por_id,
    calcular_total,
    calcular_totais,
    explodir,
    localizar,
    profundidade,
    simular_producao,
)
from app.domain.samples import EXEMPLOS
from app.orm import AuditoriaORM, NoORM
from app.repositories.no_repository import NoRepository

CAMPOS_EDITAVEIS = ("nome", "quantidade", "preco")


def _dinheiro(valor: Decimal) -> str:
    return f"{valor:.2f}"


class FormulaService:
    def __init__(self, session: Session, settings: Optional[Settings] = None, ator: str = "anon") -> None:
        self.session = session
        self.repo = NoRepository(session)
        self.cfg = settings or Settings()
        self.ator = ator

    # ------------------------------------------------------------ helpers
    def _no(self, id_: int, rotulo: str = "Componente", bloquear: bool = False) -> NoORM:
        no = self.repo.get(id_, bloquear)
        if no is None:
            raise NoNaoEncontradoError(f"{rotulo} id={id_} não encontrado")
        return no

    def _produto(self, id_: int, bloquear: bool = False) -> NoORM:
        no = self.repo.get(id_, bloquear)
        if no is None or no.parent_id is not None:
            raise NoNaoEncontradoError(f"Produto id={id_} não encontrado")
        return no

    def _raiz_de(self, no: NoORM) -> NoORM:
        ancestrais = self.repo.ancestrais(no)
        return ancestrais[-1] if ancestrais else no

    def _arvore(self, raiz_id: int) -> TreeNode:
        rows = self.repo.subarvore(raiz_id)
        registros = [
            RegistroNo(r.id, r.nome, None if r.id == raiz_id else r.parent_id, r.quantidade, r.preco)
            for r in rows
        ]
        return build_tree_por_id(registros)

    def _pai_valido(self, parent_id: int) -> NoORM:
        pai = self.repo.get(parent_id)
        if pai is None:
            raise PaiInexistenteError(f"Pai id={parent_id} não existe")
        return pai

    def _arvore_de(self, raiz_id: int, rows: list[NoORM]) -> TreeNode:
        return build_tree_por_id(
            RegistroNo(r.id, r.nome, None if r.id == raiz_id else r.parent_id, r.quantidade, r.preco) for r in rows
        )

    def _validar_arvore(self, raiz_id: int) -> TreeNode:
        """Revalida limites e integridade da árvore do produto. Chamada antes do commit de toda escrita."""
        c = self.cfg
        rows = self.repo.subarvore(raiz_id)
        if len(rows) > c.max_nos_por_produto:
            raise LimiteExcedidoError(f"Um produto pode ter no máximo {c.max_nos_por_produto} nós")
        filhos = Counter(r.parent_id for r in rows if r.parent_id is not None and r.id != raiz_id)
        if filhos and max(filhos.values()) > c.max_filhos_por_no:
            raise LimiteExcedidoError(f"Um nó pode ter no máximo {c.max_filhos_por_no} filhos")
        arvore = build_tree_por_id(
            RegistroNo(r.id, r.nome, None if r.id == raiz_id else r.parent_id, r.quantidade, r.preco) for r in rows
        )
        if profundidade(arvore) > c.max_profundidade:
            raise LimiteExcedidoError(f"Profundidade máxima da árvore é {c.max_profundidade} níveis")
        if calcular_total(arvore) > c.max_valor_total:
            raise LimiteExcedidoError("O custo total do produto excede o limite permitido")
        return arvore

    def _auditar(
        self, acao: str, no_id: Optional[int], nome: Optional[str], antes: Optional[str] = None,
        depois: Optional[str] = None, detalhe: Optional[str] = None,
    ) -> None:
        self.session.add(AuditoriaORM(ator=self.ator[:80], acao=acao, no_id=no_id, nome=nome, antes=antes, depois=depois, detalhe=detalhe))

    # ---------------------------------------------------------------- CRUD
    def criar_no(self, nome: str, parent_id: Optional[int], quantidade: int, preco: Decimal) -> NoORM:
        if parent_id is not None:
            self._pai_valido(parent_id)
        no = self.repo.add(NoORM(nome=nome, parent_id=parent_id, quantidade=quantidade, preco=preco))
        self._validar_arvore(self._raiz_de(no).id)
        self._auditar("criar", no.id, nome, depois=_dinheiro(preco), detalhe=f"pai={parent_id} qtd={quantidade}")
        self.session.commit()
        return no

    def editar_no(self, id_: int, campos: dict[str, Any], produto: bool = False) -> NoORM:
        no = self._produto(id_, bloquear=True) if produto else self._no(id_, bloquear=True)
        raiz_antiga = self._raiz_de(no).id
        preco_antes = no.preco
        pai_antes = no.parent_id
        for chave in CAMPOS_EDITAVEIS:
            if chave in campos:
                setattr(no, chave, campos[chave])
        moveu = "parent_id" in campos and campos["parent_id"] != no.parent_id
        if moveu:
            self._mover(no, campos["parent_id"])
        self.session.flush()
        raiz_nova = self._raiz_de(no).id
        for raiz in {raiz_antiga, raiz_nova}:
            if self.repo.get(raiz) is not None:
                self._validar_arvore(raiz)
        if no.preco != preco_antes:
            self._auditar("preco", no.id, no.nome, _dinheiro(preco_antes), _dinheiro(no.preco))
        if moveu:
            self._auditar("mover", no.id, no.nome, detalhe=f"pai {pai_antes} -> {no.parent_id}")
        if set(campos) & {"nome", "quantidade"}:
            self._auditar("editar", no.id, no.nome, detalhe=f"campos={sorted(set(campos) & {'nome', 'quantidade'})}")
        self.session.commit()
        return no

    def _mover(self, no: NoORM, novo_pai_id: Optional[int]) -> None:
        if novo_pai_id is not None:
            self._pai_valido(novo_pai_id)
            if novo_pai_id in {n.id for n in self.repo.subarvore(no.id)}:
                raise CicloError("Um nó não pode ser movido para dentro da própria subárvore")
        no.parent_id = novo_pai_id

    def excluir_no(self, id_: int, produto: bool = False) -> int:
        """Exclusão em CASCATA: remove o nó e toda a subárvore. Retorna quantos nós saíram."""
        no = self._produto(id_, bloquear=True) if produto else self._no(id_, bloquear=True)
        removidos = len(self.repo.subarvore(no.id))
        nome, no_id = no.nome, no.id
        self.repo.delete(no)
        self._auditar("excluir", no_id, nome, detalhe=f"{removidos} nó(s) removido(s) em cascata")
        self.session.commit()
        return removidos

    # ------------------------------------------------------------- leitura
    def obter_no(self, id_: int) -> NoORM:
        return self._no(id_)

    def obter_produto(self, id_: int) -> NoORM:
        return self._produto(id_)

    def resumo_produto(self, raiz: NoORM, linhas: Optional[list[NoORM]] = None) -> dict:
        arvore = self._arvore(raiz.id) if linhas is None else self._arvore_de(raiz.id, linhas)
        return {
            "id": raiz.id,
            "nome": raiz.nome,
            "quantidade": raiz.quantidade,
            "preco": raiz.preco,
            "custo_total": calcular_total(arvore),
            "total_componentes": len(explodir(arvore)) - 1,
            "profundidade": profundidade(arvore),
        }

    def listar_produtos(self, limit: int, offset: int) -> dict:
        raizes, total = self.repo.listar_raizes(limit, offset)
        por_raiz = self.repo.subarvores([r.id for r in raizes])  # 1 consulta para a página inteira
        return {"total": total, "itens": [self.resumo_produto(r, por_raiz[r.id]) for r in raizes]}

    def listar_componentes(self, produto_id: int, limit: int, offset: int) -> dict:
        self._produto(produto_id)
        rows = self.repo.subarvore(produto_id)
        return {"total": len(rows), "itens": rows[offset : offset + limit]}

    def detalhe_componente(self, id_: int) -> dict:
        no = self._no(id_)
        ancestrais = self.repo.ancestrais(no)
        raiz = ancestrais[-1] if ancestrais else no
        node = localizar(self._arvore(raiz.id), id_)
        return {
            "id": no.id,
            "nome": no.nome,
            "parent_id": no.parent_id,
            "quantidade": no.quantidade,
            "preco": no.preco,
            "produto_id": raiz.id,
            "caminho": [n.nome for n in reversed(ancestrais)] + [no.nome],
            "custo_total": calcular_total(node),
            "total_descendentes": len(explodir(node)) - 1,
        }

    # ------------------------------------------------------------ explosão
    def explosao(self, produto_id: int) -> dict:
        self._produto(produto_id)
        arvore = self._arvore(produto_id)
        if profundidade(arvore) > self.cfg.max_profundidade:
            raise LimiteExcedidoError(f"Profundidade máxima da árvore é {self.cfg.max_profundidade} níveis")
        totais = calcular_totais(arvore)
        if totais[id(arvore)] > self.cfg.max_valor_total:
            raise LimiteExcedidoError("O custo total do produto excede o limite permitido")

        def montar(no: TreeNode) -> dict:
            total = totais[id(no)]
            return {
                "id": no.id,
                "nome": no.nome,
                "parent_id": no.pai.id if no.pai else None,
                "quantidade": no.quantidade,
                "preco": no.preco,
                "custo_unitario": total / no.quantidade,
                "custo_total": total,
                "filhos": [],
            }

        # Montagem iterativa (sem recursão): a profundidade já é limitada, mas o código não depende disso.
        raiz = montar(arvore)
        pilha = [(arvore, raiz)]
        while pilha:
            no, dado = pilha.pop()
            for filho in no.filhos:
                d = montar(filho)
                dado["filhos"].append(d)
                pilha.append((filho, d))
        return {"produto_id": produto_id, "nome": arvore.nome, "total": totais[id(arvore)], "raiz": raiz}

    # ------------------------------------------------------------ simulação
    def simulacao(self, produto_id: int, unidades: int) -> dict:
        self._produto(produto_id)
        arvore = self._arvore(produto_id)
        if unidades * calcular_total(arvore) > self.cfg.max_valor_total:
            raise LimiteExcedidoError("O valor total da simulação excede o limite permitido")
        linhas = simular_producao(arvore, unidades, self.cfg.max_quantidade_total)
        return {
            "produto_id": produto_id,
            "unidades": unidades,
            "total": sum((l.custo for l in linhas), Decimal("0")),
            "linhas": [
                {"id": l.id, "nome": l.nome, "caminho": list(l.caminho), "quantidade_total": l.quantidade_total, "custo": l.custo}
                for l in linhas
            ],
        }

    # ------------------------------------------------------------- implosão
    def atualizar_preco(self, id_: int, novo_preco: Decimal) -> dict:
        no = self._no(id_, bloquear=True)
        raiz = self._raiz_de(no)
        arvore = self._arvore(raiz.id)
        preco_antes = no.preco
        passos = atualizar_preco(arvore, id_, novo_preco)
        if passos[-1].depois > self.cfg.max_valor_total:
            raise LimiteExcedidoError("O custo total do produto excede o limite permitido")
        no.preco = novo_preco
        self._auditar(
            "preco", no.id, no.nome, _dinheiro(preco_antes), _dinheiro(novo_preco),
            detalhe=f"total do produto {_dinheiro(passos[-1].antes)} -> {_dinheiro(passos[-1].depois)}",
        )
        self.session.commit()  # StaleDataError aqui = outra escrita concorrente no mesmo nó → 409
        return {
            "produto_id": raiz.id,
            "ids_afetados": [p.id for p in passos],
            "total_antes": passos[-1].antes,
            "total_depois": passos[-1].depois,
            "passos": [
                {"id": p.id, "nome": p.nome, "antes": p.antes, "depois": p.depois, "diferenca": p.diferenca}
                for p in passos
            ],
        }

    # ----------------------------------------------------------------- seed
    def carregar_exemplo(self, chave: str) -> NoORM:
        """Cria o exemplo. Idempotente: substitui um produto de mesmo nome já existente."""
        raiz_dominio = build_tree(EXEMPLOS[chave])
        existente = self.repo.raiz_por_nome(raiz_dominio.nome)
        if existente is not None:
            self.repo.delete(existente)
            self.session.flush()
        ids: dict[int, int] = {}
        raiz_db: Optional[NoORM] = None
        for _, node, _ in explodir(raiz_dominio):  # pré-ordem: pai sempre antes do filho
            parent_id = ids[node.pai.id] if node.pai else None
            criado = self.repo.add(
                NoORM(nome=node.nome, parent_id=parent_id, quantidade=node.quantidade, preco=node.preco)
            )
            ids[node.id] = criado.id
            raiz_db = raiz_db or criado
        self._auditar("seed", raiz_db.id, raiz_db.nome, detalhe=f"exemplo={chave}")
        self.session.commit()
        return raiz_db

    # ------------------------------------------------------------ auditoria
    def listar_auditoria(self, limit: int, offset: int) -> dict:
        total = self.session.scalar(select(func.count()).select_from(AuditoriaORM)) or 0
        itens = self.session.scalars(
            select(AuditoriaORM).order_by(AuditoriaORM.id.desc()).limit(limit).offset(offset)
        ).all()
        return {"total": total, "itens": list(itens)}
