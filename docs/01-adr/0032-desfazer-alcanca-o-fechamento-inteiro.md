---
tipo: adr
numero: 32
status: aceita
data: 2026-09-16
modulos:
  - shopping
  - finance
  - conversation
depende_de: [ADR-0012, ADR-0025, ADR-0030, ADR-0031]
supera: []
superada_por:
corrigida_em:
---

# ADR-0032 — `desfazer` reverte o fechamento inteiro, não só o dinheiro

- **Impacta**: a Etapa 3 do [ROADMAP](../../ROADMAP.md) (cenário
  `Desfazer o fechamento`), `finance` (o estorno deixa de ser só marcar
  `reversed_at`), `shopping` (itens voltam a `PENDING`), e o escopo que a
  [ADR-0025](0025-desfazer-precedencia-e-escopo.md) definiu antes de o elo
  existir

## Contexto

A [ADR-0025](0025-desfazer-precedencia-e-escopo.md) decidiu o alvo do `desfazer`
quando não há pergunta pendente: o lançamento não estornado mais recente do
household inteiro, de qualquer membro, sem janela de tempo. Ela foi escrita
antes de o elo existir, e por isso fala só de lançamento.

Com `fecharCompra` ([ADR-0031](0031-atomicidade-do-fechamento-de-compra.md)), o
lançamento mais recente passa a poder ser o de um fechamento de compra. Estornar
só o dinheiro deixaria os itens `PURCHASED` e a lista sem o que a família ainda
precisa comprar — estado incoerente, e o cenário `Desfazer o fechamento` exige
literalmente que os itens voltem a ficar pendentes.

`reverseLatest` hoje devolve `ReversedExpense` e não conhece item de lista
nenhum. Nada no código sabe que um lançamento pode ter vindo de um fechamento.

## Decisão

Quando o lançamento alvo do `desfazer` estiver ligado a um `list_checkout`, o
estorno reverte **os dois lados, na mesma transação**: marca `reversed_at` no
lançamento e devolve a `PENDING` os itens daquele fechamento. Um `desfazer`, um
recibo, os dois lados — pela mesma fronteira transacional da ADR-0031, e no
sentido inverso.

**O fechamento é a unidade.** Fechamento parcial existe, então volta o que
aquele fechamento fechou, e não a lista inteira.

**Item devolvido que colidiria com um pendente do mesmo nome permanece
`PURCHASED`.** Se alguém já avisou de novo que o arroz acabou, existe um "Arroz"
`PENDING` e o índice único da [ADR-0030](0030-correspondencia-de-nome-por-forma-normalizada.md)
não aceita um segundo. Não é perda: o efeito que o `desfazer` queria — "isto está
faltando" — já está alcançado por quem avisou.

**Limite declarado**: item marcado como comprado **fora** de um fechamento
(`marcarItemComprado`) continua sem reversão pelo chat. Ele não cria lançamento,
logo não é alcançável pelo alvo que a ADR-0025 define. Corrigir isso é pela web,
na Etapa 4.

## Alternativas consideradas

### A. Estornar só a despesa e deixar a lista fechada
Descartada: é o estado incoerente descrito acima, e o cenário escrito exige o
contrário. Pior, é incoerência silenciosa — o recibo diria "estornei" e metade
do efeito ficaria de pé.

### B. Dois comandos separados, um para cada lado
Descartada: `desfazer` é uma palavra só, e a ADR-0025 já decidiu que ela tem um
alvo único e previsível. Pedir que a pessoa saiba qual dos dois comandos usar é
a implementação vazando pela interface — e num chat, onde a pessoa não vê menu
nenhum.

### C. Reabrir a lista inteira (status de volta a `ACTIVE`)
Descartada: fechamento parcial é cenário escrito. Reabrir a lista desfaria
fechamentos anteriores que ninguém pediu para desfazer.

### D. Deixar o `desfazer` do fechamento para a Etapa 4, só pela web
Descartada: `desfazer` no chat é a regra 7 do `CLAUDE.md` ("todo lançamento é
reversível por `desfazer` no chat e por edição na web"), e o fechamento é o
lançamento mais caro de errar que o produto tem — junta compra e dinheiro numa
mensagem só.

## Consequências

### Positivas
- A regra 7 do `CLAUDE.md` continua valendo para a operação que o produto existe
  para fazer.
- Reusa a fronteira transacional da ADR-0031, em vez de inventar outra.
- Ser agressivo na execução automática continua sustentável — é o argumento de
  que a [ADR-0004](0004-interpretacao-por-function-calling-com-politica-de-confianca.md)
  depende, e ele só vale enquanto o desfazer alcança o que foi feito.

### Negativas
- **Assimetria visível**: `comprei o arroz` não é desfazível pelo chat;
  `comprei tudo, 180` é. A diferença é o lançamento, e ela não é óbvia para quem
  usa. É o candidato mais provável a reclamação real na Etapa 5.
- **O estorno deixa de ser uma escrita numa linha só.** `reverseLatest` passa a
  precisar saber se o lançamento veio de um fechamento, o que acopla `finance` à
  existência de `list_checkout` — ou obriga `shopping` a ser quem orquestra o
  desfazer. Qual dos dois fica com o método é decisão da Etapa 3, ao escrever;
  esta ADR decide o comportamento, não o arquivo.
- Item que volta a `PENDING` perde a informação de quem o tinha comprado. É
  aceitável: ele voltou a ser algo que falta, e quem comprou está no histórico do
  lançamento estornado.

## Gatilhos de revisão

- **Etapa 5**: se a assimetria de `marcarItemComprado` aparecer na lista de erros
  reais, é sinal de que reverter item sem lançamento precisa de caminho próprio —
  provavelmente um `desfazer` que também alcança a última ação de lista, o que
  mudaria o alvo único da ADR-0025 e exigiria ADR nova.
- Se fechamento parcial se mostrar raro no uso real, a alternativa C fica mais
  simples e pode valer a troca.
