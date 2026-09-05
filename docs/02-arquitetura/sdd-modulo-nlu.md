---
tipo: sdd
modulo: nlu
status: escrito
atualizado_em: 2026-09-05
adrs:
  - ADR-0004
  - ADR-0009
---

# SDD — Módulo `nlu`

## Responsabilidade

Montar o contexto do household (categorias existentes, contas), chamar o LLM
com function calling ([ADR-0004](../01-adr/0004-interpretacao-por-function-calling-com-politica-de-confianca.md), provedor Mistral na validação, [ADR-0009](../01-adr/0009-mistral-ai-como-provedor-de-llm-na-validacao.md)) e
devolver `Intent` + confiança. Não executa nada, não persiste dado de
domínio.

## Escopo desta versão (Etapa 1)

Só a tool `registrarDespesa`, com um parâmetro de categoria restrito a um
enum das categorias de despesa que já existem no household. Cobre só os
cenários `@etapa1` do `financas-lancamento-por-chat.feature`: categoria já
existe e é reconhecida, confiança sempre alta.

**Não cobre ainda** (cenários `@etapa2`, decidido em 2026-09-05 que ficam
pra depois do esqueleto andante):
- categoria mencionada não existe no household (tool não tem esse valor no
  enum — o LLM devolveria baixa confiança ou nenhuma chamada de função;
  tratar isso como "oferecer criar categoria" é lógica de `conversation`
  ainda não escrita);
- ambiguidade entre duas categorias parecidas (política de confiança média,
  pergunta numerada);
- valor ausente (a tool exige o parâmetro, então o LLM já rejeitaria a
  chamada sem valor — mas o **texto** de "pergunta curta pedindo o valor" é
  responsabilidade de `conversation`, não escrita ainda).

## Depende de

`finance` — leitura das categorias de despesa do household, pra montar o
enum do parâmetro da tool. Esta é uma aresta que não estava desenhada no
`sdd-visao-geral.md` (a mesma situação que apareceu com `channel`→`identity`
e `conversation`→`identity`: a regra de dependência ficou incompleta até um
módulo real forçar a decisão). `nlu` só lê categoria, nunca escreve nada em
tabela de `finance`.

## Estrutura interna proposta

```
nlu/
  NluService                   -- interpret(householdId, texto) -> Intent
  ContextBuilder               -- busca categorias de despesa do household via finance
  tools/
    RegisterExpenseTool        -- schema: categoria (enum), valor_cents, conta (opcional)
  spi/
    ExpenseExtractor           -- fronteira com o provedor de LLM
  MistralExpenseExtractor      -- implementação LangChain4j (ADR-0009)
```

Duas decisões de 2026-09-05, ao implementar:

**Existe uma interface entre `nlu` e o LangChain4j** (`ExpenseExtractor`), e
nenhum tipo da biblioteca a atravessa. Dois motivos: a [ADR-0009](../01-adr/0009-mistral-ai-como-provedor-de-llm-na-validacao.md) exige que trocar
de provedor na Etapa 5 seja configuração e não reescrita; e a
`estrategia-de-testes.md` manda stubbar o LLM em todo teste de aceitação — sem
essa fronteira, o stub teria que imitar a API do LangChain4j em vez de imitar o
resultado.

**O nome da tool e dos parâmetros continua em português** (`registrarDespesa`,
`categoria`, `valor_cents`) enquanto os identificadores Java viraram inglês
(`RegisterExpenseTool`, `interpret`). Não é inconsistência: o nome da tool é
dado enviado ao modelo, exatamente como a [ADR-0004](../01-adr/0004-interpretacao-por-function-calling-com-politica-de-confianca.md) o escreveu, e o modelo
interpreta mensagem em português.

## Fluxo

1. `conversation` chama `NluService.interpretar(householdId, texto)`.
2. `ContextBuilder` busca as categorias de despesa do household (leitura em
   `finance`, RLS já ativa pelo contexto de tenant resolvido por `identity`).
3. Monta a tool `registrarDespesaTool` com o enum de categorias atual.
4. Chama o LLM (Mistral, function calling) com o texto da mensagem.
5. LLM devolve a chamada de função (categoria, valor) ou nada.
6. Confiança alta (categoria bateu exatamente com uma do enum, valor
   presente) → devolve `Intent(registrarDespesa, categoria, valor)` com
   confiança alta. Nesta etapa "alta" é determinístico, não limiar calibrado:
   nenhum número foi inventado, porque o limiar de verdade sai da Etapa 5 com
   dado real ([ADR-0004](../01-adr/0004-interpretacao-por-function-calling-com-politica-de-confianca.md), [decisão aberta #7](../DECISOES-ABERTAS.md)).
7. Qualquer outro caso (sem chamada de função, categoria fora do enum,
   valor ausente) → devolve confiança baixa. `conversation`, nesta versão,
   não sabe fazer nada além de reportar erro genérico nesse caso — tratar
   isso de verdade é o trabalho da Etapa 2.

## Erros

LLM indisponível ou timeout → mesma política da tabela de falhas
transversais do `sdd-visao-geral.md`: mensagem fica `RECEIVED`, retry com
backoff, aviso no chat após a segunda falha.

**Não implementado na Etapa 1**: o retry com backoff e o aviso após a segunda
falha. Hoje a falha vira recibo de erro no chat na primeira tentativa e a
mensagem fica `FAILED`. O usuário nunca fica sem resposta, que é a regra que não
podia ser quebrada, mas a mensagem também não é retentada. Ver a seção do que
ficou de fora em [`etapa-1-bot-telegram-e-despesa`](../05-entregas/etapa-1-bot-telegram-e-despesa.md).

Household sem nenhuma categoria ([ADR-0013](../01-adr/0013-household-novo-comeca-sem-categorias.md)) não gasta chamada de modelo: o
enum do parâmetro ficaria vazio, que não é schema válido. Devolve confiança
baixa direto.

## Testes

- ArchUnit: `nlu` não importa `channel`, `conversation`, `identity`,
  `shopping`, `tasks`. Só importa `finance` (leitura de categoria).
- Cenários `@etapa1` de `financas-lancamento-por-chat.feature`.

## Gatilhos de revisão

Etapa 2: tool ganha mais casos (criar categoria, ambiguidade) — o schema da
tool e o `ContextBuilder` mudam, mas a interface `interpretar()` não muda.
