---
tipo: adr
numero: 41
status: aceita
data: 2026-10-31
modulos:
  - channel
  - conversation
  - finance
  - banco
  - glossario
depende_de: [ADR-0005, ADR-0008, ADR-0012, ADR-0025, ADR-0029, ADR-0032, ADR-0035, ADR-0036, ADR-0037, ADR-0040]
supera: []
superada_por:
corrigida_em:
---

# ADR-0041 — O recibo ganha desfazer e trocar categoria, e o botão desfaz o lançamento do próprio recibo

- **Impacta**: `conversation` (os recibos de despesa e de fechamento passam a
  ter botões, e três redesenhos novos), `finance` (estorno de um lançamento
  determinado, e troca de categoria com histórico), `channel` (o codec passa a
  levar duas referências no mesmo botão), banco (`transaction_edit`, que a
  [ADR-0012](0012-edicao-de-lancamento-entre-membros.md) desenhou e nenhuma
  migration criou), glossário (tipos novos de Ação de botão). Encerra a
  [decisão aberta #29](../DECISOES-ABERTAS.md) e abre a #30 (janela do
  desfazer pelo botão). Entra no [ROADMAP](../../ROADMAP.md) **antes** de
  "inverter a autoridade da política de confiança", contra o que a #29
  recomendava — o motivo está em Alternativas, G.

## Contexto

O pedido, de 2026-10-31: o recibo de um lançamento deveria ter um botão de
desfazer e um botão de trocar a categoria. O caso que motiva a troca é o
fechamento pelo carrinho: desde a
[ADR-0040](0040-botoes-na-lista-de-compras-e-carrinho-por-fio.md) (item 4), a
categoria dele vem do último fechamento não estornado, sem pergunta. Quando
ela está errada, o único caminho hoje é `desfazer` e lançar de novo pelo
texto — que não passa pela lista e perde o elo.

As forças:

- **O `desfazer` digitado não serve para um botão.** Ele estorna o lançamento
  não estornado mais recente do household
  ([ADR-0025](0025-desfazer-precedencia-e-escopo.md)). Um botão preso a um
  recibo que estornasse outro lançamento seria errado por construção, e os
  dois só coincidem enquanto ninguém lançou nada depois. No fio 1:1
  ([ADR-0008](0008-interacao-1-1-por-membro-nunca-em-grupo.md)), o lançamento
  mais recente da família pode ter sido feito no telefone de outra pessoa.
- **Estorno não tem volta no domínio.** O recibo fica no histórico do chat para
  sempre, e um toque errado num recibo de semanas atrás some com uma despesa.
  A ADR-0025 recusou janela de tempo para o texto por falta de dado; o botão
  reabre a questão com motivo mais forte, porque tocar sem querer é muito mais
  fácil que escrever `desfazer` sem querer.
