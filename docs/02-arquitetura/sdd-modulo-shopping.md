---
tipo: sdd
modulo: shopping
status: escrito
atualizado_em: 2026-09-08
adrs:
  - ADR-0003
  - ADR-0004
  - ADR-0018
  - ADR-0027
---

# SDD — Módulo `shopping`

## Responsabilidade

Lista de compras do household: adicionar item, marcar comprado, consultar o que
falta. Um household tem no máximo uma lista ativa por vez (glossário).

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

- **Não cria lançamento financeiro.** `marcarItemComprado` marca item e nada
  mais — é o que o cenário "Marcar item específico como comprado" exige
  literalmente ("nenhum lançamento financeiro é criado"). O elo lista → despesa
  é `fecharCompra`, Etapa 3, e ele chama `finance` em vez de escrever em
  `transaction` (`sdd-visao-geral.md`).
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
  ShoppingService            -- addItems / markPurchased / pendingItems
  ItemDraft                  -- (name, quantity, unit) -- o que veio da interpretacao
  AddedItem, ListItemView    -- o que conversation precisa pra montar o recibo
  MarkPurchasedResult        -- selado: Purchased | NotOnTheList
  entity/
    ShoppingList, ShoppingListStatus (ACTIVE|CLOSED)
    ListItem, ListItemStatus (PENDING|PURCHASED|REMOVED)
  repository/
    ShoppingListRepository, ListItemRepository
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
`list_item (shopping_list_id, lower(name)) WHERE status = 'PENDING'`. O serviço
checa antes e devolve "já estava lá, pedido por fulano" (cenário "Item já
pendente na lista"); o índice é rede de segurança contra corrida, não o caminho
normal. Só vale enquanto `PENDING`: comprado o arroz de hoje, ele pode faltar de
novo amanhã — por isso o índice é parcial e não abrange `PURCHASED`.

**A grafia do item vem do modelo, não do texto cru.** "acabou o arroz" grava o
item `Arroz`, não `arroz` nem `o arroz`. Quem normaliza é `nlu` na extração,
como já faz com categoria — `shopping` grava o que recebe. Comparação de item
existente é sem diferenciar maiúscula/minúscula (o `lower(name)` do índice),
mesma escolha que a ADR-0026 fez para nome de categoria, e com o mesmo risco
aberto: variação por acento ou plural cria item novo em vez de reconhecer o que
já está lá.

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

## Gatilhos de revisão

- **Etapa 3**: `fecharCompra`, `list_checkout` e a atomicidade lista+lançamento
  entram aqui. É o módulo que muda mais na etapa seguinte.
- **[Decisão aberta #15](../DECISOES-ABERTAS.md)** (múltiplas listas simultâneas:
  mercado, farmácia, feira): se for decidida, a
  [ADR-0027](../01-adr/0027-lista-de-compras-unica-e-sob-demanda.md) é superada,
  "a lista ativa" deixa de ser singular e o índice único parcial cai junto. Nada
  nesta versão depende de a lista ser única além desse índice e da resolução
  implícita em `addItems`/`pendingItems`.
- Se a comparação por `lower(name)` mostrar, no uso real, item duplicado por
  acento ou plural ("cafe"/"café", "ovo"/"ovos"), a correspondência aproximada
  entra aqui e na criação de categoria ao mesmo tempo — é o mesmo problema, e a
  ADR-0024 já o registra como risco aberto do lado de finanças.
