---
tipo: adr
numero: 29
status: aceita
data: 2026-09-16
modulos:
  - conversation
  - nlu
depende_de: [ADR-0004, ADR-0018, ADR-0025, ADR-0026]
supera: []
superada_por:
corrigida_em:
---

# ADR-0029 — Pendência guarda a intenção adiada, e mensagem nova pode superá-la

- **Impacta**: `conversation` (a faixa média da [ADR-0004](0004-interpretacao-por-function-calling-com-politica-de-confianca.md)
  passa a valer para toda intenção, e `PendingAction` deixa de presumir que o
  que ficou pendente é uma despesa), `nlu` (a chamada com pendência aberta muda
  de conjunto de ferramentas), a Etapa 3 do [ROADMAP](../../ROADMAP.md)
  (`fecharCompra` passa a caber no mecanismo existente), a
  [ADR-0026](0026-hierarquia-na-criacao-de-categoria-por-chat.md) (a "segunda
  chamada ao modelo" que ela abriu deixa de existir como segunda chamada)

## Contexto

Três fatos observados no código da Etapa 2a, em auditoria de 2026-09-14.

**A faixa média da ADR-0004 quase não existe.** A tabela daquela ADR é
incondicional: confiança média pergunta, não executa. No orquestrador, `MEDIUM`
é testado em um único ponto, e mesmo ali só vira pergunta quando `nlu` conseguiu
montar duas ou mais alternativas — o que depende de uma heurística de primeira
palavra sobre os nomes das categorias. Adicionar item, marcar comprado,
consultar lista e **emitir convite** executam com confiança média como se fosse
alta. Nenhum cenário pega isso porque o stub de LLM só produz confiança média
quando duas categorias competem pela mesma primeira palavra.

**`PendingActionType.ASK_AMOUNT` é genérico no nome e específico na execução.**
Quando a resposta chega, o orquestrador chama `registrarDespesa` diretamente e
lê a primeira opção guardada como id de categoria. O gatilho de revisão do
`sdd-modulo-conversation.md` pedia, para a Etapa 3, que se verificasse se
"fechar compra sem informar valor" caberia no mecanismo existente sem tipo
especial. Não cabe.

**Mensagem nova com pendência aberta ou trava a conversa ou grava errado.**
Quando a resposta não é `sim`, `não`, `desfazer` nem um número:

- em pendência de ambiguidade, de valor ausente ou de item fora da lista, o bot
  repete a pergunta. A pessoa fica presa até o TTL — dez minutos — a menos que
  saiba que `desfazer` cancela;
- em pendência de criação de categoria, a mensagem vai para uma chamada ao
  modelo em que **só** `confirmarCategoriaSugerida` está declarada. O modelo não
  tem como dizer "isto não responde à pergunta". Uma mensagem sobre outro
  assunto pode virar categoria com nome errado, levando junto o valor que estava
  guardado na pendência — lançamento errado, em silêncio, que é exatamente o
  desfecho que a ADR-0004 considera pior do que perguntar demais.

Os três têm a mesma raiz: `PendingAction` foi construída como "pergunta sobre
uma despesa", e não como "intenção esperando confirmação".

## Decisão

**1. A pendência guarda a intenção a executar, não os campos de uma despesa.**
`PendingIntent` passa a descrever qual ação roda quando a confirmação chegar, e
o orquestrador executa o que está guardado em vez de deduzir pelo tipo. É o que
faz `fecharCompra` caber sem tipo novo na Etapa 3.

**2. Confiança média nunca executa, para nenhuma intenção.** Vira pendência com
a intenção ecoada de volta: opções numeradas quando há mais de uma alternativa,
confirmação `sim`/`não` quando há uma só. A tabela da ADR-0004 passa a valer
como está escrita.

**Uma exceção, declarada**: consulta (`consultarLista`) executa em qualquer
confiança acima da baixa. Não há o que desfazer numa leitura, e o custo de
errar é a pessoa reler uma lista — perguntar antes de ler é fricção sem risco
do outro lado. Toda intenção que **escreve** segue a regra.

**3. Com pendência aberta e mensagem que não é atalho, há uma chamada ao modelo
com as ferramentas do dia a dia**, mais `confirmarCategoriaSugerida` quando a
pendência é de criação de categoria, e com a pergunta pendente no prompt de
sistema. O modelo escolhe entre responder à pergunta e mudar de assunto — que é
o que hoje ele não tem como fazer.

**4. A mensagem nova só supera a pendência com confiança alta.** Média ou baixa
mantém a pendência e repete a pergunta. Superar é destrutivo: a pergunta sai do
fio e a informação já capturada (o valor, a categoria escolhida) deixa de estar
ao alcance de um `sim`. Quem responde `mercado` em vez de `1` a uma pergunta de
ambiguidade está respondendo, não mudando de assunto.

**5. Pendência superada tem a janela do atalho fechada, e nada mais.** Na
prática: `expires_at` passa a agora. Ela para de interceptar o fio do chat,
`resolution` continua nula, e ela continua aparecendo na central de pendências
da Etapa 4 exatamente como as que venceram por tempo. **Nenhuma coluna nova** —
a [ADR-0018](0018-central-de-pendencias.md) já define `expires_at` como aquilo
que muda o caminho de resolução e nunca o estado, e é esse mecanismo que está
sendo usado, não um contornado.

O curto-circuito determinístico continua antes de tudo: `sim`, `não`, `desfazer`
e número nunca chegam ao modelo. A regra 6 do `CLAUDE.md` fica intacta.

## Alternativas consideradas

### A. Manter a chamada só-de-correção e acrescentar a ela um jeito de dizer "não é correção"
Descartada: conserta o lançamento errado da pendência de categoria e deixa os
outros três tipos presos até o TTL. Trata o sintoma mais visível e deixa a raiz
— a pendência não saber o que é — de pé, o que obrigaria a mexer no mesmo código
de novo na Etapa 3.

### B. Reinterpretar sempre, e a pendência perde para qualquer ferramenta escolhida
Descartada: come a resposta certa. Pendência "foi em qual? 1) Mercado
2) Mercado livre", com cinquenta reais já capturados, e a pessoa digita
`mercado` em vez de `1` — uma reinterpretação sem desempate leria isso como
despesa nova sem valor, perguntaria o valor, e teria perdido o que já sabia.

