---
tipo: entrega
etapa: 2a
status: entregue
data: 2026-09-07
modulos:
  - conversation
  - nlu
  - finance
  - shopping
  - identity
  - channel
  - banco
adrs:
  - ADR-0004
  - ADR-0012
  - ADR-0013
  - ADR-0016
  - ADR-0018
  - ADR-0020
  - ADR-0023
  - ADR-0024
  - ADR-0025
  - ADR-0026
---

# Etapa 2a — Mercado, ambiguidade e convite por chat

**Critério de pronto do [ROADMAP](../../ROADMAP.md)**: `acabou o arroz` entra na
lista, `o que está faltando?` responde, `pet shop 80` oferece criar a categoria e
grava depois do `sim`, `restaurante eu e esposa 90` corrigido para "dentro de
alimentação" cria a hierarquia certa mesmo quando nada existe ainda, e `desfazer`
com pergunta pendente cancela a pergunta em vez de estornar. Os 22 cenários
`@etapa2` verdes. **Atingido**, sem ressalva de escopo.

O ROADMAP acertou o diagnóstico: **a espinha era `PendingAction`, não mercado.**
Os cinco cenários espalhados por três features são a mesma mecânica com conteúdo
diferente, e depois que ela existiu, mercado inteiro — nove cenários — saiu
quase de graça.

## Onde o código está

`server/`, mesmo módulo Maven da Etapa 1. Nenhum módulo novo de build; um pacote
de domínio novo (`shopping`) e um crescimento grande em `conversation`.

## O que foi construído

### Banco

Duas migrations. `V2__pending_action.sql` cria `pending_action` com o schema
exato do [`modelo-de-dados.md`](../02-arquitetura/modelo-de-dados.md), sem coluna
a mais. `V3__shopping_list.sql` cria `shopping_list` e `list_item`.
`list_checkout` **não** entrou: é o elo, Etapa 3, e criá-la antes do
comportamento que a usa deixaria schema morto no banco.

Dois índices únicos parciais que são invariante de estrutura, não otimização:
uma lista `ACTIVE` por household, e um item `PENDING` por nome por lista. O
segundo é parcial de propósito — comprado o arroz de hoje, ele pode faltar de
novo amanhã.

### `conversation`

O módulo que mais cresceu, como o gatilho de revisão do SDD dele previa.
`PendingAction` (entidade, repositório, serviço), o curto-circuito determinístico
de `sim`/`não`/`desfazer`/número, a política de confiança com as três faixas da
[ADR-0004](../01-adr/0004-interpretacao-por-function-calling-com-politica-de-confianca.md),
e o `desfazer` com a precedência da
[ADR-0025](../01-adr/0025-desfazer-precedencia-e-escopo.md).

A decisão estrutural: **pergunta em aberto é consultada antes de qualquer
interpretação**. É o que faz o curto-circuito da regra 6 valer de verdade — sem
essa ordem, responder `sim` gastaria uma chamada de modelo antes de alguém
descobrir que era só um `sim`.

### `nlu`

De uma tool para seis. `registrarDespesa` ganhou `categoria_sugerida`,
`descricao` e `confianca`, e perdeu `valor_cents` do `required`. Entraram
`adicionarItemLista`, `marcarItemComprado`, `consultarLista`, `convidarMembro`
e `confirmarCategoriaSugerida`.

A fronteira com o provedor mudou de forma: `ExpenseExtractor` (que só sabia
falar de despesa) virou `MessageInterpreter`, e o que atravessa é a chamada de
função crua — nome da tool e mapa de argumentos. Continua sem nenhum tipo do
LangChain4j cruzando, que é o que a
[ADR-0009](../01-adr/0009-mistral-ai-como-provedor-de-llm-na-validacao.md) exige.

### `finance`

