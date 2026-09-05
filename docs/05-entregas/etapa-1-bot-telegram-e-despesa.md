---
tipo: entrega
etapa: 1
status: entregue
data: 2026-09-05
modulos:
  - channel
  - identity
  - nlu
  - conversation
  - finance
  - banco
adrs:
  - ADR-0001
  - ADR-0002
  - ADR-0003
  - ADR-0004
  - ADR-0005
  - ADR-0007
  - ADR-0008
  - ADR-0009
  - ADR-0011
  - ADR-0013
  - ADR-0019
  - ADR-0020
  - ADR-0022
---

# Etapa 1 — Bot Telegram + despesa

Primeira etapa com código. O que existia antes era só documentação.

**Critério de pronto do [ROADMAP](../../ROADMAP.md)**: mandar `mercado 50` no
Telegram e ver a linha em `transaction` no Postgres, com recibo no chat;
reentrega do provedor não duplica nada; teste de vazamento de tenant verde.
**Atingido** — com uma ressalva operacional honesta: para o `mercado 50` do
household novo funcionar, é preciso semear as categorias na mão, porque
household novo nasce sem nenhuma ([ADR-0013](../01-adr/0013-household-novo-comeca-sem-categorias.md)) e criar categoria por chat é
Etapa 2. O passo está documentado em [`server/README.md`](../../server/README.md).

## Onde o código está

`server/`, na raiz do repositório, paralelo a `docs/` e `legacy/`. Pacote raiz
`com.novoapp`. Quarkus 3.39.2, Java 21, Maven.

## O que foi construído

### Banco

Uma migration, `V1__initial_schema.sql`, com dez tabelas: `household`,
`member`, `household_membership`, `channel_identity`, `household_invite`,
`onboarding_session`, `inbound_message`, `account`, `category`, `transaction`.

RLS ativa **e** `FORCE` em todas menos `member` — que não tem `household_id` em
que uma policy possa se apoiar ([ADR-0007](../01-adr/0007-pessoa-em-multiplos-households.md)) e por isso só é alcançável pelo
papel pré-tenant.

Três papéis de banco ([ADR-0022](../01-adr/0022-papel-de-banco-pre-tenant-para-identidade.md), decidida nesta etapa): o administrativo (só
Flyway), `novoapp_runtime` (único com `LOGIN`, `NOINHERIT`) e os dois papéis de
execução, `novoapp_app` e `novoapp_identity`. Consequência desenhada de
propósito: **esquecer o escopo de tenancy estoura permissão negada, não retorna
vazio.**

### `common/tenancy`

