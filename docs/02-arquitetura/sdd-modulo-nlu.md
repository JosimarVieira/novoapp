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
  NluService                  -- interpretar(householdId, texto) -> Intent
  tools/
    RegistrarDespesaTool       -- schema: categoria (enum), valor_cents, conta (opcional)
  ContextBuilder               -- busca categorias de despesa do household via finance
```

## Fluxo

1. `conversation` chama `NluService.interpretar(householdId, texto)`.
2. `ContextBuilder` busca as categorias de despesa do household (leitura em
   `finance`, RLS já ativa pelo contexto de tenant resolvido por `identity`).
3. Monta a tool `registrarDespesaTool` com o enum de categorias atual.
4. Chama o LLM (Mistral, function calling) com o texto da mensagem.
5. LLM devolve a chamada de função (categoria, valor) ou nada.
6. Confiança alta (categoria bateu exatamente com uma do enum, valor
   presente) → devolve `Intent(registrarDespesa, categoria, valor)` com
   confiança alta.
7. Qualquer outro caso (sem chamada de função, categoria fora do enum,
   valor ausente) → devolve confiança baixa. `conversation`, nesta versão,
   não sabe fazer nada além de reportar erro genérico nesse caso — tratar
   isso de verdade é o trabalho da Etapa 2.

## Erros

LLM indisponível ou timeout → mesma política da tabela de falhas
transversais do `sdd-visao-geral.md`: mensagem fica `RECEIVED`, retry com
backoff, aviso no chat após a segunda falha.

## Testes

- ArchUnit: `nlu` não importa `channel`, `conversation`, `identity`,
  `shopping`, `tasks`. Só importa `finance` (leitura de categoria).
- Cenários `@etapa1` de `financas-lancamento-por-chat.feature`.

## Gatilhos de revisão

Etapa 2: tool ganha mais casos (criar categoria, ambiguidade) — o schema da
tool e o `ContextBuilder` mudam, mas a interface `interpretar()` não muda.
