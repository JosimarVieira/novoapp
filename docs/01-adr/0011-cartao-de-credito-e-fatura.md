---
tipo: adr
numero: 11
status: aceita
data: 2026-08-31
modulos:
  - banco
  - finance
  - nlu
  - roadmap
depende_de:
  - ADR-0003
  - ADR-0010
supera: []
superada_por:
---

# ADR-0011 — Cartão de crédito e fatura no domínio novo

- **Impacta**: banco (`account`, `transaction`, nova tabela `invoice`),
  módulo de finanças, interpretação por chat (parsing de conta/cartão),
  Etapa 1 do roadmap; resolve [decisão aberta #10](../DECISOES-ABERTAS.md); depende de [ADR-0003](0003-isolamento-multi-tenant-por-household.md),
  [ADR-0010](0010-reescrever-modulo-financeiro-em-vez-de-reaproveitar-legado.md)
- **Termos**: [Conta, Fatura](../00-produto/glossario.md#finanças)

## Contexto

`docs/02-arquitetura/modelo-de-dados.md` já declara `account.type` incluindo
`CARD`, mas não modela fatura — não há como saber a qual fatura um
lançamento em cartão pertence. Essa lacuna é a mesma coisa que a decisão
aberta #10 ("Contas entram na Etapa 1 ou depois? Complica o parsing —
'mercado 50' não diz de qual conta saiu"): sem fatura modelada, um cartão é
uma conta comum, e `mercado 50` num cartão não tem como fechar corretamente
no extrato do mês certo.

O legado (`legacy/juntosnocontrole/server`) resolveu esse problema uma vez,
em produção anterior, sem entidade `CreditCard` separada:

- Cartão é um `Account` com `type = CREDIT_CARD` e três campos extras:
  `creditLimit`, `closingDay`, `dueDay`.
- `CreditCardInvoice` (fatura) é uma entidade própria, ligada a `Account` por
  `account_id`, com `referenceDate` (primeiro dia do mês de referência),
  `closingDate`, `dueDate`, `amount` e `status`
  (`OPEN`/`CLOSED`/`PAID`).
- `Transaction` tem `invoice_id` opcional (preenchido só quando o lançamento
  é em cartão), mais `installmentNumber`/`totalInstallments`/`installmentId`
  para parcelamento e `splitGroupId` para rateio de um mesmo lançamento
  entre categorias.
- A fatura do mês é obtida ou criada sob demanda (`getOrCreateInvoice`,
  `TransactionResource.java:335-377`), calculando `referenceDate` a partir de
  `account.closingDay` e buscando via
  `CreditCardInvoiceRepository.findByAccountAndReferenceDate`.
- Pagamento de fatura (`InvoiceResource.payInvoice`, linhas 85-157) não tem
  entidade própria: gera dois `Transaction` comuns — uma `Expense` na conta
  pagadora, uma `Income` na conta do cartão — e marca `invoice.status =
  PAID`.

A mesma auditoria que validou esse modelo encontrou o problema que a [ADR-0003](0003-isolamento-multi-tenant-por-household.md)
existe para prevenir: `CreditCardInvoice` não tinha `family_id` próprio —
família só era alcançável por join até `account`. Ninguém decidiu deixar essa
tabela desprotegida; ela só nunca precisou da coluna até precisar (mesmo
padrão em `Subcategory`, `Attachment`, `TransactionGoalLink`). Sob RLS, uma
tabela sem `household_id` direto não tem o que uma policy simples possa
filtrar.

## Decisão

Porta-se a modelagem de cartão e fatura do legado para o schema novo, com
duas adaptações obrigatórias em relação ao original: `household_id` direto
em toda tabela nova (nunca só alcançável por join), e `account` entrando já
na Etapa 1 com uma conta padrão implícita por household, para que `mercado
50` continue funcionando sem o usuário precisar nomear a conta na maioria
dos casos.

```text
account
  id, household_id, name, type (WALLET|BANK|CARD), archived_at,
  closing_day (nullable),   -- só usado quando type = CARD
  due_day (nullable),
  credit_limit_cents (nullable)

invoice
  id, household_id, account_id,
  reference_month,        -- primeiro dia do mês de referência
  closing_date, due_date,
  amount_cents, status (OPEN|CLOSED|PAID)
  UNIQUE (account_id, reference_month)

transaction
  ... (campos já definidos em docs/02-arquitetura/modelo-de-dados.md)
  invoice_id (nullable),           -- preenchido quando account.type = CARD
  installment_number (nullable),
  installment_count (nullable),
  installment_group_id (nullable), -- agrupa as parcelas de uma mesma compra
  split_group_id (nullable)        -- agrupa o rateio de um mesmo lançamento
```

Household padrão ganha uma conta implícita (`WALLET`) criada no onboarding,
para que a Etapa 1 funcione sem exigir que o usuário declare conta em toda
mensagem — a ambiguidade de "qual conta" só aparece quando há mais de uma, e
a resolução de fatura (`reference_month` a partir de `closing_day`) só entra
em jogo quando a conta envolvida é `CARD`.

Pagamento de fatura permanece dois `transaction` comuns (uma `EXPENSE` na
conta pagadora, uma `INCOME` na conta do cartão) em vez de uma entidade de
pagamento própria — reaproveitando o padrão validado no legado.

## Alternativas consideradas

### A. Entidade `CreditCard` separada de `Account`
Descartada: o legado nunca precisou dela — cartão é só uma `Account` com
campos extra nullable — e criar entidade própria duplicaria campos comuns
(nome, arquivamento, `household_id`) sem ganho, já que todo lançamento trata
cartão e conta corrente pela mesma interface (origem/destino do dinheiro).

### B. Pagamento de fatura como entidade própria (`Payment`), em vez de dois lançamentos espelhados
Descartada: o padrão de dois `Transaction` já foi validado em produção
anterior e mantém "todo lançamento é reversível" (regra 7 do CLAUDE.md) de
graça — desfazer o pagamento é desfazer os dois lançamentos como qualquer
outro. Uma entidade `Payment` própria exigiria ensinar `desfazer` um caminho
inteiramente novo só para esse caso, sem benefício correspondente.

### C. `household_id` só em `account`, alcançável por join em `invoice` (como o legado fazia)
Descartada por [ADR-0003](0003-isolamento-multi-tenant-por-household.md) e pela própria falha que a auditoria do legado
encontrou: `CreditCardInvoice` sem `household_id` direto é exatamente a
lacuna que RLS não consegue fechar sem policy por subquery.
`household_id` redundante em toda tabela filha — mesmo quando alcançável por
join — é desnormalização deliberada, não acidental, e é o que torna a policy
de RLS escrevível como filtro direto.

## Consequências

### Positivas
- Resolve a [decisão aberta #10](../DECISOES-ABERTAS.md): conta existe desde a Etapa 1, com conta
  padrão implícita por household, então `mercado 50` funciona sem o usuário
  precisar nomear a conta no caso comum — a pergunta só aparece quando há
  mais de uma conta ou o lançamento é reconhecido como cartão.
- `mercado 50` num cartão resolve a fatura de destino automaticamente via
  `closing_day` + `reference_month`, sem exigir confirmação no caminho de
  alta confiança.
- Reaproveita modelagem já testada em produção anterior (parcelamento,
  rateio, pagamento espelhado) em vez de desenhar do zero um domínio que já
  foi acertado uma vez.

### Negativas
- `invoice.household_id` redundante em relação a `account.household_id`
  exige garantir consistência entre os dois (mesma família nas duas pontas)
  — se não for garantido por FK composta ou trigger, é um vetor de bug
  diferente do que RLS resolve: RLS impede vazamento entre households, não
  impede inconsistência dentro de um household só.
- Parcelamento (`installment_group_id`) e rateio (`split_group_id`) não têm
  entidade própria — são agrupamento por identificador solto em
  `transaction`, sem integridade referencial. Nada impede um
  `installment_group_id` órfão ou com parcelas apontando para contas
  diferentes. Essa fragilidade é herdada do legado junto com o resto do
  modelo, não resolvida por esta ADR.
- ~~O mecanismo de fechamento periódico de fatura... falta decidir~~ —
  resolvido por [ADR-0014](0014-fechamento-de-fatura-sob-demanda.md) (2026-09-04): calculado sob demanda, sem
  job/trigger, mesmo padrão do `getOrCreateInvoice` do legado.
- ~~`docs/02-arquitetura/modelo-de-dados.md` fica desatualizado em relação a
  esta ADR até ser revisado~~ — corrigido: o schema de Finanças já tem
  `invoice` e os campos novos de `account` e `transaction`.

## Gatilhos de revisão

Se a Etapa 5 mostrar que quase ninguém lança despesa de cartão pelo chat
(porque resolver "qual fatura" errado tem custo alto e a confiança do parser
fica sempre baixa demais para executar direto), avaliar se cartão vira fluxo
web-only e sai do caminho de interpretação do LLM.
