# Prompt para iniciar a Etapa 3 — o elo

Contexto: a Etapa 2a está fechada, o saneamento pré-Etapa 3 foi executado, e a
Etapa 3 **já começou** — a migration e o domínio do fechamento existem, o resto
não. Este prompt é para uma sessão nova do Claude Code continuar o mesmo
repositório.

## Leia isto antes de rodar qualquer coisa

**A suíte está vermelha de propósito.** `Etapa3AcceptanceTest` saiu do
`@Disabled` no primeiro passo de código da etapa, e não no último — mesmo padrão
da Etapa 2a. Os nove cenários do elo falham por passo indefinido, e é assim que
o placar sobe cenário a cenário.

Não "conserte" isso. Recolocar o `@Disabled`, afrouxar um cenário ou marcar
algum como pendente apaga a única coisa que a suíte está sinalizando. O verde
volta quando a etapa fechar.

**O CI já sabe disso.** `Etapa3AcceptanceTest` leva `@Tag("em-construcao")`, e
o workflow roda dois passos: o portão (`-DexcludedGroups=em-construcao`, que
responde "nada regrediu" e libera o `docker build`) e um informativo com
`continue-on-error` que mostra o placar. Se o portão ficar vermelho, **alguma
coisa regrediu de verdade** — não é a Etapa 3 aparecendo.

**Tirar a tag é o ritual que fecha a etapa**, e é a última coisa a fazer, não a
primeira.

## Leitura obrigatória

1. `CLAUDE.md` inteiro. Em especial a regra 7 (todo lançamento é reversível) e o
   parágrafo do **diferencial**: o elo é a razão de existir do produto, e esta é
   a etapa que o entrega.
2. `ROADMAP.md`, seção **Etapa 3**, e a seção **"A ordem daqui em diante"** logo
   acima dela — ela explica por que a Etapa 5 não abriu e por que este trabalho
   veio primeiro.
3. `docs/03-specs/features/elo-fechamento-de-compra.feature` inteiro, incluindo o
   comentário do topo. Nove cenários, tag `@etapa3` na `Funcionalidade:`.
4. `docs/02-arquitetura/sdd-modulo-shopping.md`, seção **"O que a Etapa 3 já tem
   decidido"** — e a subseção sobre quem orquestra o `desfazer`.
5. `docs/02-arquitetura/modelo-de-dados.md`, seções **Mercado** e **"O que existe
   no banco hoje"**.

**Não use lista curada de ADRs.** O índice é `docs/adr.base` (Obsidian) ou
`docs/01-adr/`. Isto não é preferência de estilo: a entrega da Etapa 2a
registra que a ADR-0015 atravessou duas etapas sendo contrariada porque "os dois
prompts de etapa navegaram por lista curada de ADRs, e ela não estava em nenhuma
das duas". `DocumentationCoherenceTest` existe por causa disso e quebra o build
quando uma ADR aceita não é refletida por nenhum SDD. As que governam esta etapa
estão citadas nos documentos acima; siga os links de lá, não uma seleção minha.

## O que já está feito (2026-09-21)

- **`V7__list_checkout.sql`** — `list_checkout` e `list_item.list_checkout_id`.
  `transaction_id` é `NOT NULL` e **único**: fechamento sem lançamento não
  existe, e um lançamento é de no máximo um fechamento (senão o `desfazer` não
  saberia qual reverter).
- **`ShoppingService.checkout(...)`** — `@Transactional @HouseholdScoped`, chama
  `finance.registerExpense` de dentro da própria transação (ADR-0031). **Ainda
  não tem chamador.**
- **`CheckoutResult`** selado: `Closed | NoActiveList | NothingToClose`.
  `shopping` não pergunta e não lança exceção para dizer "não deu" — recusa
  nomeada vira pergunta em `conversation`, mesmo padrão de `MarkPurchasedResult`.
- **`Etapa3AcceptanceTest`** ligado.

## O que falta, em ordem

1. Tool `fecharCompra` em `nlu/tools/` e a variante `Intent.ClosePurchase`.
   Acrescentar a variante **quebra a compilação** do `switch` em
   `ConversationOrchestrator` — de propósito: é para isso que `Intent` é selada.
2. O caso no orquestrador, o recibo (`ReceiptFormatter` + chave em
   `MessageKey` + `messages_pt_BR.properties`) e o `PendingIntent.DeferredAction`
   para "fechar compra sem informar valor".
