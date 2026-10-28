"""Acesso a dados: só SQL/ORM, nenhuma regra de negócio."""
from __future__ import annotations

from typing import Optional

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.orm import NoORM


class NoRepository:
    def __init__(self, session: Session) -> None:
        self.session = session

    def get(self, id_: int, bloquear: bool = False) -> Optional[NoORM]:
        """`bloquear=True` emite SELECT ... FOR UPDATE (PostgreSQL); o SQLite ignora e usa o bloqueio otimista."""
        return self.session.get(NoORM, id_, with_for_update=bloquear)

    def add(self, no: NoORM) -> NoORM:
        self.session.add(no)
        self.session.flush()
        return no

    def delete(self, no: NoORM) -> None:
        self.session.delete(no)

    def listar_raizes(self, limit: int, offset: int) -> tuple[list[NoORM], int]:
        base = select(NoORM).where(NoORM.parent_id.is_(None))
        total = self.session.scalar(select(func.count()).select_from(base.subquery())) or 0
        itens = self.session.scalars(base.order_by(NoORM.id).limit(limit).offset(offset)).all()
        return list(itens), total

    def raiz_por_nome(self, nome: str) -> Optional[NoORM]:
        return self.session.scalars(
            select(NoORM).where(NoORM.parent_id.is_(None), NoORM.nome == nome)
        ).first()

    def subarvore(self, raiz_id: int) -> list[NoORM]:
        """Nó e todos os descendentes numa única query (CTE recursiva)."""
        cte = select(NoORM.id).where(NoORM.id == raiz_id).cte("subarvore", recursive=True)
        cte = cte.union(select(NoORM.id).where(NoORM.parent_id == cte.c.id))
        stmt = select(NoORM).where(NoORM.id.in_(select(cte.c.id))).order_by(NoORM.id)
        return list(self.session.scalars(stmt).all())

    def subarvores(self, raiz_ids: list[int]) -> dict[int, list[NoORM]]:
        """Subárvores de VÁRIAS raízes numa única consulta (evita N consultas na listagem de produtos)."""
        if not raiz_ids:
            return {}
        base = select(NoORM.id.label("id"), NoORM.id.label("raiz")).where(NoORM.id.in_(raiz_ids)).cte("subarvores", recursive=True)
        cte = base.union(select(NoORM.id, base.c.raiz).where(NoORM.parent_id == base.c.id))
        stmt = select(NoORM, cte.c.raiz).join(cte, NoORM.id == cte.c.id).order_by(NoORM.id)
        agrupado: dict[int, list[NoORM]] = {i: [] for i in raiz_ids}
        for no, raiz in self.session.execute(stmt):
            agrupado[raiz].append(no)
        return agrupado

    def ancestrais(self, no: NoORM, limite: int = 10_000) -> list[NoORM]:
        """Do pai imediato até a raiz."""
        cadeia: list[NoORM] = []
        atual = no
        while atual.parent_id is not None and len(cadeia) < limite:
            atual = self.session.get(NoORM, atual.parent_id)
            cadeia.append(atual)
        return cadeia
