---
tipo: sdd
modulo: nlu
status: escrito
atualizado_em: 2026-09-19
adrs:
  - ADR-0004
  - ADR-0009
  - ADR-0020
  - ADR-0023
  - ADR-0024
  - ADR-0026
  - ADR-0029
  - ADR-0034
  - ADR-0035
  - ADR-0036
---

# SDD — Módulo `nlu`

## O valor tem de estar escrito na mensagem (ADR-0034)

Antes de montar `Intent.RegisterExpense`, `nlu` descarta o `valor` devolvido
pelo modelo quando **a mensagem do usuário não tem nenhum dígito**. Em uso real,
em 2026-09-19, `mercado` — uma palavra — voltou duas vezes com `valor: 50` e
gravou R$ 50,00; o prompt proibia inventar valor em duas linhas, e o par
`"mercado 50"` aparecia cinco vezes nele como exemplo. A
[ADR-0034](../01-adr/0034-valor-so-vale-se-a-pessoa-escreveu-digito.md) fecha
isso em código porque é a terceira vez que o valor sai errado em produção e as
duas anteriores também só pararam quando saíram do prompt. Sem valor, o desfecho
é a pergunta da [ADR-0033](../01-adr/0033-valor-ausente-com-categoria-conhecida-pergunta-o-valor.md).


## Responsabilidade

Montar o contexto do household (categorias existentes, contas), chamar o LLM
com function calling ([ADR-0004](../01-adr/0004-interpretacao-por-function-calling-com-politica-de-confianca.md), provedor Mistral na validação, [ADR-0009](../01-adr/0009-mistral-ai-como-provedor-de-llm-na-validacao.md)) e
devolver `Intent` + confiança. Não executa nada, não persiste dado de
domínio.

## Escopo desta versão (Etapa 2a)

Cinco tools no contexto de uma mensagem comum — `registrarDespesa`,
`adicionarItemLista`, `marcarItemComprado`, `consultarLista`,
`convidarMembro` — mais uma sexta, `confirmarCategoriaSugerida`, que só entra
quando há uma categoria oferecida a corrigir
([ADR-0026](../01-adr/0026-hierarquia-na-criacao-de-categoria-por-chat.md)).

**Corrigido em 2026-09-16 ([ADR-0029](../01-adr/0029-intencao-adiada-e-precedencia-de-mensagem-nova.md))**:
a sexta tool era declarada **sozinha** nesse momento, e isso era o furo. Com um
cardápio de uma opção só, o modelo não tinha como dizer "isto não responde à
pergunta" — uma mensagem sobre outro assunto podia virar categoria com nome
errado, levando junto o valor guardado na pendência. Agora a chamada com
pergunta em aberto leva as cinco do dia a dia **mais** a correção, e o modelo
escolhe entre responder e mudar de assunto. Continua sendo uma chamada por
mensagem: o que sumiu foi a *segunda*, não a primeira.

O que `nlu` **não** decide, aqui como em tudo: se a mudança de assunto vale.
Isso é confiança, e confiança é política de `conversation`.

**Nome de categoria nova no campo errado vale como sugestão** (decidido em
2026-09-18, com dado de uso real). O parâmetro `categoria` é um enum montado com
as categorias reais, mas o modelo nem sempre o respeita: `Madeireira 300` chegava
com `categoria: "Madeireira"` — valor fora do enum — e `categoria_sugerida`
vazio, e o código descartava a mensagem inteira. `Pet shop 80`, mensagem da
mesma forma, funcionava, porque ali o modelo acertou o campo.

A ordem agora é: categoria existente que casa vence; senão, `categoria_sugerida`;
senão, o nome que ficou em `categoria` vale como sugestão. É a contrapartida da
regra defensiva que a [ADR-0024](../01-adr/0024-categoria-sugerida-por-texto-livre.md)
deixou para a implementação — com os dois preenchidos, a que existe vence; com só
o errado preenchido, o nome não se perde. Nada é criado por engano: criar
categoria exige confirmação de qualquer jeito.

**A conversão para centavos é do código, não do modelo** (mudado em 2026-09-18,
com log de produção). O parâmetro era `valor_cents` e pedia a multiplicação ao
Mistral; um modelo de 8B erra aritmética, e errou: `Mercado 500 fechar lista`
voltou com 500 centavos e `comprei toda lista 500 mercado` com 5000, quando as
duas eram R$ 500,00. Os recibos disseram R$ 5,00 e R$ 50,00 — **erro silencioso,
em dinheiro**, que é a pior classe de erro deste produto.

