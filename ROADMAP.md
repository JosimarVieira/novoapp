# Roadmap

Cada etapa termina em algo que dá para ver funcionando. Nenhuma etapa entrega
só plano.

## Etapa 0 — Fundação documental (fechada em 2026-09-04)

**Entregável**: todas as ADRs aceitas no fechamento da etapa (escopo cresceu
muito além das 0001-0006 originalmente previstas aqui — quais e quantas em
`docs/adr.base`, filtro `status: aceita`; faixa fixa neste parágrafo já
desatualizou duas vezes), glossário fechado, features das Etapas 1-3
escritas. Decisões abertas 1, 6, 9, 10, 11 e o #17 original
(fechamento de fatura) já resolvidas por virarem ADR.

Critério de saída: nenhum item marcado `[a verificar]` nas ADRs aceitas —
cumprido. A feature de vínculo de identidade (onboarding) está escrita
(`03-specs/features/vinculo-de-identidade.feature`), com a ADR-0020
(convite de membro, Aceita em 2026-09-05) registrando o schema e o fluxo.
Nada documentado bloqueia mais abrir o editor pra Etapa 1.

## Etapa 1 — Bot Telegram + despesa (fechada em 2026-09-05)

Webhook, adaptador de canal, `identity`, idempotência, uma tool no LLM,
Flyway com schema mínimo já multi-tenant e RLS ativa.

Entregue. O relato completo — o que foi construído, o que ficou de fora, as
lacunas conhecidas dentro do que foi entregue, e as decisões tomadas ao
implementar — está em
[`docs/05-entregas/etapa-1-bot-telegram-e-despesa.md`](docs/05-entregas/etapa-1-bot-telegram-e-despesa.md).
Uma decisão estrutural virou ADR nova ([ADR-0022](docs/01-adr/0022-papel-de-banco-pre-tenant-para-identidade.md), papel de banco pré-tenant
para a resolução de identidade); as demais ficaram registradas nos SDDs de
módulo.

Escopo decidido em 2026-09-05, corrigindo uma imprecisão de escopo: esta
etapa toca `channel`, `identity`, `nlu`, `conversation` e `finance` — não só
os dois primeiros. Mas cobre só o esqueleto andante do
`financas-lancamento-por-chat.feature`: cenários `@etapa1` (categoria já
reconhecida, sem ambiguidade, reentrega, identidade não vinculada). Os
cenários `@etapa2` (ambiguidade, criar categoria, valor ausente, desfazer)
exigem `PendingAction` e a política de confiança média/baixa da ADR-0004
inteira — ficam pra Etapa 2, junto com mercado/tarefas.

**Entregável**: você manda `mercado 50` no Telegram e vê a linha no Postgres,
com recibo no chat. Teste de vazamento de tenant verde. — **Cumprido**, com uma
ressalva operacional: household novo nasce sem categoria ([ADR-0013](docs/01-adr/0013-household-novo-comeca-sem-categorias.md)) e criar
categoria por chat é Etapa 2, então as categorias da família são semeadas por
SQL na validação (passo documentado em [`server/README.md`](server/README.md)).

## Nota sobre a ordem das etapas 2 e 3

A Etapa 2 original — mercado, tarefas e consultas em um bloco de ~2 semanas —
foi dividida em 2026-09-05. Tarefas não está no caminho crítico do elo, que é o
diferencial do produto (`CLAUDE.md`) e o que a Etapa 3 demonstra. Manter as três
coisas juntas colocava a demonstração que vende o produto atrás de duas semanas
de trabalho que ela não usa.

A ordem de execução é **2a → 3 → 2b**, e é nessa ordem que as seções abaixo
estão. Os números não foram reatribuídos de propósito: as tags `@etapa1`,
`@etapa2` e `@etapa3` nos `.feature`, as referências a "Etapa 5" nas decisões
abertas e as citações a etapa em várias ADRs estão ancoradas neles.
Renumerar custaria uma varredura por toda a documentação em troca de nada.

