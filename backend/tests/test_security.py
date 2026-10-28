"""Testes de segurança (regressão). Cada teste prova uma proteção do sistema."""
import secrets
import time

import fakeredis
import jwt
import pytest
from fastapi.routing import APIRoute
from fastapi.testclient import TestClient
from sqlalchemy import create_engine, select, text

from app import admin_cli, migrations
from app.config import ConfiguracaoInsegura, Settings
from app.database import Base, make_sessionmaker
from app.orm import AuditoriaORM, NoORM, UsuarioORM
from app.security.auth import AuthService, LoginThrottle, RedisLoginThrottle
from app.services.formula_service import FormulaService
from tests.conftest import SEGREDO, achar, make_client

SENHA_ADMIN = secrets.token_urlsafe(18)  # geradas a cada execução: nenhuma senha fixa no repositório
SENHA_VIEWER = secrets.token_urlsafe(18)


def cliente_auth(**extra) -> TestClient:
    c = make_client(auth_enabled=True, jwt_secret=SEGREDO, cookie_secure=False, **extra)
    with c.app.state.sessionmaker() as s:
        svc = AuthService(s, c.app.state.settings, c.app.state.login_throttle)
        svc.criar_usuario("admin1", SENHA_ADMIN, "admin")
        svc.criar_usuario("viewer1", SENHA_VIEWER, "viewer")
    return c


def login(c, u, s):
    return c.post("/api/auth/login", json={"username": u, "password": s})


def cab(c, u, s) -> dict:
    r = login(c, u, s)
    assert r.status_code == 200, r.text
    return {"Authorization": f"Bearer {r.json()['access_token']}"}


def forjar(segredo=SEGREDO, algoritmo="HS256", **claims) -> str:
    agora = int(time.time())
    base = {"sub": "1", "iat": agora, "exp": agora + 600, "jti": "x", "typ": "access", "tv": 1}
    return jwt.encode({**base, **claims}, segredo, algorithm=algoritmo)


# ------------------------------------------------------------- JWT e login
def test_sem_token_malformado_expirado_alg_none_e_outro_segredo():
    with cliente_auth() as c:
        assert c.get("/api/produtos").status_code == 401
        sem_assinatura = jwt.encode({"sub": "1", "exp": int(time.time()) + 600, "iat": 1, "jti": "x", "typ": "access", "tv": 1}, key=None, algorithm="none")
        for token in ("lixo", "a.b.c", forjar(exp=int(time.time()) - 5), sem_assinatura, forjar("x" * 48), forjar(typ="refresh")):
            assert c.get("/api/produtos", headers={"Authorization": f"Bearer {token}"}).status_code == 401, token[:20]


def test_login_nao_revela_usuario_e_bloqueia_forca_bruta():
    with cliente_auth() as c:
        a, b = login(c, "ninguem", "qualquer-coisa-123"), login(c, "admin1", "errada-errada-1")
        assert a.status_code == b.status_code == 401 and a.json() == b.json()
        for _ in range(3):
            login(c, "admin1", "errada-errada-1")
        assert login(c, "admin1", SENHA_ADMIN).status_code == 429  # bloqueado mesmo com a senha certa


def test_senha_em_argon2id_e_politica_de_senha():
    with cliente_auth() as c:
        with c.app.state.sessionmaker() as s:
            hashes = [u.password_hash for u in s.scalars(select(UsuarioORM))]
        assert all(h.startswith("$argon2id$") for h in hashes) and SENHA_ADMIN not in "".join(hashes)
        h = cab(c, "admin1", SENHA_ADMIN)
        assert c.post("/api/auth/users", json={"username": "novo", "password": "curta", "role": "viewer"}, headers=h).status_code == 422