O pacote técnico que aplica papel + `app.household_id` por transação, com
`SET LOCAL`. Tem SDD próprio: [`sdd-modulo-tenancy.md`](../02-arquitetura/sdd-modulo-tenancy.md). Era o único ponto
que o prompt da etapa deixou explicitamente em aberto ("decida você mesmo onde
ele vive") e o `sdd-modulo-identity.md` registrava como não decidido.

### `channel`

Webhook do Telegram, normalização, guarda de idempotência
(`INSERT ... ON CONFLICT DO NOTHING`), log de ingestão e envio pela Bot API.
O processamento roda fora do ciclo de request, em thread virtual, para o 200
sair em menos de 3s.

Uma correção que só apareceu ao implementar: o `message_id` do Telegram é único
por conversa, não global. Usá-lo cru como `provider_message_id` descartaria como
"reentrega" a mensagem legítima de outra pessoa. O adaptador compõe
`chat_id:message_id`.

### `identity`

Resolução de contexto (`ChannelIdentity` → `Member` → household ativo) e o
onboarding determinístico inteiro, sem LLM em ponto nenhum do caminho: primeiro
contato, criação self-service da família, escolha entre chat e aplicativo, e o
**aceite** de convite com todas as recusas (telefone errado, expirado, já
usado, convite inexistente), incluindo o caso de quem já é membro de outra
família.

### `nlu`

Uma tool, `registrarDespesa`, com o parâmetro de categoria montado como enum das
categorias reais do household — o modelo não consegue escolher categoria que a
família não criou. Provedor Mistral via LangChain4j ([ADR-0009](../01-adr/0009-mistral-ai-como-provedor-de-llm-na-validacao.md)), atrás de uma
interface própria (`ExpenseExtractor`) que nenhum tipo da biblioteca atravessa.

### `conversation`

Só confiança alta, decisão imediata, nenhuma pergunta de volta durante o
registro. Recibo com valor, categoria e a saída `desfazer` — que ainda não
funciona, e é assim de propósito: o texto antecipa a Etapa 2.

### `finance`

`registerExpense`, resolução de conta na ordem da [ADR-0019](../01-adr/0019-conta-padrao-por-membro.md)
(`default_account_id` do vínculo → `WALLET` implícita), leitura de categoria
para o `nlu`, e o observador de `HouseholdCreated` que cria a `WALLET` — na
mesma transação do onboarding, para que não exista household sem conta.

### `shopping` e `tasks`

Pacotes vazios, com `package-info.java`. Existem para que a fronteira esteja
travada por ArchUnit antes de haver código para violá-la.

## Testes

43 testes, 1 desabilitado de propósito. Os quatro obrigatórios da
[`estrategia-de-testes.md`](../04-qualidade/estrategia-de-testes.md) estão de pé, menos um:

| Camada | O que cobre |
|---|---|
| Arquitetura (ArchUnit) | 11 regras: a regra de dependência inteira do `sdd-visao-geral.md`, ausência de ciclos, regra não negociável 5, e que módulo de domínio não pode sequer referenciar o escopo pré-tenant |
| Isolamento de tenant | 5 testes, com Postgres real: dois households simultâneos; usar categoria de outro household; gravar com `householdId` diferente do contexto (recusado pelo `WITH CHECK`, não por validação em Java); sem escopo não lê nada; papel de domínio não alcança tabela pré-tenant |
| Idempotência | reentrega não vira nem linha nova em `inbound_message`; repetir a mesma mensagem de propósito **é** lançamento novo; mesmo `message_id` em conversa diferente é mensagem nova |
| Orçamento de resposta | interpretador leva 6s de propósito, 200 sai em menos de 3s |
| Aceitação (Cucumber) | 16 cenários, 113 passos, lendo os `.feature` de `docs/03-specs` direto — sem cópia |

**O quarto teste obrigatório, atomicidade do fechamento de compra, não existe**:
o fechamento de compra é Etapa 3.

## O que ficou de fora

### Por decisão de escopo (ROADMAP)

- Cenários `@etapa2` de `financas-lancamento-por-chat.feature`: ambiguidade
  entre categorias, criação de categoria, valor ausente, desfazer. Estão num
  runner Cucumber `@Disabled`, não apagados: tirar o `@Disabled` é o primeiro
  passo da Etapa 2.
- `PendingAction`, curto-circuito de confirmação, confiança média/baixa. A
  tabela `pending_action` sequer foi criada.
- Emissão de convite pelo OWNER (`convidar Bruno, +55...`). O cenário virou
  `@etapa2` no `vinculo-de-identidade.feature`, com o motivo escrito lá: parte
  de número já vinculado, atravessa o pipeline de interpretação, e o `nlu` desta
  etapa só declara uma tool. Todo o lado do aceite está pronto.
- Cartão e fatura, estorno, edição entre membros, metas.
- `mercado-lista-de-compras.feature` e `elo-fechamento-de-compra.feature`
  inteiros — Etapas 2 e 3. Nenhum dos dois tem passo implementado.
- Autenticação web e PWA ([ADR-0021](../01-adr/0021-autenticacao-web.md)). Nenhum endpoint REST para o Vue foi
  criado; `AuthenticationService` e `PasswordHasher` não existem.
- Tabelas modeladas e não criadas: `pending_action`, `invoice`,
  `transaction_edit`, `financial_goal`, `goal_transaction_link`,
  `shopping_list`, `list_item`, `list_checkout`, `task`. O schema desta etapa foi
  desenhado para não conflitar com elas.

### Lacunas conhecidas dentro do que foi entregue

Estas não são escopo adiado — são coisas que o comportamento entregue ainda não
faz, e que alguém encontraria usando.

1. **O botão nativo de compartilhar contato não é enviado.** A [ADR-0020](../01-adr/0020-convite-de-membro.md) diz
   que o bot pede o contato "pelo botão nativo do Telegram". O envio implementado
   manda só texto, sem `reply_markup`/`request_contact`. O aceite funciona quando
   a pessoa compartilha o contato pelo menu do Telegram, mas o atrito é maior do
   que a ADR descreve. Um campo a mais no `sendMessage` resolve.
2. **Não há retry com backoff quando o LLM falha.** A tabela de falhas
   transversais do `sdd-visao-geral.md` prevê "mensagem fica `RECEIVED`, retry
   com backoff, aviso no chat após a segunda falha". O que existe: uma tentativa,
   recibo de erro no chat, mensagem marcada `FAILED`. A regra que não podia ser
   quebrada — o usuário nunca fica sem resposta — está cumprida; a política de
   retentativa, não.
3. **Não há comando para trocar o household ativo.** A [ADR-0007](../01-adr/0007-pessoa-em-multiplos-households.md) prevê troca
   por comando explícito ("usar casa dos pais"). Quem tem dois vínculos continua
   mandando tudo para o household ativo, sem como trocar pelo chat. Consequência
   imediata: a resposta `ChooseHousehold` — a pergunta numerada de qual família —
   é enviada, mas a resposta a ela não é processada. Só é alcançável a partir de
   dado inconsistente (dois vínculos com `active_household_id` nulo), porque o
   aceite de convite nunca deixa esse campo nulo; ainda assim, é um caminho sem
   saída.
4. **`member` não tem RLS.** Decisão consciente ([ADR-0007](../01-adr/0007-pessoa-em-multiplos-households.md), [ADR-0022](../01-adr/0022-papel-de-banco-pre-tenant-para-identidade.md)) e
   mitigada — só o papel pré-tenant tem privilégio sobre ela —, mas quem lê
   `member` lê todo mundo.
5. ~~**Sem CI.**~~ Fechada em 2026-09-05: `.github/workflows/ci.yml` roda
   `mvn test` e o `docker build` a cada push na `main` e em todo pull request.
   Fica um resíduo que só o dono do repositório resolve: **o workflow reporta,
   não impede** — barrar merge exige branch protection na `main`, que é
   configuração do GitHub e não mora no repositório.
6. ~~**Sem configuração de deploy.**~~ Fechada em 2026-09-05: `server/Dockerfile`
   e `server/railway.json`, com a aplicação rodando na Railway. O build começou
   pelo builder automático da plataforma e foi trocado por Dockerfile depois de
   uma falha instrutiva — o Nixpacks instalava `jdk21` no ambiente, mas o pacote
   `maven` do nixpkgs traz o próprio JDK (19) e compila com ele, e o 19 recusa
   `release 21`. A variável `NIXPACKS_JDK_VERSION` não alcança isso. Com o
   Dockerfile a versão é explícita e o build é reproduzível na máquina de quem
   programa. Ganho junto: o Nixpacks injetava todas as variáveis do serviço como
   `ARG`/`ENV`, gravando `TELEGRAM_BOT_TOKEN` e `MISTRAL_API_KEY` nas camadas da
   imagem; agora os segredos só existem em runtime.
7. **Retenção de `inbound_message` não decidida** ([decisão aberta #4](../DECISOES-ABERTAS.md)). Toda
   mensagem fica armazenada, inclusive conteúdo pessoal.
8. **`nlu-eval` não existe.** A medição de taxa de acerto por tool é Etapa 5, mas
   vale registrar que nada mede o interpretador hoje — e precisão do
   interpretador é o produto.

## Achados do primeiro uso em produção (2026-09-05)

Três coisas que nenhum teste pegou porque nenhuma delas existe fora de um deploy
real. Ficam registradas porque são o tipo de coisa que se esquece.

1. **A senha do papel de runtime era gravada uma vez só, pela `V1`.** Trocar
   `NOVOAPP_DB_RUNTIME_PASSWORD` depois deixava a aplicação tentando uma senha
   que o banco não tinha — e, como o datasource de domínio é preguiçoso, o boot
   passava limpo e o erro aparecia **só na primeira requisição**, como um 500 no
   webhook com a aplicação aparentemente saudável. Corrigido com o callback
   `db/migration/afterMigrate.sql`, que roda a cada start, mesmo sem migração
   pendente, e reconcilia a senha com a variável. Isso resolve a quarta
   consequência negativa da [ADR-0022](../01-adr/0022-papel-de-banco-pre-tenant-para-identidade.md), que dizia que rotacionar exigiria
   `ALTER ROLE` manual — vale decidir se a ADR merece nota de correção.
2. **O Postgres em produção é 18.6, não 16.** `POSTGRES_VERSION=16` foi definido
   depois do volume já ter sido inicializado. Não quebra nada — o requisito real
   é 15+, por causa do `NULLS NOT DISTINCT` no índice único de `category` — mas
   os testes rodam contra 16 e a produção contra 18, o que é uma divergência a
   fechar antes de confiar demais no par.
3. **O `secret_token` do Telegram não aceita `+`, `/` nem `=`**, que é o que o
   `base64` produz. O README ensinava a gerar assim.

## Decisões tomadas ao implementar

Uma virou ADR; as outras ficaram registradas no SDD do módulo, seguindo o
padrão do projeto de nunca deixar suposição silenciosa no código.

| Decisão | Onde ficou |
|---|---|
| Papel de banco pré-tenant para a resolução de identidade | [ADR-0022](../01-adr/0022-papel-de-banco-pre-tenant-para-identidade.md) — desloca a frase "nenhuma outra tabela tem exceção equivalente" da [ADR-0003](../01-adr/0003-isolamento-multi-tenant-por-household.md) |
| Interceptor de RLS vive em `common/tenancy`, fora dos sete módulos | [`sdd-modulo-tenancy.md`](../02-arquitetura/sdd-modulo-tenancy.md) e [`sdd-modulo-identity.md`](../02-arquitetura/sdd-modulo-identity.md) |
| `resolveContext` recebe a mensagem inteira, não só `(canal, external_id)` | [`sdd-modulo-identity.md`](../02-arquitetura/sdd-modulo-identity.md), [`sdd-modulo-channel.md`](../02-arquitetura/sdd-modulo-channel.md) |
| `OutboundMessagePort` endereça por `(canal, external_id)` | [`sdd-modulo-identity.md`](../02-arquitetura/sdd-modulo-identity.md) |
| Tabela `onboarding_session` | [`sdd-modulo-identity.md`](../02-arquitetura/sdd-modulo-identity.md), [`modelo-de-dados.md`](../02-arquitetura/modelo-de-dados.md) |
| `provider_message_id` composto (`chat_id:message_id`) | [`sdd-modulo-channel.md`](../02-arquitetura/sdd-modulo-channel.md) |
| `sourceMessageId` na assinatura de `registerExpense`; método em inglês | [`sdd-modulo-finance.md`](../02-arquitetura/sdd-modulo-finance.md) |
| Recibo não nomeia a conta usada | [`sdd-modulo-conversation.md`](../02-arquitetura/sdd-modulo-conversation.md) |
| Interface própria entre `nlu` e o LangChain4j | [`sdd-modulo-nlu.md`](../02-arquitetura/sdd-modulo-nlu.md) |

## Inconsistências encontradas na documentação

Achadas ao implementar, e o processo de ADR uma-a-uma existe justamente para
que isso apareça. Duas foram resolvidas; uma continua aberta.

1. **[ADR-0003](../01-adr/0003-isolamento-multi-tenant-por-household.md) se contradizia** — "nenhuma outra tabela tem exceção
   equivalente" versus "a resolução de identidade roda sob o papel de banco usado
   por esse caminho". Resolvida pela [ADR-0022](../01-adr/0022-papel-de-banco-pre-tenant-para-identidade.md).
2. **Os SDDs nomeiam métodos em português** (`registrarDespesa`, `interpretar`,
   `processar`) contra a regra do CLAUDE.md de identificador em inglês "sem
   exceção". O código seguiu o CLAUDE.md e os SDDs foram corrigidos. O nome da
   *tool* continua `registrarDespesa`: ali é dado enviado ao modelo, não
   identificador.
3. **Aberta: a [ADR-0019](../01-adr/0019-conta-padrao-por-membro.md) afirma que "recibo sempre nomear a conta usada" é
   "regra existente"** — e ela não existe em nenhum outro documento. A mitigação
   das consequências negativas daquela ADR depende dessa regra. Enquanto só
   houver uma conta por household isso não tem efeito prático, mas na etapa em
   que a segunda conta aparecer, ou a regra é criada ou a ADR-0019 precisa de
   nota de correção. Registrado em [`sdd-modulo-conversation.md`](../02-arquitetura/sdd-modulo-conversation.md).

## Alterações em documento já existente

- `vinculo-de-identidade.feature` ganhou tags `@etapa1`/`@etapa2`, no mesmo
  padrão do `financas-lancamento-por-chat.feature`, com o motivo escrito no
  cabeçalho. Nenhum cenário foi alterado, removido ou reescrito.
- Os cinco SDDs de módulo, `sdd-visao-geral.md` e `modelo-de-dados.md` ganharam
  as seções de decisão listadas acima.
