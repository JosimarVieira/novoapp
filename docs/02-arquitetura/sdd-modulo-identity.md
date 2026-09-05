---
tipo: sdd
modulo: identity
status: escrito
atualizado_em: 2026-09-05
adrs:
  - ADR-0003
  - ADR-0007
  - ADR-0011
  - ADR-0020
  - ADR-0021
  - ADR-0022
---

# SDD — Módulo `identity`

## Responsabilidade

Duas coisas, deliberadamente no mesmo módulo porque compartilham as mesmas
tabelas (`household`, `member`, `household_membership`, `channel_identity`,
`household_invite`):

1. Resolver o contexto de tenant de toda mensagem que chega
   (`ChannelIdentity` → `Member` → household ativo, via
   `HouseholdMembership`, [ADR-0007](../01-adr/0007-pessoa-em-multiplos-households.md)).
2. Fazer o bootstrap de identidade quando ela ainda não existe: criar
   household self-service, gerar convite, aceitar convite
   ([ADR-0020](../01-adr/0020-convite-de-membro.md)).
3. Autenticar sessão web por e-mail e senha, canal `WEB`
   ([ADR-0021](../01-adr/0021-autenticacao-web.md)) — corrigido em 2026-09-05: esta seção dizia
   que autenticação web era "fora do escopo até Etapa 6, módulo `auth`
   futuro" — a ADR-0021 já decidiu o contrário, é `identity` mesmo, e a
   Etapa 4 (não a 6) que precisa disso.

## Não faz

- Não verifica posse do e-mail no cadastro nem recupera senha — negativas
  em aberto da própria ADR-0021, registradas em
  [DECISOES-ABERTAS.md](../DECISOES-ABERTAS.md) item 21, resolver antes da
  Etapa 6.
- Não formata recibo de domínio financeiro (isso é `conversation`/`finance`) —
  mas formata e envia as próprias perguntas de onboarding, porque elas não
  passam por LLM nem por política de confiança: é árvore de decisão fixa,
  sem interpretação nenhuma envolvida.
- Não seta a sessão de RLS (`SET LOCAL app.household_id`) — só resolve o
  dado. Quem aplica isso é o pacote técnico `common/tenancy`
  ([`sdd-modulo-tenancy.md`](sdd-modulo-tenancy.md), [ADR-0022](../01-adr/0022-papel-de-banco-pre-tenant-para-identidade.md)) — decidido em 2026-09-05,
  ao escrever a Etapa 1; esta seção dizia "ainda não tem SDD próprio".

## Depende de

Nada dos outros módulos de domínio — é o módulo mais "de baixo" depois de
`channel`, só toca banco.

## Quem depende dele

- `channel` — resolução de contexto e disparo de onboarding.
- `conversation` — nome do membro/household pra formatar recibo e perguntas,
  e o `OutboundMessagePort` pra enviar.
- `finance`, `shopping`, `tasks` — resolver `household_id`/`member_id` do
  contexto da requisição atual (já estabelecido no `sdd-visao-geral.md`).
- um adaptador REST pra Vue (Etapa 4, sem SDD próprio ainda — seria ficção
  detalhar agora) vai chamar `AuthenticationService.login()` do mesmo jeito
  que `channel` chama `resolveContext()` hoje.

## Estrutura interna proposta

```
identity/
  spi/
    OutboundMessagePort            -- interface: void send(ChannelIdentity destino, String texto)
                                       implementada por channel.outbound.TelegramMessageSender
  IdentityResolutionService        -- resolveContext(channel, externalId)
                                       -> ResolvedContext | ChooseHousehold | OnboardingStep
  onboarding/
    HouseholdSelfServiceFlow       -- "quer criar uma família?" -> nome ->
                                       cria household + member(OWNER) + membership
    InviteFlow                     -- OWNER pede convite; convidado aceita
                                       (telefone + contato compartilhado)
  auth/
    AuthenticationService           -- login(email, senha) -> LoginResult
                                        cadastrar(nome, email, senha, nomeFamilia) -> LoginResult
                                        criarAcessoWeb(memberId, email, senha) -> void
    PasswordHasher                  -- bcrypt, ADR-0021
  HouseholdInviteRepository, ChannelIdentityRepository,
  MemberRepository, HouseholdMembershipRepository
```

## Fluxo: resolução de contexto (caso comum)