def test_logout_revoga_access_token_na_hora_e_o_refresh():
    with cliente_auth() as c:
        r = login(c, "admin1", SENHA_ADMIN)
        h = {"Authorization": f"Bearer {r.json()['access_token']}"}
        refresh = c.cookies.get("bom_refresh")
        assert c.get("/api/produtos", headers=h).status_code == 200
        assert c.post("/api/auth/logout").status_code == 204
        assert c.get("/api/produtos", headers=h).status_code == 401  # token_version mudou
        c.cookies.set("bom_refresh", refresh, path="/api/auth")
        assert c.post("/api/auth/refresh").status_code == 401


def test_rotacao_de_refresh_e_reuso_derruba_a_familia():
    with cliente_auth() as c:
        login(c, "admin1", SENHA_ADMIN)
        antigo = c.cookies.get("bom_refresh")
        assert c.post("/api/auth/refresh").status_code == 200
        novo = c.cookies.get("bom_refresh")
        assert novo != antigo
        c.cookies.set("bom_refresh", antigo, path="/api/auth")
        assert c.post("/api/auth/refresh").status_code == 401  # reuso detectado
        c.cookies.set("bom_refresh", novo, path="/api/auth")
        assert c.post("/api/auth/refresh").status_code == 401  # a família inteira caiu


def test_cookie_httponly_samesite_strict_e_origem_nao_confiavel():
    with cliente_auth() as c:
        cookie = login(c, "admin1", SENHA_ADMIN).headers["set-cookie"].lower()
        assert "httponly" in cookie and "samesite=strict" in cookie and "path=/api/auth" in cookie
        assert c.post("/api/auth/refresh", headers={"Origin": "http://evil.example"}).status_code == 403


def test_rotacao_de_segredo_jwt():
    with cliente_auth(jwt_secret_anterior="o" * 48) as c:
        antigo = forjar("o" * 48, sub="1")
        assert c.get("/api/produtos", headers={"Authorization": f"Bearer {antigo}"}).status_code == 200
        assert c.get("/api/produtos", headers={"Authorization": f"Bearer {forjar('z' * 48)}"}).status_code == 401
    with cliente_auth() as c:  # sem segredo anterior configurado, o token do segredo antigo não vale
        assert c.get("/api/produtos", headers={"Authorization": f"Bearer {forjar('o' * 48)}"}).status_code == 401


def test_rotas_de_auth_so_existem_com_auth_ligada(client):
    assert client.post("/api/auth/login", json={"username": "a", "password": "b"}).status_code == 404
    assert client.get("/api/auth/config").json() == {"auth_enabled": False}
    with cliente_auth() as c:
        assert c.get("/api/auth/config").json() == {"auth_enabled": True}


# ------------------------------------------------------------ autorização
def test_viewer_le_e_simula_mas_nao_escreve():
    with cliente_auth() as c:
        admin, viewer = cab(c, "admin1", SENHA_ADMIN), cab(c, "viewer1", SENHA_VIEWER)
        p = c.post("/api/seed/caneta", headers=admin).json()
        assert c.get(f"/api/produtos/{p['id']}/explosao", headers=viewer).status_code == 200
        assert c.post(f"/api/produtos/{p['id']}/simulacao", json={"quantidade": 2}, headers=viewer).status_code == 200
        proibidos = [
            c.post("/api/produtos", json={"nome": "X"}, headers=viewer),
            c.delete(f"/api/produtos/{p['id']}", headers=viewer),
            c.patch(f"/api/componentes/{p['id']}/preco", json={"preco": "9"}, headers=viewer),
            c.post("/api/seed/computador", headers=viewer),
            c.get("/api/auditoria", headers=viewer),
            c.post("/api/auth/users", json={"username": "hack", "password": "x" * 14, "role": "admin"}, headers=viewer),
        ]
        assert [r.status_code for r in proibidos] == [403] * 6
        assert c.get(f"/api/produtos/{p['id']}", headers=viewer).json()["custo_total"] == "7.50"