O custo da divisão, declarado: os seis fluxos do glossário deixam de sair
juntos. Quem olhar o produto entre a 3 e a 2b vê finanças e mercado completos e
tarefas ausente.

## Etapa 2a — Mercado, ambiguidade e convite por chat (fechada em 2026-09-07)

Entregue. O relato completo — o que foi construído, o que ficou de fora, as
lacunas conhecidas dentro do que foi entregue, as decisões tomadas ao
implementar e as três contradições entre documentos aceitos que apareceram no
caminho — está em
[`docs/05-entregas/etapa-2a-mercado-ambiguidade-e-convite.md`](docs/05-entregas/etapa-2a-mercado-ambiguidade-e-convite.md).

Nenhuma ADR nova foi necessária: as 0024-0026, aceitas horas antes de a etapa
começar, cobriram o que precisava de decisão. As demais decisões ficaram
registradas nos SDDs de módulo, incluindo o [SDD de `shopping`](docs/02-arquitetura/sdd-modulo-shopping.md),
escrito nesta etapa porque o módulo não tinha nenhum.

**Três lacunas herdadas da Etapa 1 continuam abertas** — botão nativo de
compartilhar contato, retry com backoff na falha de LLM, e o comando de trocar o
household ativo ([ADR-0007](docs/01-adr/0007-pessoa-em-multiplos-households.md)).
Nenhuma delas tem cenário `@etapa2`, e nenhuma foi fechada aqui.

O plano abaixo é o que foi escrito antes de a etapa começar, mantido como
registro. O diagnóstico central dele se confirmou na prática: a espinha era
`PendingAction`, e depois que ela existiu mercado inteiro saiu quase de graça.

Começou por tirar o `@Disabled` de `Etapa2AcceptanceTest`: os 22 cenários
`@etapa2` já estavam escritos e falhavam por passo indefinido, que é o estado
correto (recontados em 2026-09-07 ao fechar as ADRs 0024-0026, que acrescentaram
cinco cenários novos aos 17 que já existiam).

**A espinha é `PendingAction` e a política de confiança média da [ADR-0004](docs/01-adr/0004-interpretacao-por-function-calling-com-politica-de-confianca.md)**, não
mercado. Cinco cenários espalhados por três features são a mesma mecânica com
conteúdo diferente — uma pergunta com opções numeradas e uma resposta que
resolve: categoria inexistente, ambiguidade entre categorias parecidas, valor
ausente, item mencionado que não está na lista, e (já na Etapa 3) fechar compra
sem informar valor. Construir mercado antes do mecanismo significa construí-lo
duas vezes.

