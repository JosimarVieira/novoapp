---
tipo: sdd
modulo: finance
status: escrito
atualizado_em: 2026-09-05
adrs:
  - ADR-0010
  - ADR-0011
  - ADR-0019
---

# SDD — Módulo `finance`

## Responsabilidade

Lançamentos, categorias, contas, estorno. Não fala com canal nenhum — só é
chamado por `conversation` (chat) e, na Etapa 4+, pelo REST do Vue (mesma
camada de serviço, regra 4 do CLAUDE.md).

## Escopo desta versão (Etapa 1)

Um único caso de uso: `registrarDespesa(householdId, memberId, categoryId,
amountCents, sourceMessageId)`.

Duas correções de 2026-09-05, ao implementar. **`sourceMessageId` entrou na
assinatura**: o fluxo abaixo já exigia `source_message_id` gravado — é o que
torna a Etapa 5 mensurável —, mas a assinatura desenhada aqui não tinha por onde
recebê-lo. **O nome do método em código é `registerExpense`**, não
`registrarDespesa`: identificador em inglês é regra sem exceção no CLAUDE.md, e
o português fica no Gherkin, na ADR e no comentário. O nome `registrarDespesa`
continua valendo onde ele de fato é português — a *tool* declarada ao LLM
([ADR-0004](../01-adr/0004-interpretacao-por-function-calling-com-politica-de-confianca.md)), que é dado enviado ao modelo, não identificador.

Sem conta explícita no parâmetro nesta versão — resolve sozinho, na ordem:

1. `household_membership.default_account_id` do member, se preenchido
   ([ADR-0019](../01-adr/0019-conta-padrao-por-membro.md));
2. senão, a única conta `WALLET` do household (é a única conta que existe
   neste ponto — household novo só tem a `WALLET` implícita).

**Não cobre ainda**: cartão/fatura ([ADR-0011](../01-adr/0011-cartao-de-credito-e-fatura.md) — só entra quando um teste
real precisar de conta tipo `CARD`, sem previsão de etapa), estorno/edição
([ADR-0012](../01-adr/0012-edicao-de-lancamento-entre-membros.md) — Etapa 2, cenário `@etapa2` "Desfazer"), metas
([ADR-0017](../01-adr/0017-meta-financeira.md)), múltiplas contas escolhidas por nome (`conta` na tool do
`nlu` fica como parâmetro opcional não usado ainda).

## Depende de

`identity` — contexto de tenant (`householdId`/`memberId` já resolvidos
antes de chegar aqui), e o evento `HouseholdCreated` (observado, não
chamado) pra criar a conta `WALLET` implícita — decisão já registrada no
`sdd-modulo-identity.md`: `identity` nunca escreve direto em `account`,
`finance` que observa o evento e escreve.

## Estrutura interna proposta

```
finance/
  FinanceService                -- registrarDespesa(householdId, memberId, categoryId, amountCents)
  AccountResolver                -- household_membership.default_account_id -> WALLET do household
  HouseholdCreatedListener       -- @Observes HouseholdCreated -> cria account WALLET implicita
  CategoryQueryService           -- lista categorias de despesa do household (lido por nlu)
```

## Fluxo

1. `conversation` chama `FinanceService.registrarDespesa(...)`.
2. `AccountResolver` decide a conta, na ordem acima.
3. Grava `transaction` (`kind=EXPENSE`, `source=CHAT`, `source_message_id`
   apontando pro `inbound_message` original — rastreabilidade da Etapa 5).
4. Devolve o `transaction` criado pra `conversation` formatar o recibo.

Em paralelo, sem relação com o fluxo acima: `HouseholdCreatedListener`
reage a `identity` publicar `HouseholdCreated(householdId)` e cria a conta
`WALLET` implícita — é o que faz `AccountResolver` sempre achar uma conta,
mesmo pro primeiro lançamento do household.

## Erros

Falha ao gravar `transaction` → propaga erro pra `conversation`, que
converte em recibo de erro no chat (nunca silêncio).

## Testes

- ArchUnit: `finance` não importa `channel`, `nlu`, `conversation`,
  `shopping`, `tasks` — só `identity`.
- Cenários `@etapa1` de `financas-lancamento-por-chat.feature`, mais o
  teste de vazamento de tenant que o próprio ROADMAP da Etapa 1 exige.
- O teste de vazamento cobre três formas de errar, não uma: ler o household
  errado, usar categoria de outro household, e passar um `householdId` diferente
  do que está no contexto resolvido — este último é recusado pelo `WITH CHECK`
  da policy, não por validação em Java.

## Gatilhos de revisão

Etapa 2: estorno (`desfazer`) e edição entre membros ([ADR-0012](../01-adr/0012-edicao-de-lancamento-entre-membros.md)) entram
aqui. Quando cartão for testado de verdade: `AccountResolver` ganha a
lógica de fatura ([ADR-0011](../01-adr/0011-cartao-de-credito-e-fatura.md)).
