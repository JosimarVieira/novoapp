---
tipo: adr
numero: 10
status: aceita
data: 2026-08-31
modulos:
  - banco
  - finance
  - roadmap
depende_de:
  - ADR-0001
  - ADR-0003
  - ADR-0007
supera: []
superada_por:
---

# ADR-0010 — Reescrever módulo financeiro em vez de reaproveitar o legado

- **Impacta**: banco, módulo de finanças, Etapas 1-3 do roadmap; decisão de
  reaproveitamento de `legacy/`; depende de [ADR-0001](0001-monolito-modular-em-quarkus.md), [ADR-0003](0003-isolamento-multi-tenant-por-household.md), [ADR-0007](0007-pessoa-em-multiplos-households.md)

## Contexto

`legacy/juntosnocontrole/server` é um projeto anterior, só finanças, em
Quarkus + Panache, sob avaliação de reaproveitamento (ver seção "Sobre
`legacy/`" do CLAUDE.md). Uma auditoria de código levantou os seguintes fatos:

- 13 repositórios, 72 métodos/usos de consulta ao todo.
- 60 desses 72 não filtram por família na própria query (8 métodos nomeados,
  4 usos inline e 51 chamadas built-in do Panache sem predicado). Parte é
  coberta por checagem manual no código chamador, mas 10 pontos não têm
  nenhuma checagem — incluindo dois endpoints de `delete` que apagam por
  qualquer `id` sem nunca carregar `family`.
- 4 das 13 entidades (`Subcategory`, `Attachment`, `TransactionGoalLink`,
  `CreditCardInvoice`) não têm coluna de família — só alcançável por join
  indireto até a entidade pai.
- Retrofit de Row Level Security ([ADR-0003](0003-isolamento-multi-tenant-por-household.md)) sobre esse código — adicionar
  `household_id` às 4 entidades que não têm, escrever policy para as 13
  tabelas, montar o mecanismo de `SET LOCAL` por request, isolar as rotas que
  hoje rodam sem contexto de tenant (login por e-mail, aceite de convite por
  token, `DataMigrationService`) — foi estimado em 6,5 a 7,5 dias.

Esse número não é o único retrofit necessário, e é o menor dos três:

1. **RLS** (acima), 6,5-7,5 dias, sobre os mesmos 13 repositórios / 72
   queries.
2. **Identidade multi-household** ([ADR-0007](0007-pessoa-em-multiplos-households.md)): o legado tem `User.family_id`
   direto — uma pessoa pertence a exatamente uma família. [ADR-0007](0007-pessoa-em-multiplos-households.md) substitui
   isso por `household_membership` + `active_household_id`, o que muda a
   resolução de contexto em cada um dos mesmos 13 repositórios e 72 queries
   que o retrofit de RLS já vai tocar. Este segundo refactor não foi
   estimado.
3. **Extração de camada de serviço**: a regra de negócio de fatura
   (`getOrCreateInvoice`, cálculo de `referenceDate` a partir de
   `closingDay`) mora dentro de `TransactionResource`, um controller REST
   (`legacy/juntosnocontrole/server/src/main/java/com/juntosnocontrole/resource/TransactionResource.java:335-377`).
   A regra 4 do CLAUDE.md exige que bot e web consumam a mesma camada de
   serviço de domínio — o legado não tem essa camada. Extrair regra de 13
   resources é o terceiro refactor, também não estimado.

Os três refactors são estruturais, concorrentes, e incidem sobre a mesma
superfície de código ao mesmo tempo — nenhuma auditoria mediu o custo
somado, nem o de regressão sobre o que ela explicitamente não cobriu (não há
suíte de teste auditada). Além disso, o legado foi escrito para REST síncrono
de formulário web; o caminho crítico do produto novo é mensagem assíncrona
idempotente por chat ([ADR-0002](0002-telegram-primeiro-whatsapp-depois.md), [ADR-0005](0005-idempotencia-de-mensagens-recebidas.md)) — um modelo de concorrência e de
erro diferente do que o código foi desenhado para tratar. O legado é Quarkus + Panache e a [ADR-0001](0001-monolito-modular-em-quarkus.md) decidiu Quarkus, então **não há custo de porte de framework entre os dois**. O que não se herda é o *uso* de Panache sem RLS e sem camada de serviço (ver Alternativa A abaixo), não o framework em si.

O que tem valor real no legado é conhecimento de domínio, não a
implementação: `closingDay`/`dueDay` em `Account`, cálculo de
`referenceDate` da fatura, rastreio de parcelamento (`installmentNumber`,
`totalInstallments`, `installmentId`), rateio por `splitGroupId`, e
pagamento de fatura modelado como dois lançamentos espelhados (`Expense` na
conta pagadora, `Income` na conta do cartão) em vez de uma entidade de
pagamento própria. Esse é exatamente o ponto em aberto no modelo novo (ver
[ADR-0011](0011-cartao-de-credito-e-fatura.md)) — já foi decidido e validado uma vez em produção anterior.

**Nomeado aqui para não virar perda silenciosa**: das 13 entidades do
legado, três carregavam funcionalidade que esta ADR, na versão original,
não decidia portar nem descartar. Resolvido em 2026-09-04, depois desta
ADR ser aceita: `FinancialGoal`/`TransactionGoalLink` (meta financeira) virou
[ADR-0017](0017-meta-financeira.md), `Subcategory` virou [ADR-0016](0016-subcategoria.md) — ambas aceitas. `Attachment`
(anexo de arquivo a um lançamento) segue tratado como [decisão aberta #13](../DECISOES-ABERTAS.md),
ainda sem ADR própria. Nenhuma das três é o elo lista→despesa que é o
diferencial do produto (CLAUDE.md) — por isso nenhuma bloqueou esta ADR nem
a Etapa 1-3.

## Decisão

O módulo financeiro é reescrito do zero em Java 21 + Quarkus ([ADR-0001](0001-monolito-modular-em-quarkus.md)),
seguindo [ADR-0003](0003-isolamento-multi-tenant-por-household.md) (RLS desde a primeira migration) e [ADR-0007](0007-pessoa-em-multiplos-households.md)
(`household_membership` desde o schema inicial). Do legado, porta-se
**conhecimento de domínio e funcionalidade validada**, nunca o código
Quarkus/Panache em si: cartão e fatura completos (campos, cálculos e a
lógica de `getOrCreateInvoice`, adaptados ao schema novo — ver [ADR-0011](0011-cartao-de-credito-e-fatura.md)).
Metas financeiras e subcategoria, decididas depois em [ADR-0017](0017-meta-financeira.md) e [ADR-0016](0016-subcategoria.md),
seguem o mesmo princípio desta ADR: porta-se o conhecimento de domínio do
legado, não o código. Nunca se porta
`DataMigrationService` nem o endpoint de `TRUNCATE` em `AdminResource` —
esses dois não são conhecimento de domínio, são utilitário administrativo
do legado, sem equivalente necessário no produto novo.

## Alternativas consideradas

### A. Reaproveitar o repositório legado, aplicando os três retrofits em cima do código existente
Descartada: os três refactors (RLS, identidade multi-household, extração de
camada de serviço) incidem sobre os mesmos 13 repositórios e 72 queries ao
mesmo tempo, e só um foi estimado (6,5-7,5 dias) — os outros dois, que tocam
exatamente a mesma superfície, não. Regressão sobre pontos que a auditoria
não cobriu (não há suíte de teste auditada) fica de fora da conta. Porte de
framework não entra no argumento: legado e projeto novo são ambos Quarkus
([ADR-0001](0001-monolito-modular-em-quarkus.md)). Os três refactors
concorrentes acima, sozinhos, sustentam descartar esta alternativa.

### B. Reescrever do zero sem portar nada do legado, nem a modelagem de cartão
Descartada: joga fora conhecimento de domínio caro — cartão e fatura são
exatamente o ponto que faltava no modelo novo, e o legado já errou e
acertou uma vez em produção anterior (parcelamento, rateio, pagamento
espelhado). Refazer esse conhecimento do zero custa mais do que portar as
entidades e a lógica de `getOrCreateInvoice`, que a auditoria já qualificou
como dias de trabalho, não semanas.

### C. Manter o legado rodando como serviço separado (strangler fig) e migrar aos poucos
Descartada nesta fase: o legado é REST síncrono de formulário; o produto
novo tem caminho crítico assíncrono por mensagem idempotente ([ADR-0005](0005-idempotencia-de-mensagens-recebidas.md)) —
os dois modelos de concorrência não convivem bem atrás do mesmo domínio.
Manter dois runtimes e (potencialmente) dois bancos para migrar depois tem
custo operacional maior que reescrever direto, considerando que não existe
cliente externo em produção ainda — o único motivo forte para strangler fig
(dado real que não pode parar) não se aplica.

## Consequências

### Positivas
- Elimina de uma vez os três refactors estruturais concorrentes (RLS,
  identidade multi-household, extração de camada de serviço) em vez de
  empilhá-los uns sobre os outros no mesmo código legado.
- Código nasce alinhado a [ADR-0001](0001-monolito-modular-em-quarkus.md), [ADR-0003](0003-isolamento-multi-tenant-por-household.md) e [ADR-0007](0007-pessoa-em-multiplos-households.md) desde a primeira
  migration, sem dívida de retrofit nem período em que o sistema roda sem
  isolamento garantido por RLS.
- Preserva o conhecimento de domínio mais caro do legado (cartão e fatura,
  [ADR-0011](0011-cartao-de-credito-e-fatura.md)) sem herdar a superfície de bugs de isolamento que a auditoria
  encontrou (60 dos 72 métodos de consulta sem filtro de família na query,
  10 sem nenhuma checagem em nenhum nível).

### Negativas
- Reescrever CRUD básico (`account`, `category`, `transaction`) que já
  existia funcionando no legado tem custo real, mesmo não sendo o gargalo da
  decisão.
- Esta ADR não entrega paridade funcional com o legado: metas financeiras e
  subcategoria ficam sem decisão (não sem uso — ver decisões abertas #17 e
  #18), então quem espera o mesmo conjunto de features do legado no dia um
  da Etapa 1 vai notar a ausência.
- Risco de reintroduzir, no código novo, os mesmos padrões de bug de
  isolamento do legado (os casos de IDOR sem checagem nenhuma) se a revisão
  de schema não for cuidadosa — RLS ([ADR-0003](0003-isolamento-multi-tenant-por-household.md)) é mecanismo automático e não
  depende de disciplina de query, mas não protege contra erro na modelagem
  do schema em si (ex.: esquecer `household_id` numa tabela nova).
- Não há suíte de teste do legado auditada nesta análise — a suíte de
  regressão do domínio novo começa do zero, sem herdar cobertura alguma.

## Gatilhos de revisão

Se portar o modelo de cartão e fatura (entidades + `getOrCreateInvoice`,
[ADR-0011](0011-cartao-de-credito-e-fatura.md)) ultrapassar a estimativa de dias e se aproximar da ordem de
semanas, reavaliar se vale a pena reescrever também essa parte do zero em
vez de adaptar o conhecimento portado do legado.