A primeira migration da etapa foi a tabela `pending_action` (`V2`), seguida de
`shopping_list` e `list_item` (`V3`). O TTL é a
[decisão aberta #8](docs/DECISOES-ABERTAS.md) (sugerido 10 minutos, sem base):
entrou como config global do app, provisória e explícita, nunca como constante
escondida no código — junto com os dois limiares de confiança
([decisão aberta #7](docs/DECISOES-ABERTAS.md)), pelo mesmo motivo.

Criação de categoria por chat ganhou desenho próprio depois da Etapa 1 (ADRs
[0024](docs/01-adr/0024-categoria-sugerida-por-texto-livre.md),
[0025](docs/01-adr/0025-desfazer-precedencia-e-escopo.md) e
[0026](docs/01-adr/0026-hierarquia-na-criacao-de-categoria-por-chat.md), todas
aceitas em 2026-09-07) — não é só "pergunta sim/não e cria". A tool
`registrarDespesa` ganha `categoria_sugerida` (texto livre, mutuamente
exclusivo com o enum `categoria`; para household sem nenhuma categoria a
propriedade `categoria` some do schema, forçando esse caminho desde a
primeira mensagem). A `PendingAction` de confirmação ganha uma terceira via
além de sim/não: correção livre ("restaurante dentro de alimentação"), que
processa por uma segunda chamada ao modelo — exceção explícita à regra 6
("confirmações não gastam LLM"), só para esse tipo de pendência — e pode
criar categoria-pai e subcategoria juntas, respeitando o limite de um nível
da [ADR-0016](docs/01-adr/0016-subcategoria.md). E `desfazer` ganha
precedência definida: pendência aberta sempre vence sobre estorno; sem
pendência, estorna a transação não estornada mais recente do household
inteiro (qualquer membro, [ADR-0012](docs/01-adr/0012-edicao-de-lancamento-entre-membros.md)), sem janela de tempo inventada.

Herda três lacunas conhecidas da Etapa 1, listadas na entrega dela: botão
nativo de compartilhar contato, retry com backoff na falha de LLM, e o comando
de trocar o household ativo ([ADR-0007](docs/01-adr/0007-pessoa-em-multiplos-households.md)).
— Nenhuma das três foi fechada; seguem abertas, como dito no topo desta seção.

Entra também a descrição do lançamento ([ADR-0023](docs/01-adr/0023-descricao-de-lancamento-extraida-pelo-llm.md), aceita em 2026-09-05,
a partir do primeiro uso real): a tool `registrarDespesa` ganha o parâmetro
opcional `descricao`, extraído pelo LLM e mantido fora da política de
confiança — nunca vira pergunta. Três cenários `@etapa2` já estão escritos
no `financas-lancamento-por-chat.feature`. Sem migration: a coluna já existe.

Existem `ExpenseByChatSteps` e `IdentityLinkSteps`. O glue de mercado é arquivo
novo, não adaptação. O glue dos cinco cenários novos de categoria/hierarquia/
desfazer (ADRs 0024-0026) também é novo — nenhum reaproveita passo existente
de `ExpenseByChatSteps` sem revisão, porque nenhum desses fluxos existia
quando esses steps foram escritos. — Cumprido: `ShoppingListSteps` é arquivo
próprio, e os passos de finanças que foram revisados mudaram de fato
(`uma despesa é registrada` passou a ignorar lançamento estornado, que antes não
existia).

**Entregável**: `acabou o arroz` entra na lista, `o que está faltando?`
responde, `pet shop 80` oferece criar a categoria e grava depois do `sim`,
`restaurante eu e esposa 90` corrigido para "dentro de alimentação" cria a
hierarquia certa mesmo quando nada existe ainda, e `desfazer` com pergunta
pendente cancela a pergunta em vez de estornar. Os 22 cenários `@etapa2`
verdes. — **Cumprido**, sem ressalva de escopo. Semear categoria e inserir
convite por SQL, os dois passos manuais que a Etapa 1 documentava em
[`server/README.md`](server/README.md), deixaram de existir: são exatamente os
dois fluxos que esta etapa entregou.

## Saneamento antes da Etapa 3 (executado em 2026-09-16)

Não é etapa e não entrega funcionalidade: é o que precisava estar de pé para a
Etapa 3 não nascer torta. Saiu de uma auditoria de código em 2026-09-14, com o
plano e a validação de cada furo em
[`PLANO-SANEAMENTO-PRE-ETAPA-3.md`](PLANO-SANEAMENTO-PRE-ETAPA-3.md).

Quatro ADRs novas, todas aceitas no mesmo dia:

- [ADR-0029](docs/01-adr/0029-intencao-adiada-e-precedencia-de-mensagem-nova.md) —
  a pendência guarda a intenção adiada, confiança média deixa de executar em
  toda intenção que escreve, e mensagem nova pode superar a pergunta aberta.
  Fecha três furos de uma vez, incluindo o mais grave: mensagem sobre outro
  assunto virando categoria errada com o valor de outra despesa dentro.
- [ADR-0030](docs/01-adr/0030-correspondencia-de-nome-por-forma-normalizada.md) —
  nome de categoria e de item casa sem acento, por coluna persistida e índice
  único. Plural fica de fora, declaradamente.
- [ADR-0031](docs/01-adr/0031-atomicidade-do-fechamento-de-compra.md) e
  [ADR-0032](docs/01-adr/0032-desfazer-alcanca-o-fechamento-inteiro.md) — o
  desenho do elo, escrito antes do código da Etapa 3: onde fica a transação, e
  o que `desfazer` reverte. Zero código nestas duas.

Também saíram: a guarda que recusa subir em produção sem o segredo do webhook,
o recibo de erro que deixou de afirmar "não gravei nada" (o orquestrador não é
transacional, então às vezes gravou), e a promessa do comando `usar <família>`
retirada da mensagem de convite — o comando é decisão da
[ADR-0007](docs/01-adr/0007-pessoa-em-multiplos-households.md) e **continua não
implementado**; o que mudou é que o bot parou de ensiná-lo.

Cenários novos levam a tag `@saneamento`, com `SaneamentoAcceptanceTest`
próprio, pelo mesmo motivo que a Etapa 2a teve o seu: escopo novo não pode
pintar de vermelho o portão de uma etapa fechada.

## Uso real e correções (2026-09-17 a 2026-09-19)

Não é etapa, não estava previsto e não entregou funcionalidade nova. Registrado
aqui porque a alternativa é trabalho invisível — e porque é, de fato, **a
Etapa 5 acontecendo fora de ordem**: a família usa o produto em produção desde
2026-09-05, e todo o material abaixo nasceu de mensagem real, não de auditoria.

Sete commits, duas ADRs novas e seis cenários `@saneamento` a mais (hoje 12 no
total). Todos os defeitos são da mesma família: **o bot sabia o que a pessoa
quis e respondeu que não sabia, ou gravou o que ela não escreveu.**

- **2026-09-17** — a suíte completa rodou pela primeira vez depois do saneamento
  (107 testes, verde). Categoria inexistente passou a oferecer criação também na
  faixa baixa: `Pet shop 80`, sem ambiguidade nenhuma, chegava do Mistral com
  `confianca` 0,3 e virava "não entendi essa"
  (`sdd-modulo-conversation.md`).
- **2026-09-18** — a conversão para centavos saiu do modelo: o parâmetro virou
  `valor`, em reais, e `NluService` multiplica com `BigDecimal` a partir da forma
  textual. Um modelo de 8B errava a aritmética, e R$ 500,00 virou R$ 5,00 e
  R$ 50,00 em produção. Junto: nome de categoria nova escrito no campo errado
  passou a valer como sugestão, e `MistralMessageInterpreter.recoverFromText`
  passou a recuperar a chamada que o provedor devolve como texto
  (`sdd-modulo-nlu.md`).
- **2026-09-18** — [ADR-0033](docs/01-adr/0033-valor-ausente-com-categoria-conhecida-pergunta-o-valor.md):
  categoria resolvida sem valor pergunta o valor em qualquer faixa de confiança.
  Ela fixa a ordem dos passos dentro de `registerExpense`, e é o lastro de uma
  frase que estava no SDD desde a Etapa 2a sem nenhuma ADR que a sustentasse. No
  mesmo commit, `InboundPipeline` passou a responder pela falha nascida **antes**
  do orquestrador, que até então terminava sem resposta nenhuma.
- **2026-09-19** — [ADR-0034](docs/01-adr/0034-valor-so-vale-se-a-pessoa-escreveu-digito.md):
  `nlu` descarta o valor devolvido pelo modelo quando a mensagem não tem nenhum
  dígito. `mercado` — uma palavra — voltou duas vezes com `valor: 50` e gravou
  R$ 50,00. É a terceira vez que dinheiro sai errado em produção, e a terceira
  fechada em código e não em prompt.

Quatro decisões abertas novas saíram daqui, todas de uso real:
[#22, #23, #24 e #25](docs/DECISOES-ABERTAS.md). As três primeiras já não são
dúvida de futuro — são comportamento acontecendo hoje sem cenário que o cubra.

**O que este período torna visível sobre a Etapa 5**: ela pressupõe "sem feature
nova, só uso e medição", e quatro dias de uso real produziram duas ADRs e sete
commits. A premissa está errada, e nada disso foi medido — `nlu-eval` continua
não existindo, e `inbound_message` não grava qual prompt nem qual modelo produziu
cada interpretação, então o log deste período não é comparável consigo mesmo.

## A ordem daqui em diante, decidida em 2026-09-19

A Etapa 5 **não abre agora**, e a razão é a que o diagnóstico daquele dia
expôs: entre 2026-09-16 e 2026-09-19, todo o trabalho foi em `nlu` e
`conversation` — sete commits, quatro ADRs, quatro decisões abertas novas — e
nenhuma linha em `fecharCompra`. Cada correção era justificável sozinha;
`mercado` gravando R$ 50,00 do nada tinha de ser consertado no dia. O efeito
somado é outro: o produto está sendo polido no eixo em que já é bom o bastante
para uso familiar, e o eixo que decide se ele existe continua vazio. Uso real é
um gerador infinito de defeitos pequenos e reais — ele não vai parar, e "conserto
o que aparecer antes de seguir" é uma regra que nunca deixa a Etapa 3 começar.

Há um motivo mecânico além do estratégico: **`fecharCompra` muda o que precisa
ser medido.** Acrescenta uma sétima tool ao cardápio — e o
[SDD de `nlu`](docs/02-arquitetura/sdd-modulo-nlu.md) já registra que seis é
muito contexto —, cria `comprei tudo, 180`, que é a mensagem mais cara de errar
que o produto terá, e ativa as [decisões #23 e #25](docs/DECISOES-ABERTAS.md),
que são justamente as que sangram hoje. Gabarito anotado antes disso descreve um
sistema que vai deixar de existir, e a
[ADR-0035](docs/01-adr/0035-o-que-a-etapa-5-mede.md) só considera gabarito
congelado.

O que torna a espera barata é o instrumento da
[ADR-0036](docs/01-adr/0036-instrumento-de-medicao-da-etapa-5.md): a proveniência
grava sozinha, sem ninguém anotar nada. **A Etapa 5 acumula corpus enquanto a
Etapa 3 é construída** — e o corpus passa a incluir as mensagens do elo.

A ordem:

1. ~~**Retry com backoff na falha de LLM.**~~ **Feito em 2026-09-21.** Duas
   tentativas dentro da mesma tarefa assíncrona, 2s entre elas, 429 e defeito
   nosso sem repetição (`sdd-modulo-nlu.md`). Lacuna aberta desde a Etapa 1, paga
   agora não pelo motivo que o plano de saneamento dava ("revisar antes da
   Etapa 5"), e sim porque durante a Etapa 3 a família usa mais e mensagem
   perdida corrompe o corpus que a
   [ADR-0036](docs/01-adr/0036-instrumento-de-medicao-da-etapa-5.md) começou a
   gravar. **Sem ADR**: a suspeita de que contrariava as ADRs 0014/0020/0028 não
   se confirmou — aquelas recusam job para manter **estado derivado**, que sempre
   tem um leitor natural, e isto é **trabalho inacabado**, que não tem nenhum; e
   a solução não precisou de agendador, porque `InboundDispatcher` já roda cada
   mensagem numa virtual thread própria.
2. **Etapa 3**, com as decisões #23 (`açúcar 20`) e #25 (remover item) resolvidas
   **dentro** dela: as duas são o elo chegando cedo, não trabalho paralelo.
3. **Inverter a autoridade da política de confiança** — guardas determinísticas
   sobre o payload decidem o que der para decidir, faixa como resíduo. O
   diagnóstico está fechado (o `confianca` do modelo aparece anticorrelacionado
   nos casos que importam: 0,3 em `petshop`, `Pet shop 80` e `casa 20`, que
   estavam certos; 0,8 e 0,9 em `mercado`, `açúcar 20` e `remover chocolate`, que
   estavam errados). A ADR fica para **depois** da Etapa 3, para não congelar a
   lista de guardas antes de `fecharCompra` acrescentar as dele.
4. **Etapa 2b.**
5. **Etapa 5**, com gabarito anotado uma vez, sobre um sistema que parou de mudar
   de forma.

Fica deliberadamente de lado até lá, por não ser caminho crítico do elo: a
[decisão #22](docs/DECISOES-ABERTAS.md) (nome de produto sozinho), a
[#24](docs/DECISOES-ABERTAS.md) (consultar categorias pelo chat), o comando
`usar <família>` e o beco sem saída do `ChooseHousehold`.

## Etapa 3 — O elo (~1 semana)

`fecharCompra` atômico, `list_checkout`, `desfazer` reversível dos dois lados.

Os nove cenários estão escritos e marcados `@etapa3` na linha `Funcionalidade:`
do [`elo-fechamento-de-compra.feature`](docs/03-specs/features/elo-fechamento-de-compra.feature).
O `Etapa3AcceptanceTest` existe desde 2026-09-19 e **nasceu desabilitado**, pelo
mesmo motivo que o da Etapa 2 nasceu: escopo que falta deve ser visível na
própria suíte, e não só aqui. O `@Disabled` sai no primeiro passo de código da
etapa, não no último.

Levantamento feito ao criá-lo, antes de qualquer código: dos **42 passos
distintos** do arquivo, **18 já são atendidos** por `ExpenseByChatSteps` e
`ShoppingListSteps` — enviar mensagem, assertar despesa registrada, consultar a
lista, desfazer, reentrega. Os **24 restantes são novos**, e quase todos são
sobre o que só passa a existir agora: item mudando de status em lote, o
`list_checkout` ligando os dois lados, fechamento parcial, e a falha atômica. O
passo `que o registro de despesas está indisponível` é o mais importante dos 24:
sem ele o cenário de falha não prova a atomicidade da
[ADR-0031](docs/01-adr/0031-atomicidade-do-fechamento-de-compra.md) — o terceiro
dos quatro testes obrigatórios da
[estratégia de testes](docs/04-qualidade/estrategia-de-testes.md), e o único que
nunca existiu.

Nenhum cenário do elo é destacável para a 2a. Todos passam por `fecharCompra`,
e o cenário de falha ("nenhum item muda de status, nenhuma despesa é
registrada") só significa algo se a atomicidade existir.

**Entregável**: `comprei tudo, 180` fecha a lista e lança a despesa. É a
demonstração que vende o produto.

## Etapa 2b — Tarefas e agenda (~0,5 semana)

Executada depois da Etapa 3. Começa **escrevendo a `.feature` de tarefas**, que
não existe — é a única feature dos três domínios ainda não escrita, e sem ela
não há código a fazer (`CLAUDE.md`: comportamento vira `.feature` antes de
virar código).

Ao escrevê-la, decidir ou excluir explicitamente a [decisão aberta #12](docs/DECISOES-ABERTAS.md)
(recorrência de tarefas — modelo de dados). Tarefa que repete toda semana é
outro problema de modelagem, não um campo a mais.

Os cenários dela **não** levam `@etapa2`: essa tag é o portão da 2a e ficaria
vermelha por escopo que ainda não começou. Tag própria, decidida ao escrever.

**Entregável**: `lembrar de pagar o IPTU sexta` vira tarefa e a consulta de
pendências responde por chat. Com isso os seis fluxos do glossário estão
completos.

## Etapa 4 — PWA Vue (~2-3 semanas)

Foco na tela de **correção** de lançamento, não na de criação. É onde o usuário
conserta o erro da IA, e é o que decide se ele confia no sistema. A tela de
criação manual é secundária — quem quer criar manualmente já tem planilha.

Dois itens novos, registrados em 2026-09-04 ao validar a visão do produto com
o autor — fazem parte do escopo desta etapa, ainda sem desenho de tela:

- **Dashboard** ao abrir o app: últimas transações, entradas/saídas por
  período (diário, semanal, mensal, anual), e outras informações relevantes
  à primeira vista. Sem ADR própria ainda — layout e métricas exatas ficam
  para quando esta etapa começar de fato.
- **Central de pendências**: lista toda `PendingAction` ainda sem resolução
  — inclusive as que expiraram no chat sem resposta — com notificação,
  resolvível ali dentro do app. Mecanismo já decidido em [ADR-0018](docs/01-adr/0018-central-de-pendencias.md); a tela em si
  é trabalho desta etapa.

**Entregável**: instalável no celular, mostra o que veio do chat, permite
corrigir e recategorizar, com dashboard inicial e central de pendências.

## Etapa 5 — Uso real na família (4 semanas)

Sem feature nova era a premissa, e ela **já se provou errada**: a família usa o
produto desde 2026-09-05, e quatro dias de uso real (ver a seção acima)
produziram duas ADRs e sete commits. A etapa é uso, medição **e** o conserto do
que o uso expuser.

**Entregável**: as três métricas da
[ADR-0035](docs/01-adr/0035-o-que-a-etapa-5-mede.md), matriz de confusão por
tool, lista dos erros reais, limiar de confiança calibrado. Decisões abertas
3, 7, 8 resolvidas.

**Critério de continuidade**, com fórmula desde a
[ADR-0035](docs/01-adr/0035-o-que-a-etapa-5-mede.md) — antes dela o número
existia sem definição:

- **taxa de acerto de interpretação abaixo de 90% no fluxo de despesa → a Etapa 6
  não começa.** O desfecho anotado é executar, perguntar ou não entender;
  pergunta esperada conta como acerto, pergunta desnecessária conta como erro, e
  **nenhuma mensagem sai do denominador** — senão bastaria afrouxar o limiar para
  o portão subir com o produto piorando;
- **qualquer ocorrência de dinheiro gravado que a pessoa não escreveu → a Etapa 6
  não começa**, independente da taxa acima. É a única classe de erro que não se
  anuncia, e a [ADR-0034](docs/01-adr/0034-valor-so-vale-se-a-pessoa-escreveu-digito.md)
  foi a terceira correção da mesma coisa em duas semanas.

Precisão do interpretador é o produto.

**Pré-requisito, metade feita em 2026-09-19**
([ADR-0036](docs/01-adr/0036-instrumento-de-medicao-da-etapa-5.md)):
`inbound_message` passou a gravar `prompt_version` e `model_name`, a métrica é
calculada por versão de prompt — que é o que permite corrigir dinheiro errado no
meio da medição sem contaminar a janela anterior —, e o gabarito ganhou formato e
lugar ([`docs/04-qualidade/nlu-eval/`](docs/04-qualidade/nlu-eval/)).

**Continua faltando**: o `nlu-eval` em si (item 8 do que ficou de fora da Etapa 1)
e o dataset anotado. É o que ainda bloqueia a etapa — não o calendário.

## Etapa 6 — Verticalização comercial

Endurecer signup e verificação de telefone pra uso fora da família
(o mecanismo de convite em si já existe desde a Etapa 1, ADR-0020),
billing por household, LGPD (política, base legal, exclusão real).

**Entregável**: alguém de fora da sua família consegue criar conta e usar
sozinho, sem você intervir.

## Etapa 7 — WhatsApp

Meta Business verificado, número dedicado, templates aprovados, adaptador novo
em `channel`.

**Entregável**: o mesmo produto, no canal onde as famílias já estão.

## Etapa 8 — Proatividade

Lembretes, resumo semanal, alerta de lista. Preferindo Telegram por custo
(ADR-0006). Modelar custo por household antes de ligar.
