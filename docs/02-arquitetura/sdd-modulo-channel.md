---
tipo: sdd
modulo: channel
status: escrito
atualizado_em: 2026-09-05
adrs:
  - ADR-0002
  - ADR-0005
  - ADR-0008
  - ADR-0020
---

# SDD — Módulo `channel`

## Responsabilidade

Adaptador de canal: webhook do provedor → `InboundMessage` normalizado →
idempotência → resposta pelo canal de origem. Primeiro adaptador: Telegram
([ADR-0002](../01-adr/0002-telegram-primeiro-whatsapp-depois.md)). WhatsApp entra na Etapa 7 como adaptador novo, mesma
interface — ver Gatilhos de revisão.

## Não faz

- Não interpreta intenção — isso é `nlu`, chamado através de `conversation`.
- Não decide regra de negócio de domínio nenhuma.
- Nenhum código abaixo de `channel` sabe de qual canal a mensagem veio (regra 5
  do CLAUDE.md) — tudo que roda depois de `channel` trabalha só com
  `InboundMessage`, `Member` e `household`, nunca com "é Telegram" ou "é
  WhatsApp".
- Não trata o canal `WEB` de `channel_identity` (ADR-0021) — login e sessão
  web passam por um adaptador REST separado (Etapa 4, sem SDD ainda), nunca
  por webhook. `channel` aqui é só TELEGRAM/WHATSAPP.

## Depende de

- `identity` — resolver contexto de tenant logo após normalizar a mensagem, e
  disparar o onboarding determinístico quando a identidade não resolve
  ([ADR-0020](../01-adr/0020-convite-de-membro.md)). `channel` implementa a interface
  `identity.spi.OutboundMessagePort` (ver `sdd-modulo-identity.md`) — não o
  contrário, pra não inverter a direção de dependência.
- `conversation` — dispara o pipeline de interpretação quando a identidade
  resolve com sucesso.

Ninguém depende de `channel`. É o módulo mais externo: só ele conhece o
protocolo HTTP do provedor.

## Estrutura interna proposta

```
channel/
  inbound/
    TelegramWebhookResource        -- @Path("/webhook/telegram"), recebe Update
    InboundMessageNormalizer       -- Update -> InboundMessage
                                       (channel, external_id, provider_message_id,
                                        raw_text, received_at)
    InboundMessageIdempotencyGuard -- INSERT ... ON CONFLICT (channel, provider_message_id)
                                       DO NOTHING, ADR-0005
  outbound/
    TelegramMessageSender implements identity.spi.OutboundMessagePort
```

## Fluxo (caminho feliz)

1. Telegram faz `POST /webhook/telegram` com um `Update`.
2. `InboundMessageNormalizer` extrai `channel=TELEGRAM`, `externalId`
   (telegram user id), `providerMessageId` (telegram message id), `rawText`,
   `receivedAt`.
3. `InboundMessageIdempotencyGuard` tenta inserir `inbound_message`
   (`household_id` ainda nulo, `status=RECEIVED`). Conflito de unique
   constraint → descarta silenciosamente, responde 200, processamento para
   aqui ([ADR-0005](../01-adr/0005-idempotencia-de-mensagens-recebidas.md)).
4. Webhook responde 200 imediatamente (regra 3 do CLAUDE.md) — o resto roda
   assíncrono.
5. Chama `identity.resolveContext(channel, externalId)`:
   - identidade resolvida com household ativo → `inbound_message.household_id`
     preenchido, segue pro pipeline de `conversation`/`nlu`;
   - identidade resolvida mas pessoa com mais de um household e nenhum ativo
     definido → `identity` devolve a pergunta de qual família (troca
     explícita, [ADR-0007](../01-adr/0007-pessoa-em-multiplos-households.md), não é onboarding); `channel` só envia;
   - identidade não resolvida (nenhum `channel_identity` pro external_id) →
     `identity` assume o onboarding determinístico ([ADR-0020](../01-adr/0020-convite-de-membro.md)) e devolve
     o texto da próxima pergunta; `channel` não decide esse texto, só envia.

## Erros

- `identity` indisponível (erro técnico, não "identidade não encontrada") →
  mensagem fica `RECEIVED`, retry com backoff, aviso no chat após a segunda
  falha — mesma política da tabela de falhas transversais do
  `sdd-visao-geral.md`.
- Envio ao provedor falha → tenta de novo; o usuário nunca fica sem resposta
  nenhuma depois que o processamento já rodou até o fim (sempre existe um
  recibo, de sucesso ou de erro).

## Dados que este módulo escreve

- `inbound_message` — sempre, mesmo quando algo falha depois (é o log de
  ingestão, não um efeito colateral do sucesso).

## Testes

- ArchUnit: `channel` não pode ser importado por nenhum outro módulo
  (`identity`, `conversation`, `nlu`, `finance`, `shopping`, `tasks`) — só ele
  importa os outros, nunca o contrário.
- Reentrega da mesma `providerMessageId` → exatamente um processamento
  (cenário já coberto em `financas-lancamento-por-chat.feature` e
  `elo-fechamento-de-compra.feature`).

## Gatilhos de revisão

Etapa 7 (WhatsApp, [ADR-0002](../01-adr/0002-telegram-primeiro-whatsapp-depois.md)): novo adaptador dentro de `channel`
(`WhatsAppWebhookResource`, `WhatsAppMessageSender`), mesma normalização pra
`InboundMessage`, mesma implementação de `OutboundMessagePort`. Nada em
`identity`, `conversation` ou `nlu` muda.