Agora o parâmetro é `valor`, em reais, "exatamente como a pessoa escreveu", e
`NluService` multiplica por cem com `BigDecimal` construído a partir da forma
textual — nunca de `double`, porque `new BigDecimal(49.90d)` vale
49.8999999999999985.

Vale como princípio além deste parâmetro: **o que é determinístico não se
delega ao modelo.** O modelo lê intenção; conta, o código faz.

**A chamada escrita como texto é recuperada no adaptador** (2026-09-18). O
ministral-8b às vezes devolve `finish_reason: stop`, `tool_calls: null`, e a
chamada no corpo da mensagem: `casa 20` voltou com
`{"categoria_sugerida": "Casa", "valor": 20, "confianca": 0.3}` — interpretação
certa, formato certo, campo errado. Descartar isso e responder "não entendi" com
a resposta na mão é o pior desfecho disponível.

`MistralMessageInterpreter.recoverFromText` extrai o JSON do conteúdo e deduz a
tool pelos nomes dos parâmetros, **só quando a dedução é única** — `confianca`
existe em todas, então conteúdo só com ela não decide nada e o método desiste.
Desistir devolve "nenhuma tool escolhida", que já é o caminho de confiança baixa
da ADR-0004: nunca uma tool adivinhada.

Mora no adaptador do provedor, e não aqui: é defeito de provedor, e quem trocar
de modelo na Etapa 5 leva o problema — ou não — junto com o adaptador. Não é
coberto por cenário de aceitação porque o stub substitui exatamente a classe onde
o defeito mora; a cobertura é `MistralToolCallRecoveryTest`, com a resposta
literal colhida em produção.

**`convidarMembro` não está na lista de tools que a ADR-0004 enumera.** Aquela
lista descreve o mecanismo com os fluxos de domínio conhecidos em 2026-08-31, e
a [ADR-0020](../01-adr/0020-convite-de-membro.md) é posterior: ela exige que o
`OWNER` peça o convite ao bot, e esse comando parte de número já vinculado —
atravessa o pipeline de interpretação como qualquer outra mensagem, logo precisa
de tool. Não é contradição com a ADR-0004; é um fluxo que ela não conhecia.

**Não cobre ainda**: `registrarReceita`, `consultarSaldo`, `criarTarefa` e
`fecharCompra` — as quatro tools restantes da ADR-0004. As três primeiras não
têm `.feature` nesta etapa; `fecharCompra` é a Etapa 3.

## Depende de

- `finance` — leitura das categorias de despesa do household, com a hierarquia,
  pra montar o enum do parâmetro da tool.
- `shopping` — leitura dos itens pendentes da lista, pro modelo casar "comprei o
  arroz" com o item "Arroz".

As duas são **só leitura**, e nenhuma estava desenhada no `sdd-visao-geral.md`:
a de `finance` entrou na Etapa 1 e a de `shopping` na Etapa 2a, sempre pelo
mesmo motivo — a ADR-0004 manda dar ao modelo "as categorias e listas reais do
household como contexto", e isso obriga `nlu` a ler onde esses dados moram. A
regra de dependência do `sdd-visao-geral.md` foi corrigida junto, e o teste
ArchUnit que a trava também.

## Estrutura interna proposta

```
nlu/
  NluService                 -- interpret(...) -> Intent; interpretCategoryCorrection(...)
  ContextBuilder             -- categorias (finance) e itens pendentes (shopping)
  Intent                     -- interface selada: uma variante por acao possivel
  tools/
    RegisterExpenseTool          -- categoria (enum) | categoria_sugerida, valor,
                                    descricao, conta, confianca
    AddListItemTool              -- itens[] (nome, quantidade, unidade), confianca
    MarkItemPurchasedTool        -- item, confianca
    QueryListTool                -- confianca
    InviteMemberTool             -- nome, telefone, confianca (ADR-0020)
    ConfirmSuggestedCategoryTool -- nome, categoria_pai, confianca (ADR-0026)
  spi/
    MessageInterpreter         -- fronteira com o provedor de LLM
    InterpretationRequest      -- texto + contexto real do household + proposito
    ToolCall                   -- a chamada escolhida, crua (nome + argumentos)
    InterpretationProvenance   -- quem interpretou: prompt_version + model_name (ADR-0036)
  MistralMessageInterpreter  -- implementação LangChain4j (ADR-0009)
```

Duas decisões de 2026-09-05, ao implementar a Etapa 1:

**Existe uma interface entre `nlu` e o LangChain4j** (`ExpenseExtractor`), e
nenhum tipo da biblioteca a atravessa. Dois motivos: a [ADR-0009](../01-adr/0009-mistral-ai-como-provedor-de-llm-na-validacao.md) exige que trocar
de provedor na Etapa 5 seja configuração e não reescrita; e a
`estrategia-de-testes.md` manda stubbar o LLM em todo teste de aceitação — sem
essa fronteira, o stub teria que imitar a API do LangChain4j em vez de imitar o
resultado.

**O nome da tool e dos parâmetros continua em português** (`registrarDespesa`,
`categoria`, `valor` — `valor_cents` à época, renomeado em 2026-09-18, ver
acima) enquanto os identificadores Java viraram inglês
(`RegisterExpenseTool`, `interpret`). Não é inconsistência: o nome da tool é
dado enviado ao modelo, exatamente como a [ADR-0004](../01-adr/0004-interpretacao-por-function-calling-com-politica-de-confianca.md) o escreveu, e o modelo
interpreta mensagem em português.

Cinco de 2026-09-07, ao implementar a Etapa 2a.

**`ExpenseExtractor` virou `MessageInterpreter`, e o que atravessa a fronteira é
a chamada de função crua.** A interface antiga só sabia falar de despesa. Com
mercado e convite no mesmo pipeline há mais de uma tool possível para a mesma
mensagem, e quem escolhe entre elas é o modelo — então o que cruza a fronteira
passou a ser "a chamada que ele escolheu" (`ToolCall`: nome + mapa de
argumentos), e não "a despesa que ele extraiu". Continua sem nenhum tipo do
LangChain4j atravessando, que é o que a [ADR-0009](../01-adr/0009-mistral-ai-como-provedor-de-llm-na-validacao.md)
exige. Um mapa, e não um record por tool, de propósito: se a fronteira soubesse
quais tools existem, cada tool nova exigiria mexer no adaptador do provedor.

**A confiança vem do modelo, num parâmetro `confianca` declarado em toda tool.**
É o desenho que a própria ADR-0004 pressupõe ao registrar como fraqueza central
que "`confidence` reportado por LLM é mal calibrado por natureza" e que o limiar
terá de ser calibrado empiricamente — não haveria o que calibrar se o número
fosse calculado em código. `nlu` só valida a faixa 0-1 e trata ausência como
zero; quem compara com limiar é `conversation`.

**As alternativas da pergunta de ambiguidade são montadas aqui, por semelhança
de nome.** Quando a confiança é média, a pergunta precisa de opções numeradas, e
o modelo devolve uma categoria só. `nlu` monta a lista com a escolhida na frente
e as categorias que compartilham a primeira palavra com ela ("Mercado" ↔
"Mercado livre"). É heurística sobre os **nomes das categorias**, nunca sobre o
texto da pessoa — quem julgou a mensagem ambígua foi o modelo, ao devolver
confiança média; aqui só se monta o cardápio. Se essa heurística se mostrar
pobre na Etapa 5, o conserto é declarar as candidatas na própria tool, que é a
alternativa registrada como não escolhida agora.

**`valor` saiu de `required`** (`valor_cents` à época). "paguei o mercado" precisa ser expressável
como despesa sem valor. Com o parâmetro obrigatório, o modelo não chamaria tool
nenhuma, e o bot perderia a categoria que já tinha reconhecido — sem ela não há
como fazer a "pergunta curta pedindo o valor" que o cenário exige. O único
parâmetro obrigatório de `registrarDespesa` hoje é `confianca`.

**Se o modelo preencher `categoria` e `categoria_sugerida` juntos, a categoria
existente vence.** A ADR-0024 deixa essa regra defensiva explicitamente para a
implementação. O critério: criar categoria é o caminho caro e irreversível dos
dois, então na dúvida não se cria. Chamada sem nenhum dos dois vira confiança
baixa — não dá para registrar sem categoria, nem para oferecer criar o que não
tem nome.

## Fluxo

1. `conversation` chama `NluService.interpret(householdId, texto)`.
2. `ContextBuilder` busca as categorias de despesa (em `finance`) e os itens
   pendentes da lista (em `shopping`). RLS já ativa pelo contexto de tenant
   resolvido por `identity`.
3. Monta as tools. O enum de `categoria` recebe as categorias reais; **quando o
   household não tem nenhuma, a propriedade some do schema** em vez de virar um
   enum vazio, que não é schema válido ([ADR-0024](../01-adr/0024-categoria-sugerida-por-texto-livre.md)).
   Sem ela, o único caminho que resta ao modelo é `categoria_sugerida` — é assim
   que a primeira mensagem de um household novo dispara o fluxo de criação que a
   [ADR-0013](../01-adr/0013-household-novo-comeca-sem-categorias.md) prometeu.
