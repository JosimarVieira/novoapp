---
tipo: sdd
modulo: shopping
status: escrito
atualizado_em: 2026-09-21
adrs:
  - ADR-0003
  - ADR-0004
  - ADR-0018
  - ADR-0027
  - ADR-0030
  - ADR-0031
  - ADR-0032
  - ADR-0037
  - ADR-0038
  - ADR-0039
---

# SDD — Módulo `shopping`

## Responsabilidade

Lista de compras do household: adicionar item, marcar comprado, **remover**,
consultar o que falta — e **fechar a compra gerando o lançamento**, que é o elo
e o diferencial do produto. Um household tem no máximo uma lista ativa por vez
(glossário).

O lastro é a [ADR-0027](../01-adr/0027-lista-de-compras-unica-e-sob-demanda.md),
mais o glossário (`ShoppingList`, `ListItem`, `ListCheckout`), o modelo de dados
e os nove cenários de
[`mercado-lista-de-compras.feature`](../03-specs/features/mercado-lista-de-compras.feature).

**A ADR-0027 foi escrita depois deste SDD e depois do código**, em 2026-09-08, e
isso é uma inversão do processo, não o padrão. Este módulo foi construído na
Etapa 2a com as decisões de produto registradas só aqui — o autor apontou ao
revisar a entrega que SDD sem ADR é design sem lastro, e a correção foi escrever
a ADR e travar a regra em `DocumentationCoherenceTest`, que agora quebra o build
quando um SDD não declara em que ADR se apoia.

## Não faz

- **Não escreve em `transaction`.** Nem mesmo `fecharCompra`: ele chama
  `finance.registerExpense`, que é a única porta de entrada do lançamento
  (`sdd-visao-geral.md`). A aresta `shopping` → `finance` sempre foi permitida e
  desde a Etapa 3 existe em tempo de execução.
- **`marcarItemComprado` e `removerItemLista` não mexem em dinheiro.** O
  primeiro marca item e nada mais — é o que o cenário "Marcar item específico
  como comprado" exige literalmente ("nenhum lançamento financeiro é criado") —,
  e o segundo tira da lista o que a família desistiu de comprar
  ([ADR-0039](../01-adr/0039-remover-item-da-lista.md)). Quem junta os dois
  domínios é `fecharCompra`, e só ele.
- **Não pergunta nada.** Quando o item mencionado não está na lista, `shopping`
  devolve "não achei" e é `conversation` quem transforma isso em
  `PendingAction` — mesma divisão que `finance` tem com a criação de categoria
  (ADR-0018: quem pergunta e resolve por chat é `conversation`).
- **Não fala com canal nenhum**, nem formata recibo.
- **Não resolve nome de membro.** Devolve `member_id`; quem traduz para "pedido
  por Ana" é `conversation`, que já depende de `identity`. O papel de domínio
  não tem grant sobre `member` (ADR-0022) — ir buscar o nome aqui exigiria o
  papel pré-tenant dentro de um módulo de domínio, que é justamente o que a
  ADR-0022 proíbe.

## Depende de

`identity` — só o contexto de tenant já resolvido (`householdId`, `memberId`
chegam prontos). Nenhuma chamada.

## Quem depende dele

- `conversation` — executa as intenções de mercado e formata o recibo.
- `nlu` — **só leitura**, para pôr os itens pendentes no contexto do modelo.
  Essa aresta não estava no `sdd-visao-geral.md`; entrou na Etapa 2a pelo mesmo
  motivo que `nlu → finance` entrou na Etapa 1: a ADR-0004 manda dar ao modelo
  "as categorias e listas reais do household como contexto", e sem os nomes dos
  itens pendentes o modelo não tem como casar "comprei o arroz" com o item
  "Arroz" da lista. `nlu` nunca escreve aqui.

## Estrutura interna proposta

