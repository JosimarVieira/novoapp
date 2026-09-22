---
tipo: entrega
etapa: 3
status: entregue
data: 2026-09-21
modulos:
  - shopping
  - finance
  - nlu
  - conversation
  - banco
adrs:
  - ADR-0030
  - ADR-0031
  - ADR-0032
  - ADR-0033
  - ADR-0034
  - ADR-0037
  - ADR-0038
  - ADR-0039
---

# Etapa 3 — O elo

**Critério de pronto do [ROADMAP](../../ROADMAP.md)**: `comprei tudo, 180` fecha
a lista e lança a despesa. **Atingido**, e com escopo maior do que o planejado:
14 cenários no lugar dos nove escritos, porque as decisões abertas #23 e #25
foram resolvidas por dentro, como o ROADMAP mandava.

A suíte inteira: **148 testes, 64 cenários de aceitação, zero falhas.** A tag
`em-construcao` saiu de `Etapa3AcceptanceTest` — o ritual que fecha a etapa — e
o elo passou a integrar o portão do CI.

## O que foi construído

| Onde | O quê |
|---|---|
| `nlu/tools/ClosePurchaseTool` | `fecharCompra` — itens, valor, categoria do cardápio da família |
| `nlu/tools/RemoveListItemTool` | `removerItemLista` — o destino que faltava para "remover chocolate" |
| `nlu/Intent` | `ClosePurchase` e `RemoveListItem`; a interface selada quebrou o `switch` do orquestrador, que é para isso que ela é selada |
| `shopping/ShoppingService` | `checkout` ganhou chamador, mais `removeItem`, `reopenCheckout` e `reopenedItemsOf` |
| `shopping/ExpenseReversedListener` | o outro lado do `desfazer`, por evento CDI síncrono |
| `finance/ExpenseReversed` | o anúncio do estorno, dentro da própria transação |
| `conversation` | `CLOSE_PURCHASE` e `REMOVE_LIST_ITEM` em `DeferredAction`, recibos do elo, e o recibo do estorno nomeando o que voltou para a lista |
| `V8__list_item_removal.sql` | `removed_by_member_id` e `removed_at` |

Três ADRs novas, todas escritas **antes** do código que elas governam:

- **[ADR-0037](../01-adr/0037-categoria-da-despesa-do-fechamento.md)** — de qual
  categoria sai a despesa do fechamento. Era uma decisão estrutural que nenhuma
  ADR cobria, e o primeiro cenário não tinha como ser implementado sem ela.
- **[ADR-0038](../01-adr/0038-item-com-valor-e-fechamento-parcial.md)** —
  `açúcar 20` é fechamento parcial de um item só. Encerra a decisão aberta #23.
- **[ADR-0039](../01-adr/0039-remover-item-da-lista.md)** — remover item é
  ferramenta própria e não é desfazível pelo chat. Encerra a #25.

## O que a etapa ensinou

### O teste obrigatório da atomicidade não provava atomicidade

O passo `que o registro de despesas está indisponível` estava marcado desde
2026-09-19 como "o mais importante dos 24 novos". Escrevê-lo expôs um problema
que nenhuma revisão de código teria pego: `ShoppingService.checkout` chamava
`finance` **antes** de tocar em qualquer item, e o comentário no código
justificava essa ordem dizendo, corretamente, que "se este passo falhar nada
mais chegou a ser escrito".

É exatamente por isso que o cenário não provava nada. Sem nada escrito antes do
passo que falha, "nenhum item muda de status" é verdade trivialmente — o cenário
teria passado igual com `@Transactional` removido. O terceiro dos quatro testes
obrigatórios da [estratégia de testes](../04-qualidade/estrategia-de-testes.md),
o que cobre o diferencial do produto, seria uma afirmação verde.

Os itens passaram a ser marcados e descarregados no banco **antes** da chamada a
`finance`. A sensibilidade foi verificada invertendo a garantia: com as duas
escritas em transações separadas, **exatamente um cenário fica vermelho**, e é o
da atomicidade. Verde agora significa alguma coisa.

### O conserto cobrou um preço, e ele quase passou em silêncio

Com duas escritas em `list_item` na mesma transação, a segunda — feita pelo
estado das entidades — fazia o Hibernate reemitir a linha inteira com os valores
de **antes** do primeiro flush. O status voltava a `PENDING` e o
`purchased_by_member_id` a nulo, enquanto o `list_checkout` e o lançamento
ficavam gravados normalmente. O recibo dizia "Fechei a compra" e listava os
mesmos três itens em "Ainda falta".

