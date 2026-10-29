---
tipo: sdd
modulo: channel
status: escrito
atualizado_em: 2026-10-29
adrs:
  - ADR-0002
  - ADR-0005
  - ADR-0008
  - ADR-0020
  - ADR-0022
  - ADR-0036
  - ADR-0040
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
    TelegramInviteLink    implements identity.spi.InviteLinkPort
```

`TelegramInviteLink` entrou na Etapa 2a: o link do convite
(`t.me/<bot>?start=<token>`, [ADR-0020](../01-adr/0020-convite-de-membro.md)) tem
formato de provedor, e por isso é montado aqui e não em `identity` — mesmo
argumento e mesmo padrão do `OutboundMessagePort`. Precisa do username do bot
(`novoapp.channel.telegram.bot-username`): não dá para derivá-lo do token da Bot
API sem uma chamada de rede, e o link é montado no meio de uma resposta de chat.
Vazio devolve o token cru, que é o suficiente em desenvolvimento e em teste, onde
não há bot publicado.

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
5. Chama `identity.resolveContext(...)`. Corrigido em 2026-09-05, ao
   implementar: a assinatura desenhada aqui era
   `resolveContext(channel, externalId)`, e não fecha — o token de convite chega
   dentro do texto e o telefone chega no contato compartilhado. Passa a mensagem
   inteira; o porquê está em `sdd-modulo-identity.md`. Os desfechos são os
   mesmos:
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
- **Quem responde pelo erro é `InboundPipeline`, e não o orquestrador
  (corrigido em 2026-09-18).** Achado lendo o código enquanto se investigava uma
  mensagem que parecia ter ficado sem resposta em produção — o log de ingestão
  mostrou depois que aquela mensagem estava `EXECUTED`, e o silêncio era do
  recorte do transcript. O caso era falso; o furo não.
  `ConversationOrchestrator.process`
  captura a falha dele próprio e responde recibo de erro; o `InboundDispatcher`
  confiava nisso e registrava só no log. Nada respondia pela falha nascida
  **antes** do orquestrador — `resolveContext`, onboarding,
  `attachHousehold` —, e a mensagem terminava sem resposta nenhuma, que é o
  desfecho que a tabela de falhas transversais do `sdd-visao-geral.md` proíbe em
  uma linha ("o usuário nunca fica sem resposta") e o pior modo de falha deste
  módulo, pelo mesmo motivo registrado em `provider_message_id` mais abaixo:
  quem não recebe nada reenvia, e pode duplicar o que já foi gravado. O
  `InboundPipeline` agora responde o recibo de erro e marca a mensagem `FAILED`.
  `recordOutcome` ficou isolado em `try` próprio: falhar ao gravar o desfecho
  depois de o recibo ter saído é problema de observabilidade, e mandar "não
  consegui" atrás de um "Anotado:" que está gravado seria mentir para o
  usuário.

## Dados que este módulo escreve

- `inbound_message` — sempre, mesmo quando algo falha depois (é o log de
  ingestão, não um efeito colateral do sucesso). É a única tabela que `channel`
  toca, e sempre sob o papel pré-tenant ([ADR-0022](../01-adr/0022-papel-de-banco-pre-tenant-para-identidade.md)), o único com privilégio
  sobre ela.

Desde 2026-09-19 `recordOutcome` grava também `prompt_version` e `model_name`
([ADR-0036](../01-adr/0036-instrumento-de-medicao-da-etapa-5.md)) — a proveniência
da interpretação, sem a qual as três métricas da
[ADR-0035](../01-adr/0035-o-que-a-etapa-5-mede.md) não são calculáveis. Os valores
vêm de `nlu` **através do `ProcessingOutcome`**, e não de uma chamada de `channel`
a `nlu`: a regra de dependência não tem essa aresta, e o desfecho já era o
caminho pelo qual `conversation` devolve o que este módulo grava. Ficam **nulos**
quando a mensagem não gastou modelo — curto-circuito, onboarding, falha antes da
interpretação —, e esse nulo é o que distingue "nenhum modelo opinou" de
"esqueceram de gravar".

## `provider_message_id` no Telegram (decidido em 2026-09-05, ao implementar)

O `message_id` da Bot API é único **por conversa**, não global: duas pessoas
diferentes recebem o mesmo número. Usá-lo cru como `provider_message_id` faria
a chave `(channel, provider_message_id)` da [ADR-0005](../01-adr/0005-idempotencia-de-mensagens-recebidas.md) descartar como
"reentrega" a mensagem legítima de outra pessoa — o pior modo de falha
possível: silêncio. O adaptador compõe `chat_id:message_id`. A chave da ADR não
muda; muda o que o adaptador põe nela.

## Testes

- ArchUnit: `channel` não pode ser importado por nenhum outro módulo
  (`identity`, `conversation`, `nlu`, `finance`, `shopping`, `tasks`) — só ele
  importa os outros, nunca o contrário.
- Reentrega da mesma `providerMessageId` → exatamente um processamento
  (cenário já coberto em `financas-lancamento-por-chat.feature` e
  `elo-fechamento-de-compra.feature`), mais um teste de integração que confere
  que a reentrega não vira sequer linha nova em `inbound_message` — se virasse, a
  calibração da Etapa 5 contaria a mesma mensagem duas vezes.
- Orçamento de resposta: o interpretador demora 6s de propósito e o 200 sai em
  menos de 3s assim mesmo. É o único teste que roda com o pipeline assíncrono
  ligado — os demais rodam síncrono para não virar espera com relógio.

## Ações de botão (ADR-0040, desenhado em 2026-10-29, ainda não implementado)

A [ADR-0040](../01-adr/0040-botoes-na-lista-de-compras-e-carrinho-por-fio.md)
põe botões na lista de compras. Botão é recurso de canal, e por isso o que muda
aqui é a maior parte do trabalho: o resto do sistema vê só uma Ação de botão
(glossário) e uma resposta com ações neutras.

### Entrada

Hoje `InboundMessageNormalizer` descarta todo `Update` que não é `message` — o
comentário dele cita "callback de botão" nominalmente. Passa a aceitar
`callback_query`, sob as mesmas condições de hoje (chat privado, `from`
presente; [ADR-0008](../01-adr/0008-interacao-1-1-por-membro-nunca-em-grupo.md)).

- **`provider_message_id` = `cb:<callback_query.id>`.** O id do
  `callback_query` é único globalmente, ao contrário do `message_id`; o prefixo
  só existe para nunca colidir com `chat_id:message_id`, que é o formato das
  mensagens de texto. A chave da
  [ADR-0005](../01-adr/0005-idempotencia-de-mensagens-recebidas.md) não muda —
  reentrega do mesmo clique é descartada como reentrega de texto. Toque duplo
  gera dois `callback_query` distintos e **não** é deduplicado aqui; quem o torna
  inofensivo é o clique carregar o estado de destino (ADR-0040, item 6).
- **`raw_text` = o `callback_data` cru.** É o registro forense do que o
  Telegram entregou, no mesmo sentido do texto de uma mensagem.
- **`inbound_message.kind` = `ACTION`** (texto continua `TEXT`). A coluna é o
  que permite à calibração da [ADR-0035](../01-adr/0035-o-que-a-etapa-5-mede.md)
  separar clique de mensagem sem inferir pelo formato de `raw_text`.
- **Identificador de origem**: `chat_id:message_id` da mensagem onde o botão
  estava. Vai para `InboundMessage` como string opaca — `conversation` só o
  devolve na resposta, nunca o interpreta (regra 5).

### Formato do `callback_data`

Limite de 64 bytes do Telegram. Código curto da ação + id do item:
`c+:<uuid>`, `c-:<uuid>`, `rm:<uuid>`, `rt:<uuid>` (devolver) e `fx` (fechar).
Um UUID tem 36 caracteres, então o maior caso fica em 39 bytes. O mapeamento
entre esses códigos e os tipos de `ChatAction` vive aqui e em nenhum outro
lugar; `callback_data` que não casa com nenhum é tratado como `IGNORED`, como
`Update` desconhecido já é.

O id do item vem do cliente e é, portanto, entrada não confiável. Não há
verificação extra neste módulo: a leitura do item roda sob RLS
([ADR-0003](../01-adr/0003-isolamento-multi-tenant-por-household.md)), e id de
outro household simplesmente não é encontrado.

### Saída

`OutboundMessagePort` ganha uma segunda operação, que recebe uma resposta com
texto, ações (rótulo + ação, em linhas) e, opcionalmente, "substituir a mensagem
de origem". O adaptador do Telegram traduz:

- ações → `reply_markup` com `inline_keyboard`;
- substituir → `editMessageText` sobre o identificador de origem;
- o aviso curto da resposta ("Café já saiu da lista") → `answerCallbackQuery`.
  **Todo clique é respondido**, com ou sem aviso: sem isso o cliente do Telegram
  mantém o botão em estado de carregamento.

Dois desfechos de edição que não são erro:

- *message is not modified* — o redesenho deu exatamente o mesmo texto e
  teclado (toque duplo, clique que não mudou nada). Tratado como sucesso.
- mensagem que não pode mais ser editada — o adaptador envia a resposta como
  mensagem nova. O usuário nunca fica sem resposta (seção Erros).

A operação de texto puro continua existindo: onboarding e recibos não mudam.

### O webhook continua respondendo em menos de 3s

Nada no fluxo de clique é síncrono. O `callback_query` entra no mesmo
`InboundDispatcher` que a mensagem de texto, e `answerCallbackQuery` sai no fim
do processamento assíncrono, junto com a edição (regra 3).

### WhatsApp (Etapa 7)

A mesma resposta com ações vira lista interativa — até dez linhas, sem edição
de mensagem enviada `[a verificar contra a documentação da Cloud API]`. Por
isso "substituir a origem" é pedido, não ordem: o adaptador do WhatsApp envia
mensagem nova. Lista com mais de dez itens precisa de paginação, que é gatilho
de revisão da ADR-0040 e não está desenhada.

### Estrutura

```
channel/
  inbound/
    InboundMessageNormalizer   -- + callback_query -> NormalizedInbound com acao
    TelegramActionCodec        -- callback_data <-> ChatAction (o unico lugar
                                  que conhece os codigos curtos)
  outbound/
    TelegramMessageSender      -- + send com acoes, editMessageText,
                                  answerCallbackQuery
```

### Testes

- Normalizador: `callback_query` de chat privado vira ação com o
  `provider_message_id` prefixado; de grupo é descartado.
- Codec: todo tipo de ação faz ida e volta, e nenhum `callback_data` passa de 64
  bytes.
- Reentrega do mesmo `callback_query.id` → uma linha em `inbound_message`, como
  já é testado para texto.
- Edição que devolve *message is not modified* não vira recibo de erro.

## Gatilhos de revisão

Etapa 7 (WhatsApp, [ADR-0002](../01-adr/0002-telegram-primeiro-whatsapp-depois.md)): novo adaptador dentro de `channel`
(`WhatsAppWebhookResource`, `WhatsAppMessageSender`), mesma normalização pra
`InboundMessage`, mesma implementação de `OutboundMessagePort`. Nada em
`identity`, `conversation` ou `nlu` muda.