`registerExpense` ganhou `description`
([ADR-0023](../01-adr/0023-descricao-de-lancamento-extraida-pelo-llm.md), sem
migration — a coluna já existia). Entraram `reverseLatest` (o estorno) e
`CategoryService` (criação de categoria confirmada, com hierarquia e com o
limite de um nível da [ADR-0016](../01-adr/0016-subcategoria.md)). `CategoryView`
passou a carregar o pai.

### `shopping`

Módulo novo, com [SDD próprio](../02-arquitetura/sdd-modulo-shopping.md) escrito
antes do código. Adicionar item (um ou vários numa mensagem), marcar comprado,
consultar o que falta.

### `identity` e `channel`

`InviteIssuer` (emissão de convite pelo `OWNER`,
[ADR-0020](../01-adr/0020-convite-de-membro.md)), `MemberDirectory` (nome de
membro para quem precisa escrever "pedido por Ana"), `InviteLinkPort` +
`TelegramInviteLink`, e `channel_identity_id` dentro de `ResolvedContext`.

## Testes

89 no total, todos verdes: 22 cenários `@etapa2` (193 passos), 16 `@etapa1`, os
quatro obrigatórios da
[`estrategia-de-testes.md`](../04-qualidade/estrategia-de-testes.md), as 11
regras de ArchUnit, o `CategoryHierarchyTest` novo e — a partir da revisão de
2026-09-08 — `MessageBundleTest` e `DocumentationCoherenceTest`.

O teste de vazamento de tenant ganhou as três tabelas novas.
`pending_action` em especial guarda texto de conversa — é o tipo de dado cujo
vazamento não teria conserto.

`CategoryHierarchyTest` cobre o limite de um nível pelo serviço direto. A
[ADR-0016](../01-adr/0016-subcategoria.md) exigia esse teste desde que foi
aceita, e a ADR-0026 acrescentou que ele precisa cobrir os dois caminhos de
entrada — o do chat está no cenário `@etapa2` correspondente.

### Sobre o stub do LLM

Uma entrada fixa, e só uma: `restaurante eu e esposa 90`. A regra geral do stub
("tudo que acompanha o valor é o nome da categoria sugerida") resolve
`pet shop 80` e `rodízio de pizza 40`, mas produziria a categoria "Restaurante eu
e esposa" nesse caso — separar o nome do resíduo que vira descrição é exatamente
o julgamento que a ADR-0023 delega ao modelo. A
`estrategia-de-testes.md` chama isso de "Intent fixa"; está declarado no próprio
stub para não virar parser escondido.

Um passo de asserção compara descrição sem acento e sem caixa. O cenário manda
`60 farmacia - remedio joaquim` (sem acento) e espera "remédio" e "Joaquim": o
que ele de fato garante é que o resíduo chegou ao campo, não que o modelo
corrigiu a grafia — e a qualidade da descrição está fora da métrica da Etapa 5
por decisão da própria ADR-0023.

## Decisões tomadas ao implementar

Estão nos SDDs, não repetidas aqui. As quatro que mudam o desenho e não só o
código:

1. **O tipo da pendência vive no campo `type` de `intent_json`**, não em coluna
   nem deduzido de `options_json` (`sdd-modulo-conversation.md`). Era a decisão
   que o prompt desta etapa deixou explicitamente em aberto.
2. **A pendência pertence à conversa, não ao household.** A ADR-0025 diz
   "household **ou** membro" e os dois não são a mesma coisa; venceu o membro,
   por [ADR-0008](../01-adr/0008-interacao-1-1-por-membro-nunca-em-grupo.md) —
   chat é 1:1, a pergunta foi feita num fio e é nele que se resolve. O estorno
   continua household-wide.
3. **A confiança vem do modelo**, num parâmetro `confianca` em toda tool, com
   dois limiares em config provisória. É o desenho que a ADR-0004 pressupõe ao
   chamar de fraqueza central o fato de o `confidence` do LLM ser mal calibrado.
