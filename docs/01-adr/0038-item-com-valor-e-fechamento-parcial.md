---
tipo: adr
numero: 38
status: aceita
data: 2026-09-21
modulos:
  - nlu
  - shopping
  - conversation
depende_de: [ADR-0030, ADR-0031, ADR-0033, ADR-0034, ADR-0037]
supera: []
superada_por:
corrigida_em:
---

# ADR-0038 — Item com valor na mesma mensagem é fechamento parcial

- **Impacta**: a [decisão aberta #23](../DECISOES-ABERTAS.md), que esta ADR
  encerra; a Etapa 3 do [ROADMAP](../../ROADMAP.md), que manda resolvê-la
  **dentro** da etapa; `nlu` (a descrição de `fecharCompra` passa a cobrir a
  mensagem de um item só), `shopping` (nada muda — é `checkout` com um nome na
  lista) e `conversation` (o desfecho quando o item citado não está pendente)

## Contexto

`açúcar 20` foi mandada em uso real e teve dois desfechos errados em dois dias
seguidos, ambos silenciosos:

- **2026-09-18**: virou `adicionarItemLista` e os 20 foram descartados sem aviso.
  A pessoa comprou e pagou; o sistema anotou que estava faltando.
- **2026-09-19**, depois do prompt do tempo do verbo: virou
  `registrarDespesa{categoria: Alimentação, valor: 20}` com confiança 0,9. O
  valor sobreviveu e o **item** se perdeu — "Açúcar" continuou pendente na lista,
  e a despesa entrou numa categoria que a pessoa não escreveu.

Trocou-se um descarte silencioso por outro. Os dois erram na mesma direção: a
mensagem diz duas coisas e o sistema só sabia guardar uma.

A [decisão #23](../DECISOES-ABERTAS.md) registrou o que faltava decidir antes de
codar: *"o desfecho do caso parcial — um item, um valor, e nenhuma lista
fechando: despesa avulsa, item marcado e despesa, ou pergunta?"*. Ela ficou
aberta porque a resposta dependia de `fecharCompra` existir, e ele não existia.

Agora existe. `ShoppingService.checkout(...)` já recebe `itemNames`, e a própria
assinatura dele documenta que a lista de nomes fecha **só os que casarem pela
forma normalizada** ([ADR-0030](0030-correspondencia-de-nome-por-forma-normalizada.md))
— é o fechamento parcial, que tem cenário escrito
(`Fechamento parcial`, `Segundo fechamento parcial na mesma lista`).

## Decisão

**`açúcar 20` é `fecharCompra` com um item só.** Um nome de produto acompanhado
de um valor é a mesma operação que `comprei o arroz e o leite, 60` — fechamento
parcial —, e não um caso próprio: o item é marcado como comprado e a despesa é
registrada, na mesma transação da
[ADR-0031](0031-atomicidade-do-fechamento-de-compra.md), com a categoria vinda do
cardápio da família ([ADR-0037](0037-categoria-da-despesa-do-fechamento.md)).

Nada no domínio muda. O que muda é a descrição da tool: `fecharCompra` passa a
dizer que serve também para a mensagem de um item só com valor, e
`adicionarItemLista` passa a dizer que **não** serve quando há valor na mensagem.

**Item citado que não está pendente na lista não vira fechamento.** A recusa
`CheckoutResult.NothingToClose` vira a mesma pergunta que a lista inexistente já
gera: o bot oferece registrar **apenas a despesa**, e a pessoa responde `sim`.
Nada é escrito antes da resposta.

## Alternativas consideradas

### A. Despesa avulsa, ignorando a lista
Descartada: é literalmente o defeito de 2026-09-19, promovido a decisão. O item
continuaria pendente depois de comprado, e a família voltaria do mercado com a
lista mandando comprar o que já está no armário. É também a recusa mais direta
do diferencial: o `CLAUDE.md` manda toda priorização favorecer o elo, e esta
alternativa o desliga justamente na mensagem em que ele é mais barato de
entregar.

### B. Marcar o item **e** lançar a despesa, mas em duas escritas separadas
Descartada: são as duas escritas que a ADR-0031 acabou de pôr na mesma transação.
Marcar o item por `markPurchased` e lançar por `registerExpense` produz o mesmo
efeito no caminho feliz e um estado incoerente na falha, sem `list_checkout`
ligando os dois — e sem ele o `desfazer` da
[ADR-0032](0032-desfazer-alcanca-o-fechamento-inteiro.md) não alcança nada, o que
quebra a regra 7 do `CLAUDE.md` para esta mensagem.

### C. Perguntar sempre: "comprou o açúcar por R$ 20,00 ou quer só anotar?"
Descartada: a pergunta não tem o que desambiguar. Valor é fato consumado — não
se sabe o preço do açúcar antes de comprá-lo, e a
[decisão #22](../DECISOES-ABERTAS.md) já registra que `açúcar` **sozinho** vai
para a lista. O número é o que separa os dois casos, e ele está na mensagem.
Perguntar aqui é fricção sem informação nova, que é o que a
[ADR-0029](0029-intencao-adiada-e-precedencia-de-mensagem-nova.md) reserva para
confiança média.

### D. Deixar para a Etapa 5, com dado de uso real
Descartada: o dado já existe e está registrado na decisão #23 — dois desfechos
errados, dois dias, a mesma mensagem. O que a Etapa 5 acrescentaria é frequência,
e o [ROADMAP](../../ROADMAP.md) já decidiu resolver esta dentro da Etapa 3
justamente porque ela sangra hoje e porque anotar gabarito sobre um sistema que
vai mudar de forma é o que a [ADR-0035](0035-o-que-a-etapa-5-mede.md) recusa.

## Consequências

### Positivas

- O elo passa a valer para a mensagem mais curta que o produto recebe, e não só
  para `comprei tudo, 180`. É o diferencial cobrindo o caso frequente.
- Zero código de domínio novo: é `checkout` com uma lista de um nome. A decisão
  cara já foi paga pela ADR-0031.
- O `desfazer` alcança `açúcar 20` de graça, pela ADR-0032 — a mensagem vira um
  `list_checkout` como qualquer outro fechamento.

### Negativas

- **A fronteira entre `adicionarItemLista` e `fecharCompra` passa a ser um
  número**, e passa a depender de o modelo não perder o valor. A mensagem
  `açúcar 20` e a mensagem `açúcar` têm desfechos opostos, e a diferença é um
  dígito. É o tipo de fronteira que a Etapa 5 mede, e é o candidato mais provável
  a aparecer na matriz de confusão.
- **Erro aqui é mais caro do que era.** Antes, errar `açúcar 20` perdia metade da
  mensagem; agora escreve dos dois lados. A rede é o `desfazer`, e ela só existe
  porque o fechamento é a unidade da ADR-0032.
- Item comprado fora da lista, com valor, continua sem caminho direto: vira
  pergunta. É uma mensagem a mais para quem compra algo que ninguém pediu, e a
  simetria com `marcarItemComprado` — que oferece registrar como comprado — não
  se completa, porque lá não há dinheiro envolvido.

## Gatilhos de revisão

- **Etapa 5**, matriz de confusão por tool: se `adicionarItemLista` e
  `fecharCompra` se confundirem com frequência em mensagens de um item só, o
  cardápio de tools disputa contexto demais e é o gatilho do
  [SDD de `nlu`](../02-arquitetura/sdd-modulo-nlu.md) que se aplica — encolher o
  cardápio por situação, e não reescrever descrição pela terceira vez.
- Se a pergunta de item fora da lista com valor se mostrar frequente, ela merece
  virar oferta de duas opções numeradas ("só a despesa" / "a despesa e o item
  como comprado") em vez de sim/não.
