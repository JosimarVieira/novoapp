---
tipo: sdd
modulo: conversation
status: escrito
atualizado_em: 2026-09-05
adrs:
  - ADR-0004
---

# SDD — Módulo `conversation`

## Responsabilidade

Política de confiança, `PendingAction`, curto-circuito de confirmação,
formatação de recibo ([ADR-0004](../01-adr/0004-interpretacao-por-function-calling-com-politica-de-confianca.md)). Não decide regra de negócio de domínio —
só orquestra `nlu` → confiança → módulo de domínio → recibo.

## Escopo desta versão (Etapa 1)

Cobre só confiança alta, decisão imediata, sem pergunta nenhuma no meio —
os cenários `@etapa1` do `financas-lancamento-por-chat.feature` nunca geram
uma pergunta de volta pro usuário durante o registro.

**Não cobre ainda** (decidido em 2026-09-05 que fica pra Etapa 2, junto com
os cenários `@etapa2`):
- `PendingAction` — não existe ainda nenhum caso que precise perguntar e
  esperar resposta; a tabela é a mesma do modelo de dados, só não tem
  código que escreve nela nesta etapa;
- curto-circuito de confirmação (`sim`, `não`, `1`, `desfazer`) — não tem
  sentido sem `PendingAction` pra confirmar;
- confiança média/baixa — nesta versão, qualquer coisa que não seja
  confiança alta vira só um recibo de erro genérico ("não entendi, tente de
  novo"), não uma pergunta específica.

## Depende de

- `nlu` — interpretação.
- `finance` — executar `registrarDespesa` quando a confiança é alta.
- `identity` — nome do membro/household pra formatar o recibo, e
  `OutboundMessagePort` pra enviar (aresta já registrada no
  `sdd-modulo-identity.md`).

## Estrutura interna proposta

```
conversation/
  ConversationOrchestrator     -- process(InboundMessage, ResolvedContext, canal, externalId)
  ReceiptFormatter              -- formata texto do recibo de sucesso/erro
  ProcessingOutcome             -- o que aconteceu, devolvido pra channel registrar
```

Três coisas decididas em 2026-09-05, ao implementar:

**O desfecho é devolvido, não escrito.** `inbound_message.status` é de `channel`,
e `conversation` não pode importar `channel` (sdd-visao-geral.md). Então
`process()` devolve um `ProcessingOutcome(resultado, confiança, intenção)` e é
`channel` quem grava.

**`process`, não `processar`.** Identificador em inglês é regra sem exceção no
CLAUDE.md. Mesma correção feita em `sdd-modulo-finance.md`.

**O orquestrador não é transacional.** A chamada ao LLM tem cauda de latência
imprevisível ([ADR-0005](../01-adr/0005-idempotencia-de-mensagens-recebidas.md)) e não pode segurar conexão de banco aberta. Cada
passo — ler categorias, gravar lançamento, atualizar o log — abre a própria
transação curta.

## Fluxo

1. `channel` já resolveu o contexto (via `identity`) e repassa
   `InboundMessage` + `ResolvedContext(householdId, memberId)`.
2. `ConversationOrchestrator.processar()` chama
   `nlu.interpretar(householdId, rawText)`.
3. Confiança alta → chama `finance.registrarDespesa(householdId, memberId,
   categoryId, amountCents)`.
4. `ReceiptFormatter` monta o texto de sucesso (valor, categoria, "responda
   desfazer" — mesmo que `desfazer` ainda não funcione nesta versão; o
   texto do recibo já antecipa a Etapa 2, mas a ação em si só existe lá).
   O household é nomeado **só** quando a pessoa tem mais de um vínculo, que é o
   que a [ADR-0007](../01-adr/0007-pessoa-em-multiplos-households.md) exige para tornar erro de contexto visível — e o que ela
   proíbe de aparecer para quem tem uma família só.
5. Envia via `OutboundMessagePort`.
6. Qualquer coisa diferente de confiança alta → recibo de erro genérico,
   sem pergunta específica (ver limitação acima).

## O recibo não nomeia a conta (decidido em 2026-09-05)

A [ADR-0019](../01-adr/0019-conta-padrao-por-membro.md) afirma de passagem, ao listar as mitigações dela, que "recibo
sempre nomear a conta usada" seria "regra existente". Essa regra não existe em
nenhum outro documento: nem no glossário (verbete `Recibo`), nem no
`financas-lancamento-por-chat.feature`, nem neste SDD. O recibo da Etapa 1 segue
o Gherkin — valor, categoria e como desfazer — e não nomeia conta: nesta etapa só
existe uma conta por household, e `default_account_id` não tem como ser
preenchido antes da Etapa 4, então nomear só acrescentaria ruído sem tornar
nenhum erro visível.

Quando houver mais de uma conta escolhível, isso vira decisão explícita — e
provavelmente uma correção de registro na ADR-0019.

## Erros

- Falha em `finance.registrarDespesa` → recibo de erro no chat, nunca
  silêncio (regra geral do `sdd-visao-geral.md`).

## Testes

- ArchUnit: `conversation` não importa `channel`, `shopping`, `tasks`.
- Cenários `@etapa1` de `financas-lancamento-por-chat.feature`.

## Gatilhos de revisão

Etapa 2: `PendingAction`, curto-circuito de confirmação e as perguntas de
ambiguidade/categoria-ausente/valor-ausente entram aqui — é o módulo que
mais cresce na Etapa 2.
