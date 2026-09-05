---
tipo: adr
numero: 21
status: aceita
data: 2026-09-05
modulos:
  - banco
  - identity
depende_de:
  - ADR-0007
  - ADR-0020
supera: []
superada_por:
corrigida_em:
---

# ADR-0021 — Autenticação web por e-mail e senha, reaproveitando `channel_identity` como canal `WEB`

- **Impacta**: banco (`member.email`, `member.password_hash`, `channel_identity.channel`
  ganha valor `WEB`), `identity`; depende de
  [ADR-0007](0007-pessoa-em-multiplos-households.md) (múltiplos households por pessoa) e
  [ADR-0020](0020-convite-de-membro.md) (reaproveita os mesmos fluxos de criação de
  household e convite, só troca o canal de entrada)

## Contexto

Duas lacunas ficaram expostas quando o autor levantou, em 2026-09-05, que
parte das pessoas vai usar só o app e ignorar completamente o chat
([DECISOES-ABERTAS.md](../DECISOES-ABERTAS.md), item 21):

1. **Login de um membro que já existe** (criado por chat) pra acessar a
   Etapa 4 — nunca foi decidido, independente de qualquer coisa nova. Etapa 4
   ("correção de lançamento") não tem sentido sem alguma forma de dizer
   "sou a Ana" no navegador.
2. **Criar a família inteira e o próprio membro sem nunca ter usado
   Telegram** — a parte nova. `identity` já expõe esses fluxos como serviço
   reaproveitável (regra 4 do CLAUDE.md: bot e REST consomem a mesma camada
   de domínio, `sdd-modulo-identity.md`), então não é redesenho de backend —
   é decidir só o mecanismo de autenticação em si.

O autor escolheu usuário e senha tradicional (não e-mail com link mágico,
não telefone por SMS) — evita depender de um provedor de envio (SMS tem
custo por mensagem; e-mail exigiria decidir infraestrutura de envio agora).

Isso deixa uma pergunta em aberto que a escolha por senha não responde
sozinha: **qual é o identificador de login?** `member` hoje só tem
`phone_number` (nullable, ADR-0020) — não serve como identificador universal
porque quem é 100% app não necessariamente informa telefone. Decisão: usar
e-mail como identificador (só como *username*, sem verificação de posse
nesta ADR — ver Negativas).

## Decisão

Campos novos em `member`:

```text
member
  ...
  email (nullable, unico quando preenchido)
  password_hash (nullable)   -- bcrypt, null enquanto o membro nunca configurou login web
```

`channel_identity.channel` ganha o valor `WEB` (o glossário já previa isso —
`Canal` já lista `TELEGRAM, WHATSAPP, WEB`; o schema estava desatualizado
em relação ao próprio glossário). Uma sessão web é só mais um
`channel_identity`:

```text
channel_identity (sem mudança de coluna, só de valores possíveis)
  channel = WEB
  external_id = member.email          -- estável, único por member
  active_household_id                 -- mesma regra da ADR-0007: nulo até
                                         precisar, preenchido automático se
                                         só houver um vínculo
  verified_at = created_at            -- não existe etapa de verificação
                                         separada nesta ADR: a senha correta
                                         *é* a prova
```

Login reaproveita `IdentityResolutionService.resolveContext(WEB, email)`
sem mudar a assinatura do método (`sdd-modulo-identity.md`) — o mesmo
código que já resolve household ativo pro chat resolve pro web.

Dois fluxos de entrada:

**A. Criar conta 100% pelo app (sem chat)**
Formulário pede nome da família, nome da pessoa, e-mail, senha → chama
`HouseholdSelfServiceFlow` (já desenhado na ADR-0020/SDD de `identity`),
criando `household`, `member` (com `email`/`password_hash` em vez de
`phone_number`/`channel_identity` Telegram), `household_membership(OWNER)`,
evento `HouseholdCreated` (conta WALLET via `finance`, mesmo caminho do
chat). Cria também o `channel_identity(channel=WEB)`.

