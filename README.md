# TreeBOM

Sistema de **Lista de Materiais (BOM)** modelado com **árvore genérica** (cada nó tem N filhos). Cadastre a fórmula de um produto,
veja a **explosão** da árvore, **simule a produção** de N unidades e **atualize o preço** de um componente para ver a **implosão**
do novo custo subindo até a raiz.

Projeto acadêmico de Estrutura de Dados II. A árvore e os percursos são implementados à mão (classe de nó e algoritmos próprios),
sem estruturas prontas que escondam a árvore.

## Funcionalidades

- **Cadastro** de produtos e componentes em vários níveis, com edição, mover de lugar e exclusão em cascata com confirmação.
- **Explosão:** árvore interativa (zoom, arrastar, expandir/recolher, busca com destaque, navegação por teclado) e painel com quantidade,
  preço, custo da subárvore e caminho até a raiz.
- **Simulação de produção:** para N unidades, a quantidade necessária de cada componente (multiplicada ao longo do caminho da raiz até o nó),
  o custo de aquisição e um gráfico de composição.
- **Atualização de preço + implosão:** o novo custo é recalculado subindo do nó alterado até a raiz, animado nó a nó, com "antes → depois" e a diferença em R$.
- **Dashboard** com os produtos, resumo e gráfico de custo; **tema claro/escuro**; layout responsivo (no celular a navegação fica numa barra inferior).
- **Login opcional** (JWT, perfis `admin` e `viewer`), trilha de auditoria das alterações de preço e exclusões. Veja o [SECURITY.md](SECURITY.md).

## Tecnologias

| Camada | Tecnologias |
|---|---|
| Backend | Python 3.13, FastAPI, Pydantic, SQLAlchemy 2, Alembic, SQLite (padrão) ou PostgreSQL |
| Frontend | React 18, TypeScript, Vite, Tailwind CSS 4, TanStack Query, React Router, Recharts, react-hook-form + zod |
| Animações | Framer Motion (`motion`) e GSAP (`@gsap/react`, ScrollTrigger) |
| Testes | pytest (backend) e Vitest + React Testing Library (frontend) |

## Como rodar

### Pré-requisitos
- **Python 3.13** (no Windows, com o launcher `py`) e **Node.js 20 ou mais novo**.
- Os scripts de atalho usam `py -3` e `taskkill`, ou seja, **funcionam no Windows**. Em macOS/Linux, veja "Rodar sem o atalho".

### Instalar (uma vez)
```powershell
npm run setup
```
Instala as dependências do Python (`backend/requirements.txt`, com verificação de hash) e as do front (`frontend`).

### Desenvolvimento (API + front juntos)
```powershell
node dev.mjs
```
Abra **http://localhost:5173**. `Ctrl + C` encerra os dois. O banco SQLite (`backend/bom.db`) e as tabelas são criados sozinhos no primeiro uso.
Na tela inicial, use **"Carregar exemplo"** (Caneta ou Computador) para ter dados.

- API: http://localhost:8000 · documentação interativa (Swagger): http://localhost:8000/api/docs

### Rodar sem o atalho (macOS/Linux ou separado)
```bash
cd backend  && python3 -m uvicorn app.main:app --reload          # terminal 1 (porta 8000)
cd frontend && npm run dev                                       # terminal 2 (porta 5173)
```

### Produção (o FastAPI serve o front compilado)
```powershell
cd frontend ; npm ci ; npm run build          # gera frontend/dist (HTML/CSS/JS estáticos)
cd ../backend
$env:APP_ENV="production"; $env:AUTH_ENABLED="true"; $env:JWT_SECRET="<32+ caracteres aleatórios>"
$env:ADMIN_USERNAME="admin"; $env:ADMIN_PASSWORD="<12+ caracteres>"; $env:CORS_ORIGINS="https://seu-dominio"
$env:STATIC_DIR="../frontend/dist"
py -3 -m uvicorn app.main:app --host 127.0.0.1 --port 8000 --proxy-headers --no-server-header
```
Coloque HTTPS na frente (proxy reverso ou a hospedagem). Em produção o login é obrigatório e o Swagger e o `seed` ficam desligados.
Todas as variáveis estão no [SECURITY.md](SECURITY.md); `.env.example` mostra as principais.

## Testes

```powershell
# backend
cd backend
py -3 -m pip install -r requirements-dev.in
py -3 -m pytest -q
py -3 -X utf8 -m app.demo        # Testes 1 e 2 do enunciado, com saída formatada no console

# frontend
cd frontend ; npm test
```
O `app.demo` imprime a árvore, o total (Caneta = **R$ 7,50**, Computador = **R$ 8.080,00**) e a simulação (10 canetas e 5 computadores).
Um workflow de CI (`.github/workflows/ci.yml`) roda tudo isso, mais `bandit`, `pip-audit` e `npm audit`, a cada push.

## Regra de cálculo

```
custo_unitario(nó) = preço_próprio(nó) + soma( custo_total(filho) )   para cada filho
custo_total(nó)    = quantidade(nó) × custo_unitario(nó)
```
Todos os nós somam, inclusive os intermediários ("Tinta", "Placa-Mãe"), que têm preço próprio (montagem/mão de obra).

**Suposição declarada:** o enunciado fala em somar o "custo unitário" de cada filho, mas o total esperado do Computador (R$ 8.080,00) só
fecha se cada filho entrar **com a sua própria quantidade** (a Memória RAM é 2 × R$ 200,00). Por isso o filho contribui com o seu `custo_total`.