4. **O link do convite é montado em `channel`**, através de um porto novo. A
   ADR-0020 diz que o bot devolve o link, mas o formato é do provedor: montá-lo
   em `identity` quebraria a regra 5 de um jeito que o ArchUnit não pegaria,
   porque é string e não tipo.

## Contradições entre documentos aceitos, e como foram resolvidas

Três, todas registradas no SDD do módulo correspondente:

**ADR-0026 contra si mesma, sobre onde procurar a categoria-pai.** Ela diz
"procura `categoria_pai` por nome entre as categorias-**raiz**" e, duas frases
depois, "se o nome resolvido já for, ele mesmo, uma subcategoria, a criação é
recusada". As duas não fecham: procurando só entre raízes, um pai que é
subcategoria nunca seria encontrado e a recusa jamais dispararia — "rodízio de
pizza dentro de restaurante" criaria uma raiz "Restaurante" homônima em
silêncio. Prevaleceu a segunda frase, que é a que tem cenário escrito.

**ADR-0018 contra ADR-0025, sobre `desfazer` fora do prazo.** A 0018 manda
responder "expirou, veja no aplicativo" a toda resposta curta fora do TTL; a
0025 dá precedência sobre o estorno só à pendência **não expirada**. Prevalece a
0025 para `desfazer` e a 0018 para `sim`/`não`/número.

