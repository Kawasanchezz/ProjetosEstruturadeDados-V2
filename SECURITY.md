# Segurança do TreeBOM

Projeto acadêmico. Esta página resume a política de segurança, como configurar e como verificar.

## Como reportar uma vulnerabilidade
Não abra issue pública. Use **Security > Report a vulnerability** neste repositório (aviso privado do GitHub) com: descrição,
passos para reproduzir, impacto e versão. Versão suportada: apenas a `main` (projeto acadêmico, sem garantia de correções para versões antigas).

## Modos de execução

| | Desenvolvimento (padrão) | Produção (`APP_ENV=production`) |
|---|---|---|
| Autenticação | desligada (`AUTH_ENABLED=false`): todos são `anon` com papel admin | **obrigatória** (a aplicação não inicia sem ela) |
| `POST /api/seed/*` | ligado | desligado (`ENABLE_SEED=true` reativa; exige admin) |
| Cookie de refresh | `Secure` desligado (HTTP local) | `Secure` obrigatório |
| HSTS | desligado | ligado |

> Com `AUTH_ENABLED=true` o front-end mostra a tela de login (usuário e senha). Perfil `viewer` vê só Visão geral, Explosão e Simulação;
> `admin` vê tudo. Esconder telas é só conveniência da interface: **a autorização real é da API** (403/401).

## Como rodar

- **Desenvolvimento / apresentação (sem Docker):** `npm run setup` (uma vez) e `node dev.mjs`; abra http://localhost:5173.
- **Produção simples, sem Docker:** na pasta `frontend`, `npm ci && npm run build`; na pasta `backend-java`, `mvn -DskipTests package` (ou deixe o `node dev.mjs` compilar),
  defina `APP_ENV=production`, `AUTH_ENABLED=true`, `JWT_SECRET`, `ADMIN_USERNAME`/`ADMIN_PASSWORD`, `CORS_ORIGINS`,
  `STATIC_DIR=../frontend/dist` (e `FORWARD_HEADERS=native` atrás de proxy confiável) e rode `java -jar target/treebom.jar`
  (porta `PORT`, padrão 8000). Coloque HTTPS na frente (proxy reverso ou a hospedagem). Docker não é necessário para este projeto.
- **Administração sem e-mail (criar usuário / redefinir senha):** com as mesmas variáveis de ambiente da API (como `DATABASE_URL`),
  `java -Dloader.main=com.treebom.AdminCli -cp target/treebom.jar org.springframework.boot.loader.launch.PropertiesLauncher criar-usuario NOME admin|viewer`
  ou `... redefinir-senha NOME`. A senha é pedida no terminal (ou lida de `BOM_NOVA_SENHA`), nunca como argumento.

## Variáveis de ambiente

| Variável | Obrigatória | Descrição |
|---|---|---|
| `APP_ENV` | não | `production` liga os padrões restritivos |
| `AUTH_ENABLED` | produção | `true` ativa JWT e papéis |
| `JWT_SECRET` | com auth | 32+ caracteres aleatórios (`openssl rand -base64 48`) |
| `JWT_SECRET_ANTERIOR` | só na rotação | segredo antigo aceito **apenas para verificar** tokens; remova após o TTL do access token (10 min) |
| `REQUEST_TIMEOUT_S` | não | 15; a API responde 504 e, no PostgreSQL, o servidor aborta a consulta (`statement_timeout`) |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | 1º start | cria o admin inicial se não houver usuários (senha 12+ caracteres) |
| `DATABASE_URL` / `DATABASE_USER` / `DATABASE_PASSWORD` | não | URL JDBC; padrão SQLite local (`jdbc:sqlite:./bom.db?...`); produção: `jdbc:postgresql://host:5432/banco` |
| `CORS_ORIGINS` | não | lista separada por vírgula; `*` é recusado na partida |
| `RATE_LIMIT` / `WRITE_RATE_LIMIT` / `LOGIN_RATE_LIMIT` | não | `120/minute`, `30/minute`, `10/minute` |
| `MAX_BODY_BYTES` | não | 65536 |
| `MAX_PROFUNDIDADE`, `MAX_NOS_POR_PRODUTO`, `MAX_FILHOS_POR_NO` | não | 50, 2000, 100 |
| `ENABLE_SEED`, `COOKIE_SECURE`, `HSTS` | não | sobrescrevem o padrão do ambiente |
| `FORWARD_HEADERS` | atrás de proxy | `native` faz a API usar `X-Forwarded-For` (IP real no limite de requisições); só com proxy confiável |
| `PORT` | não | porta HTTP (padrão 8000) |

