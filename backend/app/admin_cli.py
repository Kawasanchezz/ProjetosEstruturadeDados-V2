"""Administração fora da API (sem e-mail): criar usuário e redefinir senha.

Uso (na pasta backend, com as mesmas variáveis de ambiente da API, como DATABASE_URL):
    py -3 -m app.admin_cli criar-usuario NOME admin|viewer
    py -3 -m app.admin_cli redefinir-senha NOME
A senha é pedida no terminal (ou lida de BOM_NOVA_SENHA); nunca é aceita como argumento, para não ir ao histórico do shell.
"""
import getpass
import os
import sys

from app.config import Settings
from app.database import make_engine, make_sessionmaker
from app.security.auth import AuthService, ErroAutenticacao, LoginThrottle


def _senha() -> str:
    senha = os.environ.get("BOM_NOVA_SENHA")
    if senha:
        return senha
    senha = getpass.getpass("Nova senha (12+ caracteres): ")
    if getpass.getpass("Repita a senha: ") != senha:
        sys.exit("As senhas não conferem.")
    return senha


def main(argv: list[str]) -> int:
    if len(argv) < 2 or argv[0] not in {"criar-usuario", "redefinir-senha"}:
        print(__doc__)
        return 2
    settings = Settings.from_env()
    with make_sessionmaker(make_engine(settings.database_url))() as sessao:
        auth = AuthService(sessao, settings, LoginThrottle())
        try:
            if argv[0] == "criar-usuario":
                if len(argv) != 3:
                    print(__doc__)
                    return 2
                user = auth.criar_usuario(argv[1], _senha(), argv[2])
                print(f"Usuário criado: {user.username} ({user.role}).")
            else:
                user = auth.redefinir_senha(argv[1], _senha())
                print(f"Senha redefinida para {user.username}; todas as sessões foram encerradas.")
        except ErroAutenticacao:
            print("Usuário não encontrado.")
            return 1
        except ValueError as exc:
            print(f"Erro: {exc}")
            return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