**B. Membro criado por chat quer acessar a web pela primeira vez**
Não tem e-mail/senha ainda. Fluxo de "criar acesso web": pede e-mail e
senha novos, mas confirma posse da identidade enviando um link de
confirmação de uso único pelo **mesmo canal que a pessoa já tem**
(`OutboundMessagePort`, via Telegram) — reaproveita a infraestrutura de
envio da ADR-0020 em vez de inventar uma segunda. Depois disso, login é
sempre e-mail + senha, para qualquer membro.

Sessão: cookie de sessão `HttpOnly`/`Secure`, não JWT em `localStorage` —
app e API são a mesma origem (monólito servindo PWA), então não há motivo
pra token portável entre origens diferentes; evita a superfície de roubo de
token via XSS que token em `localStorage` teria.

## Alternativas consideradas

### A. E-mail com link mágico (sem senha)
Era a recomendação inicial: zero custo de envio, sem senha pra gerenciar.
Descartada porque o autor decidiu por senha tradicional — mais familiar
pra usuário leigo que nunca usou esse tipo de fluxo, e evita depender de
entregabilidade de e-mail transacional logo de saída.

### B. Telefone por SMS (OTP)
Reaproveitaria `member.phone_number`, mas tem custo por mensagem e exige um
provedor de SMS novo — mesmo tipo de dependência de custo por mensagem que
já pesou contra o WhatsApp cedo demais (ADR-0002, DECISOES-ABERTAS item 2).
Descartada pelo mesmo motivo.

### C. Token JWT em vez de cookie de sessão
Mais comum em API pública multi-cliente. Descartada por enquanto: não há
cliente além do próprio PWA servido pela mesma origem; cookie de sessão é
mais simples de implementar com segurança (não exige guardar token no
cliente) e mais fácil de revogar (invalidar sessão no servidor). Reavaliar
se um dia existir app nativo ou API pra terceiros — hoje não existe
(CLAUDE.md: "sem app nativo").

## Consequências

### Positivas
- Reaproveita quase tudo que a ADR-0020 já desenhou (`HouseholdSelfServiceFlow`,
  `InviteFlow`, `IdentityResolutionService.resolveContext`) — o novo é só o
  canal `WEB` e o par e-mail/senha.
- `WEB` como valor de `channel_identity.channel` corrige uma inconsistência
  que já existia: o glossário previa `WEB` desde antes desta ADR, o schema
  não.
- Cookie de sessão evita a complexidade de gerenciar token no cliente.

### Negativas
- **Sem verificação de posse do e-mail no fluxo A** (criar conta 100% pelo
  app). Alguém pode se cadastrar com um e-mail que não é dele — não abre
  acesso à conta de outra pessoa (a senha continua sendo o segredo), mas
  significa que "recuperar senha por e-mail" não é confiável enquanto isso
  não for resolvido. Registrado como gatilho de revisão, não bloqueia esta
  ADR.
- **Sem fluxo de recuperação de senha decidido.** Membro criado só pelo app
  (fluxo A) que esquece a senha não tem canal alternativo pra provar quem é
  — diferente do membro criado por chat (fluxo B), que sempre pode receber
  um link novo pelo Telegram. Fica registrado como decisão aberta, não
  resolvida aqui.
- Duplicidade de conceito de "identidade verificada": telefone verificado
  por compartilhar-contato (ADR-0020) e e-mail nunca verificado (esta ADR)
  convivem no mesmo campo `channel_identity.verified_at`, com significados
  diferentes. Aceitável por ora, mas pode confundir auditoria futura.

## Gatilhos de revisão

- Antes de abrir cadastro pra fora da família (Etapa 6): decidir verificação
  de e-mail e recuperação de senha — as duas negativas acima não são
  aceitáveis fora de um ambiente de validação familiar controlado.
- Se um app nativo ou API de terceiros aparecer: revisitar cookie de sessão
  vs. token (Alternativa C).
