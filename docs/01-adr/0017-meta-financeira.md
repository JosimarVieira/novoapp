---
tipo: adr
numero: 17
status: aceita
data: 2026-09-04
modulos:
  - banco
  - finance
  - glossario
depende_de:
  - ADR-0003
supera: []
superada_por:
---

# ADR-0017 — Meta financeira com progresso calculado, nunca armazenado

- **Impacta**: banco (`financial_goal`, `goal_transaction_link`), `finance`,
  glossário; encerra a decisão aberta sobre metas financeiras (registrada
  e removida do `DECISOES-ABERTAS.md` em 2026-09-04 — número #17 já usado
  antes por outro item, não usar como referência: ver [ADR-0014](0014-fechamento-de-fatura-sob-demanda.md), que resolveu
  o #17 original); depende de [ADR-0003](0003-isolamento-multi-tenant-por-household.md)

## Contexto

O legado tem `FinancialGoal` (valor-alvo, prazo, `accumulatedAmount`
armazenado) e `TransactionGoalLink` (N:N entre lançamento e meta) — usada e
validada em produção anterior, mas fora da reescrita ([ADR-0010](0010-reescrever-modulo-financeiro-em-vez-de-reaproveitar-legado.md)) sem
decisão, registrada como decisão aberta em `DECISOES-ABERTAS.md` (já
removida de lá, resolvida por esta ADR). O autor confirmou em 2026-09-04:
meta financeira precisa estar na modelagem, não é opcional.

O legado guarda `accumulatedAmount` como coluna própria, atualizada
manualmente a cada vínculo. Isso é uma segunda fonte de verdade: a regra 7 do
CLAUDE.md ("todo lançamento é reversível") significa que um lançamento
vinculado a uma meta pode ser estornado depois — se `accumulatedAmount` não
for decrementado em todo caminho de estorno, a meta mostra progresso que já
não existe, e ninguém percebe até conferir a soma manualmente.

## Decisão

Duas tabelas novas:

```text
financial_goal
  id, household_id, name, description (nullable),
  target_amount_cents, start_date, end_date (nullable),
  created_by_member_id, archived_at

goal_transaction_link
  id, household_id, goal_id, transaction_id
  UNIQUE (goal_id, transaction_id)
```

Progresso da meta **nunca é armazenado** — é sempre `SUM(transaction.amount_cents)`
dos lançamentos vinculados com `reversed_at IS NULL`, calculado em tempo de
leitura. O mesmo princípio que já vale para `invoice.status` (recalculado a
partir de `closing_day` e da data atual, nenhum job) — nenhuma segunda fonte
de verdade que possa dessincronizar do estorno.

`goal_transaction_link` é N:N, não uma FK direta em `transaction`: um
lançamento pode contribuir para mais de uma meta ao mesmo tempo (ex.: uma
economia que conta tanto para "viagem" quanto para "reserva de emergência").

## Alternativas consideradas

### A. Armazenar `accumulated_amount_cents` em `financial_goal`, como o legado
Descartada: cria segunda fonte de verdade que precisa ser mantida em todo
caminho de escrita — vínculo novo, desvínculo, estorno de lançamento
vinculado. Um caminho esquecido (ex. `desfazer` implementado antes da meta
existir, ou vice-versa) deixa o número errado silenciosamente, sem erro
visível. Calcular na leitura elimina a classe de bug inteira, ao custo de
uma agregação a mais por leitura — aceitável no volume da validação.

### B. `goal_id` nullable direto em `transaction`, sem tabela de vínculo
Descartada: força um lançamento a contribuir para no máximo uma meta.
`split_group_id` já existe em `transaction` para modelar rateio entre
lançamentos-espelho — meta é outro eixo de agrupamento independente, e o
próprio legado já usava tabela de vínculo (`TransactionGoalLink`) por esse
motivo, não por acidente.

## Consequências

### Positivas
- Progresso sempre consistente com estorno — não existe caminho de código
  que precise "lembrar" de atualizar um contador.
- N:N permite um lançamento contar para mais de uma meta sem redesenho
  futuro.
- Mesmo princípio de "derivado, não armazenado" já usado em `invoice.status`
  — consistência de estilo no módulo financeiro.

### Negativas
- Meta com muitos lançamentos vinculados soma em toda leitura — custo de
  leitura crescente com o histórico, não mitigado nesta ADR (paginação ou
  cache ficam para quando o volume real justificar).
- Duas tabelas novas para uma feature que não é o elo lista→despesa
  (CLAUDE.md) — custo de RLS, unicidade e teste de isolamento de tenant que
  esta ADR está pedindo antes de haver dado de uso real.

## Gatilhos de revisão

Se a soma em tempo de leitura aparecer como consulta lenta em `finance` com
household de uso real e histórico longo, considerar coluna de cache
recalculada por job, mantendo a soma sob demanda como fonte de verdade de
auditoria.