4. Chama o LLM (Mistral, function calling). Itens de lista vão no prompt de
   sistema e **não** viram enum: comprar algo que ninguém pediu é caso legítimo,
   com cenário próprio. Categoria vira enum porque o modelo precisa ser impedido
   de inventar uma (ADR-0004) — a assimetria é deliberada.
5. O adaptador devolve `ToolCall` cru; `NluService` traduz em `Intent` tipada,
   resolvendo nome de categoria para id.
6. Uma tool só por mensagem, mesmo que o modelo devolva várias: uma mensagem é
   uma ação e um recibo. "acabou arroz, leite e café" já é um único
   `adicionarItemLista` com três itens.

### O rótulo do enum de categoria

É o nome simples quando ele é único no household. Quando duas categorias têm o
mesmo nome em ramos diferentes — "Presente" dentro de "Educação" e dentro de
"Lazer", exemplo da própria [ADR-0016](../01-adr/0016-subcategoria.md) —, ambas
passam a se apresentar como "Pai > Filho". Enum com valor repetido seria uma
escolha que o modelo faz e o código não consegue desfazer.

### A extração não inventa hierarquia

Na chamada original, `nlu` extrai só o nome literal mencionado
("restaurante"), nunca a categoria-pai — mesma disciplina de "só o resíduo" da
[ADR-0023](../01-adr/0023-descricao-de-lancamento-extraida-pelo-llm.md) e da
ADR-0024, e exigência explícita da
[ADR-0026](../01-adr/0026-hierarquia-na-criacao-de-categoria-por-chat.md). O pai
só aparece quando a pessoa o escreve, respondendo à pergunta.

## Erros

LLM indisponível ou timeout → **duas tentativas dentro da mesma tarefa**, com
espera de 2s entre elas, e recibo de erro no chat quando as duas falham. A
mensagem fica `FAILED`.

Implementado em 2026-09-21, fechando a lacuna aberta desde a Etapa 1 — "retry com
backoff" estava no desenho e nunca no código, e a mensagem morria na primeira
falha de rede. O agravante é a idempotência da
[ADR-0005](../01-adr/0005-idempotencia-de-mensagens-recebidas.md): o webhook já
respondeu 200, então o Telegram não reenvia, e se reenviasse o guard descartaria.
Não há segunda chance vinda de fora.

Quatro decisões que fazem parte disso, e não são detalhe de implementação:

- **Não é job nem agendador.** `InboundDispatcher` já roda cada mensagem numa
  virtual thread própria, fora do ciclo do request; isto é um laço com espera
  dentro dessa mesma tarefa. Nada do que as ADRs
  [0014](../01-adr/0014-fechamento-de-fatura-sob-demanda.md),
  [0020](../01-adr/0020-convite-de-membro.md) e
  [0028](../01-adr/0028-recorrencia-de-tarefa.md) recusaram entra aqui: aquelas
  falam de **estado derivado**, que sempre tem um leitor natural, e isto é
  **trabalho inacabado**, que não tem nenhum.
- **Duas tentativas, não três.** O LangChain4j já tenta três vezes sozinho, em
  ~1,7s (comentado em `application.properties`) — duas aqui são até seis chamadas
  ao provedor.
- **429 não é repetido**, apesar de a biblioteca classificar `RateLimitException`
  como retriável. Insistir gasta cota do tier gratuito para provavelmente tomar
  outro 429, e a [ADR-0009](../01-adr/0009-mistral-ai-como-provedor-de-llm-na-validacao.md)
  já registra o throttling como negativa conhecida. O que não é
  `RetriableException` — schema inválido, chave errada — também não é repetido:
  é defeito nosso, e repetir só atrasa o aviso.
- **A política mora no adaptador**, e não em `NluService`, pelo mesmo motivo de
  `recoverFromText`: decidir o que vale repetir exige olhar o tipo da exceção do
  provedor, e nenhum tipo do LangChain4j atravessa a fronteira `MessageInterpreter`
  ([ADR-0009](../01-adr/0009-mistral-ai-como-provedor-de-llm-na-validacao.md)).

Custo declarado: com `timeout` de 20s e duas tentativas, o pior caso até o recibo
de erro fica em torno de **42s**. Se isso se mostrar longo demais no uso real, o
que se mexe é o timeout, não o número de tentativas — é ele que domina a conta.

