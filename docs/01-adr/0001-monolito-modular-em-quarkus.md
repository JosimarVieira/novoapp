---
tipo: adr
numero: 1
status: aceita
data: 2026-08-31
corrigida_em: 2026-09-05
modulos:
  - backend
depende_de: []
supera: []
superada_por:
---

# ADR-0001 — Monolito modular em Quarkus

> **Correção de registro — 2026-09-05.** A primeira versão desta ADR foi
> gerada por IA e registrou Spring Boot na Decisão. Nunca foi decisão do
> autor: Quarkus era a escolha desde o início, e o texto entrou sem
> confirmação. Corrigido no próprio documento, sem ADR de superação —
> registro errado se corrige, decisão mudada é que se supera (ver "Como
> trabalhamos" no CLAUDE.md). O nome do arquivo foi corrigido na mesma data.

- **Impacta**: todo o backend

## Contexto

Time de uma pessoa na validação. Domínio pequeno (três agregados) mas com
forte acoplamento semântico entre eles — o elo lista→despesa é o diferencial
do produto e exige transação única. A base técnica existente é Java, Postgres
e Vue.

## Decisão

Um único deploy Quarkus / Java 21 (versão LTS mais recente disponível na
Etapa 1), organizado em pacotes com fronteira explícita: `channel`, `nlu`, `conversation`, `finance`, `shopping`, `tasks`,
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


### C. Spring Boot 3
Descartada por familiaridade e domínio de ferramenta: o conhecimento
acumulado do time (de uma pessoa) com Quarkus, já usado em produção em
`legacy/`, pesa mais que qualquer vantagem de ecossistema do Spring Boot no
volume da validação. Ver nota em
[ADR-0010](0010-reescrever-modulo-financeiro-em-vez-de-reaproveitar-legado.md)
sobre o que exatamente **não** se herda do legado apesar do framework
coincidir.

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
- Mesmo framework do `legacy/` (Quarkus). Isso **não** reabre a decisão de
  não reaproveitar o código legado ([ADR-0010](0010-reescrever-modulo-financeiro-em-vez-de-reaproveitar-legado.md)) — os bugs de isolamento que a
  auditoria encontrou (60 de 72 consultas sem filtro de família) são de uso
  de Panache/JPA sem RLS e sem camada de serviço, não do framework em si.
  Mas exige disciplina explícita para não repetir o padrão: RLS ativa desde
  a primeira migration ([ADR-0003](0003-isolamento-multi-tenant-por-household.md)) é o que protege, não a escolha de
  framework.

## Gatilhos de revisão

Se a chamada de LLM começar a competir por thread com o tráfego web, extrair
`nlu` para serviço próprio antes de qualquer outra divisão.