Segredos ficam **só** em variáveis de ambiente / `.env` (no `.gitignore`). `.env.example` não tem valores.
Se algum segredo for commitado por engano, trate-o como vazado e rotacione.

## O que o projeto faz para se proteger

- Login com senhas em Argon2id, tokens de acesso curtos, renovação com rotação e perfis `admin` e `viewer` verificados no servidor.
- Validação de toda entrada, limites de tamanho da árvore, limite de requisições e trilha de auditoria das alterações.
- Cabeçalhos de segurança, mensagens de erro sem detalhes internos e dependências do front verificadas (`npm audit`) e CodeQL no CI.
- Segredos só em variáveis de ambiente; nenhum valor sensível é versionado.

## Verificação (comandos)

```powershell
# backend (na pasta backend-java; precisa de JDK 21+)
node dev.mjs --test                          # testes de domínio, API e segurança (JUnit 5 + MockMvc)
# front (na pasta frontend)
npm ci ; npm test ; npm audit                # Vitest + 0 vulnerabilidades (produção e build)
```

## Checklist de deploy seguro

- [ ] `APP_ENV=production`, `AUTH_ENABLED=true`
- [ ] `JWT_SECRET` aleatório de 32+ caracteres, fora do Git
- [ ] `ADMIN_PASSWORD` forte (12+); troque-a após o primeiro login e remova a variável
- [ ] `CORS_ORIGINS` com a origem HTTPS real (sem `*`)
- [ ] HTTPS no proxy, HTTP→HTTPS ativo e HSTS presente nas respostas
- [ ] `POST /api/seed/*` responde 404
- [ ] Banco sem acesso público; credenciais só por variável de ambiente; backup
- [ ] Processo rodando com usuário sem privilégios de administrador
- [ ] `FORWARD_HEADERS=native` só se houver proxy reverso confiável na frente
- [ ] `npm audit` sem achados críticos/altos; dependências Java atualizadas (Dependabot, ecossistema Maven)
- [ ] Rate limit e bloqueio de login são em memória: com mais de uma réplica, cada uma conta separado
- [ ] Trilha de auditoria conferida (`GET /api/auditoria`) e logs sem segredos

## Limitações conhecidas (honestidade técnica)

Este é um projeto acadêmico: não há garantia de que esteja livre de falhas.

- **Sem login em desenvolvimento:** com `AUTH_ENABLED=false` (padrão e do `dev.mjs`) todos entram como administrador. Em produção a API **recusa iniciar** sem login (`APP_ENV=production` exige `AUTH_ENABLED=true`).
- **Limites em memória:** o limite de requisições e o bloqueio de login valem por processo; com várias réplicas cada uma conta separado. Não há Redis nesta versão.
- **Sem MFA** e sem recuperação de senha por e-mail (só pela CLI do administrador do servidor).
- **Timeout (504):** o cliente recebe o erro e a conexão é liberada, mas trabalho que ignora interrupção termina sozinho; a defesa principal são os tetos de tamanho da árvore. No PostgreSQL o `statement_timeout` interrompe a consulta no servidor.
- **SQLite** serve a um usuário/poucos usuários; para uso real, prefira PostgreSQL.
- **PostgreSQL:** o esquema e o driver existem, mas os testes automáticos rodam só em SQLite.
- **Dependências Java:** não há verificação automática de CVEs no CI além do Dependabot (Maven e npm) e do `npm audit` no front. A análise de código roda no CodeQL (`.github/workflows/codeql.yml`); para CVEs de dependências Java, habilite os alertas do Dependabot no GitHub.
- **Ações do GitHub** no CI estão fixadas por tag de versão, não por SHA de commit.
