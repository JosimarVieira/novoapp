# Plano de saneamento antes da Etapa 3

> **Estado: executado em 2026-09-16.** Os cinco blocos saíram. O que mudou em
> relação ao planejado, e o que ficou pendente de verificação, está em
> [Depois da execução](#depois-da-execução), no fim deste documento. O corpo do
> plano fica como estava, como registro do que foi proposto e validado.

Consolida a auditoria de código feita em 2026-09-14, depois de a Etapa 2a estar
fechada e mergeada. **Este documento é para validação, não para execução**: cada
furo vem com como provar que ele é real antes de mexer no código, e cada solução
com como provar que resolveu.

Não é uma etapa do ROADMAP e não entrega funcionalidade nova. É o que precisa
estar de pé para a Etapa 3 (o elo) não nascer torta.

## Como validar cada item

Três níveis de evidência, do mais forte para o mais fraco. Cada furo abaixo
declara qual deles usa e por quê:

1. **Cenário que falha hoje** — `.feature` novo, rodando contra a aplicação
   inteira. É a prova que o CLAUDE.md prefere ("o Gherkin é a fonte de verdade").
2. **Teste de integração ou passo manual** — para o que não é comportamento
   observável de chat: guarda de configuração, transação, arquitetura.
3. **Leitura lado a lado** — só onde o furo é ausência (uma instrução que não
   está no prompt de uma tool, uma promessa em mensagem sem código atrás). Não
   há como escrever teste vermelho para ausência de texto num prompt.

## Pré-condições

- **Docker rodando.** Os Testcontainers sobem Postgres real; sem Docker, nada
  da suíte de aceitação roda. Na máquina onde auditei, o Docker estava
  indisponível e por isso **nenhum número de teste deste plano foi confirmado
  por execução** — tudo aqui é leitura de código.
- **Baseline verde antes de começar.** Rodar `mvn test` e confirmar os 89 atuais
  antes do primeiro commit. Sem baseline, não dá para saber se um vermelho novo
  é o furo ou é o ambiente.
- **Tag e runner próprios.** Os cenários de validação **não podem** levar
  `@etapa2` — deixariam vermelha a suíte de uma etapa fechada. Entram com
  `@saneamento` e um `SaneamentoAcceptanceTest` novo, nascendo com `@Disabled`
  pelo mesmo motivo que o `Etapa2AcceptanceTest` nasceu: escopo que falta deve
  ser visível na própria suíte. O `@Disabled` sai no primeiro commit do bloco,
  não no último.

## Índice

| # | Furo | Evidência | Vira |
|---|---|---|---|
| P1 | `adicionarItemLista` não manda o modelo usar a grafia da lista | leitura | 1 linha |
| P2 | "não entendi" não conta que `desfazer` cancela | leitura | 1 linha |
| P3 | Webhook aceita qualquer um quando o segredo não está setado | teste/manual | código |
| P4 | Recibo de erro afirma "não gravei nada" e pode ter gravado | teste | texto agora, atomicidade depois |
| P5 | O bot ensina o comando `usar <família>`, que não existe | manual | texto + teste |
| P6 | Confiança média executa como alta em quase tudo | cenário | ADR + código |
| P7 | `ASK_AMOUNT` só sabe executar despesa | leitura | ADR + código |
| P8 | Mensagem nova com pendência aberta trava ou grava errado | cenário | ADR + código |
| P9 | Acento faz item e categoria duplicarem | cenário | ADR + migration |
| P10 | Não existe fronteira transacional para `fecharCompra` | leitura | ADR |
| P11 | `desfazer` não sabe reverter um fechamento | leitura | ADR |

P6, P7 e P8 são o mesmo pedaço de código e saem numa ADR só — ver Bloco 3.

---

# Bloco 1 — Duas linhas, hoje

Sem ADR, sem migration, sem risco. Faça antes de qualquer discussão, porque
reduzem o dano dos furos grandes enquanto eles não são resolvidos.

## P1 — `adicionarItemLista` não instrui a grafia da lista

**Furo.** `MarkItemPurchasedTool` já diz ao modelo: *"Se ele estiver entre os
itens pendentes informados no contexto, use a grafia de lá"*
([MarkItemPurchasedTool.java:35-37](server/src/main/java/com/novoapp/nlu/tools/MarkItemPurchasedTool.java#L35-L37)).
`AddListItemTool` não tem nada equivalente
([AddListItemTool.java:83-85](server/src/main/java/com/novoapp/nlu/tools/AddListItemTool.java#L83-L85)) —
só manda singular, inicial maiúscula e sem artigo.

**Como validar que é real.** Leitura lado a lado dos dois arquivos. Não há teste
possível: o stub de LLM não lê `description` de tool, então nenhum cenário
exercita a instrução. Isso está declarado de propósito.

**Solução.** A mesma frase, no `nome` do item.

**Como validar que resolveu.** Não valida por teste — valida em uso real, na
Etapa 5, pela taxa de item duplicado. É mitigação, não conserto; o conserto é
P9.

**Custo.** Uma linha.

## P2 — A saída existe e ninguém é avisado dela

**Furo.** `desfazer` cancela qualquer pendência na hora
([ConversationOrchestrator.java:146-150](server/src/main/java/com/novoapp/conversation/ConversationOrchestrator.java#L146-L150),
ADR-0025). Mas a mensagem que a pessoa recebe quando erra a resposta é
`pending.not.understood=Não entendi a resposta. A pergunta era:\n{0}`
([messages_pt_BR.properties:44](server/src/main/resources/messages_pt_BR.properties#L44)) —
não menciona a saída. Quem não souber de cor fica repetindo até o TTL.

**Como validar que é real.** Leitura das duas linhas. A saída funciona; só não
é anunciada.

**Solução.** Acrescentar a saída ao texto da chave.

**Como validar que resolveu.** `MessageBundleTest` já cobre chave e
placeholders. Os cenários existentes que assertam sobre esse texto precisam ser
conferidos — assertam por `contains`, então devem continuar verdes.

**Custo.** Uma linha, mais conferir os passos de aceitação que tocam a chave.

---

# Bloco 2 — Segurança e honestidade de mensagem

Independentes entre si e independentes dos blocos 3 a 5. Podem sair em paralelo.

## P3 — Webhook aberto por omissão

**Furo.** `novoapp.channel.telegram.webhook-secret=${TELEGRAM_WEBHOOK_SECRET:}`
([application.properties:44](server/src/main/resources/application.properties#L44))
tem default vazio, que o SmallRye entrega como `Optional.empty()`, o que faz
[TelegramWebhookResource.java:48](server/src/main/java/com/novoapp/channel/inbound/TelegramWebhookResource.java#L48)
pular a verificação inteira. Esquecer a variável em produção não falha, não
loga, não avisa. Quem descobrir a URL injeta mensagem com o `from.id` de
qualquer pessoa — e `from.id` é a identidade inteira do sistema: despesa,
convite, estorno.

**Como validar que é real.** Com a aplicação rodando sem `TELEGRAM_WEBHOOK_SECRET`,
um `POST /webhook/telegram` com corpo de update válido e **sem** o header
`X-Telegram-Bot-Api-Secret-Token` responde 200 e processa. Vale também como
teste automatizado, no molde do `WebhookResponseBudgetTest` que já existe:
sem segredo configurado → 200; com segredo configurado e header errado → 403.

**Solução.** Em `%prod`, segredo vazio derruba o boot com mensagem explícita.
Fora de `%prod`, continua desligável — é legítimo em desenvolvimento local, e o
comentário no arquivo já diz isso.

**Como validar que resolveu.** Subir o perfil de produção sem a variável e
confirmar que a aplicação não sobe, e que a mensagem diz o que fazer.

**Custo.** Poucas linhas. É a única coisa da lista que um terceiro explora.

## P4 — O recibo de erro afirma o que não sabe

**Furo.** `failure=Deu erro aqui ao registrar e não gravei nada.` O `catch` em
[ConversationOrchestrator.java:106-112](server/src/main/java/com/novoapp/conversation/ConversationOrchestrator.java#L106-L112)
cobre tudo, e como o orquestrador é deliberadamente não-transacional, cada passo
já commitou o seu:

- `amountAnswer`: `pendingActions.resolve` commita → `registerExpense` estoura →
  a pendência foi consumida, nada foi lançado, e o texto diz que nada mudou;
- `registerOrAskAmount`: a despesa commita → `reply.send` estoura → a despesa
  existe, e o texto diz que não.

**Como validar que é real.** Teste de integração: fazer `StubOutboundMessagePort`
lançar **só na primeira chamada**, mandar `mercado 50`, e assertar que existe
uma linha em `transaction` **e** que a mensagem de falha foi enviada. As duas
coisas verdadeiras ao mesmo tempo são a contradição.

**Solução, em dois tempos.** Agora: trocar o texto por um que não afirme o que
não sabe. Depois (Bloco 5): quando a atomicidade existir, o texto pode voltar a
afirmar.

**Como validar que resolveu.** O mesmo teste, agora assertando o texto novo.

**Custo.** Uma chave de mensagem, um teste. A parte cara é o Bloco 5.

## P5 — O bot ensina um comando que não existe

**Furo.** [ADR-0007, linha 77](docs/01-adr/0007-pessoa-em-multiplos-households.md#L77)
decide que a troca de household ativo é por comando explícito no chat
(`usar Silva`). O comando nunca foi implementado — mas
[messages_pt_BR.properties:64](server/src/main/resources/messages_pt_BR.properties#L64)
manda literalmente `Para trocar, diga: usar {0}`, e
[IdentityLinkSteps.java:351](server/src/test/java/com/novoapp/acceptance/IdentityLinkSteps.java#L351)
tem um teste **verde** assertando essa promessa.

**Como validar que é real.** Manual, ou leitura: `grep` por handler de `usar`
não acha nada; quem digitar `usar Silva` cai em `interpretFresh`, nenhuma tool
casa, e recebe `Não entendi essa. Tente algo como: mercado 50`.

**Solução proposta: parar de prometer, não implementar agora.** Tirar a promessa
da mensagem e ajustar o passo de aceitação que a asserta. Com isso a ADR-0007
volta a ser decisão aceita ainda não implementada — o que é honesto — em vez de
decisão contrariada, que pela Definition of Done é severidade máxima.

**Se você discordar** e quiser o comando agora: ele é pequeno (curto-circuito
determinístico sobre `usar <nome>`, `UPDATE channel_identity.active_household_id`),
mas atravessa o papel pré-tenant e mexe em `identity`, então cresce o diff de um
bloco que já é grande. Minha recomendação é a primeira opção; a segunda é
defensável se você quiser validar multi-household na família ainda nesta fase.

**Como validar que resolveu.** O cenário `Pessoa que já é membro de outra
família aceita um convite novo` continua verde, agora sem prometer o que não
existe.

**Custo.** Uma linha de mensagem, um passo de teste. Mais uma linha no ROADMAP
registrando que a lacuna segue aberta.

---

# Bloco 3 — O bloco de `conversation`

**P6, P7 e P8 são o mesmo pedaço de código e a mesma decisão de desenho.** Os
três precisam que a `PendingAction` deixe de ser "pergunta sobre categoria" e
passe a ser "intenção guardada esperando confirmação". Fazer separado é fazer
três vezes.

Uma ADR cobre os três. Ela estende a ADR-0004 (faixa média), estende a ADR-0018
(o que acontece com pendência preterida) e supera o recorte da ADR-0026 (que
fechou a segunda chamada ao modelo em "vale só aqui").

## P6 — A faixa média da ADR-0004 quase não existe

**Furo.** A tabela da
[ADR-0004](docs/01-adr/0004-interpretacao-por-function-calling-com-politica-de-confianca.md#L37)
é incondicional: *"Confiança média, ou categoria inexistente | UMA pergunta com
opções numeradas"*. No código, `MEDIUM` é testado **em um lugar só** —
[linha 343](server/src/main/java/com/novoapp/conversation/ConversationOrchestrator.java#L343) —
e ainda assim só pergunta quando há duas ou mais alternativas. Todo o resto só
testa `LOW`:

| Linha | Intenção | Hoje, com confiança média |
|---|---|---|
| [403](server/src/main/java/com/novoapp/conversation/ConversationOrchestrator.java#L403) | `adicionarItemLista` | executa |
| [425](server/src/main/java/com/novoapp/conversation/ConversationOrchestrator.java#L425) | `marcarItemComprado` | executa |
| [447](server/src/main/java/com/novoapp/conversation/ConversationOrchestrator.java#L447) | `consultarLista` | executa |
| [456](server/src/main/java/com/novoapp/conversation/ConversationOrchestrator.java#L456) | `convidarMembro` | **emite o convite** |
| [343](server/src/main/java/com/novoapp/conversation/ConversationOrchestrator.java#L343) | `registrarDespesa`, 1 candidata | **lança a despesa** |

E a lista de candidatas vem de
[`alternativesFor`](server/src/main/java/com/novoapp/nlu/NluService.java#L146),
a heurística de primeira palavra que a própria entrega da 2a registra como
frágil. Quando ela não acha vizinha, a faixa média vira faixa alta em silêncio.

**Como validar que é real.** Cenário `@saneamento` novo. Exige mexer no stub:
hoje ele só devolve `0.5` quando duas categorias competem pela mesma primeira
palavra ([StubMessageInterpreter.java:167-171](server/src/test/java/com/novoapp/support/StubMessageInterpreter.java#L167-L171)).
A forma honesta é uma entrada fixa, no mesmo mecanismo de `FIXED_EXPENSES` que
já existe e já está declarado como "Intent fixa" na estratégia de testes — não
uma regra nova no parser do stub.

Dois cenários bastam:

- despesa com confiança média e **uma** categoria candidata → hoje registra
  direto; deveria perguntar;
- `convidarMembro` com confiança média → hoje emite o convite; deveria
  perguntar.

**Solução.** Confiança média nunca executa. Para qualquer intenção, ela vira
pendência com a intenção ecoada de volta — com opções numeradas quando há mais
de uma, e confirmação sim/não quando há uma só. É isso que exige P7.

**Como validar que resolveu.** Os dois cenários acima passam a verdes, e os 38
cenários existentes continuam verdes sem alteração nos `.feature` — como na
implementação da ADR-0015, é isso que prova que o comportamento contratado não
mudou.

**Por que não pode esperar.** É ADR aceita contrariada, é o mesmo padrão da
ADR-0015, e contamina a métrica da Etapa 5 antes de ela começar: o portão de 90%
mediria um sistema que executa palpite.

## P7 — `ASK_AMOUNT` só sabe executar despesa

**Furo.** [ConversationOrchestrator.java:241-257](server/src/main/java/com/novoapp/conversation/ConversationOrchestrator.java#L241-L257)
chama `finance.registerExpense` direto e lê `options.get(0).id()` como id de
categoria. O tipo é genérico no nome e específico na execução.

**Como validar que é real.** Leitura. E o
[gatilho de revisão do SDD de `conversation`](docs/02-arquitetura/sdd-modulo-conversation.md#L257-L260)
já mandava verificar exatamente isto para a Etapa 3: *"deve caber no mecanismo
existente sem tipo especial. Se não couber, o desenho de `PendingActionType` é
que precisa mudar, não o caso."* **Não cabe** — a resposta ao gatilho é que o
desenho muda.

**Solução.** `PendingIntent` passa a carregar o que executar quando a resposta
chegar, em vez de o orquestrador deduzir pelo tipo. O cenário
`Fechar sem informar valor` da Etapa 3 passa a caber sem tipo novo, que é o que
o SDD pediu.

**Como validar que resolveu.** Os cenários `@etapa2` de valor ausente continuam
verdes, e o cenário `Fechar sem informar valor` da Etapa 3 (hoje sem glue)
deixa de exigir desenho novo — verificável quando a Etapa 3 começar, não antes.

## P8 — Mensagem nova com pendência aberta: trava ou grava errado

**Furo, em dois sintomas com a mesma raiz** em
[`otherAnswer`](server/src/main/java/com/novoapp/conversation/ConversationOrchestrator.java#L211-L239):

- `CHOOSE_CATEGORY`, `ASK_AMOUNT`, `CONFIRM_PURCHASE` → repete a pergunta. A
  pessoa fica presa até o TTL (10 min), a menos que saiba digitar `desfazer` —
  que P2 passa a avisar.
- `CREATE_CATEGORY` → manda a mensagem nova para `interpretCategoryCorrection`,
  que declara **só** `confirmarCategoriaSugerida`. O modelo não tem como dizer
  "isto não é resposta à pergunta". Pendência "criar Pet shop e lançar R$ 80?"
  aberta, a pessoa manda outra coisa, e o sistema pode criar uma categoria com
  o nome errado e lançar **os R$ 80 da pendência** dentro dela. Não trava:
  grava errado, calado.

**Como validar que é real.** Dois cenários `@saneamento`, e o segundo é barato
porque **o stub reproduz o bug fielmente**:
[`categoryCorrection`](server/src/test/java/com/novoapp/support/StubMessageInterpreter.java#L200-L213)
devolve sempre um `ToolCall` com confiança máxima, usando o texto inteiro como
nome quando não acha o marcador `dentro de`. Ou seja, com pendência de categoria
aberta e uma mensagem qualquer, o teste de hoje mostra uma categoria absurda
sendo criada com o valor da pendência dentro. É a prova mais forte deste plano e
não exige mexer no stub.

**Solução.** Pendência aberta + mensagem que não é atalho → **uma** chamada ao
modelo com as tools gerais **mais** `confirmarCategoriaSugerida` (esta só quando
a pendência é de categoria), com a `question_asked` no prompt de sistema. O
modelo ganha como escolher entre "isto responde à pergunta" e "isto é outra
coisa" — que é exatamente o que hoje ele não tem. Custo igual ou menor que hoje:
uma chamada, não duas.

**Furo na solução ingênua, que a ADR precisa evitar.** "Abortar a pendência
antiga" incondicionalmente come a resposta certa: pendência
`Foi em qual categoria? 1) Mercado 2) Mercado livre` com R$ 50 já capturados, e a
pessoa digita `mercado` em vez de `1` — reinterpretação geral leria isso como
despesa nova sem valor, perguntaria "quanto foi?", e teria perdido os R$ 50. O
critério de desempate é conteúdo da ADR, não detalhe de implementação.

**O miolo que ninguém decidiu ainda, e é a parte que não cabe em "correção
rápida".** Se a mensagem nova vence, o que acontece com a pendência preterida?
Hoje `resolved_at IS NULL` faz dois trabalhos ao mesmo tempo: intercepta o fio
do chat, e aparece na central de pendências da Etapa 4 (ADR-0018). É preciso
parar o primeiro sem tocar o segundo — senão a mensagem seguinte cai na mesma
armadilha. E não há valor disponível: `REJECTED` seria mentira (ninguém disse
não) e `EXPIRED` está reservado para desistência explícita pela web. Falta um
estado ou uma coluna (`superseded_at`). **Isto é schema**, e é por isso que P8
carrega uma migration pequena junto.

**Como validar que resolveu.** Os dois cenários `@saneamento` passam a verdes,
e um terceiro cobre o desempate: resposta parcial à pergunta continua sendo
lida como resposta, não como intenção nova.

## Entregável do Bloco 3

- Uma ADR: precedência entre mensagem nova e pendência aberta, faixa média
  aplicada a toda intenção, e o destino da pendência preterida. Estende 0004 e
  0018, recorta 0026.
- `sdd-modulo-conversation.md` e `sdd-modulo-nlu.md` atualizados — o
  `DocumentationCoherenceTest` cobra o vínculo nos dois sentidos.
- Migration pequena (`superseded_at` ou equivalente).
- 4 a 5 cenários `@saneamento`.

---

# Bloco 4 — Normalização de nome

## P9 — Acento faz item e categoria duplicarem

**Furo, em dois lugares com pesos diferentes.**

O lado de **item** é o que morde, e o pior caso é o silencioso:
[`findPendingByName`](server/src/main/java/com/novoapp/shopping/repository/ListItemRepository.java#L183-L187)
compara com `lower(name)`, e o índice único que sustenta "item repetido não
duplica" —
[`list_item_unique_pending_name`](server/src/main/resources/db/migration/V3__shopping_list.sql#L45-L46) —
**também é `lower(name)`**. Logo "acabou cafe" com "Café" já pendente insere uma
segunda linha, sem pergunta e sem aviso. No checkout o estrago é mais visível:
cai em `NotOnTheList` e vira pergunta, mas gera duplicata se a pessoa responder
`sim`.

O lado de **categoria** é mais estreito do que parece à primeira leitura:
[`NluService.normalize`](server/src/main/java/com/novoapp/nlu/NluService.java#L217-L221)
**já tira acento** ao casar a categoria escolhida pelo modelo contra as
existentes — por isso `60 farmacia - remedio joaquim` está verde contra
"Farmácia". Quem não normaliza é
[`CategoryRepository.findExpenseByName`](server/src/main/java/com/novoapp/finance/repository/CategoryRepository.java#L56-L60),
usado só por `CategoryService`. O sintoma é `restaurante dentro de alimentacao`
criando uma raiz "Alimentacao" ao lado da "Alimentação" que já existe.

**Como validar que é real.** Dois cenários `@saneamento`, ambos reproduzíveis
com o stub atual:

- "Café" pendente, `acabou cafe` → hoje entra um segundo item; deveria dizer que
  já estava na lista;
- "Alimentação" existente, pendência de criar "Restaurante", resposta
  `restaurante dentro de alimentacao` → hoje cria raiz duplicada.

**Solução.** Coluna `name_normalized` persistida em `category` e `list_item`,
preenchida em Java, com os índices únicos passando a usá-la.

**Por que coluna e não `unaccent` no índice.** `unaccent` é `STABLE`, não
`IMMUTABLE`, e o Postgres recusa em índice — sai wrapper `IMMUTABLE` mais
extensão, ou coluna. A coluna resolve outra coisa de quebra: a função de
normalização está **copiada em três lugares de produção**
([ShortCircuit:103](server/src/main/java/com/novoapp/conversation/ShortCircuit.java#L103),
[Answers:44](server/src/main/java/com/novoapp/identity/onboarding/Answers.java#L44),
[NluService:219](server/src/main/java/com/novoapp/nlu/NluService.java#L219))
mais três nos testes. Uma `common/text/Normalization` única alimenta a coluna e
os três usos.

**Risco operacional que precisa de passo próprio.** O banco de validação da
família pode já ter duplicatas que só existem porque o índice atual é frouxo. Se
existirem, a criação do índice novo **falha no meio da migration**. Antes de
escrever a V5, rodar uma consulta de checagem em cada tabela e resolver à mão o
que aparecer. Isso não é opcional.

**Escopo que a ADR precisa recusar explicitamente.** Acento sim; **plural não**.
"café" / "cafés" é stemming, é outra decisão, e é assim que este escopo estoura.
A recusa escrita na ADR vale mais que o silêncio.

**Como validar que resolveu.** Os dois cenários acima verdes, mais o
`CategoryHierarchyTest` existente continuando verde, mais a consulta de checagem
retornando zero.

**Entregável.** Uma ADR (a entrega da 2a já registrou que a decisão "deveria
valer para os dois ao mesmo tempo"), uma migration V5 com backfill,
`common/text/Normalization`, dois cenários.

---

# Bloco 5 — Desenho do elo

**Zero código.** São duas ADRs escritas antes de a Etapa 3 começar, porque as
duas decidem coisa que o código da Etapa 3 pressupõe.

## P10 — Não existe fronteira transacional para `fecharCompra`

**Furo.** [ConversationOrchestrator.java:46-48](server/src/main/java/com/novoapp/conversation/ConversationOrchestrator.java#L46-L48)
declara, com razão, que o orquestrador não é transacional — a cauda de latência
do LLM não pode segurar conexão de banco. Consequência: cada chamada de domínio
abre a própria transação curta, e não há lugar onde `shopping` e `finance`
commitem juntos.

O cenário
[`Falha ao registrar a despesa não deixa a lista fechada`](docs/03-specs/features/elo-fechamento-de-compra.feature#L35-L40)
exige exatamente isso. E o teste obrigatório nº 3 da
[estratégia de testes](docs/04-qualidade/estrategia-de-testes.md#L28) —
"Atomicidade do fechamento de compra" — não existe no repositório.

**Nota de correção.** A entrega da Etapa 2a afirma que "os quatro obrigatórios"
estão verdes. São três: o nº 3 cobre `fecharCompra`, que é Etapa 3. Vale uma
nota de correção no documento de entrega, pelo critério do CLAUDE.md — é
registro que nunca foi verdade, não decisão que mudou.

**Como validar que é real.** Leitura, mais a ausência do teste. Não há como
escrever cenário vermelho para comportamento que ainda não foi especificado em
código.

**Direção proposta.** Um método `@Transactional @HouseholdScoped` em `shopping`,
que fecha os itens e chama `finance` dentro da mesma transação. A aresta já é
permitida e é a única dirigida liberada pelo ArchUnit
([ModuleBoundariesTest.java:132](server/src/test/java/com/novoapp/architecture/ModuleBoundariesTest.java#L132)).
O orquestrador continua não-transacional; a atomicidade vive no domínio.

## P11 — `desfazer` não sabe reverter um fechamento

**Furo.** [`reverseLatest`](server/src/main/java/com/novoapp/finance/FinanceService.java#L114)
devolve `ReversedExpense` e marca `reversed_at`. O cenário
[`Desfazer o fechamento`](docs/03-specs/features/elo-fechamento-de-compra.feature#L63-L68)
exige que os itens voltem a `PENDING`. A
[ADR-0025](docs/01-adr/0025-desfazer-precedencia-e-escopo.md) decidiu precedência
e escopo do estorno e não diz nada sobre item de lista.

**Como validar que é real.** Leitura do tipo de retorno e da ADR.

**Direção proposta.** ADR nova, escrita junto com P10 — desfazer um fechamento é
a mesma atomicidade ao contrário, e separar as duas decisões produziria duas
ADRs que se citam sem parar.

---

# O que fica de fora, declarado

Não é esquecimento. Cada um tem o motivo:

- **Mensagem que falha antes do orquestrador some sem recibo e sem retry.**
  [InboundPipeline.process](server/src/main/java/com/novoapp/channel/inbound/InboundPipeline.java#L47-L69)
  tem `finally` e não tem `catch`; a linha fica `RECEIVED` para sempre e a
  reentrega do Telegram é corretamente descartada pelo guard de idempotência —
  ou seja, o mecanismo que protege contra duplicata garante que a mensagem nunca
  mais será processada. É o furo mais grave da lista de fora, mas só aparece com
  falha de infraestrutura, e a Etapa 3 é demonstração, não uso contínuo.
  **Revisar antes da Etapa 5**, que é quando começam as quatro semanas de uso
  real.
- **`ChooseHousehold` é beco sem saída.**
  [InboundPipeline.java:54-58](server/src/main/java/com/novoapp/channel/inbound/InboundPipeline.java#L54-L58)
  pergunta qual família e não guarda estado nenhum, então a pergunta se repete
  para sempre. Hoje só é alcançável a partir de estado que o próprio código
  considera inconsistente. Anda junto do comando `usar` (P5), quando ele for
  feito.
- **Acento em categoria** entra no Bloco 4 junto com item porque a ADR é a
  mesma, mas se o bloco precisar encolher, é esta metade que sai: o caminho que
  mais importa já normaliza.
- **Código morto e higiene**: `Intent.RegisterExpense.hasAmount()` sem chamador;
  `ContextBuilder.expenseCategoriesByLabel(UUID)` com parâmetro nunca usado;
  `PendingAction.createdAt`, `ListItem.createdAt` e `ShoppingList.openedAt`
  usando `Instant.now()` em vez do `Clock` injetado que o resto do código usa.
  O último tem consequência real — `listPending` ordena por `createdAt`, e itens
  criados no mesmo laço podem empatar e mudar a ordem do recibo —, então vale
  carona em qualquer commit do Bloco 4, que já mexe em `list_item`.
- **Corrida em `activeListOrCreate` / `addItems`**: dois membros adicionando o
  primeiro item ao mesmo tempo violam o índice único e viram "Deu erro aqui".
  Escala familiar torna raro. Revisar se aparecer em uso real.
- **`docs/adr.base` não commitado**, com diff só de reformatação do Obsidian.

---

# Ordem proposta e portões

| Ordem | Bloco | Portão para seguir |
|---|---|---|
| 1 | Pré-condições | `mvn test` verde, 89 testes, Docker de pé |
| 2 | Bloco 1 (P1, P2) | suíte continua verde |
| 3 | Bloco 2 (P3, P4, P5) | suíte verde + boot de `%prod` falhando sem segredo |
| 4 | Bloco 3 (P6, P7, P8) | 4-5 cenários `@saneamento` verdes, 38 antigos intactos |
| 5 | Bloco 4 (P9) | 2 cenários verdes + consulta de duplicata em zero |
| 6 | Bloco 5 (P10, P11) | duas ADRs aceitas, zero código |
| 7 | Etapa 3 | — |

Blocos 2, 3 e 4 são independentes entre si e podem ser reordenados. O Bloco 5
pode ser escrito em paralelo a qualquer um, porque não toca código.

**Registro no ROADMAP.** Este bloco precisa aparecer lá, entre a Etapa 2a e a
Etapa 3, senão é trabalho invisível. Uma seção curta, com link para este
arquivo, e o custo declarado.

---

# O que preciso que você valide

Cada item abaixo tem uma recomendação minha. Nenhum é pergunta aberta — se você
concordar, sigo; se discordar, é o ponto em que o plano muda.

1. **O corte.** Bloco 3 e Bloco 4 antes da Etapa 3, Blocos 1 e 2 imediatamente,
   e o resto depois. Se o objetivo for chegar à demonstração do elo o mais rápido
   possível, o Bloco 4 é o candidato a adiar — mas aí a Etapa 3 nasce sabendo que
   "comprei o café" pode não achar o "Café" da lista.
2. **P5 — o comando `usar`.** Recomendo tirar a promessa da mensagem em vez de
   implementar o comando agora.
3. **A tag `@saneamento` e o runner novo**, nascendo com `@Disabled`, no mesmo
   molde do `Etapa2AcceptanceTest`.
4. **Bloco 3 como uma ADR só**, e não três. O risco de juntar é uma ADR grande;
   o risco de separar é três ADRs que se citam em círculo e um refactor feito
   três vezes.
5. **Bloco 4 recusando plural explicitamente.**

Se você validar, o próximo passo é escrever a ADR do Bloco 3 — é a que cobre
mais superfície e a única cujo desenho ainda tem pergunta aberta de schema.

---

# Depois da execução

Registro do que saiu diferente do planejado, em 2026-09-16.

## O que mudou em relação ao plano

**O Bloco 3 não precisou de migration.** O plano previa uma coluna
(`superseded_at`) para que a pendência superada parasse de interceptar o fio sem
ser marcada como resolvida. Não foi preciso: a
[ADR-0018](docs/01-adr/0018-central-de-pendencias.md) já define `expires_at` como
aquilo que muda o *caminho* de resolução e nunca o estado, então fechar a janela
do atalho é gravar `expires_at` — `resolution` continua nula e a pendência segue
na central de pendências. A alternativa da coluna está registrada e recusada com
prazo na [ADR-0029](docs/01-adr/0029-intencao-adiada-e-precedencia-de-mensagem-nova.md).
Efeito colateral: `PendingAction.isExpiredAt` passou a ser inclusivo — `expires_at`
alcançado já é vencido —, que além de necessário é a leitura natural do nome.

**P6 e P7 exigiram mais do que "aplicar a faixa média".** Confiança média
precisa guardar a intenção inteira, e guardar a intenção inteira é exatamente o
que `ASK_AMOUNT` não sabia fazer. Os dois viraram um desenho só:
`PendingActionType` descreve a pergunta, `PendingIntent.deferred` descreve a
ação, e `executeDeferred` decide pela segunda. Acrescentar `fecharCompra` na
Etapa 3 passa a ser um valor de enum e um caso — que era a resposta que o
gatilho de revisão do SDD de `conversation` pedia.

**`consultarLista` ficou fora da faixa média, por decisão declarada.** Leitura
não tem o que desfazer, e perguntar antes de ler é fricção sem risco do outro
lado. Está na ADR-0029 como a única exceção, e não como omissão.

**Uma decisão de compatibilidade que o plano não previu.** Linha de
`pending_action` gravada antes da ADR-0029 desserializa sem `deferred`, e um
`switch` sobre enum nulo estouraria em cima de uma resposta legítima. O
construtor canônico de `PendingIntent` preenche o padrão a partir do tipo —
todos os tipos anteriores terminavam em despesa, menos a pergunta de item fora
da lista.

**P5 mexeu num `.feature` de etapa fechada.** Tirar a promessa do comando
`usar <família>` mudou comportamento observável, e o Gherkin é a fonte de
verdade — então o cenário `@etapa1` correspondente mudou junto, com nota de
correção dentro do arquivo. O passo de aceitação que assertava a promessa passou
a assertar o aviso que resta.

**`Etapa2AcceptanceTest` ganhou uma exclusão.** O cenário de acentuação pertence
a `mercado-lista-de-compras.feature`, cuja tag `@etapa2` está no nível da
Funcionalidade e é herdada por todo cenário novo. A seleção virou
`@etapa2 and not @saneamento` — base positiva com uma exceção escrita, que é
diferente da seleção negativa recusada em `Etapa1AcceptanceTest`.

## O que não pôde ser verificado aqui

Docker estava indisponível na máquina no dia em que o bloco foi escrito. Em
**2026-09-17** ele voltou e a suíte completa rodou:

```
Tests run: 107, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

107 = os 89 anteriores, mais `TelegramWebhookSecretGuardTest` (5) e
`SaneamentoAcceptanceTest` (13, cobrindo 6 cenários e 60 passos). A aritmética
fechar exatamente é a evidência que interessa: **nenhum cenário das Etapas 1 e
2a foi perdido, alterado ou acrescentado**, e eles passaram sem nenhuma mudança
nos `.feature` deles — que é o que prova que a ADR-0029 não mexeu em
comportamento contratado.

**O que continua sem prova, e é honesto dizer**: os 6 cenários `@saneamento`
nunca foram vistos vermelhos. Foram escritos junto com a correção, porque não
havia Docker para rodá-los antes. Eles afirmam a regressão em vez de tê-la
demonstrado. Provar exige reverter cada correção e rodar só esta suíte — barato
para os cenários do Bloco 3, que usam glue existente; caro para o do Bloco 4,
cujo passo novo depende da coluna que a `V5` cria.

## Antes de aplicar a V5 no banco da validação

A migration aborta de propósito se encontrar linha que só não colidia por causa
do acento, listando quais. **Isso é esperado e o conserto é manual** — arquivar
ou renomear uma das categorias e reapontar os lançamentos; marcar um dos itens
como `REMOVED`. Rodar antes, para não descobrir no deploy:

> **Corrigido em 2026-09-17.** A primeira versão destas consultas agrupava por
> `lower(name)` — que é exatamente o que os índices únicos **atuais** já
> impedem. Elas retornavam vazio com qualquer dado, e não provavam nada. O que
> precisa ser agrupado é a forma **sem acento**, que é a que passa a colidir.
> Agrupam pelo mesmo conjunto que o índice novo vai cobrir: `category` sem
> filtro de `archived_at` (o índice dela não tem cláusula `WHERE`), `list_item`
> só nos pendentes (o dela é parcial).

```sql
-- Categorias que passariam a colidir. Espera-se zero linhas.
SELECT household_id, parent_category_id, kind,
       lower(regexp_replace(translate(trim(name),
         'ÁÀÂÃÄáàâãäÉÈÊËéèêëÍÌÎÏíìîïÓÒÔÕÖóòôõöÚÙÛÜúùûüÇçÑñ',
         'AAAAAaaaaaEEEEeeeeIIIIiiiiOOOOOoooooUUUUuuuuCcNn'), '\s+', ' ', 'g')) AS normalizado,
       count(*), string_agg(name, ' | ')
  FROM category
 GROUP BY 1, 2, 3, 4
HAVING count(*) > 1;

-- Itens pendentes que passariam a colidir. Espera-se zero linhas.
SELECT shopping_list_id,
       lower(regexp_replace(translate(trim(name),
         'ÁÀÂÃÄáàâãäÉÈÊËéèêëÍÌÎÏíìîïÓÒÔÕÖóòôõöÚÙÛÜúùûüÇçÑñ',
         'AAAAAaaaaaEEEEeeeeIIIIiiiiOOOOOoooooUUUUuuuuCcNn'), '\s+', ' ', 'g')) AS normalizado,
       count(*), string_agg(name, ' | ')
  FROM list_item
 WHERE status = 'PENDING'
 GROUP BY 1, 2
HAVING count(*) > 1;
```

## O que continua aberto, de propósito

Segue o corte do plano — o que só irrita espera o uso real medir:

- mensagem que falha antes do orquestrador some sem recibo e sem retry, e a
  reentrega do Telegram é descartada pela idempotência (revisar antes da Etapa 5);
- `ChooseHousehold` é beco sem saída, e anda junto do comando `usar <família>`
  quando ele for feito;
- plural continua duplicando item e categoria ([ADR-0030](docs/01-adr/0030-correspondencia-de-nome-por-forma-normalizada.md) recusa por prazo);
- higiene: `ContextBuilder.expenseCategoriesByLabel(UUID)` com parâmetro nunca
  usado, e `createdAt`/`openedAt` de três entidades ainda em `Instant.now()` em
  vez do `Clock` injetado. `Intent.RegisterExpense.hasAmount()` deixou de ser
  código morto — a faixa média passou a usá-lo.
