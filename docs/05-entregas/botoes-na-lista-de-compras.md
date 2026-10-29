---
tipo: entrega
etapa: botoes-na-lista
status: entregue
data: 2026-10-29
modulos:
  - channel
  - conversation
  - shopping
  - finance
  - banco
adrs:
  - ADR-0040
---

# Botões na lista de compras

**Critério de pronto do [ROADMAP](../../ROADMAP.md)**: carrinho → fechar → `60`
gera o fechamento ligado à despesa, e `desfazer` reverte os dois lados.
**Atingido nos testes.** Os 15 cenários de
[`mercado-botoes-na-lista.feature`](../03-specs/features/mercado-botoes-na-lista.feature)
verdes, e a suíte inteira: **214 testes, 84 cenários de aceitação, zero
falhas.** A tag `em-construcao` saiu de `AcoesNoChatAcceptanceTest`, que passou a
integrar o portão do CI.

**Não atingido ainda: a verificação no Telegram de verdade.** O entregável de
cada um dos três passos pedia testar no bot de desenvolvimento antes de seguir,
e isso não foi feito — quem implementou não tinha acesso ao bot nem a um cliente
do Telegram. Não é detalhe: o adaptador de canal (`callback_query`, teclado
inline, `editMessageText`, `answerCallbackQuery`) é exatamente a parte que o stub
de aceitação substitui, e o que o cobre hoje é um teste de unidade com a API do
Telegram falsa. O roteiro mínimo está em [Lacunas](#lacunas).

Nenhuma ADR nova. `staticFingerprint()` de `MistralMessageInterpreter` deu
`5eb5b298b145` antes e depois: nada de botão vazou para o prompt, e
`prompt_version` não mudou.

## O que foi construído

Um commit por passo do ROADMAP, com o placar subindo como o plano dizia: 6
cenários verdes com remover, 11 com o carrinho, 15 com o fechamento.

| Onde | O quê |
|---|---|
| `common/message` | `ChatAction` (a Ação de botão do glossário, com o estado de destino) e `ChatReply` (texto, botões neutros em linhas, origem do clique, substituir, aviso). `InboundMessage` ganha ação e origem opaca, mantendo o construtor de texto |
| `identity/spi/OutboundMessagePort` | a segunda operação, com `ChatReply`; a de texto puro não mudou |
| `channel/inbound` | `TelegramUpdate` com `callback_query`; o normalizador o aceita sob as mesmas condições da mensagem; `TelegramActionCodec`, o único lugar que conhece `c+`, `c-`, `rm`, `rt`, `fx`; `TelegramClickOrigin`; `InboundMessageKind` |
| `channel/outbound` | `TelegramMessageSender` responde todo clique, edita a mensagem de origem e cai para mensagem nova quando ela não pode ser editada; `TelegramApi` ganha `editMessageText`, `answerCallbackQuery` e o mapeador de erro que guarda a descrição |
| `conversation` | `Reply` fora do orquestrador; `action/ChatActionHandler` (os cinco tipos, antes da busca por pendência); `action/ShoppingListView`; `cart/` (`ChatCartService`, entidade e repositório) |
| `conversation/ConversationOrchestrator` | passo 0 para ação; consulta da lista com botões; escolha numerada olhando a ação guardada; carrinho de quem fechou perde o que fechou |
| `shopping` | `ListItemView` com id; `ListItemSnapshot` e `item(householdId, itemId)`; `lastCheckoutCategory` |
| `finance` | `categoryOfStandingExpense`, vazio para lançamento estornado |
| `V9__inbound_message_kind.sql` | `inbound_message.kind` (`TEXT`\|`ACTION`) |
| `V10__chat_cart_item.sql` | `chat_cart_item`, com `household_id`, RLS ativa e `FORCE` |
| `messages_pt_BR.properties` | rótulos de botão, avisos curtos e as duas perguntas do fechamento pelo carrinho (ADR-0015) |
| `application.properties` | `novoapp.conversation.cart.ttl=PT3H` (decisão aberta #28) |

Testes novos fora da aceitação: `TelegramActionCodecTest`,
`InboundMessageNormalizerTest`, `TelegramMessageSenderTest`, dois casos em
`IdempotencyTest` (reentrega de clique e `kind`) e um em `TenantIsolationTest`
(o carrinho de um household não aparece para outro).

## Decisões tomadas ao implementar

Nenhuma contradiz ADR aceita; todas estão registradas no SDD do módulo onde
moram.

- **O identificador de origem carrega também o id do `callback_query`**, e não
  só `chat_id:message_id` como o SDD de `channel` desenhou. `answerCallbackQuery`
  precisa dele, e esta é a única forma de chegar ao adaptador de saída sem que o
  resto do sistema carregue um campo com nome de canal. Continua opaco.
- **`callback_data` desconhecido é descartado sem resposta ao clique**, como
  `Update` desconhecido. Responder exigiria chamar o Telegram dentro do webhook.
- **Clique de quem não tem contexto resolvido** (onboarding, escolha de
  household) é respondido e marcado `IGNORED`, sem entrar no onboarding; o
  `callback_data` nunca é passado a `identity` como texto.
- **O ponto `[a verificar ao implementar]` do SDD de `conversation`** — a escolha
  de categoria só sabia terminar em despesa — se resolveu olhando a ação
  guardada na pendência, como `executeDeferred` já faz. É a regra da ADR-0029;
  não pediu ADR.
- **`fechar` supera a pergunta aberta** (`closeShortcutWindow`). Sem isso, a
  pergunta antiga voltaria a interceptar o fio assim que a do fechamento fosse
  respondida.
- **`fechar` não redesenha a lista**: nada nela mudou. A pergunta sai como
  mensagem nova.
- **`carrinho+` revive a linha vencida** (`ON CONFLICT ... DO UPDATE` só quando já
  passou do prazo), e não `DO NOTHING` como o modelo de dados dizia — com `DO
  NOTHING` o botão pareceria não funcionar depois de 3 horas.
- **"Esvaziar o carrinho" é tirar dele o que deixou de estar pendente**, depois
  de todo fechamento concluído. Pelo carrinho, isso é tudo; num fechamento
  parcial por texto, o que ainda está pendente continua separado.
- **Família sem nenhuma categoria de despesa** recebe uma orientação ao tocar em
  fechar, sem pendência aberta — o buraco da decisão aberta #26 pela porta do
  botão, anotado lá.
- **Rótulos**: o botão de carrinho mostra o estado atual (`🛒 Arroz` para pôr,
  `✅ Arroz` para tirar), e o texto marca o que está no carrinho com o mesmo `✅`.

## O que ficou de fora, e por quê

- **Adaptador do WhatsApp e paginação de lista longa** — Etapa 7, como a ADR
  manda.
- **Botões em qualquer outra resposta** (recibos, perguntas numeradas) — outra
  ADR.
- **Botão nativo de compartilhar contato** (lacuna da Etapa 1). O contrato novo
  **não** sai de graça para isso: `ChatReply` modela botão inline que devolve uma
  `ChatAction`, e compartilhar contato é teclado de resposta que devolve uma
  mensagem com contato — outro recurso do canal, outra forma de entrada. Fica
  registrado como não-oportunidade, para ninguém ter de redescobrir.
- **A inversão da política de confiança** — é o próximo item do ROADMAP.

## Lacunas

- **Verificação no bot de desenvolvimento, pendente.** Roteiro mínimo, na ordem
  dos passos: `o que está faltando?` chega com dois botões por item; tocar em
  `✖ Tirar` redesenha **a mesma** mensagem com `↩ Devolver`; tocar duas vezes no
  mesmo botão não empilha lista; tocar no carrinho marca só o seu fio (pedir a
  lista pelo outro telefone); `Fechar compra` → `60` → recibo com `desfazer`; e
  `desfazer` devolve os itens. Vale também tocar num botão de uma lista de mais de
  48 horas, para ver o desfecho real de "mensagem que não pode ser editada".
- **Lista longa no Telegram.** O teclado tem dois botões por item mais o rodapé;
  nada limita o tamanho. Uma lista de dezenas de itens deve bater no limite do
  teclado inline antes de bater no de 4096 caracteres `[a verificar contra a
  Bot API]`. Não é o uso real hoje, e a paginação já é gatilho da ADR-0040.
- **Responder a pergunta de categoria do primeiro fechamento pelo nome**
  ("mercado", em vez de `1`) vai ao modelo, como em toda pergunta numerada, e
  com confiança alta supera a pergunta (ADR-0029) — o fechamento pelo carrinho se
  perde e vira uma despesa avulsa. Não há cenário para isso, e o mesmo vale para
  as outras perguntas numeradas.
- **Prazo do carrinho sem cenário**, de propósito: é config provisória (decisão
  aberta #28), e cenário de prazo cristalizaria um número sem base.
- **`server/scripts/reset-household-for-family-launch.sql`** (fora do
  versionamento) apaga `list_item` de um household e não cita
  `chat_cart_item`. Continua funcionando — a FK do carrinho é `ON DELETE CASCADE`
  —, mas a lista do que ele apaga no cabeçalho ficou incompleta.

## Alterações em documento já existente

- **Correção de contagem**: o ROADMAP e o mapa de `03-specs` diziam 16 cenários;
  o arquivo sempre teve 15. Achado ao ligar o runner, que contou 15. Commit
  próprio, antes do código.
- **Passos existentes ajustados para o novo contexto**, cada um mantendo o
  sentido antigo quando nada novo foi montado: `que não há itens pendentes na
  lista` passou a montar o estado (o `Contexto` novo já põe itens); `o item fica
  registrado como solicitado por` olha o item em que o cenário tocou quando há
  vários; `nenhum item sai da lista` conta a partir do último toque; `uma despesa
  ... é registrada na categoria` e `o fechamento fica ligado à despesa criada`
  ignoram o fechamento plantado como histórico do household.
- **O stub de canal virou um adaptador falso**: dá id às mensagens, aplica a
  substituição e registra a resposta a cada clique. O clique dos cenários entra
  pelo webhook, codificado pelo codec de produção — e por isso "Reentrega do
  mesmo clique" exercita a ADR-0005 de verdade.
- [ROADMAP](../../ROADMAP.md): item 3 da ordem feito, com a verificação manual
  pendente declarada; a inversão da política de confiança passa a ser o próximo.
- SDDs de [`channel`](../02-arquitetura/sdd-modulo-channel.md),
  [`conversation`](../02-arquitetura/sdd-modulo-conversation.md) e
  [`shopping`](../02-arquitetura/sdd-modulo-shopping.md): de "ainda não
  implementado" para implementado, com as decisões acima.
- [Modelo de dados](../02-arquitetura/modelo-de-dados.md): `V9` e `V10` em "O que
  existe no banco hoje" — e a `V8`, que faltava ali desde a Etapa 3.
- [DECISOES-ABERTAS](../DECISOES-ABERTAS.md): #26 atualizada com o caminho do
  botão.