1. `channel` chama `resolveContext(TELEGRAM, externalId)`.
2. Busca `channel_identity` por `(channel, external_id)`:
   - não existe → devolve `OnboardingStep` (ver fluxo de onboarding abaixo);
   - existe, `active_household_id` preenchido → devolve
     `ResolvedContext(householdId, memberId)`;
   - existe, `active_household_id` nulo, pessoa com exatamente um
     `household_membership` → preenche `active_household_id` neste momento
     (chegar aqui com um só vínculo e ainda nulo seria dado inconsistente de
     uma aceitação de convite que não seguiu a regra) e devolve
     `ResolvedContext`;
   - existe, `active_household_id` nulo, pessoa com mais de um
     `household_membership` → devolve `ChooseHousehold` (pergunta qual
     família, [ADR-0007](../01-adr/0007-pessoa-em-multiplos-households.md)).

## Fluxo: onboarding (ADR-0020)

1. `/start` sem token de convite e sem `channel_identity` prévia → pergunta
   genérica: quer criar família nova? (o caminho de quem tem convite chega
   normalmente pelo link com token, não por esta pergunta).
2. `/start` com token de convite → pula direto pra pedir compartilhar
   contato (Cenário "Convidado aceita" do `vinculo-de-identidade.feature`).
3. Confirma criar família → pede nome → cria `household`, `member` (com esse
   `channel_identity`), `household_membership(role=OWNER)`,
   `active_household_id` preenchido. Publica evento CDI
   `HouseholdCreated(householdId)` — não escreve na tabela `account`
   diretamente.
4. Aceita convite → valida telefone compartilhado contra
   `household_invite.phone_number` do convite `PENDING` não expirado → cria
   ou reaproveita `member` ([ADR-0007](../01-adr/0007-pessoa-em-multiplos-households.md) se a pessoa já é membro de outro
   household), cria `channel_identity`, `household_membership(role=MEMBER)`,
   marca convite `ACCEPTED`, preenche `active_household_id` só se for o
   único vínculo.
5. Telefone não bate, convite expirado ou já aceito → mensagens de recusa
   específicas (`vinculo-de-identidade.feature`), nenhum estado muda.

Toda mensagem de onboarding é enviada via `OutboundMessagePort`, texto
decidido aqui — nunca passa por `nlu`.

## Fluxo: autenticação web (ADR-0021)

Correção de 2026-09-05: a ADR-0021 dizia que login "reaproveita
`resolveContext(WEB, email)` sem mudar a assinatura" — isso é impreciso.
`resolveContext` só confere se existe um `channel_identity` pro
`(channel, external_id)` informado; nunca conferiu senha nenhuma, porque
pro chat isso nunca foi necessário — o Telegram user id já é uma prova de
identidade que o próprio canal garante, ninguém pode forjar mandando
mensagem em nome de outro user id. Web não tem esse tipo de prova: qualquer
requisição pode alegar qualquer e-mail. Chamar `resolveContext(WEB, email)`
direto, sem checar senha antes, deixaria entrar em qualquer household só
sabendo o e-mail de quem é membro — falha de segurança, não só imprecisão
de documentação.

Por isso existe `AuthenticationService`, um passo antes de
`resolveContext`, nunca substituindo:

1. **Login** (`AuthenticationService.login(email, senha)`): busca `member`
   por `email`, confere `password_hash` (bcrypt). Só depois de confirmado
   chama `resolveContext(WEB, email)` internamente pra montar o
   `LoginResult` (household ativo ou `ChooseHousehold`) e devolve pro
   chamador (o adaptador REST da Etapa 4) criar a sessão (cookie
   `HttpOnly`/`Secure`, ADR-0021). Requisições seguintes usam a sessão já
   estabelecida — não repetem `resolveContext` com e-mail em texto puro a
   cada request.
2. **Cadastro 100% pelo app** (`AuthenticationService.cadastrar(...)`):
   mesmo `HouseholdSelfServiceFlow` do onboarding por chat, só que criando
   `member` com `email`/`password_hash` em vez de `phone_number`/
   `channel_identity` Telegram, e um `channel_identity(channel=WEB)` em vez
   de um Telegram.
3. **Criar acesso web pra membro que já existe** (criado por chat,
   `criarAcessoWeb`): pede e-mail/senha novos, confirma posse da
   identidade mandando um link de confirmação de uso único pelo
   `OutboundMessagePort` (Telegram) antes de gravar — não é o
   `AuthenticationService.login` que faz essa confirmação, é esse método
   separado, chamado uma vez só, no primeiro acesso web.

## Decisão tomada agora, não em ADR: quem cria a account WALLET implícita

O modelo de dados já previa que household novo ganha uma conta WALLET
implícita. Faltava decidir **quem escreve essa linha**. `identity` não pode
chamar `finance` diretamente pra isso — inverteria a direção de dependência
que o `sdd-visao-geral.md` trava (`finance` depende de `identity`, nunca o
contrário; um `identity` → `finance` direto criaria ciclo). Solução: `identity`
só publica `HouseholdCreated(householdId)`; `finance` observa esse evento
(CDI `@Observes`, mesmo processo, sem HTTP) e cria a conta. `identity` nunca
escreve em tabela de `finance`.

