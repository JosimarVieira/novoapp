---
tipo: adr
numero: 14
status: aceita
data: 2026-08-31
modulos:
  - banco
  - finance
depende_de:
  - ADR-0011
supera: []
superada_por:
---

# ADR-0014 — Fechamento de fatura calculado sob demanda, sem job periódico

- **Impacta**: banco (`invoice`), módulo de finanças; resolve
  [decisão aberta #17](../DECISOES-ABERTAS.md); depende de
  [ADR-0011](0011-cartao-de-credito-e-fatura.md)
- **Termos**: [Fatura](../00-produto/glossario.md#finanças)

## Contexto

A [ADR-0011](0011-cartao-de-credito-e-fatura.md) modelou `invoice` com
`status` (`OPEN`/`CLOSED`/`PAID`), mas deixou em aberto o mecanismo que muda
`status` de `OPEN` para `CLOSED` quando o dia de fechamento (`closing_day` da
`account`) passa — job/trigger periódico, ou cálculo sob demanda como no
legado (`getOrCreateInvoice`, que resolve `reference_date`/status na hora em
que alguém consulta a fatura, sem processo em background).

Nenhuma etapa do roadmap até a Etapa 7 depende de `invoice.status` estar
`CLOSED` de forma proativa — a Etapa 1 só precisa que `mercado 50` num cartão
resolva a fatura correta do mês (ADR-0011). O único cenário que exigiria
saber que uma fatura fechou *sem alguém perguntar* é notificação proativa
("sua fatura fecha amanhã"), que é escopo da Etapa 8, hoje sem data.

## Decisão

`invoice.status` é calculado sob demanda, no mesmo padrão do
`getOrCreateInvoice` do legado: toda vez que uma fatura é lida ou criada, o
status é recalculado a partir de `closing_day` e da data atual — nenhum job
ou trigger roda em background para manter `status` atualizado
proativamente.

## Alternativas consideradas

### A. Job periódico (ex.: rotina diária que varre contas e fecha fatura vencida)
Descartada por agora: exige infraestrutura de scheduler que nenhuma outra
parte do sistema usa ainda, para resolver um caso — notificação proativa de
fechamento — que só existe na Etapa 8, sem data prevista. Construir a
infraestrutura antes de haver funcionalidade que a use é especulação, não
necessidade.

### B. Trigger de banco (Postgres) disparado por passagem de `closing_day`
Descartada: Postgres não tem um jeito nativo de disparar algo só pela
passagem do tempo sem um agente externo (`pg_cron`, job do SO) chamando a
função — na prática vira a mesma alternativa A com um passo a mais, sem
ganho sobre calcular sob demanda.

## Consequências

### Positivas
- Zero infraestrutura nova: reaproveita exatamente o padrão já validado em
  produção anterior (`getOrCreateInvoice`), sem reescrever o que já
  funcionou.
- `invoice.status` nunca fica "errado" por atraso de job — é sempre
  recalculado na leitura, então não existe janela onde o dado está
  desatualizado.

### Negativas
- `invoice.status` só reflete a realidade quando alguém consulta a fatura —
  não há como consultar `WHERE status = 'CLOSED'` para "todas as faturas
  fechadas este mês" sem antes tocar em cada uma para forçar o recálculo.
  Relatório que dependa disso vai precisar de uma varredura, não de uma
  query direta.
- Esta ADR bloqueia implicitamente qualquer notificação proativa de
  fechamento de fatura até que o mecanismo de job seja desenhado — a Etapa 8
  não pode assumir que `invoice.status` já está correto sem tocar nela.

## Gatilhos de revisão

Quando a Etapa 8 (Proatividade) começar a ser desenhada, revisar esta ADR:
se lembrete de fechamento de fatura entrar no escopo, o mecanismo de job
(alternativa A) deixa de ser especulação e passa a ser necessidade real.
