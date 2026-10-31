# Prompt para implementar os botões na lista de compras (ADR-0040)

Contexto: a [ADR-0040](docs/01-adr/0040-botoes-na-lista-de-compras-e-carrinho-por-fio.md)
foi aceita em 2026-10-29, e a documentação inteira já está escrita: ADR, 16
cenários Gherkin, SDDs de `channel`, `conversation` e `shopping`, modelo de dados,
glossário, decisão aberta #28 e ROADMAP. **Nenhuma linha de código de produção
existe ainda.** Este prompt é para uma sessão nova do Claude Code fazer a
implementação no mesmo repositório.

## Antes de qualquer código

1. **A documentação ainda não está commitada.** Está na working tree da `main`.
   Crie a branch `feat/botoes-na-lista` e faça um commit só com ela
   (`docs: ADR-0040, botões na lista de compras`) antes de tocar em Java. Assim a
   revisão separa o que foi decidido do que foi implementado.
2. **Rode `DocumentationCoherenceTest`.** Ela foi conferida por um script que
   replica as regras, mas não pelo build. Se quebrar, o problema está nos
   documentos, não no teste. Conserte os documentos.
3. Rode a suíte inteira e confirme que está verde. É a linha de base: daqui em
   diante, vermelho fora do runner novo é regressão.

## Leitura obrigatória

1. `CLAUDE.md` inteiro, com atenção às regras 2, 3, 5, 6 e 7. Esta mudança
   esbarra em todas.
2. A ADR-0040 inteira, inclusive as alternativas. A alternativa A (botão
   "comprei" que marca o item) é o desenho óbvio e **está recusada**: ela desliga
   o elo. Se em algum momento parecer mais simples fazer assim, releia o Contexto
   da ADR.
3. `docs/03-specs/features/mercado-botoes-na-lista.feature`, comentário do topo
   incluído.
4. `ROADMAP.md`, seção **"Botões na lista de compras (planejado em 2026-10-29)"**:
   os três passos e o entregável de cada um.
5. As seções marcadas "ADR-0040, desenhado em 2026-10-29" em
   `docs/02-arquitetura/sdd-modulo-channel.md`, `sdd-modulo-conversation.md`,
   `sdd-modulo-shopping.md` e `modelo-de-dados.md` (Ingestão).

**Não use lista curada de ADRs.** O índice é `docs/adr.base` ou `docs/01-adr/`.
As ADRs que governam esta mudança estão no `depende_de` da 0040 e nos links dos
SDDs; siga de lá.

Antes do primeiro grep, rode `graphify query` (regra do `CLAUDE.md`) se o
`graphify` estiver instalado.

## Ordem de execução

### Passo 0 — ligar o runner

`AcoesNoChatAcceptanceTest` existe com `@Disabled`. No primeiro commit de código,
tire o `@Disabled` e ponha `@Tag("em-construcao")`. O CI já trata essa tag: ela
fica fora do portão e roda num passo informativo
(`.github/workflows/ci.yml`). Os 16 cenários vão falhar por passo indefinido, e é
assim que o placar sobe. **Não recoloque o `@Disabled` nem afrouxe cenário para
ver verde.**

### Passo 1 — contrato neutro + remover

Comece pelo cenário **"Remover pelo botão"**. Ele força a cadeia inteira de uma
vez: `callback_query` → normalizador → codec → `InboundMessage` com ação →
curto-circuito em `conversation` → `removeItems` → redesenho → edição da mensagem
→ `answerCallbackQuery`.

- Migration `V9`: `inbound_message.kind` (`NOT NULL DEFAULT 'TEXT'`). Siga o
  padrão da `V6`, porque `inbound_message` é exclusiva do papel pré-tenant
  (ADR-0022).
- Depois: "Devolver o item removido pelo botão", "Botão de uma lista
  desatualizada", "Reentrega do mesmo clique pelo provedor", "Consultar a lista
  devolve botões por item" e "Lista vazia não tem botões".

**Entregável**: no Telegram de verdade, tocar no botão de remover tira o item e
a mesma mensagem se redesenha. Teste no bot de desenvolvimento antes de seguir.

### Passo 2 — carrinho

Migration `V10`: `chat_cart_item`, com `household_id`, RLS ativa e `FORCE`, e os
grants do papel de runtime, no padrão das tabelas novas da `V3` e da `V7`. Cenários de carrinho: pôr, tirar, ser de
quem tocou, toque duplo, mensagem de texto no meio.

**Entregável**: tocar no carrinho marca o item só na tela de quem tocou.

### Passo 3 — fechar pelo carrinho

`ASK_AMOUNT` com `DeferredAction.CLOSE_PURCHASE`, categoria do último fechamento
**não estornado**, e `CHOOSE_CATEGORY` no primeiro fechamento da família. Comece
por "Fechar compra pelo carrinho" e deixe para depois "Primeiro fechamento do
household pergunta a categoria uma vez", que tem o ponto em aberto descrito
abaixo.

**Entregável**: carrinho → fechar → `60` gera o fechamento ligado à despesa, e
`desfazer` reverte os dois lados.

## Armadilhas concretas