- **Resposta digitada vai ao modelo.** Uma troca de categoria por texto ("qual
  a categoria certa?") abriria uma pendência cuja resposta não é curto-circuito
  (regra 6). A lacuna já registrada na
  [entrega dos botões](../05-entregas/botoes-na-lista-de-compras.md) mostra o
  que acontece: "mercado", respondido com confiança alta, supera a pergunta
  ([ADR-0029](0029-intencao-adiada-e-precedencia-de-mensagem-nova.md)) e vira
  outra intenção. Categoria que não existe abriria a oferta de criar no meio de
  uma correção.
- **A correção já tem modelo de dados.** A ADR-0012 decidiu em 2026-09-04 que
  correção de campo de lançamento é uma linha em `transaction_edit`, com quem
  editou, quando e o que mudou de quê para quê. A tabela nunca foi criada:
  nenhuma correção existia até aqui.
- **O botão tem 64 bytes.** O `callback_data` do Telegram cabe um código e um
  UUID em forma textual (39 bytes no maior caso de hoje, `TelegramActionCodec`).
  Trocar o lançamento X para a categoria Y leva dois UUIDs — 72 caracteres só
  neles.

O que já existe e pode ser reaproveitado: `reverseLatest` publica
`ExpenseReversed` na mesma transação, e é o observador em `shopping` que
devolve os itens do fechamento ([ADR-0032](0032-desfazer-alcanca-o-fechamento-inteiro.md))
— estornar um lançamento determinado é o mesmo caminho com outro alvo.
`lastCheckoutCategory` lê a categoria **atual** do lançamento de cada
fechamento. `ChatReply` e `ChatAction` já comportam botão, substituição da
mensagem de origem e aviso curto.

## Decisão

**1. O recibo de lançamento ganha dois botões: desfazer e trocar categoria.**
Vale para o recibo de despesa e para o recibo de fechamento, venha o
lançamento de onde vier — texto, `sim` a uma confirmação, resposta a uma
pergunta, botão de fechar compra. Os outros recibos não mudam: o de item
anotado já tem `✖ Tirar` (ADR-0040), e o de estorno não tem o que desfazer.

**2. O botão de desfazer estorna o lançamento do próprio recibo.** Não o mais
recente do household: o alvo é o lançamento cujo id está no botão. Quando o
lançamento é de um fechamento, os itens voltam para a lista junto, pelo mesmo
evento da ADR-0032. Depois do estorno, a mesma mensagem é redesenhada como
recibo de estorno, sem botões. O `desfazer` digitado continua exatamente como
a ADR-0025 define — a palavra passa a ter dois alcances, e isso é declarado
em Consequências.

**3. Fora da janela, o desfazer pede confirmação em vez de estornar.** Dentro
de `novoapp.conversation.receipt.undo-window`, contada a partir de quando o
lançamento foi gravado, um toque estorna. Depois dela o toque não estorna:
redesenha a mensagem com a data, a categoria e o valor do lançamento, e dois
botões, `Estornar` e `Voltar`. `Estornar` estorna sem olhar a janela — é o
segundo toque, deliberado, vendo o que vai sumir. `Voltar` redesenha o recibo
como estava. A janela é config global e provisória, **24 horas, sem base**,
mesmo tratamento do TTL da decisão aberta #8 e do carrinho (#28): é a
[decisão aberta #30](../DECISOES-ABERTAS.md).

**4. Trocar categoria é escolher num teclado, não escrever.** O toque redesenha
a mensagem com as categorias de despesa do household — as mesmas, na mesma
forma, da pergunta do primeiro fechamento (ADR-0040, item 4) —, a atual
marcada, e um botão `Voltar`. O toque numa categoria troca a categoria do
lançamento, grava uma linha em `transaction_edit` com quem trocou, quando, e
de qual para qual (ADR-0012), e redesenha o recibo com a categoria nova e os
dois botões. Sem modelo, sem pendência, sem prazo: a troca não destrói nada,
fica no histórico e se reverte trocando de novo. Escolher a categoria que o
lançamento já tem não grava edição.

**5. Desfazer estorna o lançamento, nunca a troca.** A troca não cria
lançamento: edita o que existe. O desfazer — pelo botão ou digitado — estorna
o lançamento com a categoria que ele tiver no momento. Quem quer a categoria
anterior de volta troca de novo.

**6. A troca no fechamento vale para o próximo fechamento pelo botão.** Como
`lastCheckoutCategory` lê a categoria atual do lançamento, corrigir o
fechamento corrige a herança: o próximo fechamento pelo carrinho já pergunta
o valor na categoria corrigida. É intencional, e tem cenário.

**7. O botão carrega o estado de destino, e o banco é a fonte de verdade.** Os
tipos novos são `desfazer` e `estornar` (com o lançamento), `trocar` e
`voltar` (com o lançamento, só redesenham), e `categoria` (com o lançamento e
a categoria de destino). Repetir um toque dá o mesmo resultado que fazer uma
vez — em particular, o segundo toque em `desfazer` **não** estorna outro
lançamento, porque o alvo está no botão e já foi estornado. Lançamento já
estornado, por qualquer caminho, responde a qualquer botão do recibo com um
aviso curto e o redesenho como estornado (ADR-0040, item 6).

**8. O clique não toca a pendência aberta.** Ação de botão é resolvida por
curto-circuito antes da pendência, como na ADR-0040: o desfazer pelo botão não
cancela pergunta nenhuma, e a pergunta aberta continua esperando a resposta
depois dele. É uma das duas negativas da ADR-0025 que a #29 apontava como
resolvidas pelo botão.

**9. O contrato continua sem canal (regra 5).** `ChatAction` deixa de carregar
só o id de um item de lista: passa a ter uma referência tipada (item ou
lançamento) e, na troca, a categoria de destino. Como isso vira
`callback_data` é assunto só de `channel`: o codec passa a escrever UUID em
base64url, 22 caracteres, e o maior botão — código, lançamento e categoria —
fica em 48 bytes. Nenhum desses códigos sai do `TelegramActionCodec`.

## Alternativas consideradas

### A. Trocar categoria por texto: o bot pergunta, a pessoa escreve
Foi o desenho do pedido original. Descartada pelo que o Contexto mostra: a
resposta escrita vai ao modelo, pode superar a própria pergunta (ADR-0029) e
virar outra intenção, e categoria que não existe abriria a oferta de criar no
meio de uma correção. Precisaria ainda de uma pendência com TTL — um estado a
mais para uma escolha que um teclado resolve sem estado nenhum.

### B. `desfazer <número>`, com um número curto por lançamento no recibo
Levantada na mesma conversa, para desfazer lançamento antigo de propósito.
Descartada: com números curtos e sequenciais, um erro de digitação acerta
outro lançamento válido da família, então precisaria de confirmação de
qualquer jeito; quem procura o número já está rolando até o recibo, onde o
botão está; e exigiria um contador por household (global revelaria o volume
do sistema a um cliente), além de decidir quem vence em `desfazer 2` com uma
pergunta numerada aberta. A confirmação fora da janela (item 3) cobre o mesmo
caso sem nada disso.

### C. Botão de desfazer sem janela, como o texto
Coerente com a ADR-0025. Descartada: o recibo fica no chat para sempre, um
toque é muito mais fácil de errar que uma frase, e estorno não tem refazer.

### D. Janela rígida: passado o prazo, o botão deixa de funcionar
Mais simples que o item 3. Descartada porque tira do chat a única forma de
estornar um lançamento antigo determinado — o `desfazer` digitado só alcança
o mais recente — até a tela de correção da Etapa 4 existir. A confirmação
mantém a proteção contra o toque acidental sem perder a capacidade.

### E. Desfazer depois da troca reverte a troca
É o que um "desfazer" genérico de editor faria. Descartada: misturaria duas
pilhas — estorno, que o glossário define como o que `desfazer` faz, e edição
de campo — na mesma palavra. A pessoa que toca desfazer num recibo de
fechamento espera ver o dinheiro e os itens voltarem, não a categoria de
ontem.

### F. A troca como pendência, com as opções numeradas
Reaproveita `CHOOSE_CATEGORY` e o curto-circuito numérico. Descartada: a
pendência seria superada por qualquer mensagem nova (ADR-0029), apareceria na
Central de pendências ([ADR-0018](0018-central-de-pendencias.md)) como
pergunta sem resposta, e um nome digitado em vez do número cairia no modelo —
o mesmo buraco de A, pela porta da pergunta numerada.

### G. Decidir depois de inverter a autoridade da política de confiança, como a #29 recomendava
O argumento da #29: o item 4 faz mais coisa executar sem pergunta, e decidir
antes fixaria o desenho sem saber o que passou a executar sozinho. Descartada
por três motivos. A troca corrige uma escolha que o sistema **já faz hoje**
sem perguntar — a categoria herdada no fechamento —, e essa necessidade não
espera o item 4. O item 4 muda *quando* um lançamento executa sem pergunta,
não *quais* recibos registram dinheiro, e o escopo do item 1 é definido pelo
segundo. E o raciocínio que pôs a ADR-0040 antes do item 4 vale aqui: o que
sai do modelo antes não precisa ser calibrado depois.

## Consequências

### Positivas

- O fechamento pelo carrinho deixa de ter categoria às cegas sem saída: errou,
  troca no próprio recibo, e o próximo já vem certo.
- O desfazer de um toque passa a alcançar o lançamento que a pessoa está
  vendo, e não o que outra pessoa fez depois — a negativa mais citada da
  ADR-0025.
- Desfazer por engano não cancela mais uma pergunta aberta, porque o clique não
  toca a pendência.
- A regra 7 ganha o caminho que faltava no chat para lançamento antigo, sem
  esperar a Etapa 4.
- Toda troca fica em `transaction_edit`. Para a Etapa 5, cada troca de
  categoria é um **sinal** de interpretação possivelmente errada que ninguém
  precisou anotar — sinal, não gabarito: a pessoa também troca porque mudou de
  ideia ([ADR-0035](0035-o-que-a-etapa-5-mede.md)).
- O domínio quase não muda: o estorno determinado reaproveita o evento da
  ADR-0032, e `shopping` não ganha nenhuma linha.

### Negativas

- **`desfazer` passa a ter dois alcances.** Pelo botão, o lançamento do recibo;
  digitado, o mais recente da família. A dica "Se eu errei, responda:
  desfazer" continua no recibo, e no momento em que ele chega os dois
  coincidem — mas um recibo relido dias depois aponta para dois alvos
  diferentes conforme a forma.
- **A janela de 24 horas é número sem base**, como o TTL da pendência e a vida
  do carrinho. Registrada como decisão aberta #30.
- **Uma confirmação deixada aberta no chat é um estorno a um toque.** O botão
  `Estornar` não olha a janela, então uma mensagem que ficou no estado de
  confirmação continua perigosa indefinidamente. Aceito por ora: chegar a esse
  estado já exige um toque deliberado; a mitigação, se o uso mostrar
  necessidade, é o botão levar a hora em que a confirmação foi mostrada.
- **Dois redesenhos novos da mesma mensagem** (teclado de categorias e
  confirmação), e a mensagem de recibo passa a ter quatro estados. É mais
  estado de tela para o adaptador, embora nenhum no servidor.
- **Lista de categorias longa** pode não caber no teclado: dezenas de
  categorias com subcategorias batem no limite do teclado inline do Telegram
  `[a verificar contra a Bot API]`. Não é o uso real hoje. No WhatsApp, a lista
  interativa tem no máximo dez linhas `[a verificar na Etapa 7]`.
- **Trocar para uma categoria que não existe não é possível pelo recibo.** O
  teclado só oferece as que existem; criar categoria continua sendo pelo
  caminho da despesa ([ADR-0024](0024-categoria-sugerida-por-texto-livre.md)).
- **O lançamento de outro membro continua fora do alcance do botão**: o recibo
  dele está no fio dele. Para isso existe o `desfazer` digitado, se for o mais
  recente, e a tela de correção da Etapa 4.
- **Uma migration nova** (`transaction_edit`, com `household_id` e RLS) e uma
  mudança no contrato de `ChatAction`, que a ADR-0040 tinha acabado de fixar.
- **A pergunta do primeiro fechamento continua numerada e digitada.** O mesmo
  teclado de categorias serviria a ela e fecharia a lacuna de "responder pelo
  nome", mas isso mudaria o item 4 da ADR-0040 por um detalhe. Fica como
  gatilho.

## Desdobramentos

Fixados ao aceitar, em 2026-10-31:

1. **Glossário**: os tipos novos de *Ação de botão* (`desfazer`, `estornar`,
   `trocar`, `voltar`, `categoria`), e *Recibo* passa a dizer que o de
   lançamento traz os dois botões. *Estorno* ganha o alcance do botão ao lado
   do alcance do texto.
2. **Decisão aberta #30**, janela do desfazer pelo botão, com o valor
   provisório de 24 horas e o sinal de calibração: estorno por engano em
   recibo antigo, ou confirmação pedida em recibo que a pessoa considerava
   recente.
3. **Decisão aberta #29** sai para "Números encerrados", apontando para cá.
4. **Ordem no ROADMAP**: antes de inverter a autoridade da política de
   confiança, em dois passos com entregável — desfazer pelo botão, depois
   trocar categoria —, com o runner da `.feature` nascendo desabilitado.

## Gatilhos de revisão

- Se a lacuna "responder pelo nome" da pergunta do primeiro fechamento
  aparecer em uso real, a pergunta passa a usar o teclado de categorias desta
  ADR — e aí a ADR-0040 é superada nesse item.
- Se aparecer estorno por engano a partir de uma confirmação deixada aberta, o
  botão `Estornar` passa a ter prazo próprio.
- Etapa 4: a tela de correção passa a ser o caminho principal para lançamento
  antigo, e a confirmação fora da janela pode ficar redundante.
- Etapa 7 (WhatsApp): sem edição de mensagem, cada redesenho vira mensagem
  nova, e quatro estados de recibo viram quatro mensagens. Se isso não
  comportar o uso, o desenho do recibo muda lá.
- Se a família tiver mais categorias do que o teclado comporta, o teclado de
  troca precisa de paginação ou de dois níveis (categoria-pai, depois
  subcategoria).