### C. Marcar a pendência superada como `REJECTED`
Descartada: `REJECTED` significa "a pessoa disse não" ([ADR-0025](0025-desfazer-precedencia-e-escopo.md)), e usá-la aqui
encheria a central de pendências de desistências que ninguém declarou. Pior que
não registrar nada, porque registra errado.

### D. Coluna nova (`superseded_at`) para distinguir superada de vencida por tempo
Descartada por ora: a ADR-0018 conta "zero mudança de schema" entre as
consequências positivas dela, e a distinção não muda o comportamento de nenhuma
superfície que exista ou esteja desenhada — a central de pendências mostra as
duas do mesmo jeito. Fica como gatilho de revisão para a Etapa 4, se a tela
precisar separar as duas coisas.

### E. Deixar como está
Descartada: é o estado que produz o travamento de dez minutos e o lançamento
errado silencioso, e contraria a ADR-0004 aceita.

## Consequências

### Positivas
- A faixa média da ADR-0004 deixa de ser letra morta em quatro das cinco
  intenções, inclusive na emissão de convite, que é a mais cara de errar.
- `fecharCompra` (Etapa 3) e "fechar compra sem informar valor" cabem no
  mecanismo existente, que é o que o gatilho de revisão do SDD pedia.
- A mensagem sobre outro assunto deixa de poder virar categoria errada com o
  valor de outra despesa dentro.
- **Some a segunda chamada ao modelo** que a ADR-0026 abriu como exceção: passa
  a haver exatamente uma chamada por mensagem que não é atalho, com ou sem
  pendência aberta. A frase daquela ADR sobre "segunda chamada" descreve um
  desenho que deixou de existir; a decisão dela — como a correção livre cria
  nome e hierarquia — continua inteira e implementada.

### Negativas
- **Mensagem que não é atalho com pendência aberta passa a custar uma chamada
  ao modelo em três tipos de pendência que antes custavam zero** (ambiguidade,
  valor ausente, item fora da lista). O caso que paga é "pendência esquecida no
  fio", e ele é justamente o mais provável.
- **O desempate depende do `confidence` do modelo**, que a própria ADR-0004
  chama de mal calibrado por natureza. Exigir confiança alta para superar erra
  para o lado conservador — a pessoa continua vendo a pergunta repetida — em vez
  do destrutivo. É a escolha certa entre as duas, não uma escolha sem custo.
- **Mais perguntas.** A ADR-0004 alerta que "se cada mensagem gera uma pergunta
  de confirmação, o produto perde a razão de existir". Só a faixa média
  pergunta, e onde ela começa é a [decisão aberta #7](../DECISOES-ABERTAS.md),
  a calibrar na Etapa 5 — mas até lá o limiar é palpite, e um palpite baixo
  demais vira exatamente o produto que aquela ADR não quer.
- A consequência "zero mudança de schema" da ADR-0018 continua verdadeira, mas
  agora por uma decisão a mais e não por sobra: a alternativa D existe e foi
  recusada com prazo.

## Gatilhos de revisão

- **Etapa 5**: medir a fração de mensagens que viram pergunta. Se passar de um
  quinto, o limiar `high` está baixo demais e o remédio é calibração, não voltar
  atrás nesta decisão. Medir também quantas pendências são superadas por
  mensagem nova — se for raro, o desempate por confiança alta está apertado
  demais.
- **Etapa 4**: se a central de pendências precisar mostrar "você mudou de
  assunto" diferente de "você não respondeu a tempo", a alternativa D volta à
  mesa, aí sim com superfície que justifique a coluna.
