# Estratégia de testes

Substitui o que seria "os TDDs". TDD é prática de codificação, não documento —
o que se define antes é **o que se testa, onde, e o que barra um merge**.

## Camadas

| Camada | Ferramenta | O que cobre | Alvo |
|---|---|---|---|
| Unidade | JUnit 5 + AssertJ | Regra de domínio pura: cálculo, transição de status, validação | Rápido, sem contexto CDI/Quarkus |
| Arquitetura | ArchUnit | Regra de dependência entre módulos ([ADR-0001](../01-adr/0001-monolito-modular-em-quarkus.md)); nenhum código abaixo de `channel/` referencia tipo específico de canal (regra não negociável 5 do CLAUDE.md) | Falha o build |
| Integração | Testcontainers (Postgres real) | Repositório, transacionalidade, **RLS** | Nunca com H2 |
| Aceitação | Cucumber JVM sobre os `.feature` | Comportamento observável ponta a ponta | Fonte de verdade |
| Contrato de canal | WireMock | Webhook de Telegram/Meta, incluindo reentrega, timeout e orçamento de resposta (<3s) | — |

## O que é obrigatório

Quatro testes que não podem faltar, porque cobrem as regras não negociáveis:

1. **Vazamento de tenant** — suite que executa operações de dois households
   simultaneamente e falha se qualquer query retornar dado do outro. Roda em
   todo build. É o teste mais importante do projeto.
2. **Idempotência** — entregar o mesmo `provider_message_id` duas vezes produz
   exatamente um efeito. Cobre [ADR-0005](../01-adr/0005-idempotencia-de-mensagens-recebidas.md).
3. **Atomicidade do fechamento de compra** — falha em `finance` deixa a lista
   intacta. Cobre o diferencial do produto.
4. **Orçamento de resposta do webhook** — persistência da `InboundMessage` e
   resposta 200 ficam sob 3s mesmo com interpretação e execução acontecendo
   fora do ciclo de request. Cobre a regra não negociável 3 do CLAUDE.md.

## O que não testamos

- **Precisão do LLM não é teste unitário.** Prompt muda, modelo muda, e um
  assert de string vira ruído vermelho. A precisão é medida por um conjunto de
  avaliação separado (`nlu-eval`), rodado sob demanda: N mensagens reais
  anotadas com a intenção esperada, reportando taxa de acerto por tool e
  matriz de confusão. Não bloqueia merge; bloqueia release.
- **Estrutura interna.** Teste que quebra em refactor sem mudança de
  comportamento é passivo. Testamos a fronteira do módulo.
- **Frontend Vue** até a Etapa 4. Antes disso não há frontend.

## Duplas e stubs

- LLM sempre stubbado nos testes de aceitação, com `Intent` fixa. O que se
  testa ali é a política de confiança e a execução, não o modelo.
- Canal sempre stubbado. Nenhum teste envia mensagem de verdade.

## Onde isso roda

`.github/workflows/ci.yml`, a cada push na `main` e em todo pull request:
`mvn test` (arquitetura, isolamento de tenant, idempotência, orçamento de
resposta e os cenários Gherkin) e, se passar, `docker build` da imagem de
deploy. Existe desde 2026-09-05 — antes disso, "falha o build" e "barra um
merge" dependiam de alguém lembrar de rodar os testes, que é o tipo de
disciplina que a [ADR-0003](../01-adr/0003-isolamento-multi-tenant-por-household.md) diz não funcionar.

O `docker build` está no CI por um motivo específico: o build de deploy pula os
testes, então nada mais garante que o `Dockerfile` compila. Foi a regressão que
quebrou o deploy duas vezes.

**O workflow reporta, não impede.** Barrar merge de fato exige branch protection
na `main`, que é configuração do GitHub e não vive no repositório.

## Definition of Done

Uma tarefa está pronta quando:

- [ ] O cenário `.feature` correspondente passa
- [ ] Existe teste do caminho de erro, não só do feliz
- [ ] Se tocou dado de usuário: teste de isolamento de tenant cobre a tabela
- [ ] Se tocou fluxo de chat: existe recibo para sucesso **e** para falha
- [ ] Nenhuma ADR aceita foi contrariada (ou existe ADR nova superando)
- [ ] Termos novos entraram no glossário
- [ ] Migration Flyway é reversível ou tem plano de rollback declarado
