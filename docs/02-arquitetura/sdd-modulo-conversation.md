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
  ConversationOrchestrator     -- processar(InboundMessage, ResolvedContext)
  ReceiptFormatter              -- formata texto do recibo de sucesso/erro
```

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
5. Envia via `OutboundMessagePort`.
6. Qualquer coisa diferente de confiança alta → recibo de erro genérico,
   sem pergunta específica (ver limitação acima).

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
