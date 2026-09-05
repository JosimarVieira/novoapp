# server

[![CI](https://github.com/JosimarVieira/novoapp/actions/workflows/ci.yml/badge.svg)](https://github.com/JosimarVieira/novoapp/actions/workflows/ci.yml)

Backend Quarkus do produto. Etapa 1 do [ROADMAP](../ROADMAP.md): bot Telegram +
despesa.

Antes de mexer aqui, leia [`CLAUDE.md`](../CLAUDE.md) e
[`docs/02-arquitetura/sdd-visao-geral.md`](../docs/02-arquitetura/sdd-visao-geral.md).
O que esta etapa entregou e o que ficou de fora está em
[`docs/05-entregas/etapa-1-bot-telegram-e-despesa.md`](../docs/05-entregas/etapa-1-bot-telegram-e-despesa.md).

## Pré-requisitos

| | |
|---|---|
| Java | 21 (LTS) |
| Maven | 3.9+ |
| Docker | só para os testes (Postgres real via Testcontainers) e para subir o banco local |
| Postgres | 16 — o índice único de `category` usa `NULLS NOT DISTINCT`, que é 15+ |

## Rodar os testes

```bash
mvn test
```

Sobe um Postgres em container, aplica o Flyway e roda tudo: arquitetura
(ArchUnit), isolamento de tenant, idempotência, orçamento de resposta do webhook
e os cenários Gherkin de `docs/03-specs/features`.

Os `.feature` **não** são copiados para dentro do módulo: o Cucumber lê os
arquivos de `docs/` direto. O Gherkin é a fonte de verdade; cópia viraria duas
verdades.

Um teste fica desabilitado de propósito — `Etapa2AcceptanceTest`, com os
cenários `@etapa2`. Tirar o `@Disabled` é o primeiro passo da Etapa 2.

O mesmo comando roda no CI (`.github/workflows/ci.yml`) a cada push na `main` e
em todo pull request, seguido de um `docker build` da imagem de deploy.

## Rodar localmente

### 1. Banco

```bash
docker run -d --name novoapp-db \
  -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=novoapp \
  -p 5432:5432 postgres:16-alpine
```

### 2. Variáveis

```bash
export NOVOAPP_DB_URL=jdbc:postgresql://localhost:5432/novoapp
export NOVOAPP_DB_ADMIN_USER=postgres          # dono do schema, só o Flyway usa
export NOVOAPP_DB_ADMIN_PASSWORD=postgres
export NOVOAPP_DB_RUNTIME_PASSWORD=<senha>     # a aplicação conecta com esta
export TELEGRAM_BOT_TOKEN=<token do BotFather>
export TELEGRAM_WEBHOOK_SECRET=<qualquer string longa>
export MISTRAL_API_KEY=<chave do tier gratuito>   # ADR-0009
```

### 3. Subir

```bash
mvn quarkus:dev
```

O Flyway cria o schema **e os três papéis de banco** na primeira subida.

### 4. Criar o bot

No Telegram, fale com **@BotFather** → `/newbot` → nome de exibição → username
terminando em `bot`. Ele devolve o token (`123456789:AAF...`), que é o
`TELEGRAM_BOT_TOKEN`.

Gere o segredo do webhook — só aceita `A-Z`, `a-z`, `0-9`, `_` e `-`, de 1 a
256 caracteres:

```bash
export TELEGRAM_WEBHOOK_SECRET=$(head -c 32 /dev/urandom | base64 | tr -d '=+/')
```

### 5. Apontar o Telegram para cá

O Telegram só entrega webhook em **HTTPS**, e só nas portas **443, 80, 88 ou
8443** — quem faz esse mapeamento é o proxy da plataforma (ou o túnel, em
desenvolvimento), não a aplicação. Em desenvolvimento, exponha a porta 8080 com
ngrok/cloudflared e registre:

```bash
curl "https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/setWebhook" \
  -d "url=https://<seu-host-publico>/webhook/telegram" \
  -d "secret_token=$TELEGRAM_WEBHOOK_SECRET" \
  -d 'allowed_updates=["message"]'

# conferir
curl "https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/getWebhookInfo"
```

`allowed_updates=["message"]` porque esta etapa só trata mensagem: não faz
sentido receber edição, reação e callback de botão para descartar.

O `secret_token` volta no header `X-Telegram-Bot-Api-Secret-Token` a cada
entrega e é conferido. Sem ele, qualquer um que descubra a URL injeta mensagem
em nome de qualquer pessoa. Se `TELEGRAM_WEBHOOK_SECRET` estiver vazio, a
verificação é desligada — aceitável só em máquina local.

## Deploy no Railway

O `railway.json` e o `Dockerfile` estão aqui, ao lado do `pom.xml`. Na Railway,
o serviço precisa de **Root Directory = `server`**, porque a raiz do repositório
não tem `pom.xml`.

O build usa o **`Dockerfile` deste diretório**, não o builder automático da
plataforma. O motivo é concreto, não preferência: o Nixpacks instalava `jdk21`
no ambiente, mas o pacote `maven` do nixpkgs traz o próprio JDK (19) e compila
com ele — o `javac` que rodava era o 19, e o 19 recusa `release 21`. A variável
`NIXPACKS_JDK_VERSION` não alcança isso, porque o wrapper do Maven amarra o
próprio `JAVA_HOME`. Com o `Dockerfile`, a versão está em `FROM
maven:3.9-eclipse-temurin-21` e o build é idêntico aqui e lá.

Efeito colateral bem-vindo: o Nixpacks injetava **todas** as variáveis do
serviço como `ARG`/`ENV` na imagem, inclusive `TELEGRAM_BOT_TOKEN` e
`MISTRAL_API_KEY`, gravando segredo nas camadas de build. O `Dockerfile` recebe
só o código; os segredos entram apenas no runtime.

O build roda `mvn -B -DskipTests package`. **Pular os testes aqui é
deliberado**: eles sobem um Postgres via Testcontainers, e não há Docker dentro
do build. Quem barra merge com teste é o CI, não o deploy — enquanto não houver
CI, isso depende de você rodar `mvn test` antes de dar push.

Para reproduzir o build exatamente como a Railway faz:

```bash
docker build -t novoapp-server .
```

### Postgres

Adicione o template de PostgreSQL ao projeto e fixe `POSTGRES_VERSION=16` — o
índice único de `category` usa `NULLS NOT DISTINCT`, que é 15+. O usuário
provisionado é superusuário, o que a nossa `V1` exige: ela cria os três papéis
de banco. Postgres gerenciado que não conceda `CREATEROLE` ao usuário principal
**não roda este schema**.

### Variáveis do serviço

Use referência entre serviços para não copiar credencial na mão:

```
NOVOAPP_DB_URL              = jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}
NOVOAPP_DB_ADMIN_USER       = ${{Postgres.PGUSER}}
NOVOAPP_DB_ADMIN_PASSWORD   = ${{Postgres.PGPASSWORD}}
NOVOAPP_DB_RUNTIME_PASSWORD = <gere uma; é a senha do papel novoapp_runtime>
TELEGRAM_BOT_TOKEN          = <BotFather>
TELEGRAM_WEBHOOK_SECRET     = <o mesmo do setWebhook>
MISTRAL_API_KEY             = <console.mistral.ai>
```

Três armadilhas, na ordem em que costumam aparecer:

- **`Postgres` nas referências é o nome do serviço**, não uma palavra reservada.
  Se você renomear o serviço de banco, as quatro referências quebram. O editor
  de variáveis da Railway monta a referência para você — use ele em vez de
  digitar.
- **`NOVOAPP_DB_RUNTIME_PASSWORD` é gravada no papel do banco na primeira
  migration** e não muda depois: migration com checksum fixo não roda de novo.
  Trocar essa variável sozinha derruba a aplicação. Ver "Papéis de banco".
- **Não defina `PORT`.** A Railway injeta; o `%prod.quarkus.http.port` já lê.

Depois do primeiro deploy, gere o domínio do serviço e rode o `setWebhook`
apontando para `https://<dominio>/webhook/telegram`.

### O que saber antes de depender disso

- **Deploy derruba processamento em voo.** O pipeline roda depois do 200
  (ADR-0005) e vive em memória; um restart no meio deixa a mensagem em
  `RECEIVED` e ninguém a retenta — não há retry ainda. É uma das lacunas
  conhecidas da Etapa 1.
- **Não há healthcheck.** A Railway só sabe que o processo morreu, não que ele
  ficou ruim. Adicionar `quarkus-smallrye-health` e apontar `healthcheckPath`
  para `/q/health` resolve, e é uma dependência nova — fica para quando alguém
  decidir que vale.

## O que ainda se faz na mão (e por quê)

### Semear as categorias do household

Household novo nasce **sem nenhuma categoria** ([ADR-0013](../docs/01-adr/0013-household-novo-comeca-sem-categorias.md)), e criar categoria
por chat é Etapa 2. Sem categoria, `mercado 50` sempre cai em confiança baixa —
o enum da tool ficaria vazio. Para validar a Etapa 1, insira na mão as
categorias da sua família:

```sql
-- conecte como o usuário administrativo (o Flyway), não como novoapp_runtime:
-- RLS não deixa novoapp_app escrever categoria de um household que ainda não
-- está no contexto da transação.
INSERT INTO category (household_id, name, kind)
SELECT id, nome, 'EXPENSE'
FROM household, unnest(ARRAY['Mercado', 'Farmácia', 'Transporte']) AS nome
WHERE household.name = 'Silva';
```

Isto **não** contraria a ADR-0013: não é o produto pré-criando categoria, é você
plantando dado de teste. O fluxo de criação por chat continua intocado para ser
validado de verdade na Etapa 2.

### Emitir um convite

A criação do convite pelo OWNER (`convidar Bruno, +55...`) é `@etapa2` — o
comando parte de número já vinculado e atravessaria o pipeline de interpretação,
que nesta etapa só conhece `registrarDespesa`. Todo o **aceite** está
implementado. Para testar, insira o convite:

```sql
INSERT INTO household_invite
    (household_id, invited_by_member_id, phone_number, token, status, expires_at)
SELECT h.id, m.id, '+5511900000002', 'teste-' || gen_random_uuid(),
       'PENDING', now() + interval '7 days'
FROM household h
JOIN household_membership hm ON hm.household_id = h.id AND hm.role = 'OWNER'
JOIN member m ON m.id = hm.member_id
WHERE h.name = 'Silva';

SELECT token FROM household_invite WHERE status = 'PENDING';
```

O convidado abre `https://t.me/<seu-bot>?start=<token>` e compartilha o contato.

## Papéis de banco

Três, e a separação é o isolamento multi-tenant ([ADR-0003](../docs/01-adr/0003-isolamento-multi-tenant-por-household.md),
[ADR-0022](../docs/01-adr/0022-papel-de-banco-pre-tenant-para-identidade.md)):

| Papel | Para quê |
|---|---|
| o usuário administrativo (`NOVOAPP_DB_ADMIN_USER`) | dono do schema; só o Flyway o usa |
| `novoapp_runtime` | único com `LOGIN`. **`NOINHERIT`**: sozinho não tem privilégio nenhum |
| `novoapp_app` / `novoapp_identity` | sem `LOGIN`. Cada transação entra num deles por `SET LOCAL ROLE` |

Consequência a conhecer antes de depurar: **esquecer `@HouseholdScoped` ou
`@IdentityScoped` num método novo dá "permission denied", não resultado vazio.**
É de propósito — RLS mal configurada falhando em silêncio é o risco que a
ADR-0003 registra como o mais caro de diagnosticar.

Papéis no Postgres são do cluster, não do banco: dois ambientes no mesmo cluster
compartilham `novoapp_app` e `novoapp_identity`.

**Rotacionar a senha de `novoapp_runtime`** não é migration. A `V1` cria o papel
com a senha vinda de um placeholder do Flyway, e migration com checksum fixo não
roda de novo. Para trocar:

```sql
ALTER ROLE novoapp_runtime WITH PASSWORD '<nova senha>';
```

e atualize `NOVOAPP_DB_RUNTIME_PASSWORD`.

## Estrutura

```
com.novoapp
  common/tenancy    -- aplica papel + app.household_id na transação (ADR-0022)
  common/message    -- InboundMessage normalizado, sem traço do canal
  channel           -- webhook, idempotência, envio. Ninguém depende dele
  identity          -- resolve tenant, onboarding, convite
  nlu               -- function calling, uma tool
  conversation      -- política de confiança e recibo
  finance           -- lançamento, conta, categoria
  shopping / tasks  -- vazios: existem para a fronteira já estar travada
```

A regra de dependência entre eles é
[`sdd-visao-geral.md`](../docs/02-arquitetura/sdd-visao-geral.md) e está travada
em `ModuleBoundariesTest` — falha o build, não a revisão de código.