3. O stub de LLM (`StubMessageInterpreter`) entendendo `comprei tudo, 180`.
4. Os passos de glue.
5. O `desfazer` do fechamento (ADR-0032), pelo evento CDI já decidido.

**Comece pelo cenário "Fechar a lista inteira com valor"** — é o que força tool,
`list_checkout` e a chamada a `finance` a existirem de uma vez.

**Escreva cedo o passo `que o registro de despesas está indisponível.`** É o
único passo que prova a atomicidade da ADR-0031, é o terceiro dos quatro testes
obrigatórios da `estrategia-de-testes.md`, e é o único deles que nunca existiu.
Deixado para o fim, a fronteira transacional vira afirmação em vez de garantia.

## Levantamento do glue, feito em 2026-09-21

Dos **42 passos distintos** do `.feature`, **18 já são atendidos** por
`ExpenseByChatSteps` e `ShoppingListSteps` — enviar mensagem, assertar despesa
registrada, consultar a lista, desfazer, reentrega. **24 são novos**, quase todos
sobre o que só passa a existir agora: item mudando de status em lote, o
`list_checkout` ligando os dois lados, fechamento parcial, e a falha atômica.

O casamento foi por regex contra as anotações existentes, então é aproximado:
alguns dos 18 podem precisar de revisão ao rodar.

## Três armadilhas concretas

**A tool nova precisa entrar no `staticFingerprint()`** de
`MistralMessageInterpreter`. Se não entrar, a `prompt_version` da ADR-0036 para
de enxergar mudanças na descrição dos parâmetros dela — o hash continua
"funcionando" e some com exatamente o que ele existe para detectar. É silencioso,
e `InterpretationFingerprintTest` não pega (ele testa estabilidade e formato, não
cobertura).

**O cardápio vai de 6 para 7 tools.** O gatilho de revisão do
`sdd-modulo-nlu.md` já dizia que 6 era bastante contexto disputando a escolha do
modelo. Não é motivo para parar; é motivo para anotar como candidato da Etapa 5,
onde a matriz de confusão por tool decide se o cardápio encolhe por situação.

**Cenário novo em arquivo de etapa fechada herda a tag da `Funcionalidade:`.**
Foi o que obrigou `Etapa2AcceptanceTest` a selecionar `@etapa2 and not
@saneamento`. Se acrescentar cenário a `mercado-lista-de-compras.feature`,
confira o que ele pinta de vermelho.

## Decisões já tomadas — não reabra sem ADR

- **Atomicidade mora em `shopping`** (ADR-0031). `conversation` continua sem
  transação própria.
- **`desfazer` reverte os dois lados** (ADR-0032), e o fechamento é a unidade:
  volta o que aquele fechamento fechou, não a lista inteira.
- **Item comprado fora de um fechamento não é desfazível pelo chat.** Limite
  declarado, visível no schema pela coluna `list_checkout_id` nula.
- **`finance` publica `ExpenseReversed`; `shopping` observa** — decidido em
  2026-09-21, registrado em `sdd-modulo-shopping.md` e `sdd-modulo-finance.md`.
  O risco declarado é acoplamento que o ArchUnit não vê: um segundo `@Observes`
  de evento de `finance` em `shopping` pede revisão de fronteira antes do código.

## O que NÃO construir agora

- Tarefas e agenda — Etapa 2b, `.feature` ainda não escrita.
- Qualquer tela — Etapa 4.
- `nlu-eval` e a anotação do gabarito — Etapa 5. O formato está em
  `docs/04-qualidade/nlu-eval/`, e a proveniência já grava sozinha.
- Decisões abertas #22, #24, comando `usar <família>`, `ChooseHousehold`.

**#23 e #25 são exceção**: `açúcar 20` (item com valor na mesma mensagem) e
`remover chocolate` são o elo chegando cedo, e o ROADMAP diz para resolvê-las
**dentro** desta etapa. As duas estão sangrando em produção hoje.

## Regra de trabalho

A de sempre, e ela vale mais nesta etapa do que nas outras: **comportamento vira
`.feature` antes de virar código**, o Gherkin é a fonte de verdade, e decisão
estrutural vira ADR antes de virar código. Se a implementação contradisser uma
ADR aceita, escreva ADR nova superando — não edite a antiga.

E veja vermelho antes de verde. Cenário escrito junto com a correção afirma a
regressão em vez de tê-la demonstrado; o plano de saneamento registra esse custo
por ter pago ele uma vez.