**Mudar `InboundMessage` e `OutboundMessagePort` quebra a compilação em lugares
conhecidos.** `new InboundMessage(` aparece em `InboundPipeline` e nos testes.
`OutboundMessagePort` é implementada por `TelegramMessageSender` e por
`StubOutboundMessagePort`. O stub precisa passar a **registrar ações e
substituições**, não só texto: é por ele que passos como "a mesma mensagem de
'Bruno' é atualizada" e "cada item da lista tem um botão" vão assertar. Mantenha
a operação de texto puro do jeito que está, porque onboarding e recibos não mudam.

**O `prompt_version` não pode mudar.** Esta mudança não acrescenta tool nem mexe
em descrição de parâmetro. Se `staticFingerprint()` de
`MistralMessageInterpreter` mudar de valor, alguma coisa de botão vazou para o
prompt, e isso é bug. Confira o hash antes e depois.

**Regra 5 tem dois lados.** Os códigos curtos do `callback_data` (`c+:`, `c-:`,
`rm:`, `rt:`, `fx`) existem **só** em `channel`, no codec. `conversation`
trabalha com o enum de `ChatAction` e com o identificador de origem como string
opaca. Se o identificador precisar ser lido fora de `channel`, o desenho está
errado. O ArchUnit pega import de `channel`, mas não pega string com formato de
canal.

**A ação entra antes de `PendingActionService.findOpen`, e clique não supera
pendência**, exceto `fechar` (SDD de `conversation`). Pôr a ação depois da busca
de pendência faria um toque no carrinho ser lido como resposta à pergunta aberta.

**Todo clique é respondido com `answerCallbackQuery`**, inclusive quando não
mudou nada. Sem isso o botão fica em estado de carregamento no cliente. E
*message is not modified* na edição é sucesso, não recibo de erro.

**O passo de glue do clique deve entrar pelo mesmo ponto que os passos de texto
usam.** Se os passos de texto entram pelo webhook, o clique também deve entrar
por lá, com um `Update` que tenha `callback_query`. Assim normalizador, codec e
idempotência são exercitados pelo cenário. Atalho direto no orquestrador faria
"Reentrega do mesmo clique" passar sem testar a ADR-0005.

**Rótulos de botão são texto para o chat.** Vão para `MessageKey` e
`messages_pt_BR.properties` (ADR-0015), nunca literal no código.

## Ponto em aberto que este trabalho provavelmente força

O SDD de `conversation` marca `[a verificar ao implementar]`: hoje a escolha de
categoria (`CHOOSE_CATEGORY`) desemboca em `registerOrAskAmount`, que só sabe
terminar em despesa. O cenário "Primeiro fechamento do household pergunta a
categoria uma vez" precisa que ela termine em `CLOSE_PURCHASE`. É o acoplamento
que o gatilho da decisão #26 já descreve. Se resolvê-lo virar um `switch` sobre
`deferred`, como `executeDeferred` já é, isso é implementação: registre no SDD e
siga. Se exigir mudar o que uma ADR aceita decidiu, **pare e escreva ADR** antes.

## Decisões já tomadas — não reabra sem ADR

- "Peguei" é carrinho do fio, não `PURCHASED` (ADR-0040, alternativa A).
- O carrinho não é `PendingAction` (alternativa C) e não vive no teclado da
  mensagem (alternativa D).
- A mensagem é editada, não reenviada, no Telegram (alternativa F).
- O banco é a fonte de verdade. A mensagem nunca é.
- Categoria do fechamento por botão = último fechamento não estornado.
- Remover pelo botão continua fora do `desfazer` (ADR-0039). A volta é
  `devolver`, que é `addItems`.
- Vida útil do carrinho: `novoapp.conversation.cart.ttl`, 3h, config provisória
  (decisão aberta #28). Nunca constante no código.
- `shopping` não ganha escrita por id, só as duas leituras do SDD.

## O que NÃO construir agora

- Adaptador do WhatsApp e paginação de lista longa (Etapa 7).
- Botões em qualquer outra resposta além da lista (recibos, perguntas
  numeradas). É tentador e fica para outra ADR.
- Botão nativo de compartilhar contato. É lacuna da Etapa 1, mas é outro
  recurso de canal. Se reaproveitar o contrato novo de saída sair de graça,
  registre como oportunidade, não implemente.
- A inversão da política de confiança: é o próximo item do roadmap, depois
  deste.

## Ao fechar

1. Os 16 cenários verdes e a suíte inteira verde.
2. Tire a `@Tag("em-construcao")` de `AcoesNoChatAcceptanceTest`, para que ele
   entre no portão do CI.
3. Os SDDs deixam de dizer "ainda não implementado", e `modelo-de-dados.md`
   ganha `V9` e `V10` em "O que existe no banco hoje".
4. Escreva a entrega em `docs/05-entregas/` no mesmo formato das anteriores: o
   que foi construído, o que ficou de fora, as lacunas e as decisões tomadas ao
   implementar.
5. Marque o item 3 da ordem no ROADMAP como feito, com data.
6. Rode `graphify update .`.

## Regra de trabalho

A de sempre: veja o vermelho antes do verde, o Gherkin é a fonte de verdade, e
decisão estrutural vira ADR antes de virar código. Se a implementação
contradisser uma ADR aceita, escreva uma ADR nova que a supere. Não edite a
antiga.
