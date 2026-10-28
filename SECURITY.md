# Segurança do TreeBOM

Projeto acadêmico. Esta página resume a política de segurança, como configurar e como verificar.

## Como reportar uma vulnerabilidade
Não abra issue pública. Use **Security > Report a vulnerability** neste repositório (aviso privado do GitHub) com: descrição,
passos para reproduzir, impacto e versão. Versão suportada: apenas a `main` (projeto acadêmico, sem garantia de correções para versões antigas).

## Modos de execução

| | Desenvolvimento (padrão) | Produção (`APP_ENV=production`) |
|---|---|---|
| Autenticação | desligada (`AUTH_ENABLED=false`): todos são `anon` com papel admin | **obrigatória** (a aplicação não inicia sem ela) |
| `/api/docs`, `/api/openapi.json` | ligados | desligados (`ENABLE_DOCS=true` reativa) |
| `POST /api/seed/*` | ligado | desligado (`ENABLE_SEED=true` reativa; exige admin) |
| Cookie de refresh | `Secure` desligado (HTTP local) | `Secure` obrigatório |
| HSTS | desligado | ligado |

> Com `AUTH_ENABLED=true` o front-end mostra a tela de login (usuário e senha). Perfil `viewer` vê só Visão geral, Explosão e Simulação;
> `admin` vê tudo. Esconder telas é só conveniência da interface: **a autorização real é da API** (403/401).

## Como rodar

- **Desenvolvimento / apresentação (sem Docker):** `npm run setup` (uma vez) e `node dev.mjs`; abra http://localhost:5173.
- **Produção simples, sem Docker:** na pasta `frontend`, `npm ci && npm run build`; na pasta `backend`, defina `APP_ENV=production`,
  `AUTH_ENABLED=true`, `JWT_SECRET`, `ADMIN_USERNAME`/`ADMIN_PASSWORD`, `CORS_ORIGINS` e `STATIC_DIR=../frontend/dist`, e rode
  `py -3 -m uvicorn app.main:app --host 127.0.0.1 --port 8000 --proxy-headers --no-server-header`. Coloque HTTPS na frente
  (proxy reverso ou a hospedagem). Docker não é necessário para este projeto.

## Variáveis de ambiente

| Variável | Obrigatória | Descrição |
|---|---|---|
| `APP_ENV` | não | `production` liga os padrões restritivos |
| `AUTH_ENABLED` | produção | `true` ativa JWT e papéis |
| `JWT_SECRET` | com auth | 32+ caracteres aleatórios (`python -c "import secrets; print(secrets.token_urlsafe(48))"`) |
| `JWT_SECRET_ANTERIOR` | só na rotação | segredo antigo aceito **apenas para verificar** tokens; remova após o TTL do access token (10 min) |
| `REQUEST_TIMEOUT_S` | não | 15; a API responde 504 e, no PostgreSQL, o servidor aborta a consulta (`statement_timeout`) |
| `REDIS_URL` | recomendado em produção/réplicas | rate limit e bloqueio de login compartilhados entre réplicas; sem ele usa memória local |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | 1º start | cria o admin inicial se não houver usuários (senha 12+ caracteres) |
| `DATABASE_URL` | não | padrão SQLite local; produção: `postgresql+psycopg://...` |
| `CORS_ORIGINS` | não | lista separada por vírgula; `*` é recusado na partida |
| `RATE_LIMIT` / `WRITE_RATE_LIMIT` / `LOGIN_RATE_LIMIT` | não | `120/minute`, `30/minute`, `10/minute` |
| `MAX_BODY_BYTES` | não | 65536 |
| `MAX_PROFUNDIDADE`, `MAX_NOS_POR_PRODUTO`, `MAX_FILHOS_POR_NO` | não | 50, 2000, 100 |
| `ENABLE_DOCS`, `ENABLE_SEED`, `COOKIE_SECURE`, `HSTS` | não | sobrescrevem o padrão do ambiente |
| `FORWARDED_ALLOW_IPS` | atrás de proxy | IPs do proxy confiáveis (uvicorn `--proxy-headers`) |

Segredos ficam **só** em variáveis de ambiente / `.env` (no `.gitignore`). `.env.example` não tem valores.
Se algum segredo for commitado por engano, trate-o como vazado e rotacione.

## O que o projeto faz para se proteger

- Login com senhas em Argon2id, tokens de acesso curtos, renovação com rotação e perfis `admin` e `viewer` verificados no servidor.
- Validação de toda entrada, limites de tamanho da árvore, limite de requisições e trilha de auditoria das alterações.
- Cabeçalhos de segurança, mensagens de erro sem detalhes internos e dependências verificadas (`bandit`, `pip-audit`, `npm audit`) no CI.
- Segredos só em variáveis de ambiente; nenhum valor sensível é versionado.

## Verificação (comandos)

```powershell
# backend (na pasta backend)
py -3 -m pip install -r requirements-dev.in   # pytest, fakeredis, bandit, pip-audit (não fazem parte da aplicação)
py -3 -m pytest -q                           # testes de domínio, API e segurança
py -3 -m bandit -r app                       # análise estática
py -3 -m pip_audit -r requirements.txt --require-hashes   # CVEs das dependências
# front (na pasta frontend)
npm ci ; npm test ; npm audit                # Vitest + 0 vulnerabilidades (produção e build)
```

## Checklist de deploy seguro

- [ ] `APP_ENV=production`, `AUTH_ENABLED=true`
- [ ] `JWT_SECRET` aleatório de 32+ caracteres, fora do Git
- [ ] `ADMIN_PASSWORD` forte (12+); troque-a após o primeiro login e remova a variável
- [ ] `CORS_ORIGINS` com a origem HTTPS real (sem `*`)
- [ ] HTTPS no proxy, HTTP→HTTPS ativo e HSTS presente nas respostas
- [ ] `/api/docs` e `/api/openapi.json` respondem 404; `POST /api/seed/*` responde 404
- [ ] Banco sem acesso público; credenciais só por variável de ambiente; backup
- [ ] Processo rodando com usuário sem privilégios de administrador
- [ ] `FORWARDED_ALLOW_IPS` restrito ao proxy
- [ ] `bandit`, `pip-audit`, `npm audit` sem achados críticos/altos
- [ ] Trilha de auditoria conferida (`GET /api/auditoria`) e logs sem segredos
