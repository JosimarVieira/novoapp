# ADR-0001 — Monolito modular em Spring Boot

- **Status**: Proposta
- **Data**: 2026-08-31
- **Impacta**: todo o backend

## Contexto

Time de uma pessoa na validação. Domínio pequeno (três agregados) mas com
forte acoplamento semântico entre eles — o elo lista→despesa é o diferencial
do produto e exige transação única. A base técnica existente é Java, Postgres
e Vue.

## Decisão

Um único deploy Spring Boot 3 / Java 21, organizado em pacotes com fronteira
explícita: `channel`, `nlu`, `conversation`, `finance`, `shopping`, `tasks`,
`identity`. Comunicação entre módulos apenas por interface pública de serviço.

## Alternativas consideradas

### A. Microsserviços por domínio
Descartada: o elo entre mercado e finanças viraria transação distribuída ou
consistência eventual — complexidade alta para resolver um problema que não
temos (escala), destruindo o que mais importa (o elo atômico).

### B. Serverless por função (Lambda por intent)
Descartada: cold start conflita com o alvo de resposta em menos de 3s no chat,
e o estado conversacional (`PendingAction`) exigiria store externo desde o dia
um. Ganho de custo irrelevante no volume de validação.

## Consequências

### Positivas
- Uma transação de banco cobre o fechamento de compra inteiro.
- Um deploy, um log, um profiler. Debug do fluxo ponta a ponta é trivial.
- Fronteira de módulo pode virar serviço depois, se o volume justificar.

### Negativas
- Fronteira só existe por disciplina; nada impede um import atravessado.
  Mitigação: teste de arquitetura (ArchUnit) barrando import proibido.
- Escala de um módulo arrasta os outros.
- Um bug em `nlu` derruba a interface web junto.

## Gatilhos de revisão

Se a chamada de LLM começar a competir por thread com o tráfego web, extrair
`nlu` para serviço próprio antes de qualquer outra divisão.