def test_papel_vem_do_banco_e_desativado_perde_acesso():
    with cliente_auth() as c:
        h, hv = cab(c, "admin1", SENHA_ADMIN), cab(c, "viewer1", SENHA_VIEWER)
        with c.app.state.sessionmaker() as s:
            s.scalar(select(UsuarioORM).where(UsuarioORM.username == "admin1")).role = "viewer"
            s.scalar(select(UsuarioORM).where(UsuarioORM.username == "viewer1")).ativo = False
            s.commit()
        assert c.post("/api/produtos", json={"nome": "X"}, headers=h).status_code == 403
        assert c.get("/api/produtos", headers=hv).status_code == 401


def test_negar_por_padrao_toda_rota_da_api_exige_papel():
    c = make_client(auth_enabled=True, jwt_secret=SEGREDO, cookie_secure=False)
    publicas = {"/api/saude", "/api/auth/config", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout"}

    def chamadas(dep):
        yield dep.call
        for sub in dep.dependencies:
            yield from chamadas(sub)

    sem = [r.path for r in c.app.routes if isinstance(r, APIRoute) and r.path.startswith("/api") and r.path not in publicas
           and not {getattr(f, "__name__", "") for f in chamadas(r.dependant)} & {"exigir_viewer", "exigir_admin"}]
    assert sem == []


@pytest.mark.parametrize("extra", [
    dict(auth_enabled=True, jwt_secret=None), dict(auth_enabled=True, jwt_secret="curto"), dict(cors_origins=("*",)),
    dict(app_env="production", auth_enabled=False),
    dict(app_env="production", auth_enabled=True, jwt_secret=SEGREDO, cookie_secure=False),
    dict(auth_enabled=True, jwt_secret=SEGREDO, jwt_secret_anterior="curto"),
])
def test_configuracao_insegura_impede_a_partida(extra):
    with pytest.raises(ConfiguracaoInsegura):
        Settings(**extra).validar()


def test_producao_desliga_docs_seed_e_liga_hsts():
    c = make_client(app_env="production", auth_enabled=True, jwt_secret=SEGREDO)
    for url in ("/api/docs", "/api/redoc", "/api/openapi.json"):
        assert c.get(url).status_code == 404
    assert "max-age" in c.get("/api/saude").headers["strict-transport-security"]
    with c.app.state.sessionmaker() as s:
        AuthService(s, c.app.state.settings, c.app.state.login_throttle).criar_usuario("adm", SENHA_ADMIN, "admin")
    assert c.post("/api/seed/caneta", headers=cab(c, "adm", SENHA_ADMIN)).status_code == 404


# ------------------------------------------------- cabeçalhos, erros, entrada
def test_cabecalhos_de_seguranca(client):
    h = client.get("/api/produtos").headers
    assert "script-src 'self'" in h["content-security-policy"] and "unsafe" not in h["content-security-policy"]
    assert h["x-content-type-options"] == "nosniff" and h["x-frame-options"] == "DENY" and h["referrer-policy"] == "no-referrer"
    assert "x-request-id" in h and "strict-transport-security" not in h


def test_erro_interno_nao_vaza_detalhes(client, monkeypatch):
    def quebra(*_a, **_k):
        raise RuntimeError("segredo-interno SELECT * FROM usuarios")

    monkeypatch.setattr(FormulaService, "listar_produtos", quebra)
    r = client.get("/api/produtos")
    assert r.status_code == 500 and "segredo-interno" not in r.text and "Traceback" not in r.text
    assert r.json()["request_id"] == r.headers["x-request-id"]


def test_validacao_nao_ecoa_valor_e_corpo_e_tipo_sao_limitados(client):
    assert "SEGREDO-123" not in client.post("/api/produtos", json={"nome": "ok", "preco": "SEGREDO-123"}).text
    assert client.post("/api/produtos", json={"nome": "x", "z": "y" * 70_000}).status_code == 413
    assert client.post("/api/produtos", content='{"nome":"a"}', headers={"Content-Type": "text/plain"}).status_code == 415


@pytest.mark.parametrize("preco", ["NaN", "Infinity", "1e999", "-1", "0.001", "99999999999999", "abc", None, []])
def test_precos_invalidos(client, preco):
    p = client.post("/api/produtos", json={"nome": "B"}).json()
    assert client.post("/api/componentes", json={"nome": "X", "parent_id": p["id"], "preco": preco}).status_code == 422


@pytest.mark.parametrize("qtd", [0, -1, 1.5, "5", True, None, 1_000_001, 10**30])
def test_quantidades_invalidas(client, qtd):
    p = client.post("/api/produtos", json={"nome": "B"}).json()
    assert client.post("/api/componentes", json={"nome": "X", "parent_id": p["id"], "preco": "1", "quantidade": qtd}).status_code == 422


@pytest.mark.parametrize("nome", ["a\nb", "a\x00b", "a‮b", "a\x1b[31mb", "", "x" * 121])
def test_nomes_invalidos(client, nome):
    assert client.post("/api/produtos", json={"nome": nome}).status_code == 422


def test_campos_extras_ids_gigantes_e_injecao(client):
    for extra in ({"id": 9}, {"version": 5}, {"role": "admin"}, {"parent_id": 1}):
        assert client.post("/api/produtos", json={"nome": "X", **extra}).status_code == 422
    for url in ("/api/produtos/99999999999999999999", "/api/componentes/0", "/api/produtos/-1"):
        assert client.get(url).status_code == 422
    for nome in ("x'); DROP TABLE componentes;--", '<img src=x onerror="alert(1)">', "<script>alert(1)</script>"):
        assert client.post("/api/produtos", json={"nome": nome}).json()["nome"] == nome  # texto inerte
    with client.app.state.engine.connect() as con:
        assert con.execute(text("SELECT count(*) FROM componentes")).scalar() == 3


# ----------------------------------------------------- árvore: limites e ciclos
def test_ciclos_limites_e_overflow(client):
    p = client.post("/api/produtos", json={"nome": "R", "preco": "1"}).json()["id"]
    a = client.post("/api/componentes", json={"nome": "A", "parent_id": p, "preco": "1"}).json()["id"]
    b = client.post("/api/componentes", json={"nome": "B", "parent_id": a, "preco": "1"}).json()["id"]
    assert [client.put(f"/api/componentes/{x}", json={"parent_id": y}).status_code for x, y in ((a, a), (a, b), (p, b))] == [409] * 3
    assert client.put(f"/api/componentes/{a}", json={"parent_id": 999999}).status_code == 422
    g = client.post("/api/componentes", json={"nome": "G", "parent_id": b, "quantidade": 1_000_000, "preco": "1"}).json()["id"]
    r = client.post("/api/componentes", json={"nome": "H", "parent_id": g, "quantidade": 1_000_000, "preco": "1"})
    assert r.status_code == 422 and "limite" in r.json()["detail"]


def test_limites_de_filhos_nos_e_profundidade():
    with make_client(max_filhos_por_no=2, max_nos_por_produto=4) as c:
        p = c.post("/api/produtos", json={"nome": "R"}).json()["id"]
        for i in range(2):
            assert c.post("/api/componentes", json={"nome": f"f{i}", "parent_id": p, "preco": "1"}).status_code == 201
        assert c.post("/api/componentes", json={"nome": "f3", "parent_id": p, "preco": "1"}).status_code == 422
    with make_client(max_profundidade=3) as c:
        no = c.post("/api/produtos", json={"nome": "n1"}).json()["id"]
        for i in (2, 3):
            no = c.post("/api/componentes", json={"nome": f"n{i}", "parent_id": no, "preco": "1"}).json()["id"]
        assert "Profundidade" in c.post("/api/componentes", json={"nome": "n4", "parent_id": no, "preco": "1"}).json()["detail"]


def test_arvore_adulterada_no_banco_responde_422_sem_derrubar():
    with make_client(max_profundidade=10) as c:
        with c.app.state.sessionmaker() as s:
            pai = None
            for i in range(300):
                n = NoORM(nome=f"n{i}", parent_id=pai, quantidade=1, preco=1)
                s.add(n)
                s.flush()
                pai = n.id
            s.commit()
        assert c.get("/api/produtos/1/explosao").status_code == 422
        assert c.get("/api/saude").status_code == 200


def test_simulacao_absurda_e_422(client):
    p = client.post("/api/produtos", json={"nome": "R", "preco": "1"}).json()["id"]
    a = client.post("/api/componentes", json={"nome": "A", "parent_id": p, "quantidade": 1000, "preco": "0.01"}).json()["id"]
    client.post("/api/componentes", json={"nome": "B", "parent_id": a, "quantidade": 1001, "preco": "0.01"})
    assert client.post(f"/api/produtos/{p}/simulacao", json={"quantidade": 1_000_000}).status_code == 422
    assert client.post(f"/api/produtos/{p}/simulacao", json={"quantidade": 1}).status_code == 200


# --------------------------------------------- CORS, rate limit, timeout, Redis
def test_cors_so_aceita_origem_configurada(client):
    ok = client.options("/api/produtos", headers={"Origin": "http://localhost:5173", "Access-Control-Request-Method": "GET"})
    mau = client.options("/api/produtos", headers={"Origin": "http://evil.example", "Access-Control-Request-Method": "GET"})
    assert ok.headers.get("access-control-allow-origin") == "http://localhost:5173"
    assert "access-control-allow-origin" not in mau.headers


def test_rate_limit_por_classe_em_memoria():
    with make_client(write_rate_limit="3/minute") as c:
        assert [c.post("/api/produtos", json={"nome": f"p{i}"}).status_code for i in range(5)] == [201] * 3 + [429] * 2
        assert c.get("/api/produtos").status_code == 200
    with cliente_auth(login_rate_limit="3/minute") as c:
        assert [login(c, "x", secrets.token_urlsafe(18)).status_code for _ in range(5)] == [401] * 3 + [429] * 2


def test_timeout_responde_504(monkeypatch):
    monkeypatch.setattr(FormulaService, "listar_produtos", lambda *a, **k: time.sleep(2.2) or {"total": 0, "itens": []})
    with make_client(request_timeout_s=1) as c:
        assert c.get("/api/produtos").status_code == 504
        time.sleep(1.5)  # deixa a thread terminar antes de fechar


def test_rate_limit_compartilhado_entre_replicas_via_redis():
    server = fakeredis.FakeServer()
    pares = [(fakeredis.FakeAsyncRedis(server=server, decode_responses=True), fakeredis.FakeRedis(server=server, decode_responses=True)) for _ in range(2)]
    with make_client(*pares[0], write_rate_limit="3/minute") as a, make_client(*pares[1], write_rate_limit="3/minute") as b:
        codigos = [a.post("/api/produtos", json={"nome": "1"}).status_code, b.post("/api/produtos", json={"nome": "2"}).status_code,
                   a.post("/api/produtos", json={"nome": "3"}).status_code, b.post("/api/produtos", json={"nome": "4"}).status_code]
        assert codigos == [201, 201, 201, 429]  # as duas réplicas somam no mesmo contador


def test_bloqueio_de_login_compartilhado_via_redis_e_fallback_se_redis_cair():
    server = fakeredis.FakeServer()
    r = fakeredis.FakeRedis(server=server, decode_responses=True)
    t1, t2 = RedisLoginThrottle(r), RedisLoginThrottle(fakeredis.FakeRedis(server=server, decode_responses=True))
    for _ in range(4):
        t1.falha("ip|admin")
    assert t2.segundos_restantes("ip|admin") >= 1  # outra réplica enxerga o bloqueio
    t1.sucesso("ip|admin")
    assert t2.segundos_restantes("ip|admin") == 0

    class Quebrado:
        def __getattr__(self, _):
            raise ConnectionError("redis fora do ar")

    t3 = RedisLoginThrottle(Quebrado())
    for _ in range(4):
        t3.falha("k")
    assert t3.segundos_restantes("k") >= 1  # caiu para a memória, não derrubou nem liberou


def test_redis_fora_do_ar_nao_derruba_a_api():
    class Quebrado:
        def pipeline(self, *a, **k):
            raise ConnectionError("fora do ar")

    with make_client(Quebrado(), None, write_rate_limit="2/minute") as c:
        assert [c.post("/api/produtos", json={"nome": f"p{i}"}).status_code for i in range(3)] == [201, 201, 429]


# ------------------------------------------------ auditoria, concorrência, CLI
def test_trilha_de_auditoria():
    with cliente_auth() as c:
        h = cab(c, "admin1", SENHA_ADMIN)
        p = c.post("/api/seed/caneta", headers=h).json()
        raiz = c.get(f"/api/produtos/{p['id']}/explosao", headers=h).json()["raiz"]
        plastico = achar(raiz, "Plástico", "Corpo")
        c.patch(f"/api/componentes/{plastico['id']}/preco", json={"preco": "1.70"}, headers=h)
        c.delete(f"/api/componentes/{plastico['id']}", headers=h)
        itens = c.get("/api/auditoria", headers=h).json()["itens"]
        preco = next(i for i in itens if i["acao"] == "preco")
        assert (preco["ator"], preco["antes"], preco["depois"]) == ("admin1", "0.70", "1.70") and "7.50 -> 8.50" in preco["detalhe"]
        assert next(i for i in itens if i["acao"] == "excluir")["nome"] == "Plástico"


def test_bloqueio_otimista_detecta_escrita_concorrente(tmp_path):
    from decimal import Decimal

    from sqlalchemy.orm.exc import StaleDataError

    engine = create_engine(f"sqlite:///{tmp_path / 'c.db'}", connect_args={"check_same_thread": False})
    Base.metadata.create_all(engine)
    sm = make_sessionmaker(engine)
    with sm() as s:
        no = NoORM(nome="N", parent_id=None, quantidade=1, preco=Decimal("1"))
        s.add(no)
        s.commit()
        nid = no.id
    a, b = sm(), sm()
    na, nb = a.get(NoORM, nid), b.get(NoORM, nid)
    na.preco = Decimal("2")
    a.commit()
    nb.preco = Decimal("3")
    with pytest.raises(StaleDataError):
        b.commit()
    a.close(), b.close()


def test_cli_redefine_senha_e_derruba_todas_as_sessoes(tmp_path, monkeypatch):
    url = f"sqlite:///{tmp_path / 'cli.db'}"
    migrations.upgrade(url)
    monkeypatch.setenv("DATABASE_URL", url)
    inicial, nova = secrets.token_urlsafe(18), secrets.token_urlsafe(18)
    monkeypatch.setenv("BOM_NOVA_SENHA", inicial)
    assert admin_cli.main(["criar-usuario", "chefe", "admin"]) == 0

    app = __import__("app.main", fromlist=["create_app"]).create_app(Settings(database_url=url, auto_migrate=False, auth_enabled=True, jwt_secret=SEGREDO, cookie_secure=False, **{"rate_limit": "9999/minute", "write_rate_limit": "9999/minute", "login_rate_limit": "9999/minute"}))
    with TestClient(app) as c:
        h = cab(c, "chefe", inicial)
        monkeypatch.setenv("BOM_NOVA_SENHA", nova)
        assert admin_cli.main(["redefinir-senha", "chefe"]) == 0
        assert c.get("/api/produtos", headers=h).status_code == 401  # sessão antiga morreu
        assert c.post("/api/auth/refresh").status_code == 401
        assert login(c, "chefe", inicial).status_code == 401
        assert login(c, "chefe", nova).status_code == 200
    monkeypatch.setenv("BOM_NOVA_SENHA", "curta")
    assert admin_cli.main(["redefinir-senha", "chefe"]) == 1  # política de senha vale na CLI
    assert admin_cli.main(["redefinir-senha", "fantasma"]) == 1


def test_throttle_em_memoria_tem_limite_de_chaves():
    t = LoginThrottle()
    for i in range(LoginThrottle.MAX_CHAVES + 50):
        t.falha(f"k{i}")
    assert len(t._estado) <= LoginThrottle.MAX_CHAVES + 1
