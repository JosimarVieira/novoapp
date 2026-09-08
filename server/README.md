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
export TELEGRAM_BOT_USERNAME=<username do bot, sem o @>   # so pro link do convite
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

Gere o segredo do webhook. O Telegram só aceita `A-Z`, `a-z`, `0-9`, `_` e `-`,
de 1 a 256 caracteres:

```bash
export TELEGRAM_WEBHOOK_SECRET=$(LC_ALL=C tr -dc 'A-Za-z0-9_-' < /dev/urandom | head -c 48)
echo "[$TELEGRAM_WEBHOOK_SECRET]"
```

`tr -dc` **mantém só** o conjunto permitido. Não use `base64`: ele produz `+`,
`/` e `=`, e o `setWebhook` responde
`Bad Request: secret token contains illegal characters`. Os colchetes no `echo`
revelam espaço ou quebra de linha colada junto no copiar.

### 5. Apontar o Telegram para cá

O Telegram só entrega webhook em **HTTPS**, e só nas portas **443, 80, 88 ou
8443** — quem faz esse mapeamento é o proxy da plataforma (ou o túnel, em
desenvolvimento), não a aplicação. Em desenvolvimento, exponha a porta 8080 com
ngrok/cloudflared e registre:

```bash
curl "https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/setWebhook" \
  --data-urlencode "url=https://<seu-host-publico>/webhook/telegram" \
  --data-urlencode "secret_token=$TELEGRAM_WEBHOOK_SECRET" \
  --data-urlencode 'allowed_updates=["message"]'

# conferir
curl "https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/getWebhookInfo"
```

`allowed_updates=["message"]` porque esta etapa só trata mensagem: não faz
sentido receber edição, reação e callback de botão para descartar.

O segredo precisa ser **o mesmo** aqui e na variável de ambiente da aplicação.
Ao trocá-lo em produção, atualize a variável primeiro e espere o redeploy: até
lá a aplicação confere o segredo antigo e responde 403 a tudo.

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
TELEGRAM_BOT_USERNAME       = <username do bot, sem o @>
TELEGRAM_WEBHOOK_SECRET     = <o mesmo do setWebhook>
MISTRAL_API_KEY             = <console.mistral.ai>
```

Duas armadilhas, na ordem em que costumam aparecer:

- **`Postgres` nas referências é o nome do serviço**, não uma palavra reservada.
  Se você renomear o serviço de banco, as quatro referências quebram. O editor
  de variáveis da Railway monta a referência para você — use ele em vez de
  digitar.
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

## O que deixou de se fazer na mão (Etapa 2a)

Duas coisas que a Etapa 1 exigia por SQL agora acontecem pelo chat, e as
instruções antigas foram removidas em vez de mantidas como alternativa: seguir
usando SQL para elas passaria por cima justamente do fluxo que a Etapa 2a
entregou.

**Semear as categorias.** Household novo continua nascendo sem nenhuma
([ADR-0013](../docs/01-adr/0013-household-novo-comeca-sem-categorias.md)), mas
agora a primeira mensagem cria a que faltar: `pet shop 80` oferece criar "Pet
shop" e grava depois do `sim`; `restaurante eu e esposa 90` corrigido para
"restaurante dentro de alimentação" cria a hierarquia inteira (ADRs
[0024](../docs/01-adr/0024-categoria-sugerida-por-texto-livre.md) e
[0026](../docs/01-adr/0026-hierarquia-na-criacao-de-categoria-por-chat.md)).

**Emitir convite.** `convidar Bruno, +5511900000002`, mandado por quem é `OWNER`,
cria o convite e devolve o link ([ADR-0020](../docs/01-adr/0020-convite-de-membro.md)).
Para o link sair como `https://t.me/<bot>?start=<token>` e não como o token cru,
configure `TELEGRAM_BOT_USERNAME` com o username do bot, sem o arroba. O sistema
**não** entrega o convite: quem repassa é o `OWNER` — a Bot API não deixa um bot
iniciar conversa com quem nunca falou com ele.

## Configuração provisória, declarada como tal

Três valores em `application.properties` são palpite, não calibração, e estão
marcados assim no próprio arquivo:

| Propriedade | Hoje | Decide |
|---|---|---|
| `novoapp.conversation.confidence.high` | `0.8` | acima disso, executa direto |
| `novoapp.conversation.confidence.low` | `0.4` | abaixo disso, pergunta aberta |
| `novoapp.conversation.pending-action.ttl` | `PT10M` | prazo do atalho de resposta no chat |

São config global do app — editáveis e redeployáveis, nunca constante escondida
no código e nunca preferência por household
([decisões abertas #7 e #8](../docs/DECISOES-ABERTAS.md)). Os números de verdade
saem da Etapa 5, com dado real.

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

**Rotacionar a senha de `novoapp_runtime`** é só trocar
`NOVOAPP_DB_RUNTIME_PASSWORD` e reiniciar. O callback
`db/migration/afterMigrate.sql` roda a cada start — inclusive quando não há
migração pendente — e reconcilia a senha do papel com a variável. A variável é a
fonte da verdade.

Nem sempre foi assim: até 2026-09-05 a senha era gravada só pela `V1`, e trocar
a variável depois deixava a aplicação tentando uma senha que o banco não tinha.
Pior, falhava **só na primeira requisição** — o datasource de domínio é
preguiçoso, então o boot passava limpo e o webhook devolvia 500 com a aplicação
aparentemente saudável.

## Estrutura

```
com.novoapp
  common/tenancy    -- aplica papel + app.household_id na transação (ADR-0022)
  common/message    -- InboundMessage normalizado, sem traço do canal
  channel           -- webhook, idempotência, envio. Ninguém depende dele
  identity          -- resolve tenant, onboarding, convite
  nlu               -- function calling, seis tools
  conversation      -- política de confiança, pendência, curto-circuito, recibo
  finance           -- lançamento, conta, categoria, estorno
  shopping          -- lista de compras e itens
  tasks             -- vazio: existe para a fronteira já estar travada
```

A regra de dependência entre eles é
[`sdd-visao-geral.md`](../docs/02-arquitetura/sdd-visao-geral.md) e está travada
em `ModuleBoundariesTest` — falha o build, não a revisão de código.
