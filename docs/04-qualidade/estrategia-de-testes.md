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
   intacta. Cobre o diferencial do produto. **Existe desde 2026-09-21**, no
   cenário `Falha ao registrar a despesa não deixa a lista fechada`
   ([elo-fechamento-de-compra.feature](../03-specs/features/elo-fechamento-de-compra.feature)),
   com `finance` indisponível por uma subclasse de teste que participa da
   transação de verdade.

   Escrevê-lo obrigou a **mudar a ordem das escritas** em `ShoppingService`.
   Com `finance` sendo chamado antes de qualquer item mudar de status — que era
   o desenho original, e o intuitivo —, o cenário passava **por ordenação**: não
   havia nada escrito para o `rollback` desfazer, e o teste não poderia ficar
   vermelho nem com a transação removida. Hoje os itens são marcados e
   descarregados no banco antes, e a sensibilidade foi verificada invertendo a
   garantia (duas transações separadas → cenário vermelho). Ver
   [sdd-modulo-shopping.md](../02-arquitetura/sdd-modulo-shopping.md).

   **É o caso a citar quando alguém perguntar por que um teste verde não basta.**
4. **Orçamento de resposta do webhook** — persistência da `InboundMessage` e
   resposta 200 ficam sob 3s mesmo com interpretação e execução acontecendo
   fora do ciclo de request. Cobre a regra não negociável 3 do CLAUDE.md.

## O que não testamos

- **Precisão do LLM não é teste unitário.** Prompt muda, modelo muda, e um
  assert de string vira ruído vermelho. A precisão é medida por um conjunto de
  avaliação separado (`nlu-eval`), rodado sob demanda sobre N mensagens reais
  anotadas. Não bloqueia merge; bloqueia release. **O que ele reporta está
  decidido na [ADR-0035](../01-adr/0035-o-que-a-etapa-5-mede.md)** — ver a seção
  abaixo. `nlu-eval` ainda não existe: é o item 8 do que ficou de fora da
  Etapa 1, e continua aberto.
- **Estrutura interna.** Teste que quebra em refactor sem mudança de
  comportamento é passivo. Testamos a fronteira do módulo.
- **Frontend Vue** até a Etapa 4. Antes disso não há frontend.

## As três métricas da Etapa 5

Decididas na [ADR-0035](../01-adr/0035-o-que-a-etapa-5-mede.md), em 2026-09-19,
porque o portão de 90% do [ROADMAP](../../ROADMAP.md) existia desde 2026-08-31
sem nenhum documento dizendo como era calculado.

O gabarito anota **o desfecho esperado** — executar (com quais campos), perguntar
(o quê), ou não entender —, e não a tool esperada. **Nenhuma mensagem sai do
denominador de nenhuma das três.**

| Métrica | O que é | Portão |
|---|---|---|
| **Acerto de interpretação** | Desfecho do sistema bate com o anotado. Pergunta esperada conta como acerto; pergunta desnecessária, como erro | **90% no fluxo de despesa** — é este que trava a Etapa 6 |
| **Dano** | Escrita que a pessoa não pediu, **sem nenhuma confirmação no meio**: valor, categoria ou status de item que ela não escreveu | **Qualquer ocorrência de dinheiro inventado trava a Etapa 6**, independente do acerto |
| **Fricção** | Fração das mensagens que vira pergunta, separando necessária de desnecessária | Nenhum. É diagnóstico: fricção alta com acerto alto é limiar mal calibrado ([#7](../DECISOES-ABERTAS.md)), com acerto baixo é interpretador ruim ([#3](../DECISOES-ABERTAS.md)) |

Campos considerados no acerto: **categoria, valor e conta**. A **descrição** fica
fora — é texto livre, não tem gabarito contra o qual comparar, e incluí-la
contaminaria o portão
([ADR-0023](../01-adr/0023-descricao-de-lancamento-extraida-pelo-llm.md)).
Avaliada à parte, por amostra manual.

A matriz de confusão **por tool** continua sendo reportada, mas como recorte e
não como métrica principal: ela não enxerga a tool certa com o campo errado, que
é o caso que mais dói (`registrarDespesa` com valor inventado acerta a tool,
[ADR-0034](../01-adr/0034-valor-so-vale-se-a-pessoa-escreveu-digito.md)). Serve
para decidir se o cardápio de seis tools precisa encolher.

**O instrumento entrou em 2026-09-19**
([ADR-0036](../01-adr/0036-instrumento-de-medicao-da-etapa-5.md)):
`inbound_message` passou a gravar `prompt_version` e `model_name`, e a métrica é
calculada **por `prompt_version`** — o prompt não se congela durante a medição,
versiona-se, porque congelar exigiria deixar dinheiro errado de pé por quatro
semanas. Nulo nas duas colunas significa "nenhum modelo opinou" (curto-circuito
da regra 6, onboarding), e não lacuna.

O gabarito anotado mora em [`nlu-eval/`](nlu-eval/), versionado, e não no banco:
congelá-lo entre duas medições é o que o git faz. **O dataset ainda não existe** —
a pasta tem o formato, não o conteúdo —, e `nlu-eval` como ferramenta também não:
é o item 8 do que ficou de fora da Etapa 1, e continua aberto. Retenção da
mensagem original segue a posição interina da ADR-0036: retém tudo durante a
validação, decide na Etapa 6 ([#4](../DECISOES-ABERTAS.md), escopo LGPD).

## Duplas e stubs

- LLM sempre stubbado nos testes de aceitação, com `Intent` fixa. O que se
  testa ali é a política de confiança e a execução, não o modelo.
- Canal sempre stubbado. Nenhum teste envia mensagem de verdade.

## Onde isso roda

`.github/workflows/ci.yml`, a cada push na `main` e em todo pull request.
Existe desde 2026-09-05 — antes disso, "falha o build" e "barra um merge"
dependiam de alguém lembrar de rodar os testes, que é o tipo de disciplina que a
[ADR-0003](../01-adr/0003-isolamento-multi-tenant-por-household.md) diz não
funcionar.

**São dois passos de teste, e só o primeiro é portão** (desde 2026-09-21):

- `mvn test -DexcludedGroups=em-construcao` — arquitetura, isolamento de tenant,
  idempotência, orçamento de resposta e os cenários das etapas fechadas. Verde
  aqui significa **"nada regrediu"**, e é o que libera o `docker build`.
- `mvn test -Dgroups=em-construcao`, com `continue-on-error` — a suíte da etapa
  em construção, que nasce vermelha de propósito. Mostra o placar subindo cenário
  a cenário sem derrubar o job.

A separação saiu do primeiro push da Etapa 3: com a suíte do elo ligada, o job
inteiro caía e levava junto o `docker build` — que existe justamente por uma
regressão que já quebrou o deploy duas vezes. É a mesma disciplina que deu runner
próprio ao `@saneamento` na Etapa 2a: escopo novo não pinta de vermelho o portão
de uma etapa fechada.

**Tirar `@Tag("em-construcao")` da classe é o ritual que marca a etapa fechada** —
sem ela, a suíte passa a integrar o portão. A tag não se chama `etapa3` de
propósito: na Etapa 2b ela muda de classe em vez de acumular uma por etapa.

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