**A regra de dependência do `sdd-visao-geral.md` estava incompleta**, do mesmo
jeito que ficou na Etapa 1: `conversation → shopping` (execução) e
`nlu → shopping` (leitura do contexto) não estavam desenhadas, e a segunda é
exigência direta da ADR-0004 ("as categorias e listas reais do household como
contexto"). Documento e teste ArchUnit corrigidos juntos.

## O que ficou de fora

### Por decisão de escopo (ROADMAP)

- O elo lista → despesa (`fecharCompra`, `list_checkout`) — Etapa 3.
- Tarefas e agenda — Etapa 2b, `.feature` ainda não escrita.
- A tela da central de pendências — Etapa 4. O mecanismo está pronto e a consulta
  é `WHERE household_id = ? AND resolution IS NULL`, como a ADR-0018 previu.

### Herdado da Etapa 1 e ainda não feito

As três lacunas que o ROADMAP mandava herdar **não** foram fechadas, e nenhuma
tem cenário `@etapa2` que as cubra:

- **botão nativo de compartilhar contato** no Telegram (hoje a pessoa digita);
- **retry com backoff na falha de LLM** — continua virando recibo de erro na
  primeira tentativa, com a mensagem em `FAILED`;
- **comando de trocar o household ativo**
  ([ADR-0007](../01-adr/0007-pessoa-em-multiplos-households.md)) — o bot ainda só
  pergunta qual família quando há mais de uma e nenhuma ativa; não há como
  trocar depois.

Ficam para a Etapa 3 ou para quando o uso real cobrar.

### Lacunas conhecidas dentro do que foi entregue

- **Mensagem nova com pergunta pendente recebe a pergunta de volta.** Se há
  pergunta aberta e a pessoa manda `farmácia 60`, o bot repete a pergunta em vez
  de registrar a despesa. É o comportamento genérico que a ADR-0018 descreve, e
  nenhum cenário testa o contrário — mas pode ser irritante na prática. Está
  registrado como gatilho de revisão em `sdd-modulo-conversation.md`, não
  resolvido por conta própria.
- **`EXPIRED` é valor morto em `pending_action.resolution`.** Está no `CHECK` e
  nunca é gravado, porque a ADR-0018 descartou marcar expiração
  automaticamente. Fica reservado para uma desistência explícita registrada pela
  web.
- **Correspondência de nome é exata, sem acento nem plural.** "Alimentacao" sem
  acento cria uma raiz nova em vez de reconhecer "Alimentação"; "cafe" e "café"
  são dois itens de lista. A ADR-0024 já registra esse risco em aberto para
  categoria; vale igual para item, e a decisão de aproximar deveria valer para
  os dois ao mesmo tempo.
- **Seis tools disputam a escolha do modelo em toda mensagem.** Não medido. Se a
  matriz de confusão da Etapa 5 mostrar tool errada escolhida com frequência, é
  em `nlu` que se reduz o cardápio por situação —
  `confirmarCategoriaSugerida`, declarada só no momento em que serve, já é o
  precedente de como fazer isso.
- **A lista de alternativas da pergunta de ambiguidade é heurística.** Categoria
  escolhida mais as que compartilham a primeira palavra com ela. Funciona para
  "Mercado" ↔ "Mercado livre" e não tem nada que garanta que funcione para
  sinônimos. O conserto, se preciso, é o modelo declarar as candidatas.

## Inconsistências encontradas na documentação

Três resolvidas durante a implementação, já descritas acima ("Contradições entre
documentos aceitos"). A auditoria de coerência rodada ao fechar a etapa
(2026-09-08) achou mais uma, e ela é a mais séria das quatro.

**Resolvida em 2026-09-08, e era a mais grave: a
[ADR-0015](../01-adr/0015-internacionalizacao.md) estava sendo contrariada pelo
código desde a Etapa 1.** Ela decide, para a "Etapa 1 em
diante", que "nenhum texto voltado ao usuário (recibo, pergunta de confirmação,
erro no chat) é literal no código" — tudo viria de `messages_pt_BR.properties`
resolvido por um `member.preferred_locale`. Nada disso existe: não há arquivo de
mensagens, não há `ResourceBundle`, e a coluna não está no schema.
`OnboardingMessages` (Etapa 1) e `ReceiptFormatter` (esta etapa) somam mais de
vinte textos literais em português.

Pior: é exatamente o desfecho que a própria ADR previu ao descartar a
alternativa B — "texto literal em português vai se espalhar por
`conversation`/`channel` durante as Etapas 1-5, e extrair isso pra arquivo de
mensagens depois de dezenas de recibos e perguntas escritas é refactor mecânico,
mas extenso". A alternativa descartada foi o que aconteceu, sem ninguém decidir
que aconteceria.

A ADR-0015 é também a única ADR aceita que **nenhum** documento e **nenhum**
código citam — foi aceita em 2026-08-31 e nunca chegou a um SDD. Órfã e
contrariada é o mesmo problema visto de dois ângulos.

Isto violava o item "Nenhuma ADR aceita foi contrariada (ou existe ADR nova
superando)" da Definition of Done. O autor decidiu implementar em vez de superar,
e foi feito no mesmo dia:

- `V4__member_preferred_locale.sql` — a coluna, com default `pt-BR`.
- `common/i18n/Messages` + `MessageKey` + `messages_pt_BR.properties` — 53
  mensagens, todo texto que o usuário lê.
- `ReceiptFormatter` e `OnboardingMessages` reescritos sem um literal sequer;
  `ResolvedContext` carrega o idioma, e `Reply` o leva junto do endereço de
  resposta — a ADR-0015 registra que esquecer de propagar o locale "gera resposta
  no idioma errado, silenciosamente", e andar junto do endereço é o que torna
  difícil esquecer.
- Só `pt-BR` foi escrito, que é a decisão 2 da própria ADR: `en` e `es` entram
  como tradução deste arquivo quando houver pedido real.

Os 38 cenários de aceitação passaram sem alteração nenhuma nos `.feature` — eles
afirmam sobre o texto que chega ao usuário, então continuarem verdes é a prova de
que o refactor não mudou comportamento.

**Fora de escopo, declarado**: o comando para trocar de idioma, que a ADR-0015
menciona de passagem. Trocaria para um idioma que ainda não tem conteúdo.

Duas coisas ficam registradas sobre *como* isso passou, porque a causa é
sistêmica e não distração: os dois prompts de etapa navegaram por **lista curada
de ADRs** ("ADRs, nesta ordem de relevância: 0004, 0018, 0024…"), e a 0015 não
estava em nenhuma das duas listas; e a entrega da Etapa 1 também não a lista no
frontmatter. Uma ADR aceita fora da lista de leitura de toda etapa seguinte é
invisível. `DocumentationCoherenceTest` passou a quebrar o build quando uma ADR
aceita não é refletida por nenhum SDD — é o que faltava para que isso não
dependesse de alguém lembrar.

## Depois da entrega: o que a revisão de 2026-09-08 mudou

O autor revisou esta entrega e cobrou três coisas, todas feitas no mesmo dia:

1. **A ADR-0015 foi implementada** (acima), em vez de superada.
2. **`shopping` ganhou lastro**: a
   [ADR-0027](../01-adr/0027-lista-de-compras-unica-e-sob-demanda.md) registra as
   decisões de produto de mercado que existiam só no SDD — lista única, criada
   sob demanda, item repetido reconhecido, quantidade opcional. Escrita **depois**
   do código, o que é inversão do processo e está declarado dentro dela.
3. **A recorrência de tarefa foi decidida**, e não adiada:
   [ADR-0028](../01-adr/0028-recorrencia-de-tarefa.md) — regra separada da
   ocorrência, uma linha por sábado, próxima materializada ao concluir a
   anterior, sem job. Fecha a [decisão aberta #12](../DECISOES-ABERTAS.md) e
   resolve de quebra a divergência entre ela e o modelo de dados sobre *quando*
   decidir. Nada de `tasks` foi implementado — é Etapa 2b.

E o guard-rail que amarra as três: `DocumentationCoherenceTest` cobra o vínculo
ADR ↔ SDD nos dois sentidos, mais numeração sequencial e reciprocidade de
`supera`/`superada_por`. Exceção é linha de código com motivo escrito — hoje
duas: ADR-0006 (billing, sem módulo) e ADR-0028 (tasks ainda não tem SDD). A
regra entrou no CLAUDE.md.

## Alterações em documento já existente

- `sdd-modulo-conversation.md`, `sdd-modulo-nlu.md`, `sdd-modulo-finance.md`,
  `sdd-modulo-identity.md`, `sdd-modulo-channel.md`, `sdd-visao-geral.md` —
  escopo, dependências e decisões de implementação.
- `sdd-modulo-shopping.md` — **novo**.
- `modelo-de-dados.md` — o que existe no banco hoje, e as três coisas fixadas em
  `pending_action` sem mudar o schema.
- `glossario.md` — `Ação pendente` reescrita, `Curto-circuito` acrescentado,
  `Lista de compras` e `Item da lista` detalhados.
- `server/README.md` — as duas instruções de SQL manual foram **removidas**, não
  mantidas como alternativa: seguir usando SQL passaria por cima do fluxo que
  esta etapa entregou. Entrou a seção de configuração provisória e a variável
  `TELEGRAM_BOT_USERNAME`.
- `ROADMAP.md` — Etapa 2a marcada como fechada.
- `sdd-visao-geral.md` — a seção "Regra de dependência" passou a citar a
  [ADR-0001](../01-adr/0001-monolito-modular-em-quarkus.md), que ela
  operacionaliza e que até aqui nenhum documento referenciava.

Correções de coerência feitas em 2026-09-08, depois da auditoria: três
comentários de código que descreviam o estado anterior à etapa
(`Category`, `InviteFlow`, `Fixtures`) e diziam que criar categoria e emitir
convite por chat "não entra nesta etapa"; quatro usos de "transação" para
movimento financeiro, que o glossário reserva para transação de banco; e dois
verbetes que faltavam no glossário — `Estorno` e `Correção livre` —, ambos já em
uso no código, contra a regra do CLAUDE.md de que termo entra no glossário antes
de virar código.

Nenhuma ADR nova foi necessária: as 0024-0026, aceitas em 2026-09-07 antes desta
etapa começar, cobriram o que precisava de decisão.