O conserto é um `UPDATE` em massa de uma coluna só
(`ListItemRepository.linkToCheckout`), comentado no lugar em que alguém seria
tentado a transformá-lo num `forEach` de novo.

**Só apareceu porque os cenários do elo conferem o status dos itens, e não só o
lançamento.** Vale como argumento a favor de cenário que asserta os dois lados
de uma operação que escreve nos dois — que é, afinal, a definição do elo.

### O cardápio foi de seis para oito tools

`fecharCompra` estava previsto (o ROADMAP dizia sete); `removerItemLista` entrou
junto porque a #25 era escopo declarado da etapa. O gatilho de revisão do
[SDD de `nlu`](../02-arquitetura/sdd-modulo-nlu.md) já avisava sobre seis, e
agora **quatro das oito falam de item de lista**, três delas distinguidas por
detalhes da frase — tempo do verbo e presença de um número. É candidato da Etapa
5: a matriz de confusão decide se o cardápio encolhe por situação. Reescrever
descrição pela quarta vez não é a saída.

A guarda do valor escrito ([ADR-0034](../01-adr/0034-valor-so-vale-se-a-pessoa-escreveu-digito.md))
passou a valer para as **duas** tools que lançam dinheiro. Deixá-la só em
`registrarDespesa` faria a alucinação de valor voltar pela porta nova — e nesta
porta ela fecha a lista junto.

## O que ficou de fora, e por quê

- **[Decisão aberta #26](../DECISOES-ABERTAS.md)**, aberta pela própria ADR-0037:
  família sem categoria onde a compra caiba recebe "não entendi essa" numa
  mensagem que o bot entendeu. É o mesmo defeito que a
  [ADR-0033](../01-adr/0033-valor-ausente-com-categoria-conhecida-pergunta-o-valor.md)
  consertou para despesa, aceito de propósito enquanto não há cenário escrito. A
  decisão é **escrevendo o cenário** — foi cenário faltando que abriu o buraco.
- **Remoção não é desfazível pelo chat**, e agora são duas assimetrias com o
  mesmo motivo (`marcarItemComprado` e `removerItemLista`): não criam lançamento,
  logo não são alcançáveis pelo alvo da ADR-0025. A ADR-0032 já registrava a
  primeira como o candidato mais provável a reclamação real na Etapa 5.
- **Quem removeu não avisa quem pediu.** Não há mensagem proativa nesta etapa; a
  informação está no banco e aparece na tela da Etapa 4.

## Um risco que o ArchUnit não vê

`shopping` passou a observar um evento de `finance`. Observador de evento não é
um `import`, então nenhuma regra de fronteira quebra se o módulo passar a reagir
a eventos de `finance` para coisas que não são o elo. A disciplina aqui é
humana, e está escrita em três lugares: na
[ADR-0032](../01-adr/0032-desfazer-alcanca-o-fechamento-inteiro.md), no
[SDD de `shopping`](../02-arquitetura/sdd-modulo-shopping.md) e no javadoc de
`ExpenseReversedListener`. **Um segundo `@Observes` de evento de `finance` neste
módulo pede revisão de fronteira antes do código.**

## Alterações em documento já existente

- [ROADMAP](../../ROADMAP.md): Etapa 3 marcada como entregue; item 2 da ordem
  riscado; item 3 (inverter a autoridade da política de confiança) passa a ser o
  próximo, com a lista de guardas de `fecharCompra` fechada — a ADR pode ser
  escrita.
- [estratégia de testes](../04-qualidade/estrategia-de-testes.md): o terceiro
  teste obrigatório deixou de ser uma promessa, com o relato de por que teste
  verde não basta.
- [modelo de dados](../02-arquitetura/modelo-de-dados.md), [glossário](../00-produto/glossario.md),
  e os SDDs de `shopping`, `finance`, `nlu` e `conversation`.
- [DECISOES-ABERTAS](../DECISOES-ABERTAS.md): #23 e #25 encerradas, #26 aberta,
  #22 atualizada (a fronteira com `açúcar 20` ficou mais nítida).
- `.github/workflows/ci.yml`: o passo informativo continua, sem classe marcada,
  porque é o par do portão — quem abrir a próxima etapa põe a tag na classe nova.
