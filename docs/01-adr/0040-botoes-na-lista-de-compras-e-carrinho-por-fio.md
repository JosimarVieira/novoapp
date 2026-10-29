---
tipo: adr
numero: 40
status: aceita
data: 2026-10-29
modulos:
  - channel
  - conversation
  - shopping
  - banco
  - glossario
depende_de: [ADR-0004, ADR-0005, ADR-0008, ADR-0018, ADR-0025, ADR-0027, ADR-0029, ADR-0031, ADR-0032, ADR-0035, ADR-0036, ADR-0037, ADR-0038, ADR-0039]
supera: []
superada_por:
corrigida_em:
---

# ADR-0040 — A lista de compras ganha botões, e "peguei" é carrinho do fio, não item comprado

- **Impacta**: `channel` (o adaptador passa a receber `callback_query` e a
  editar mensagem), `conversation` (curto-circuito de ação de botão e o carrinho),
  `shopping` (nenhuma regra nova — só duas leituras), banco (tabela do carrinho
  e o tipo da linha em `inbound_message`), glossário (dois termos novos), e a
  [decisão aberta #28](../DECISOES-ABERTAS.md) (vida útil do carrinho), que esta
  ADR abre. Entra no [ROADMAP](../../ROADMAP.md) **antes** de "inverter a
  autoridade da política de confiança", pelo motivo dado em Desdobramentos.

## Contexto

O pedido, de 2026-10-29: a lista de compras volta no Telegram só como texto, e
seria útil ela vir com botões de "comprar" e "remover" por item, que ao serem
tocados mudam o item e mostram a lista de novo.

O desenho óbvio — um botão "Comprei" que chama `ShoppingService.markPurchased` —
**desliga o elo**. `checkout` só fecha item `PENDING` (`listPending` /
`listPendingNamed`). Quem toca "Comprei" item a item no corredor e depois manda
`comprei tudo, 180` recebe `CheckoutResult.NothingToClose`, o bot oferece
registrar só a despesa, e o lançamento nasce sem `list_checkout`. É o diferencial
do produto (`CLAUDE.md`) falhando exatamente no momento de uso para o qual ele
existe, e falhando em silêncio — o recibo diz "anotado".

Quatro outras forças:

- **Regra 5.** Hoje `InboundMessage` carrega só `rawText`, e
  `OutboundMessagePort.send` só texto. Botão é recurso de canal; se a noção de
  "inline keyboard" chegar a `conversation`, o WhatsApp da Etapa 7 vira
  refactor. E o WhatsApp é mais pobre: botão de resposta com no máximo três
  opções, lista interativa com no máximo dez linhas, e nenhuma edição de
  mensagem já enviada `[a verificar na Etapa 7, contra a documentação da Cloud API]`.
- **Fio 1:1 ([ADR-0008](0008-interacao-1-1-por-membro-nunca-em-grupo.md)).**
  Editar a mensagem atualiza só a tela de quem tocou. A lista que a Ana recebeu
  de manhã continua com botões de itens que o Bruno já fechou. Clique em
  mensagem velha é o caso normal, não a exceção.
- **Remover é irreversível pelo chat
  ([ADR-0039](0039-remover-item-da-lista.md)).** Escrevendo `remover chocolate`
  isso é aceitável. Com dois botões lado a lado por linha, o toque errado fica
  muito mais provável que a frase errada.
- **Categoria do fechamento vem do modelo
  ([ADR-0037](0037-categoria-da-despesa-do-fechamento.md)).** `fecharCompra`
  escolhe a categoria no enum do cardápio. Um fechamento disparado por botão não
  tem frase nem chamada de modelo — e não deve ter (regra 6).

O que já existe e pode ser reaproveitado: `checkout(itemNames)` faz fechamento
parcial ([ADR-0038](0038-item-com-valor-e-fechamento-parcial.md)); a pendência
`ASK_AMOUNT` com `DeferredAction.CLOSE_PURCHASE` é a pergunta "quanto foi?" do
cenário "Fechar sem informar valor"; e `ShortCircuit.amountCents` resolve a
resposta numérica sem modelo.

## Decisão

**1. A consulta da lista responde com botões, e o clique edita a mesma
mensagem.** Cada item pendente vira uma linha com dois botões: carrinho e
remover. Nada é reenviado: a resposta ao clique substitui a mensagem de onde ele
veio. Lista vazia continua só texto.

**2. "Peguei" é carrinho, não item comprado.** O botão de carrinho não toca
`list_item`: põe o item no **carrinho** de quem tocou. O carrinho é estado do fio
— de um membro num household, como a `PendingAction` —, e mora em
`conversation`. Enquanto o carrinho tem itens, a mensagem ganha um rodapé
`Fechar compra (N itens)`.

**3. Fechar pelo carrinho é o `checkout` parcial que já existe.** O rodapé abre
a pendência `ASK_AMOUNT`/`CLOSE_PURCHASE` com os nomes do carrinho; a resposta
numérica resolve por `ShortCircuit.amountCents` e chama
`checkout(itemNames = carrinho)`. Atomicidade
([ADR-0031](0031-atomicidade-do-fechamento-de-compra.md)) e `desfazer` do
fechamento inteiro ([ADR-0032](0032-desfazer-alcanca-o-fechamento-inteiro.md))
vêm sem nenhuma linha nova em `shopping`. O que fecha são os itens do carrinho
que **ainda estão pendentes** no momento do fechamento — o que outro membro
fechou ou removeu nesse intervalo sai em silêncio, porque já não está pendente,
e a pergunta do valor cita só o que de fato vai fechar.

**4. A categoria do fechamento por botão é a do último fechamento do
household.** É determinística e é o que a família faz na prática: a compra de
mercado cai sempre na mesma categoria. A pergunta do valor diz qual é
("Quanto deu? Vai em Mercado"), e o recibo traz o `desfazer`, como todo
lançamento (regra 7). **Household sem nenhum fechamento anterior** recebe antes
uma pergunta com as categorias de despesa numeradas (`CHOOSE_CATEGORY`), uma vez
na vida do household. Isto **não supera** a ADR-0037: ela decide o parâmetro da
tool `fecharCompra`, e este é um caminho que não passa por tool.

**5. Remover pelo botão usa `removeItem`, e a mensagem oferece devolver.**
Depois da remoção, o rodapé da mensagem editada traz `↩ devolver <item>`, que é
um `addItems` comum — o mesmo caminho de `acabou o chocolate`, que a ADR-0039 já
apontou como a volta. O alvo do `desfazer`
([ADR-0025](0025-desfazer-precedencia-e-escopo.md)) não muda, e a ADR-0039
continua inteira.

**6. O clique carrega o estado de destino, nunca um "alternar".** As ações são
`carrinho+`, `carrinho-`, `remover`, `devolver` e `fechar`, cada uma com o id do
item quando há item. Duplo toque e clique em mensagem velha dão o mesmo
resultado que um toque só. Item que já não está pendente não vira erro: quem
tocou recebe um aviso curto ("Café já saiu da lista") e a mensagem é redesenhada
com o estado atual do banco — **a mensagem nunca é fonte de verdade**, só o banco.

**7. O contrato entre `channel` e o resto continua sem canal (regra 5).**

- Entrada: `InboundMessage` ganha uma **ação de botão** opcional (tipo + ref) e
  um **identificador de origem opaco**, que só `channel` sabe interpretar. No
  Telegram, o `provider_message_id` do clique é o id do `callback_query`,
  prefixado para não colidir com o `chat_id:message_id` das mensagens de texto
  — a idempotência da [ADR-0005](0005-idempotencia-de-mensagens-recebidas.md)
  vale para clique como vale para texto.
- Saída: `OutboundMessagePort` ganha uma resposta com texto, ações neutras
  (rótulo + ação) e "substituir a mensagem de origem". Quem decide como isso
  vira teclado inline, `editMessageText` e `answerCallbackQuery` é o adaptador.
  No WhatsApp, a mesma resposta vira lista interativa e mensagem nova, porque
  lá não há edição.
- Ação de botão é resolvida por curto-circuito, **antes** de qualquer modelo
  (regra 6). `prompt_version` e `model_name` ficam nulos
  ([ADR-0036](0036-instrumento-de-medicao-da-etapa-5.md)), e `inbound_message`
  passa a registrar se a linha é texto ou ação, para que a calibração da
  [ADR-0035](0035-o-que-a-etapa-5-mede.md) não precise adivinhar.

**8. O carrinho não é `PendingAction`.** Não é pergunta, não espera resposta e
não é superado por mensagem nova
([ADR-0029](0029-intencao-adiada-e-precedencia-de-mensagem-nova.md)): quem
manda `mercado 50` no meio do corredor não perde o carrinho. Também não aparece
na Central de pendências ([ADR-0018](0018-central-de-pendencias.md)).

## Alternativas consideradas

### A. Botão "Comprei" que marca o item como comprado
É o pedido original, e é o desenho que qualquer bot de lista faz. Descartada pelo
que o Contexto mostra: com `checkout` fechando só `PENDING`, cada toque tira o
item do alcance do elo, e `comprei tudo, 180` vira despesa sem lista. O botão mais
usado da tela desligaria o diferencial do produto.

### B. "Comprei" marca, e `checkout` passa a varrer também os comprados sem fechamento
Resolveria A mudando o domínio. Descartada: `comprei o arroz` de ontem, que a
[ADR-0027](0027-lista-de-compras-unica-e-sob-demanda.md) define como "não mexe em
dinheiro", entraria no fechamento de R$ 180 de hoje. O vínculo entre item e
despesa passaria a depender de quando cada um aconteceu, e não do que a pessoa
fez — que é o contrário do que a ADR-0031 garante.

### C. Carrinho como `PendingAction`
Reaproveita tabela e TTL. Descartada: a ADR-0029 faz mensagem nova superar a
pendência aberta, e o carrinho morreria no primeiro `mercado 50`; ele também
apareceria na Central de pendências como pergunta sem resposta, que ele não é.
Forçar as duas exceções esvaziaria as duas ADRs para acomodar um conceito que não
é o delas.

### D. Carrinho guardado no próprio teclado da mensagem, sem estado no servidor
O Telegram devolve o teclado no `callback_query`, então daria para ler a seleção
dali. Descartada: o `callback_data` tem 64 bytes, e o botão de fechar teria de
carregar todos os ids; o WhatsApp não edita mensagem, então não há teclado a
reler; e `channel` passaria a decidir estado de compra, o que fere a regra 5 pelo
lado de dentro.

### E. Só o botão de remover, sem carrinho
Escopo menor e real: remover já tem serviço, não toca dinheiro, e é a etapa 1 da
implementação proposta. Descartada **como escopo final**: o toque mais frequente
no mercado é "peguei", não "desisti", e entregar só o raro deixaria a pessoa
voltando para o texto justamente no caso que importa para o elo.

### F. Reenviar a lista inteira a cada clique, como no pedido
Descartada: a cada toque, uma mensagem nova, e o fio vira uma pilha de listas
desatualizadas — todas com botões vivos que operam sobre um estado que já não é o
que mostram. Editar mantém uma mensagem por consulta. No WhatsApp, onde não há
edição, reenviar é o único caminho, e é decisão do adaptador, não do domínio.

## Consequências

### Positivas

- No mercado, o elo fica a três toques e um número, sem o modelo no caminho.
  `comprei o arroz e o leite, 60` deixa de ser a única forma de fechar parcial.
- Uma parte do tráfego das tools de lista sai do modelo. Hoje quatro das oito
  tools falam de item de lista, e o
  [SDD de `nlu`](../02-arquitetura/sdd-modulo-nlu.md) registra a disputa entre
  elas como gatilho de revisão. Clique não confunde `marcarItemComprado` com
  `removerItemLista`, que foi o defeito que originou a ADR-0039.
- O toque errado em remover tem volta no mesmo lugar onde aconteceu, sem mexer
  no `desfazer`.
- O domínio não muda: `checkout`, `removeItem` e `addItems` são os mesmos, com os
  mesmos cenários. O risco fica em `channel` e `conversation`.

### Negativas

- **Muda o contrato de duas peças centrais**, `InboundMessage` e
  `OutboundMessagePort`, que todo o pipeline usa. É a primeira mudança nelas
  desde a Etapa 1.
- **Uma tabela nova e uma coluna nova** (carrinho; tipo da linha em
  `inbound_message`), com `household_id` e RLS como toda tabela de dado de
  usuário.
- **A vida útil do carrinho não está decidida.** Uma ida ao mercado pode durar
  uma hora; um carrinho esquecido por dias faria o próximo fechamento levar item
  de outra compra. Entra como config global provisória, como o TTL da decisão
  aberta #8 — é a [decisão aberta #28](../DECISOES-ABERTAS.md).
- **Devolver um item removido perde quem pediu.** O item volta pedido por quem
  tocou, não por quem avisou que faltava — é o mesmo que acontece hoje com
  `acabou o chocolate` depois de uma remoção, só que agora a um toque.
- **O primeiro fechamento por botão da vida do household faz duas perguntas**
  (categoria, depois valor), contra a regra de uma pergunta só da
  [ADR-0004](0004-interpretacao-por-function-calling-com-politica-de-confianca.md).
  Acontece uma vez por household; o custo de não perguntar seria um lançamento
  numa categoria escolhida às cegas.
- **A categoria por histórico erra quando a família muda de hábito.** Se a
  última compra foi lançada em "Farmácia", a próxima pelo botão vai para lá até
  alguém corrigir. O texto da pergunta mostra a categoria antes do valor
  justamente para isso.
- **Assimetria nova entre texto e botão.** Pelo botão, remover tem volta; por
  texto, não. A ADR-0039 já apontava assimetrias do `desfazer` como candidatas a
  reclamação; esta é mais uma, embora na direção do alívio.
- **A Etapa 5 passa a medir um sistema de outra forma.** Se as mensagens de lista
  migrarem para o botão, o corpus de texto encolhe nesse eixo, e a taxa de
  acerto antes/depois não é comparável sem separar por tipo da linha — e é por
  isso que a coluna nova existe.
- **No WhatsApp a lista degrada**: até dez linhas por mensagem, sem edição. Lista
  maior pede paginação, que este desenho não detalha.

## Desdobramentos

Fixados ao aceitar, em 2026-10-29:

1. **Vida útil do carrinho**: config global e provisória,
   `novoapp.conversation.cart.ttl`, com valor inicial de **3 horas, sem base**.
   Avaliada na leitura, sem job — mesmo padrão da `PendingAction`
   ([ADR-0018](0018-central-de-pendencias.md)): item do carrinho mais velho que
   o prazo simplesmente deixa de aparecer. Registrada como
   [decisão aberta #28](../DECISOES-ABERTAS.md), a calibrar com uso real.
2. **Ordem no roadmap**: antes de "inverter a autoridade da política de
   confiança". Esta ADR tira das tools de lista parte do tráfego que aquela teria
   de calibrar; escrever as guardas antes e depois tirar o tráfego delas seria
   calibrar duas vezes. O custo, declarado: a Etapa 5 espera mais uma mudança de
   forma.
3. **Glossário**: *Carrinho* (`ChatCart`) e *Ação de botão* (`ChatAction`)
   entram junto com esta aceitação, antes de qualquer código.
4. **Categoria do último fechamento** quer dizer o último fechamento **não
   estornado**. Fechamento estornado pode ter sido estornado justamente pela
   categoria errada, e herdá-la repetiria o erro.

## Gatilhos de revisão

- **Etapa 5**: se a categoria por histórico gerar correção recorrente, a pergunta
  do valor passa a oferecer a troca — ou a ADR-0037 é superada para os dois
  caminhos juntos.
- **Etapa 7 (WhatsApp)**: se a lista interativa de dez linhas não comportar o uso
  real, o contrato de saída precisa de paginação, e é aqui que ela entra.
- Se a decisão aberta #15 (várias listas) for tomada, o carrinho precisa saber de
  qual lista é — hoje não precisa porque só existe uma.
- Se carrinho esquecido virar fechamento errado em uso real, a vida útil deixa de
  ser config e vira regra com cenário.
