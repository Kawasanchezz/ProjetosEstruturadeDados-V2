<h1 align="center">TreeBOM</h1>

<p align="center">
  Lista de Materiais (BOM) com <b>árvore genérica</b>: monte a fórmula de um produto, veja a explosão,<br>
  simule a produção e acompanhe o novo custo subir até a raiz quando um preço muda.
</p>

<p align="center">
  <a href="https://github.com/Kawasanchezz/ProjetosEstruturadeDados-V2/actions/workflows/ci.yml"><img alt="CI" src="https://github.com/Kawasanchezz/ProjetosEstruturadeDados-V2/actions/workflows/ci.yml/badge.svg"></a>
  <img alt="Python" src="https://img.shields.io/badge/Python-3.13-3776AB?logo=python&logoColor=white">
  <img alt="React" src="https://img.shields.io/badge/React-18-61DAFB?logo=react&logoColor=black">
  <img alt="Licença" src="https://img.shields.io/badge/licença-MIT-green">
</p>

Projeto acadêmico de **Estrutura de Dados II**. A árvore e os percursos são implementados à mão, sem bibliotecas que escondam a estrutura.

## O que faz

| | |
|---|---|
| **Cadastro** | Produtos e componentes em vários níveis: editar, mover e excluir em cascata (com confirmação). |
| **Explosão** | Árvore interativa com zoom, busca e painel com quantidade, preço e custo de cada nó. |
| **Simulação** | Para N unidades, quanto de cada componente é necessário e quanto custa. |
| **Implosão** | Ao mudar um preço, o novo custo é recalculado do nó até a raiz, passo a passo. |
| **Dashboard** | Resumo dos produtos, gráfico de custos, tema claro/escuro e layout para celular. |
| **Login opcional** | Perfis `admin` e `viewer` e trilha de auditoria das alterações. |

## Como rodar

Precisa de **Python 3.13** e **Node.js 20+** (os atalhos abaixo são para Windows).

```powershell
npm run setup     # instala as dependências (uma vez)
node dev.mjs      # sobe API e site juntos; Ctrl + C encerra os dois
```

Abra **http://localhost:5173** e clique em **Carregar exemplo** (Caneta ou Computador).
A API fica em http://localhost:8000 e a documentação interativa em http://localhost:8000/api/docs.

<details>
<summary>Rodar sem o atalho (macOS/Linux)</summary>

```bash
cd backend  && python3 -m uvicorn app.main:app --reload   # terminal 1
cd frontend && npm run dev                                # terminal 2
```
</details>

<details>
<summary>Colocar em produção</summary>

```powershell
cd frontend ; npm ci ; npm run build
cd ../backend
$env:APP_ENV="production"; $env:AUTH_ENABLED="true"; $env:JWT_SECRET="<32+ caracteres aleatórios>"
$env:ADMIN_USERNAME="admin"; $env:ADMIN_PASSWORD="<12+ caracteres>"; $env:CORS_ORIGINS="https://seu-dominio"
$env:STATIC_DIR="../frontend/dist"
py -3 -m uvicorn app.main:app --host 127.0.0.1 --port 8000 --proxy-headers --no-server-header
```
Use HTTPS na frente. Todas as variáveis estão no [SECURITY.md](SECURITY.md).
</details>

## Testes

```powershell
cd backend  ; py -3 -m pip install -r requirements-dev.in ; py -3 -m pytest -q
cd frontend ; npm test
py -3 -X utf8 -m app.demo      # Caneta e Computador do enunciado, no console
```

O CI do GitHub roda tudo isso, mais `bandit`, `pip-audit`, `npm audit` e CodeQL.

## Regra de cálculo

```
custo_unitario(nó) = preço_próprio + soma( custo_total(filho) )
custo_total(nó)    = quantidade × custo_unitario
```

| Exemplo | Resultado |
|---|---|
| Caneta | **R$ 7,50** |
| Computador | **R$ 8.080,00** |
| 10 canetas | R$ 75,00 |
| 5 computadores | R$ 40.400,00 |

Todos os nós somam, inclusive os intermediários, que têm preço próprio (montagem). Cada filho entra com a **própria quantidade**: é assim que o Computador fecha em R$ 8.080,00.
Na simulação, `quantidade_total = N × produto das quantidades da raiz até o nó`.

## Como foi feito

- **Camadas:** `domain` (árvore e algoritmos, sem framework) → `services` → `repositories` → `api`.
- **Ids únicos:** "Plástico" pode aparecer sob "Corpo" e sob "Tampa", e cada um é um nó diferente.
- **Algoritmos iterativos:** sem recursão, então não estouram o limite do Python (testado com 5.000 níveis).
- **Validação:** ciclo, pai inexistente e raiz duplicada geram erro claro.
- **Dinheiro:** `Decimal` no backend, centavos no banco e string na API (`"8080.00"`), nunca `float`.
- **Banco:** SQLite por padrão (PostgreSQL opcional), com migrações Alembic.

| Camada | Tecnologias |
|---|---|
| Backend | FastAPI, Pydantic, SQLAlchemy 2, Alembic |
| Frontend | React, TypeScript, Vite, Tailwind CSS 4, TanStack Query, Recharts |
| Animações | Framer Motion e GSAP |
| Testes | pytest, Vitest, React Testing Library |

<details>
<summary>Rotas da API (prefixo <code>/api</code>)</summary>

| Método | Rota | O que faz |
|---|---|---|
| GET / POST | `/produtos` | lista e cria produtos |
| GET / PUT / DELETE | `/produtos/{id}` | lê, edita e exclui (cascata) |
| GET | `/produtos/{id}/explosao` | árvore completa com custos |
| POST | `/produtos/{id}/simulacao` | `{ "quantidade": N }` |
| GET | `/produtos/{id}/componentes` | lista plana de componentes |
| POST / GET / PUT / DELETE | `/componentes`, `/componentes/{id}` | CRUD de componentes |
| PATCH | `/componentes/{id}/preco` | atualiza o preço e devolve a implosão |
| GET | `/auditoria` | trilha de auditoria (admin) |
</details>

## Estrutura

```
backend/    app/domain · services · repositories · api · security   alembic/   tests/
frontend/   src/api · auth · components · features · lib/animations · pages
dev.mjs     sobe API e site juntos
SECURITY.md política de segurança, variáveis e checklist de deploy
```

## Limitações

- Os atalhos (`dev.mjs`) são para Windows.
- O limite de requisições é por processo (use `REDIS_URL` para várias réplicas) e não há MFA.
- Ideias futuras: importar/exportar fórmulas (CSV), histórico de versões e custos por fornecedor.

## Licença

[MIT](LICENSE)