## Dados que este módulo escreve

`household`, `member` (incluindo `email`/`password_hash`, ADR-0021),
`household_membership`, `channel_identity` (incluindo canal `WEB`),
`household_invite`. Não escreve em `account` — ver decisão acima.

## Testes

- ArchUnit: `identity` não importa `channel`, `nlu`, `conversation`,
  `finance`, `shopping` nem `tasks` — só é importado.
- Todo cenário de `vinculo-de-identidade.feature` vira teste de integração
  deste módulo.
- Login com senha errada não pode devolver `ResolvedContext` nenhum —
  teste que trava exatamente o furo corrigido nesta versão (ver "Fluxo:
  autenticação web" acima).

## Decisões tomadas ao implementar a Etapa 1 (2026-09-05)

Três coisas que este SDD deixava em aberto ou desenhava de um jeito que a
implementação mostrou não fechar. Ficam registradas aqui, e não só no código,
pelo mesmo motivo da decisão sobre a `account` WALLET acima.

**1. `resolveContext` recebe a mensagem inteira, não só `(canal, external_id)`.**
O `sdd-modulo-channel.md` desenhou `resolveContext(channel, externalId)`. Não
fecha: o token de convite chega dentro do texto (`/start <token>`, [ADR-0020](../01-adr/0020-convite-de-membro.md)) e
o telefone chega no contato compartilhado. Com só o par, `identity` não teria
como saber que a mensagem é um aceite de convite, e essa decisão vazaria pra
`channel` — que, por regra, não decide nada. A assinatura passou a receber um
`IncomingContact(canal, externalId, nomeDeQuemFalou, texto, telefoneCompartilhado)`.
É o que faz funcionar o cenário "Convite já aceito não pode ser aceito de novo",
em que a pessoa **já tem** `channel_identity` e ainda assim precisa cair no
fluxo de convite.

**2. `OutboundMessagePort` endereça por `(canal, external_id)`, não por
`ChannelIdentity`.** A assinatura desenhada aqui era
`send(ChannelIdentity destino, String texto)`. Durante o onboarding não existe
`ChannelIdentity` — criar uma é o desfecho do fluxo, não o começo dele. O porto
passou a ser `send(Channel canal, String externalId, String texto)`.

**3. Tabela `onboarding_session`.** O onboarding é uma árvore de mais de um
passo ("quer criar?" → "qual o nome?"), e quem está no meio dela ainda não tem
`channel_identity` nem household — logo não cabe em `pending_action`, que exige
os dois `NOT NULL`, e que de todo modo é Etapa 2 ([ADR-0018](../01-adr/0018-central-de-pendencias.md)). A tabela guarda
`(canal, external_id) → estado, token do convite`, vive sob o papel pré-tenant e
some assim que o vínculo existe. Sem ADR própria: é estrutura de suporte a um
fluxo que a [ADR-0020](../01-adr/0020-convite-de-membro.md) já decidiu, não decisão nova de produto.

Uma quarta coisa que **não** foi implementada: a emissão do convite pelo OWNER
(`convidar Bruno, +55...`). O comando parte de número já vinculado, então
atravessa o pipeline de interpretação, e o `nlu` da Etapa 1 só declara a tool
`registrarDespesa`. O cenário virou `@etapa2` no `.feature`. Todo o lado do
aceite está implementado.

## Gatilhos de revisão

- Etapa 7 (WhatsApp): `resolveContext` ganha um segundo `channel` possível;
  nada na lógica de onboarding muda, só a origem do `external_id`.
- ~~Quando o interceptor de RLS for escrito, decidir explicitamente se ele
  vive dentro de `identity` ou é módulo técnico à parte~~ — decidido em
  2026-09-05: pacote técnico à parte, [ADR-0022](../01-adr/0022-papel-de-banco-pre-tenant-para-identidade.md) e [`sdd-modulo-tenancy.md`](sdd-modulo-tenancy.md).
- Etapa 2, ao entrar `PendingAction`: reavaliar se `onboarding_session` continua
  tabela própria ou se as duas convergem. Hoje não convergem — os campos
  obrigatórios de uma são justamente o que a outra não tem.
- Antes da Etapa 6: verificação de posse do e-mail e recuperação de senha
  (negativas da ADR-0021, DECISOES-ABERTAS item 21) — sem isso, `cadastrar`
  fica sem caminho de recuperação pra quem nunca usou chat.