```
shopping/
  ShoppingService            -- addItems / markPurchased / removeItem /
                                checkout / reopenCheckout / pendingItems
  ExpenseReversedListener    -- o outro lado do desfazer (ADR-0032)
  ItemDraft                  -- (name, quantity, unit) -- o que veio da interpretacao
  AddedItem, ListItemView    -- o que conversation precisa pra montar o recibo
  MarkPurchasedResult        -- selado: Purchased | NotOnTheList
  RemoveItemResult           -- selado: Removed | NotOnTheList
  CheckoutResult             -- selado: Closed | NoActiveList | NothingToClose
  entity/
    ShoppingList, ShoppingListStatus (ACTIVE|CLOSED)
    ListItem, ListItemStatus (PENDING|PURCHASED|REMOVED)
    ListCheckout             -- o elo
  repository/
    ShoppingListRepository, ListItemRepository, ListCheckoutRepository
```

## Decisões desta versão

As quatro decisões abaixo estão na
[ADR-0027](../01-adr/0027-lista-de-compras-unica-e-sob-demanda.md); o que segue é
como elas aparecem no código.

**A lista ativa nasce sob demanda, não no onboarding.** `finance` cria a conta
`WALLET` implícita ao observar `HouseholdCreated` (ADR-0011), e a simetria
tentaria fazer o mesmo com a lista. Não faz: a ADR-0011 argumenta especificamente
sobre conta ("o usuário nunca escolheria um nome diferente"), e nada equivalente
foi decidido para lista. Household que nunca falou de mercado não precisa
carregar uma lista vazia. Então o primeiro `adicionarItemLista` cria a
`shopping_list` `ACTIVE` se não houver nenhuma, e `consultarLista` sem lista
alguma responde o mesmo que lista vazia ("não falta nada") em vez de erro.

Consequência aceita: `shopping` grava em duas tabelas no primeiro item, e o
cenário "Consultar lista vazia" passa a cobrir dois estados diferentes com a
mesma resposta (sem lista, e lista sem item pendente). Isso é intencional — a
diferença não é observável pelo usuário e não deve ser.

**"No máximo uma lista ativa" é índice, não disciplina de serviço.** Índice
único parcial em `shopping_list (household_id) WHERE status = 'ACTIVE'`. Mesmo
argumento do índice de irmãos de `category`: invariante de estrutura que o banco
consegue garantir não fica dependendo de quem escreve a query (ADR-0003 aplicada
ao que ela não cobre — ela fala de isolamento, mas o raciocínio é o mesmo).

