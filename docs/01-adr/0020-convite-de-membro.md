---
tipo: adr
numero: 20
status: aceita
data: 2026-09-04
modulos:
  - banco
  - identity
  - channel
  - onboarding
depende_de:
  - ADR-0003
  - ADR-0007
supera: []
superada_por:
corrigida_em:
---

# ADR-0020 — Convite de membro por telefone e token, único uso, expira em 7 dias

- **Impacta**: banco (`household_invite`, `member.phone_number`), `identity`,
  `channel`, feature de vínculo de identidade; depende de
  [ADR-0003](0003-isolamento-multi-tenant-por-household.md), [ADR-0007](0007-pessoa-em-multiplos-households.md)

## Contexto

A feature de vínculo de identidade (README, pré-requisito da Etapa 1) precisa
de um mecanismo pra alguém entrar numa família que já existe — sem isso,
toda pessoa nova cria a própria família isolada (é o que RLS por household
garante por padrão: isolamento é a regra, "entrar na família de outro"
precisa ser exceção explícita).

O autor descreveu o fluxo em 2026-09-04: administrador pré-cadastra o
telefone da pessoa convidada e gera um convite; o convite expira, é de uso
único, e só é aceito pelo telefone específico para o qual foi gerado — o
mesmo padrão do `FamilyInvite` do legado (token, expiração de 7 dias,
status `PENDING/ACCEPTED/EXPIRED`), adaptado de convite por e-mail (web)
para convite por telefone (chat).

Restrição técnica que não é de produto, é da plataforma: a Telegram Bot API
não permite a um bot iniciar conversa com um usuário que nunca interagiu com
ele antes — só responde quem já mandou `/start`. Isso significa que o
**sistema não entrega o convite** pelo Telegram; ele só gera o link
(`t.me/<bot>?start=<token>`) e devolve pro administrador, que precisa
repassar por fora (WhatsApp pessoal, SMS, verbalmente). O convite só entra
no sistema quando a pessoa convidada clica o link e o Telegram abre uma
conversa nova com o bot, carregando o token no `/start`.

## Decisão

Tabela nova:

```text
household_invite
  id, household_id, invited_by_member_id,
  phone_number,                 -- E.164, alvo do convite
  token UK,
  status (PENDING|ACCEPTED|EXPIRED),
  created_at, expires_at,       -- expires_at = created_at + 7 dias
  accepted_at, accepted_by_member_id (nullable)
```

`member` ganha `phone_number` (nullable) — capturado na primeira vez que a
pessoa compartilha contato (aceite de convite, ou onboarding do primeiro
membro), reaproveitável se a mesma pessoa precisar linkar outro canal no
futuro (WhatsApp, [ADR-0002](0002-telegram-primeiro-whatsapp-depois.md)).

Fluxo:

1. `OWNER` pede ao bot para convidar, informando nome e telefone. Bot cria
   `household_invite` (`status=PENDING`, `expires_at` em 7 dias) e devolve o
   link — **o `OWNER` repassa esse link por fora**, o sistema não entrega.
2. Convidado clica o link, Telegram abre conversa nova com o bot, token
   chega via `/start`. Bot pede pra pessoa compartilhar contato (botão
   nativo do Telegram).
3. Telefone compartilhado bate com `household_invite.phone_number` do
   convite `PENDING` e não expirado → aceita: cria `channel_identity`
   (reaproveitando `member` existente se a pessoa já for membro de outro
   household, [ADR-0007](0007-pessoa-em-multiplos-households.md); senão cria `member` novo), cria
   `household_membership` (`role=MEMBER`) para o household do convite, marca
   `household_invite.status=ACCEPTED`. `active_household_id` só é
   preenchido automaticamente se for o único vínculo da pessoa — segundo
   vínculo em diante segue a regra já decidida (troca por comando
   explícito, [ADR-0007](0007-pessoa-em-multiplos-households.md)).
4. Telefone não bate → recusa ("este convite não é para o seu número"),
   convite continua `PENDING` (tentativa errada não consome nem expira o
   convite).
5. Convite expirado ou já `ACCEPTED` → bot informa a situação, não aceita
   de novo. `status` é recalculado sob demanda a partir de `expires_at`,
   mesmo padrão do `invoice.status` ([ADR-0014](0014-fechamento-de-fatura-sob-demanda.md)) — nenhum job de limpeza.

Entrar numa família existente **sem** convite não é possível — só
self-service é criar a própria família nova (primeiro membro, `OWNER`).

## Alternativas consideradas

### A. Convite por código curto memorizável (ex.: 6 dígitos), em vez de link com token
Descartada: token longo em link evita adivinhação por força bruta; código
curto é mais fácil de repassar verbalmente, mas o autor já especificou link,
e nada no caso de uso exige ditar o convite por voz.

### B. Convite não amarrado a telefone — qualquer um com o link entra
Descartada explicitamente pelo autor: o convite tem que ser específico da
pessoa, não um link genérico de "entre na minha família" que vazaria para
qualquer um que o recebesse.

### C. Sistema tenta entregar o convite direto pelo Telegram
Descartada: tecnicamente impossível — Telegram Bot API não inicia conversa
com quem nunca mandou `/start`. Não é escolha de produto, é limite da
plataforma.

## Consequências

### Positivas
- Isolamento por household nunca é acidentalmente rompido por
  auto-associação — entrar numa família exige ação explícita de quem já é
  `OWNER` dela.
- Reaproveita conhecimento validado do legado (token, expiração, status),
  adaptado ao canal certo (telefone/chat, não e-mail/web).
- `status` calculado sob demanda mantém consistência com o padrão já
  estabelecido em [ADR-0014](0014-fechamento-de-fatura-sob-demanda.md) — sem infraestrutura de job nova.

### Negativas
- Entrega do link é manual, fora do sistema — nada garante que o
  administrador realmente repassa, ou repassa certo. Sem como o produto
  validar isso; é responsabilidade humana, não técnica.
- Se a pessoa convidada perder o link antes de aceitar, o `OWNER` precisa
  gerar um convite novo (esta ADR não decide reenvio do mesmo token).
- Verificação por "compartilhar contato" depende do Telegram permitir esse
  recurso nativo — se a pessoa negar a permissão, não há caminho alternativo
  de verificação decidido aqui.

## Gatilhos de revisão

Quando o WhatsApp entrar ([ADR-0002](0002-telegram-primeiro-whatsapp-depois.md), Etapa 7), reavaliar a limitação de
entrega: a Cloud API permite iniciar conversa por template, então o sistema
passaria a poder entregar o convite direto, sem depender do administrador
repassar por fora.
