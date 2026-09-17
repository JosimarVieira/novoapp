---
tipo: adr
numero: 31
status: aceita
data: 2026-09-16
modulos:
  - shopping
  - finance
  - conversation
depende_de: [ADR-0001, ADR-0005, ADR-0027]
supera: []
superada_por:
corrigida_em:
---

# ADR-0031 — O fechamento de compra é atômico dentro de `shopping`

- **Impacta**: a Etapa 3 do [ROADMAP](../../ROADMAP.md) — é o elo, e o
  diferencial do produto —, `shopping` (ganha `fecharCompra` e `list_checkout`),
  `finance` (é chamado de dentro da transação de `shopping`), `conversation`
  (continua sem transação própria), e o terceiro dos quatro testes obrigatórios
  da [estratégia de testes](../04-qualidade/estrategia-de-testes.md)

## Contexto

O cenário `Falha ao registrar a despesa não deixa a lista fechada`
([elo-fechamento-de-compra.feature](../03-specs/features/elo-fechamento-de-compra.feature))
exige que nenhum item mude de status e nenhuma despesa seja registrada quando o
lançamento falha. A `estrategia-de-testes.md` lista "atomicidade do fechamento
de compra" como o terceiro dos quatro testes que não podem faltar, com a
justificativa de que cobre o diferencial do produto.

**Hoje não existe lugar onde isso possa acontecer.** `ConversationOrchestrator`
é deliberadamente não-transacional: a chamada ao LLM tem cauda de latência
imprevisível ([ADR-0005](0005-idempotencia-de-mensagens-recebidas.md)) e não pode
segurar conexão de banco. Cada serviço de domínio abre a própria transação
curta, e duas chamadas seguidas do orquestrador são duas transações.

A regra de dependência já permite exatamente uma direção entre os dois módulos:
`shopping` pode depender de `finance`, `finance` nunca de `shopping` — travado
por ArchUnit desde a Etapa 2a.

## Decisão

`fecharCompra` é um método de **domínio**, em `shopping`, anotado
`@Transactional @HouseholdScoped`. Ele fecha os itens, grava o `list_checkout` e
chama `finance` para registrar a despesa **dentro da mesma transação**. Qualquer
falha derruba tudo.

`conversation` continua sem transação própria: chama esse método uma vez, depois
da interpretação, e formata o recibo com o que ele devolver. A atomicidade é
regra de domínio e mora no domínio.

## Alternativas consideradas

### A. Transação no orquestrador, abrangendo a interpretação
Descartada: seguraria uma conexão de banco durante a chamada ao LLM, que é
justamente o que a ADR-0005 e o desenho assíncrono do webhook evitam. Uma
indisponibilidade do provedor viraria pool de conexões esgotado.

### B. Transação no orquestrador, aberta só depois do LLM
Descartada, e é a mais próxima de ser aceitável. Dois problemas: põe em
`conversation` a regra "fechar compra é atômico", que é de domínio e não de
conversa — o REST da Etapa 4 teria de reimplementá-la, contra a regra 4 do
`CLAUDE.md`; e faz o orquestrador ser dono de transação em um fluxo e não nos
outros, inconsistência que convida a próxima pessoa a embrulhar tudo e a
reintroduzir a alternativa A por acidente.

### C. Compensação: fecha a lista, e reabre se a despesa falhar
Descartada: compensação existe para quando não há transação possível — fronteira
de processo, sistema externo. Aqui as duas escritas são no mesmo banco, na mesma
conexão. Trocar uma garantia por um retry é perder de graça.

### D. Evento assíncrono de `shopping` para `finance`
Descartada pelo mesmo motivo, e pior: o cenário exige que nada mude quando
falha, e evento assíncrono garante o contrário — a lista fecharia primeiro.

## Consequências

### Positivas
- O cenário de falha passa a ser exprimível, e com ele o terceiro teste
  obrigatório da estratégia de testes, que hoje não existe.
- Usa a única aresta que a regra de dependência já permitia; nada de novo no
  ArchUnit.
- O REST da Etapa 4 chama o mesmo método e herda a atomicidade — regra 4 do
  `CLAUDE.md` sem esforço extra.

### Negativas
- **`shopping` passa a depender de `finance` em tempo de execução**, e não só no
  papel. A aresta existia como permissão e agora é real: mexer em
  `registerExpense` passa a poder quebrar o fechamento de compra.
- **O retorno de `fecharCompra` carrega informação financeira** — valor e
  categoria, para o recibo. Um módulo de mercado devolvendo dado de dinheiro é
  desconfortável, e é o preço de a operação ser genuinamente das duas coisas.
  A alternativa seria o orquestrador consultar `finance` de novo depois, o que
  reintroduz a segunda transação para efeito de leitura.
- A transação é mais longa que as demais do sistema (N itens mais um lançamento).
  Em escala familiar é irrelevante, e é o tipo de coisa que muda se um dia
  houver lista com centenas de itens.

## Gatilhos de revisão

- Se `finance` sair do monolito ([ADR-0001](0001-monolito-modular-em-quarkus.md)
  decide que não sai por ora), esta é a primeira costura a arrebentar — e aí a
  alternativa C deixa de ser desperdício e passa a ser a única possível.
- **Etapa 3, ao escrever o código**: se o recibo precisar de mais dado de
  `finance` do que valor e categoria, reavaliar o formato do retorno antes de
  alargá-lo item a item.
