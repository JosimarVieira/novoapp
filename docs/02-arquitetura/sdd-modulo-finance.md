---
tipo: sdd
modulo: finance
status: escrito
atualizado_em: 2026-09-08
adrs:
  - ADR-0010
  - ADR-0011
  - ADR-0012
  - ADR-0016
  - ADR-0019
  - ADR-0023
  - ADR-0024
  - ADR-0025
  - ADR-0026
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
real precisar de conta tipo `CARD`, sem previsão de etapa), metas
([ADR-0017](../01-adr/0017-meta-financeira.md)), múltiplas contas escolhidas por nome (`conta` na tool do
`nlu` fica como parâmetro opcional não usado ainda).

## O que a Etapa 2a acrescentou (2026-09-07)

**`registerExpense` ganhou `description`** ([ADR-0023](../01-adr/0023-descricao-de-lancamento-extraida-pelo-llm.md)).
Nulo é o caso normal, nunca um erro: descrição ausente jamais vira pergunta. Sem
migration — a coluna já existia.

**`reverseLatest` — o `desfazer`** ([ADR-0025](../01-adr/0025-desfazer-precedencia-e-escopo.md)).
Alvo: o lançamento mais recente do **household inteiro** ainda não estornado, de
qualquer membro ([ADR-0012](../01-adr/0012-edicao-de-lancamento-entre-membros.md)),
sem janela de tempo. O repositório não filtra por `created_by_member_id` de
propósito — filtrar pelo remetente seria a implementação escolhendo em silêncio
um comportamento mais restrito do que a ADR-0012 já concedeu, que é exatamente o
que a ADR-0025 existe para impedir. Marca `reversed_at` e `reversed_by_member_id`;
nunca apaga.

O desempate é por `created_at`, e não por `occurred_on`: a data do lançamento é
do dia, e dois lançamentos do mesmo dia empatariam. "Mais recente" aqui é "o
último que entrou".

**`CategoryService` — criação de categoria confirmada** (ADRs
[0024](../01-adr/0024-categoria-sugerida-por-texto-livre.md) e
[0026](../01-adr/0026-hierarquia-na-criacao-de-categoria-por-chat.md)). Não
pergunta nada e não decide se deve criar; só cria o que já foi confirmado. A
ordem da ADR-0026: procura o pai pelo nome, cria como raiz se não existir, e
pendura o filho nele.

**`CategoryView` ganhou a hierarquia.** A ADR-0026 aponta a ausência dela como o
furo que impedia o chat de criar subcategoria: sem saber quem é raiz e quem é
filha, nem o contexto do modelo nem a validação de um nível tinham como existir.

### Duas leituras que a implementação precisou fixar

**A busca do pai varre a árvore inteira, não só as raízes.** A ADR-0026 diz
"procura `categoria_pai` por nome entre as categorias-raiz do household" e, duas
frases depois, "se o nome resolvido para `categoria_pai` já for, ele mesmo, uma
subcategoria (…), a criação é recusada". As duas não fecham: procurando só entre
raízes, um pai que é subcategoria nunca seria encontrado, e a recusa jamais
dispararia — "rodízio de pizza dentro de restaurante" criaria uma raiz
"Restaurante" homônima em silêncio. Vale a segunda frase, que é a que tem
cenário escrito: a busca é na árvore toda, e achar uma subcategoria recusa.

**A criação é idempotente.** A categoria confirmada normalmente não existe — se
existisse, o enum da tool teria batido e não haveria pergunta nenhuma. Mas duas
confirmações da mesma pendência, ou uma corrida entre dois membros, não podem
virar violação de índice único no meio de um lançamento.

## Depende de

`identity` — contexto de tenant (`householdId`/`memberId` já resolvidos
antes de chegar aqui), e o evento `HouseholdCreated` (observado, não
chamado) pra criar a conta `WALLET` implícita — decisão já registrada no
`sdd-modulo-identity.md`: `identity` nunca escreve direto em `account`,
`finance` que observa o evento e escreve.

## Estrutura interna proposta

```
finance/
  FinanceService            -- registerExpense(...) e reverseLatest(...)
  CategoryService           -- createExpenseCategory(nome, categoria_pai) -> CategoryCreation
  CategoryCreation          -- selado: Created | ParentIsSubcategory (recusa nomeada, nao excecao)
  AccountResolver           -- household_membership.default_account_id -> WALLET do household
  HouseholdCreatedListener  -- @Observes HouseholdCreated -> cria account WALLET implicita
  CategoryQueryService      -- categorias de despesa com hierarquia (lido por nlu)
  RegisteredExpense, ReversedExpense, CategoryView
```

A recusa de hierarquia é **resultado, não exceção**: o cenário "Correção livre
não pode criar subcategoria de subcategoria" termina em pergunta nova, não em
erro genérico, e modelar como `throw` obrigaria `conversation` a inspecionar
mensagem de exceção para escolher o texto.

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
- Cenários `@etapa1` e `@etapa2` de `financas-lancamento-por-chat.feature`, mais
  o teste de vazamento de tenant que o próprio ROADMAP da Etapa 1 exige.
- O teste de vazamento cobre três formas de errar, não uma: ler o household
  errado, usar categoria de outro household, e passar um `householdId` diferente
  do que está no contexto resolvido — este último é recusado pelo `WITH CHECK`
  da policy, não por validação em Java.
- `CategoryHierarchyTest` cobre o limite de um nível pelo serviço direto, que é
  o teste que a [ADR-0016](../01-adr/0016-subcategoria.md) exigiu e a ADR-0026
  mandou cobrir nos dois caminhos de entrada — o caminho do chat está no cenário
  `@etapa2` correspondente.

## Gatilhos de revisão

- **Edição de campo com histórico** ([ADR-0012](../01-adr/0012-edicao-de-lancamento-entre-membros.md),
  tabela `transaction_edit`) continua fora: a Etapa 2a implementou o estorno, que
  é o outro mecanismo da mesma ADR, mas nenhum cenário desta etapa corrige valor
  ou categoria de um lançamento já gravado. Entra com a tela de correção, Etapa 4.
- **Etapa 5**: a ADR-0025 não fixou janela de tempo para o `desfazer`. Se
  estornar um lançamento de dias atrás surpreender na prática, é aqui que a
  janela entra.
- Quando cartão for testado de verdade: `AccountResolver` ganha a lógica de
  fatura ([ADR-0011](../01-adr/0011-cartao-de-credito-e-fatura.md)).
