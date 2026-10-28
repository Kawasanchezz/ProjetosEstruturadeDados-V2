"""Endpoints (sem login): totais, explosão, simulação, implosão, CRUD, cascata, validação e migração."""
from fastapi.testclient import TestClient
from sqlalchemy import create_engine, inspect

from app import migrations
from app.config import Settings
from app.main import create_app
from tests.conftest import achar


def seed(client, nome="caneta") -> dict:
    r = client.post(f"/api/seed/{nome}")
    assert r.status_code == 201
    return r.json()


def test_totais_dos_testes_obrigatorios(client):
    assert seed(client)["custo_total"] == "7.50"
    assert seed(client, "computador")["custo_total"] == "8080.00"


def test_seed_invalido_e_idempotente(client):
    assert client.post("/api/seed/foguete").status_code == 422
    seed(client)
    seed(client)
    assert client.get("/api/produtos").json()["total"] == 1


def test_listagem_em_lote_tem_totais_e_contagens_corretos(client):
    seed(client), seed(client, "computador")
    itens = {i["nome"]: i for i in client.get("/api/produtos").json()["itens"]}
    assert (itens["Caneta"]["total_componentes"], itens["Caneta"]["profundidade"]) == (9, 3)
    assert (itens["Computador"]["total_componentes"], itens["Computador"]["custo_total"]) == (15, "8080.00")


def test_explosao_e_nomes_repetidos(client):
    p = seed(client)
    raiz = client.get(f"/api/produtos/{p['id']}/explosao").json()["raiz"]
    a, b = achar(raiz, "Plástico", "Corpo"), achar(raiz, "Plástico", "Tampa")
    assert a["id"] != b["id"] and (a["preco"], b["preco"]) == ("0.70", "0.50")
    assert achar(raiz, "Corpo")["custo_unitario"] == "2.00"


def test_simulacao(client):
    p = seed(client)
    r = client.post(f"/api/produtos/{p['id']}/simulacao", json={"quantidade": 10}).json()
    assert r["total"] == "75.00" and len(r["linhas"]) == 10
    assert client.post(f"/api/produtos/{p['id']}/simulacao", json={"quantidade": 0}).status_code == 422
    pc = seed(client, "computador")
    ram = next(l for l in client.post(f"/api/produtos/{pc['id']}/simulacao", json={"quantidade": 5}).json()["linhas"] if l["nome"] == "Memória RAM")
    assert (ram["quantidade_total"], ram["custo"]) == (10, "2000.00")


def test_implosao_retorna_ids_do_no_ate_a_raiz(client):
    p = seed(client)
    raiz = client.get(f"/api/produtos/{p['id']}/explosao").json()["raiz"]
    plastico, corpo = achar(raiz, "Plástico", "Corpo"), achar(raiz, "Corpo")
    r = client.patch(f"/api/componentes/{plastico['id']}/preco", json={"preco": "1.70"}).json()
    assert r["ids_afetados"] == [plastico["id"], corpo["id"], p["id"]]
    assert (r["total_antes"], r["total_depois"]) == ("7.50", "8.50")
    assert client.get(f"/api/produtos/{p['id']}").json()["custo_total"] == "8.50"


def test_crud_e_exclusao_em_cascata(client):
    p = client.post("/api/produtos", json={"nome": "  Mesa  ", "preco": "10"}).json()
    assert p["nome"] == "Mesa"
    c = client.post("/api/componentes", json={"nome": "Perna", "parent_id": p["id"], "quantidade": 4, "preco": "5.00"}).json()
    assert client.get(f"/api/produtos/{p['id']}").json()["custo_total"] == "30.00"
    assert client.put(f"/api/componentes/{c['id']}", json={"quantidade": 2}).json()["quantidade"] == 2
    assert client.get(f"/api/componentes/{c['id']}").json()["caminho"] == ["Mesa", "Perna"]

    s = seed(client)
    raiz = client.get(f"/api/produtos/{s['id']}/explosao").json()["raiz"]
    tinta = achar(raiz, "Tinta")
    assert client.delete(f"/api/componentes/{tinta['id']}").json() == {"removidos": 3}
    assert client.delete(f"/api/produtos/{s['id']}").json() == {"removidos": 7}
    assert client.get(f"/api/componentes/{tinta['id']}").status_code == 404


def test_validacao_de_entrada_e_paginacao(client):
    p = seed(client)
    base = {"nome": "X", "parent_id": p["id"], "quantidade": 1, "preco": "1"}
    for ruim in ({"nome": "   "}, {"quantidade": 0}, {"preco": "-0.01"}, {"preco": "abc"}, {"extra": 1}):
        assert client.post("/api/componentes", json={**base, **ruim}).status_code == 422, ruim
    assert client.post("/api/componentes", json={**base, "parent_id": 999}).status_code == 422
    for i in range(3):
        client.post("/api/produtos", json={"nome": f"P{i}"})
    pagina = client.get("/api/produtos", params={"limit": 2, "offset": 1}).json()
    assert pagina["total"] == 4 and len(pagina["itens"]) == 2
    assert client.get("/api/produtos", params={"limit": 1000}).status_code == 422


def test_migracoes_criam_tabelas_e_indices(tmp_path):
    url = f"sqlite:///{tmp_path / 'm.db'}"
    migrations.upgrade(url)
    insp = inspect(create_engine(url))
    assert {c["name"] for c in insp.get_columns("componentes")} == {"id", "nome", "parent_id", "quantidade", "preco", "version"}
    assert "token_version" in {c["name"] for c in insp.get_columns("usuarios")}
    assert {"refresh_tokens", "auditoria"} <= set(insp.get_table_names())
    assert any(i["column_names"] == ["parent_id"] for i in insp.get_indexes("componentes"))


def test_spa_serve_index_para_rotas_do_front(tmp_path):
    (tmp_path / "index.html").write_text('<div id="root"></div>', encoding="utf-8")
    app = create_app(Settings(database_url="sqlite://", auto_migrate=False, static_dir=str(tmp_path)))
    with TestClient(app) as c:
        assert 'id="root"' in c.get("/explosao/1").text
        assert "SQLite format" not in c.get("/..%2f..%2fbom.db").text  # sem path traversal