**Simulação:** para `N` unidades, `quantidade_total(nó) = N × produto das quantidades da raiz até o nó` e `custo(nó) = quantidade_total × preço próprio`.
A soma dos custos é igual a `N × custo_total(raiz)`.

**Implosão:** ao mudar o preço de um nó, recalcula-se o custo de cada ancestral até a raiz. A API devolve a lista de ids afetados
(do nó até a raiz), com o custo antes e depois de cada um, e é isso que a animação usa.

**Exclusão:** é **em cascata**: remover um nó remove toda a sua subárvore. A tela mostra quantos itens serão removidos e pede confirmação.

**Dinheiro:** `Decimal` no backend, inteiro em centavos no banco e **string** na API (`"8080.00"`), nunca `float`.

## Decisões de arquitetura

- **Camadas:** `domain` (nó da árvore e algoritmos, sem dependência de framework) → `services` → `repositories` → `api`.
- **Nomes repetidos** ("Plástico" aparece sob "Corpo" e sob "Tampa"): cada nó tem **id único** e o pai é resolvido pelo contexto, nunca só pelo nome.
- **Construção em O(n)** com dicionário; detecção de ciclo, pai inexistente e raiz múltipla com erros claros.
- **Percursos iterativos:** os cálculos não dependem do limite de recursão do Python (testado com 5.000 níveis).
- **Banco:** uma tabela com auto-relacionamento (`id`, `nome`, `parent_id`, `quantidade`, `preco`) e índice em `parent_id`, migrações com Alembic.
- **Limites:** profundidade 50, 2.000 nós por produto, 100 filhos por nó, tetos de quantidade e de valor (todos configuráveis).

### API (prefixo `/api`)

| Método | Rota | O que faz |
|---|---|---|
| GET/POST | `/produtos` | lista (paginada) e cria produtos |
| GET/PUT/DELETE | `/produtos/{id}` | lê, edita e exclui (cascata) |
| GET | `/produtos/{id}/explosao` | árvore completa com custos |
| POST | `/produtos/{id}/simulacao` | `{ "quantidade": N }` → componentes e custo total |
| GET | `/produtos/{id}/componentes` | lista plana (para selecionar o pai) |
| POST/GET/PUT/DELETE | `/componentes`, `/componentes/{id}` | CRUD de componentes (inclui mover de pai) |
| PATCH | `/componentes/{id}/preco` | atualiza o preço e devolve a implosão |
| POST | `/seed/{caneta\|computador}` | carrega os exemplos |
| GET | `/auditoria` | trilha de auditoria (admin) |

## Animações: Framer Motion × GSAP

Regra: **nunca animar a mesma propriedade do mesmo elemento com as duas bibliotecas** (nos cards da árvore, o elemento externo é do
Framer e o interno é do GSAP). Tudo respeita `prefers-reduced-motion`.

- **Framer Motion:** transições de página, expandir/recolher nós, posição dos cards, entrada e saída de cards e linhas de tabela, modais,
  toasts, hover/tap, `layoutId` do menu e do seletor de produto.
- **GSAP:** intro do cabeçalho com stagger, desenho das linhas conectoras (`stroke-dashoffset`), contagem animada dos valores, efeito de
  **explosão** (nós saem do centro e se organizam), timeline da **implosão** (pulso subindo do nó até a raiz) e ScrollTrigger.

## Roteiro de demonstração

1. **Dashboard:** mostre os cartões e o resumo; clique em **Carregar exemplo: Caneta** e depois **Computador**.
2. **Explosão:** abra a Caneta, destaque os dois "Plástico" (nós distintos), expanda/recolha, busque "plást", selecione um nó e mostre o caminho até a raiz; clique em "Explodir novamente".
3. **Simulação:** 10 canetas → **R$ 75,00**; 5 computadores → **R$ 40.400,00** (veja a Memória RAM: 10 unidades).
4. **Preço e implosão:** mude o "Plástico" do Corpo de 0,70 para 1,70 e assista ao pulso subir (Plástico → Corpo → Caneta), com **+R$ 1,00** em cada degrau e total R$ 7,50 → R$ 8,50.
5. **Cadastro:** crie um produto e um componente, mova um nó, tente criar um ciclo (erro claro) e exclua um nó em cascata.
6. **Perfis (opcional):** com `AUTH_ENABLED=true`, entre como `viewer` e veja que só `admin` altera preços; a trilha fica em `GET /api/auditoria`.

## Estrutura

```
backend/
  app/domain/        nó da árvore e algoritmos (sem framework)
  app/services/      casos de uso     app/repositories/  acesso a dados     app/api/  rotas e schemas
  app/security/      JWT, papéis, middlewares     alembic/  migrações     tests/  pytest
frontend/
  src/api/  src/auth/  src/components/{tree,ui,charts,layout}  src/features/  src/lib/animations/  src/pages/
dev.mjs              sobe API + front juntos (Windows)
SECURITY.md          política de segurança, variáveis de ambiente e checklist de deploy
```

## Limitações e melhorias futuras

- Os scripts de atalho são para Windows; em outros sistemas use os comandos "sem o atalho".
- Rate limit por processo (use `REDIS_URL` para compartilhar entre réplicas); sem MFA.
- Melhorias possíveis: importar/exportar fórmulas (CSV), histórico de versões de uma fórmula, custos por fornecedor, virtualização de árvores muito grandes.

## Licença

[MIT](LICENSE)