**Item repetido não duplica; é reconhecido.** Índice único parcial em
`list_item (shopping_list_id, name_normalized) WHERE status = 'PENDING'`
([ADR-0030](../01-adr/0030-correspondencia-de-nome-por-forma-normalizada.md);
era `lower(name)` até 2026-09-16). O serviço
checa antes e devolve "já estava lá, pedido por fulano" (cenário "Item já
pendente na lista"); o índice é rede de segurança contra corrida, não o caminho
normal. Só vale enquanto `PENDING`: comprado o arroz de hoje, ele pode faltar de
novo amanhã — por isso o índice é parcial e não abrange `PURCHASED`.

**A grafia do item vem do modelo, não do texto cru.** "acabou o arroz" grava o
item `Arroz`, não `arroz` nem `o arroz`. Quem normaliza é `nlu` na extração,
como já faz com categoria — `shopping` grava o que recebe. Comparação de item
existente é pela forma normalizada — minúscula e sem acento
([ADR-0030](../01-adr/0030-correspondencia-de-nome-por-forma-normalizada.md)),
gravada em `name_normalized` e usada tanto pela consulta quanto pelo índice.

**Corrigido em 2026-09-16**: os dois eram `lower(name)`, e por isso "acabou
cafe" com "Café" já pendente inseria um segundo item — a consulta não achava o
primeiro e o índice não barrava o segundo. Sem pergunta e sem aviso, que é o
pior desfecho possível dos dois. **Plural continua fora**: "ovo" e "ovos" seguem
sendo itens diferentes, por decisão explícita da ADR-0030.

**`quantity` é fracionário (`numeric`), não inteiro.** "meio quilo de queijo" é
tão comum quanto "2 kg de arroz". Não conflita com a regra de `amount_cents`
inteiro: aquela regra é sobre dinheiro, e quantidade de item não é dinheiro.

**Nem `quantity` nem `unit` viram pergunta.** "acabou o arroz" grava item sem
quantidade, e isso é o caso normal — nunca "quantos quilos?". Mesmo argumento
da ADR-0023 para `descricao`: perguntar por campo opcional custa a proposta de
valor que a ADR-0004 protege. Nenhum cenário do `.feature` pergunta por
quantidade.

## Fluxo

**Adicionar item** (`adicionarItemLista`, um ou vários numa mensagem)

1. `conversation` chama `ShoppingService.addItems(householdId, memberId, itens,
   sourceMessageId)`.
2. Acha a lista `ACTIVE` do household; se não houver, cria.
3. Para cada item: se já existe `PENDING` com o mesmo nome (sem diferenciar
   maiúscula/minúscula), não insere — devolve como "já estava lá", com o
   `member_id` de quem pediu antes. Senão insere `PENDING`.
4. Devolve a lista do que entrou e do que já estava, na ordem da mensagem —
   `conversation` monta **um único** recibo, mesmo com vários itens (cenário
   "Adicionar vários itens em uma mensagem").

**Marcar comprado** (`marcarItemComprado`)

1. Procura item `PENDING` pelo nome na lista ativa.
2. Achou → `PURCHASED`, `purchased_by_member_id`, `purchased_at`. Devolve
   `Purchased`.
3. Não achou → devolve `NotOnTheList(nome)`, sem escrever nada. `conversation`
   abre a `PendingAction` que oferece registrar o item já comprado (cenário
   "Item mencionado não existe na lista").

**Consultar** (`consultarLista`)

Devolve os itens `PENDING` da lista ativa, em ordem de inclusão. Comprado não
aparece (cenário "Consultar o que está faltando" exige que "Café" não apareça).

## Erros

Falha ao gravar → propaga para `conversation`, que converte em recibo de erro no
chat, nunca silêncio (regra do `sdd-visao-geral.md`).

## Testes

- ArchUnit: `shopping` não importa `channel`, `nlu`, `conversation`, `tasks`.
  Pode importar `finance` (o elo é dirigido nesse sentido), embora na Etapa 2a
  ainda não importe.
- Os nove cenários de `mercado-lista-de-compras.feature` (todos `@etapa2`).
- Isolamento de tenant cobrindo `shopping_list` e `list_item` — exigência da
  `estrategia-de-testes.md` para toda tabela de dado de usuário que a etapa
  toca.

## O elo, implementado (2026-09-21)

As duas decisões abaixo foram escritas em 2026-09-16, **antes** da etapa
começar, para que o código não as inventasse. O código chegou em 2026-09-21 e
está descrito depois delas.

- **`fecharCompra` é atômico e mora aqui**
  ([ADR-0031](../01-adr/0031-atomicidade-do-fechamento-de-compra.md)): um método
  `@Transactional @HouseholdScoped` deste módulo fecha os itens, grava o
  `list_checkout` e chama `finance` dentro da mesma transação. `conversation`
  continua sem transação própria. Usa a única aresta que a regra de dependência
  já permitia — `shopping` → `finance` —, agora em tempo de execução e não só no
  papel.
- **`desfazer` reverte o fechamento inteiro**
  ([ADR-0032](../01-adr/0032-desfazer-alcanca-o-fechamento-inteiro.md)): os itens
  daquele fechamento voltam a `PENDING` junto com o estorno do lançamento, na
  mesma transação. Item que colidiria com um pendente de mesmo nome normalizado
  ([ADR-0030](../01-adr/0030-correspondencia-de-nome-por-forma-normalizada.md))
  permanece `PURCHASED` — o efeito pretendido já está alcançado por quem avisou
  de novo.

Fica declarado o limite que as duas deixam: item marcado como comprado **fora**
de um fechamento não é desfazível pelo chat, porque não cria lançamento e
portanto não é alcançável pelo alvo da
[ADR-0025](../01-adr/0025-desfazer-precedencia-e-escopo.md).

### Quem orquestra o `desfazer` do fechamento (decidido em 2026-09-21)

A [ADR-0032](../01-adr/0032-desfazer-alcanca-o-fechamento-inteiro.md) deixou isto
explicitamente para a Etapa 3: *"`reverseLatest` passa a precisar saber se o
lançamento veio de um fechamento, o que acopla `finance` à existência de
`list_checkout` — ou obriga `shopping` a ser quem orquestra o desfazer. Qual dos
dois fica com o método é decisão da Etapa 3, ao escrever; esta ADR decide o
comportamento, não o arquivo."*

A regra de dependência elimina metade sozinha: `finance` não pode importar
`shopping`, então `reverseLatest` não tem como consultar `list_checkout`.
Restavam duas saídas, e nenhuma boa — `shopping` orquestrar todo `desfazer`
faria o estorno de uma despesa avulsa passar pelo módulo de mercado, e
`conversation` perguntar antes quebraria a transação única que a ADR-0032 exige.

**Decidido: `finance.reverseLatest` publica um evento CDI `ExpenseReversed`
dentro da própria transação, e `shopping` observa.** É o mesmo padrão do
`HouseholdCreated` → `finance` cria a conta `WALLET`, que existe desde a Etapa 1
exatamente para não inverter a direção de dependência (`sdd-modulo-identity.md`).
Observador CDI síncrono roda na mesma transação, então a atomicidade se mantém,
`finance` nunca ouve falar de `list_checkout`, e o `desfazer` de despesa avulsa
continua sendo só `finance`.

**Não contradiz a alternativa D da ADR-0031.** Aquela descartou *evento
assíncrono*, e a objeção era literalmente "evento assíncrono garante o contrário
— a lista fecharia primeiro". Observador síncrono na mesma transação não tem esse
problema; é o mesmo mecanismo que a
[ADR-0022](../01-adr/0022-papel-de-banco-pre-tenant-para-identidade.md) já usa ao
recusar dois datasources para manter o onboarding atômico.

O risco desta escolha, declarado: **acoplamento que o ArchUnit não vê.** Um
observador de evento não é um `import`, então nenhuma regra de fronteira quebra
se `shopping` passar a reagir a eventos de `finance` para coisas que não são o
elo. A disciplina aqui é humana, e a mitigação é esta seção — se aparecer um
segundo `@Observes` de evento de `finance` neste módulo, a fronteira merece
revisão antes do código.

### Como ficou, ao escrever (2026-09-21)

**A ordem das escritas dentro de `checkout` mudou, e a razão é o teste.** A
primeira versão chamava `finance` **antes** de tocar em qualquer item, com o
argumento de que "se ele falhar, nada mais chegou a ser escrito". O argumento é
verdadeiro e é justamente o problema: com nada escrito antes do passo que falha,
o cenário `Falha ao registrar a despesa não deixa a lista fechada` passava **por
ordenação**, não por transação. O terceiro dos quatro testes obrigatórios da
[estratégia de testes](../04-qualidade/estrategia-de-testes.md) — o que cobre o
diferencial do produto — não teria exercitado a fronteira da
[ADR-0031](../01-adr/0031-atomicidade-do-fechamento-de-compra.md) uma vez sequer.

Hoje os itens são marcados e descarregados no banco **antes** de `finance` ser
chamado, e só o `rollback` os desfaz. Verificado invertendo a garantia: com as
duas escritas em transações separadas, o cenário fica vermelho. Teste verde que
não pode ficar vermelho é afirmação, não garantia.

O `list_checkout` continua sendo escrito depois de `finance`, e isso não é
escolha: `transaction_id` é `NOT NULL`.

**A mudança de ordem cobrou um preço, e ele está no código.** Com o status
escrito antes e o `list_checkout_id` depois, `list_item` passou a ser escrito
**duas vezes na mesma transação**. Feita a segunda escrita pelo estado das
entidades, o Hibernate reemitia a linha inteira com os valores de **antes** do
primeiro flush: o status voltava a `PENDING` e o `purchased_by_member_id` a
nulo, enquanto o `list_checkout` e o lançamento ficavam gravados do mesmo jeito.
Silencioso — o recibo dizia "fechei a compra" e listava os mesmos itens em
"ainda falta".

O conserto é `ListItemRepository.linkToCheckout`: um `UPDATE` em massa de uma
coluna só, que não passa pelo estado das entidades. Está comentado lá, porque
quem mexer nesse método de novo precisa saber por que ele não é um `forEach`.

Só apareceu porque os cenários do elo conferem o **status** dos itens, e não só
o lançamento. Vale como argumento a favor de cenário que asserta os dois lados
de uma operação que escreve nos dois.

**A categoria da despesa do fechamento sai do cardápio da família**
([ADR-0037](../01-adr/0037-categoria-da-despesa-do-fechamento.md)). `checkout`
recebe `categoryId` pronto e não resolve nome nenhum — quem escolhe entre as
categorias reais é o modelo, em `nlu`, e categoria não resolvida vira "não
entendi" em vez de palpite. A negativa disso está declarada e virou a
[decisão aberta #26](../DECISOES-ABERTAS.md).

**`açúcar 20` é fechamento parcial de um item só**
([ADR-0038](../01-adr/0038-item-com-valor-e-fechamento-parcial.md)), e não um
caminho próprio: é `checkout` com um nome na lista. Nada mudou neste módulo por
causa dela — a decisão vive inteira na descrição das tools de `nlu`, e é por isso
que ela foi barata.

**Recusa nomeada, nunca exceção.** `CheckoutResult` é selado — `Closed`,
`NoActiveList`, `NothingToClose` —, mesmo padrão de `MarkPurchasedResult`.
`conversation` transforma as duas recusas na **mesma** pergunta ("registro só a
despesa?"), porque do ponto de vista de quem escreveu elas são a mesma situação.

### Remover item (ADR-0039)

`removeItem` marca `REMOVED`, grava `removed_by_member_id` e `removed_at`
(migration `V8`), e alcança só item `PENDING`. Não apaga a linha, pelo mesmo
motivo que o estorno não apaga o lançamento.

Item que não está lá devolve `RemoveItemResult.NotOnTheList` e **não vira
pergunta** — é a diferença deliberada para `markPurchased`, que oferece
registrar como comprado: comprar algo fora da lista é caso legítimo, remover o
que não está lá não tem segunda leitura útil.

Não é desfazível pelo chat: não cria lançamento, logo não é alcançável pelo alvo
da [ADR-0025](../01-adr/0025-desfazer-precedencia-e-escopo.md). O caminho de
volta é avisar de novo, e ele funciona porque o índice único da
[ADR-0030](../01-adr/0030-correspondencia-de-nome-por-forma-normalizada.md) só
abrange `PENDING`.

## Gatilhos de revisão

- ~~**Etapa 3**: `fecharCompra`, `list_checkout` e a atomicidade lista+lançamento
  entram aqui.~~ **Feito em 2026-09-21** — ver a seção acima.
- **Etapa 5**, cardápio de tools: este módulo é dono de quatro das oito
  (`adicionarItemLista`, `marcarItemComprado`, `removerItemLista`,
  `fecharCompra`) e as quatro falam de itens. Se a matriz de confusão mostrar
  que elas disputam entre si, o gatilho que se aplica é o do
  [SDD de `nlu`](sdd-modulo-nlu.md) — encolher o cardápio por situação —, e não
  reescrever descrição pela quarta vez.
- **[Decisão aberta #26](../DECISOES-ABERTAS.md)**: decidida, `fecharCompra`
  passa a poder terminar em criação de categoria, e `afterCategoryCreated` em
  `conversation` deixa de desembocar sempre em despesa.
- **[Decisão aberta #15](../DECISOES-ABERTAS.md)** (múltiplas listas simultâneas:
  mercado, farmácia, feira): se for decidida, a
  [ADR-0027](../01-adr/0027-lista-de-compras-unica-e-sob-demanda.md) é superada,
  "a lista ativa" deixa de ser singular e o índice único parcial cai junto. Nada
  nesta versão depende de a lista ser única além desse índice e da resolução
  implícita em `addItems`/`pendingItems`.
- ~~Se a comparação por `lower(name)` mostrar item duplicado por acento ou
  plural, a correspondência aproximada entra aqui e na criação de categoria ao
  mesmo tempo.~~ Metade resolvida em 2026-09-16 pela
  [ADR-0030](../01-adr/0030-correspondencia-de-nome-por-forma-normalizada.md), e
  nos dois lugares ao mesmo tempo como este gatilho pedia: acento deixou de
  duplicar. **Plural segue aberto** — se o uso real mostrar "ovo"/"ovos" como
  causa frequente, é a aproximação difusa que a ADR-0030 recusou por prazo que
  volta à mesa, com dado da Etapa 5 para escolher método e limiar.