O que **não** foi feito, e a frase anterior prometia: a mensagem não volta a
ficar `RECEIVED` para ser retomada mais tarde. Esgotadas as tentativas, ela morre
como `FAILED` com recibo, e quem reescreve é o usuário. Retomar depois exigiria
ou um gatilho vindo da próxima mensagem da conversa — que produz recibo fora de
ordem, sobre uma mensagem de horas atrás — ou o agendador que as três ADRs acima
recusaram. Nenhum dos dois se justifica pelo caso que sobra: aplicação fora do
ar, que é raro e cuja mitigação honesta é o aviso que o usuário já recebe.

~~Household sem nenhuma categoria ([ADR-0013](../01-adr/0013-household-novo-comeca-sem-categorias.md)) não gasta chamada de modelo: o
enum do parâmetro ficaria vazio, que não é schema válido. Devolve confiança
baixa direto.~~ — deixou de valer em 2026-09-07: a ADR-0024 resolveu isso
omitindo a propriedade `categoria` do schema em vez de recusar a chamada.
Household novo agora gasta chamada de modelo como qualquer outro, e é justamente
nele que o fluxo de criação de categoria precisa funcionar.

## Testes

- ArchUnit: `nlu` não importa `channel`, `conversation`, `identity` nem `tasks`.
  Importa `finance` e `shopping`, só leitura.
- Cenários `@etapa1` e `@etapa2` das três features.
- `InterpretationFingerprintTest`: a impressão digital é estável para o mesmo
  material (um hash que mudasse a cada boot faria cada mensagem cair no próprio
  grupo, e não haveria o que agrupar) e a descrição de parâmetro é de fato lida,
  e não o nome do tipo.

## O que a Etapa 5 mede aqui (decidido em 2026-09-19)

A [ADR-0035](../01-adr/0035-o-que-a-etapa-5-mede.md) escolheu **desfecho**, e não
tool escolhida, como unidade da taxa de acerto. A matriz de confusão por tool
continua sendo o dado que decide se o cardápio de seis precisa encolher — é o
gatilho de revisão logo abaixo —, mas deixou de ser a métrica principal por um
motivo que nasce neste módulo: **a tool certa com o campo errado é o caso que
mais dói**, e a matriz por tool não o enxerga. `registrarDespesa` com valor
inventado acerta a tool e destrói a confiança no saldo (ADR-0034).

Os campos que entram na conta são **categoria, valor e conta**; `descricao` fica
fora, por decisão já tomada na
[ADR-0023](../01-adr/0023-descricao-de-lancamento-extraida-pelo-llm.md).

**A fronteira passou a responder quem interpretou**
([ADR-0036](../01-adr/0036-instrumento-de-medicao-da-etapa-5.md)):
`MessageInterpreter.provenance()` devolve `prompt_version` e `model_name`, e é
método da interface **sem `default`** — adaptador novo de provedor tem de
responder isso antes de entrar em produção, pelo mesmo motivo que a ADR-0009
proíbe tipo de biblioteca atravessar aqui.

`prompt_version` é impressão digital SHA-256 (12 caracteres) da parte
**estática** do que este módulo manda ao modelo: os três textos de prompt, as
seis tools, e o nome e a **descrição de cada parâmetro**. A descrição entra
porque é ela que muda o comportamento — as duas últimas mudanças observadas em
produção foram edições em `confianca` e em `marcarItemComprado`, e nenhuma das
duas seria tratada por quem edita como "versão nova". Fica de fora o contexto
injetado por mensagem (categorias, itens, pergunta pendente): aquilo é contexto,
não versão.

A leitura da descrição é por reflexão — os tipos de schema do LangChain4j não
compartilham um `description()` em interface comum — e falhar degrada para o nome
do tipo em vez de quebrar a interpretação. `InterpretationFingerprintTest` existe
para que essa degradação não passe silenciosa num upgrade da biblioteca.

`nlu` **não grava** nada disso: quem grava é `channel`, no log de ingestão, e o
valor sobe dentro do `ProcessingOutcome`.

## Gatilhos de revisão

- **Etapa 3**: entra `fecharCompra`. O contexto já leva os itens pendentes, então
  o `ContextBuilder` não muda — só a lista de tools.
- **Etapa 5**: seis tools disputando a escolha do modelo em toda mensagem é
  bastante contexto. Se a matriz de confusão mostrar tool errada escolhida com
  frequência, é aqui que se decide reduzir o cardápio por situação — e a
  `confirmarCategoriaSugerida`, que já é declarada só no momento em que serve, é
  o precedente de como fazer isso.
- Se a exclusividade mútua entre `categoria` e `categoria_sugerida` for violada
  com frequência relevante pelo modelo, a ADR-0024 já prevê reabrir a decisão —
  inclusive voltar à tool `criarCategoria` separada que ela descartou.
