---
tipo: adr
numero: 39
status: aceita
data: 2026-09-21
modulos:
  - nlu
  - shopping
  - conversation
depende_de: [ADR-0004, ADR-0025, ADR-0029, ADR-0030, ADR-0032]
supera: []
superada_por:
corrigida_em:
---

# ADR-0039 — Remover item da lista é ferramenta própria, e não é desfazível pelo chat

- **Impacta**: a [decisão aberta #25](../DECISOES-ABERTAS.md), que esta ADR
  encerra; a Etapa 3 do [ROADMAP](../../ROADMAP.md), que manda resolvê-la
  **dentro** da etapa; `nlu` (oitava tool do cardápio), `shopping`
  (`ListItemStatus.REMOVED` sai do "sem uso") e `conversation` (recibo e
  confirmação de confiança média)

## Contexto

`remover chocolate`, `remover feijão`, `remover feijão da lista` e
`retirar arroz da lista` apareceram em uso real entre 2026-09-18 e 2026-09-19.
Nenhuma ferramenta atende a essa intenção, e o desfecho foi incoerente de duas
maneiras:

- às vezes o modelo não chamava tool nenhuma e a pessoa recebia "não entendi";
- às vezes chamava `marcarItemComprado` **com confiança 0,9** — e na prosa que o
  sistema descarta o próprio modelo escreveu que estava substituindo a intenção
  por não ter ferramenta de remover.

O segundo é o grave: **escreve o status errado sem perguntar**. Quem desistiu de
comprar não comprou, e com o elo da Etapa 3 `PURCHASED` é o status que vira
despesa. A mitigação de 2026-09-19 foi só no prompt — a descrição de
`marcarItemComprado` passou a dizer que não serve para remover —, o que troca a
escrita errada por "não entendi". É contenção, não conserto, e está registrado
como tal.

O domínio já tem a peça de dados: `ListItemStatus.REMOVED` existe desde a Etapa
2a, marcado "sem uso". O que falta é ferramenta, serviço e recibo.

A decisão #25 deixou duas perguntas explícitas para antes de codar: remover é
reversível por `desfazer`? E quem removeu aparece para quem pediu o item?

## Decisão

Uma tool própria, `removerItemLista`, com um nome de item e a confiança. Ela
marca o item pendente como `REMOVED` — não apaga a linha, pelo mesmo motivo que o
estorno não apaga o lançamento: em lista compartilhada, "sumiu" é pior que "foi
removido".

Item que não está pendente na lista **não vira pergunta**: o bot responde que não
achou aquele item na lista, e nada é escrito. Não há o que oferecer — remover
algo que não está lá não tem segunda leitura útil.

Confiança média confirma antes de escrever, como toda intenção que escreve
([ADR-0029](0029-intencao-adiada-e-precedencia-de-mensagem-nova.md)).

**Remover não é desfazível pelo chat.** É o mesmo limite declarado que a
[ADR-0032](0032-desfazer-alcanca-o-fechamento-inteiro.md) já registra para
`marcarItemComprado`: a remoção não cria lançamento, logo não é alcançável pelo
alvo único que a [ADR-0025](0025-desfazer-precedencia-e-escopo.md) define. Quem
removeu por engano avisa de novo que o item está faltando — `acabou o chocolate`
cria um item pendente novo, porque o índice único da
[ADR-0030](0030-correspondencia-de-nome-por-forma-normalizada.md) só abrange
`PENDING`.

**Quem removeu não é avisado a quem pediu.** O produto não tem mensagem proativa
nesta etapa, e ninguém recebe nada sem ter falado primeiro
([ADR-0008](0008-interacao-1-1-por-membro-nunca-em-grupo.md) mantém cada fio 1:1).
A informação fica no banco, em `list_item`, e aparece na Etapa 4.

## Alternativas consideradas

### A. Continuar só no prompt, ensinando o modelo a não confundir
Descartada: é a terceira vez que "proibir no prompt" seria a resposta, e as duas
anteriores documentaram o custo. A
[ADR-0034](0034-valor-so-vale-se-a-pessoa-escreveu-digito.md) registra
literalmente que "proibir de novo no prompt seria a terceira tentativa da mesma
coisa". Além disso, a proibição atual não dá à pessoa o que ela pediu — troca
escrita errada por "não entendi", e a intenção continua sem destino.

### B. Reusar `marcarItemComprado` com um parâmetro `removido: true`
Descartada: esconde duas intenções opostas atrás de um flag booleano, na tool que
o modelo **já** escolhe por engano para remover. Errar o flag passaria a ser
errar entre "comprei" e "desisti" dentro da mesma chamada, e a confusão ficaria
invisível na matriz de confusão por tool da
[ADR-0035](0035-o-que-a-etapa-5-mede.md), que conta tools e não parâmetros.

### C. Apagar a linha em vez de marcar `REMOVED`
Descartada: `ListItemStatus.REMOVED` existe desde a Etapa 2a exatamente para
isso, e o [modelo de dados](../02-arquitetura/modelo-de-dados.md) já decidiu, no
estorno, que histórico auditável importa quando duas pessoas mexem no mesmo dado.
Apagar também deixaria a remoção invisível para a tela da Etapa 4, que é onde
"quem removeu o que eu pedi?" tem resposta barata.

### D. Tornar `desfazer` capaz de reverter a última ação de lista
Descartada nesta etapa, e é a alternativa honesta de longo prazo. A ADR-0025
definiu um alvo **único e previsível** para `desfazer`, e a
[ADR-0032](0032-desfazer-alcanca-o-fechamento-inteiro.md) já registra como
gatilho de revisão da Etapa 5 a hipótese de o alvo passar a alcançar ação de
lista. Mudar isso agora é mudar o alvo do `desfazer` no meio da etapa em que ele
acabou de ganhar o fechamento — duas mudanças no mesmo mecanismo, medidas juntas,
não se distinguem depois.

## Consequências

### Positivas

- A intenção mais frequente sem destino do uso real ganha um, e para de escrever
  `PURCHASED` no lugar errado — que com o elo é o status que vira dinheiro.
- `REMOVED` deixa de ser coluna morta: o schema passa a significar o que já dizia.
- Nada no `desfazer` muda. O alvo único da ADR-0025 continua sendo lançamento, e
  a Etapa 5 mede as duas assimetrias (`marcarItemComprado` e `removerItemLista`)
  como uma coisa só, e não como duas decisões independentes.

### Negativas

- **O cardápio vai a oito tools.** O [SDD de `nlu`](../02-arquitetura/sdd-modulo-nlu.md)
  já registrava seis como "bastante contexto disputando a escolha do modelo", e
  `fecharCompra` levou a sete nesta mesma etapa. Não é motivo para parar — é o
  gatilho de revisão daquele SDD ficando devido, e a Etapa 5 é quem decide se o
  cardápio encolhe por situação.
- **A assimetria do `desfazer` cresce.** `comprei tudo, 180` é desfazível;
  `comprei o arroz` e `remover chocolate` não são. A ADR-0032 já apontava a
  primeira como "o candidato mais provável a reclamação real na Etapa 5"; agora
  são duas, pelo mesmo motivo, e o motivo (não existe lançamento) não é visível
  para quem usa.
- Quem pediu o item não fica sabendo que ele saiu. Em família isso resolve na
  conversa; num household maior, seria surpresa na hora da compra.

## Gatilhos de revisão

- **Etapa 5**: se remoção por engano aparecer na lista de erros reais, a
  alternativa D é a saída, e ela supera o alvo único da ADR-0025 — ADR nova, não
  emenda nesta.
- **Etapa 4**: com a tela, "quem removeu" e "desfazer a remoção" ficam baratos, e
  a assimetria acima deixa de doer no chat.
- Se a matriz de confusão mostrar `removerItemLista` disputando com
  `marcarItemComprado`, é sinal de que separar as duas não bastou e o problema é
  tamanho de cardápio, não descrição.
