---
tipo: adr
numero: 35
status: aceita
data: 2026-09-19
modulos:
  - nlu
  - conversation
  - roadmap
depende_de: [ADR-0004, ADR-0009, ADR-0023, ADR-0029, ADR-0033, ADR-0034]
supera: []
superada_por:
corrigida_em:
---

# ADR-0035 — A Etapa 5 mede três coisas, e pergunta nunca sai do denominador

- **Impacta**: a Etapa 5 e o portão da Etapa 6 no [ROADMAP](../../ROADMAP.md), a
  [estratégia de testes](../04-qualidade/estrategia-de-testes.md) (o `nlu-eval`,
  que hoje só existe como nome), `nlu` e `conversation` (é o desfecho deles que
  se mede), e as [decisões abertas #3, #4 e #7](../DECISOES-ABERTAS.md), que
  dependem deste número para serem tomadas; depende de
  [ADR-0004](0004-interpretacao-por-function-calling-com-politica-de-confianca.md),
  [ADR-0009](0009-mistral-ai-como-provedor-de-llm-na-validacao.md),
  [ADR-0023](0023-descricao-de-lancamento-extraida-pelo-llm.md),
  [ADR-0029](0029-intencao-adiada-e-precedencia-de-mensagem-nova.md),
  [ADR-0033](0033-valor-ausente-com-categoria-conhecida-pergunta-o-valor.md) e
  [ADR-0034](0034-valor-so-vale-se-a-pessoa-escreveu-digito.md)

## Contexto

O ROADMAP trava a Etapa 6 numa taxa: "se a taxa de acerto de despesa ficar
abaixo de 90%, a Etapa 6 não começa. Precisão do interpretador é o produto." A
[ADR-0004](0004-interpretacao-por-function-calling-com-politica-de-confianca.md)
e a [ADR-0009](0009-mistral-ai-como-provedor-de-llm-na-validacao.md) repetem o
número como gatilho de revisão. **Nenhum documento diz como ele é calculado.**

A `estrategia-de-testes.md` nomeia um `nlu-eval` que "reporta taxa de acerto por
tool e matriz de confusão", e a entrega da Etapa 1 registra, no item 8 do que
ficou de fora, que ele não existe. Entre a Etapa 1 e hoje nada mudou: nada mede
o interpretador.

O buraco deixou de ser teórico quando a
[ADR-0029](0029-intencao-adiada-e-precedencia-de-mensagem-nova.md) fez a faixa
média perguntar em toda intenção que escreve, e a
[ADR-0033](0033-valor-ausente-com-categoria-conhecida-pergunta-o-valor.md) fez a
faixa baixa perguntar quando a categoria está resolvida e o valor falta. A fatia
"o bot perguntou" cresceu de propósito nas duas, e ela não tem lugar definido na
conta — o que significa que o portão que decide a continuidade do produto não é
calculável hoje, nem seria depois de quatro semanas de uso.

Três desfechos que hoje cairiam todos no mesmo balde e não são a mesma coisa:

- `mercado`, sem valor, o bot pergunta o valor. **É o comportamento correto** —
  a ADR-0033 existe para garanti-lo. Não há valor a adivinhar.
- `farmácia 60`, sem ambiguidade nenhuma, e o bot pergunta assim mesmo porque o
  modelo devolveu `confianca` 0,3. **É falha**, e é a que cobra um toque a mais
  sem motivo.
- `mercado` virando R$ 50,00 que ninguém escreveu (ADR-0034). **É dano**, de
  outra ordem de gravidade — e é a única classe de erro deste produto que a
  pessoa pode não perceber nunca: o recibo passa, ela não lê, e o saldo fica
  errado.

## Decisão

A Etapa 5 mede **três** números, sobre o mesmo conjunto de mensagens reais
anotadas, e **nenhuma mensagem sai do denominador de nenhum deles**.

O gabarito anota **o desfecho esperado**, não a tool esperada: executar (com
quais campos), perguntar (o quê), ou não entender.

### 1. Taxa de acerto de interpretação — é ela que carrega o portão de 90%

Fração das mensagens em que o desfecho do sistema bate com o desfecho anotado.

| Anotado | Sistema fez | Conta como |
|---|---|---|
| executar | executou com os campos certos | acerto |
| executar | perguntou | erro |
| perguntar X | perguntou X | **acerto** |
| perguntar X | executou | erro |
| perguntar X | perguntou outra coisa, ou não entendeu | erro |
| não entender | não entendeu | acerto |
| não entender | executou ou perguntou | erro |

Campos considerados: **categoria, valor e conta**. Descrição fica fora, por
decisão já tomada na [ADR-0023](0023-descricao-de-lancamento-extraida-pelo-llm.md)
— texto livre não tem gabarito, e incluí-lo contaminaria o portão.

O portão de 90% do ROADMAP continua onde está e com o texto que tem: esta
métrica, restrita ao fluxo de despesa. O que muda é que ele passa a ter fórmula.

### 2. Taxa de dano — escrita errada sem ter perguntado

Fração das mensagens que produziram escrita que a pessoa não pediu **sem nenhuma
confirmação no meio**: valor que ela não escreveu, categoria que ela não
escreveu, item com status que ela não pediu.

**Qualquer ocorrência de dinheiro gravado que a pessoa não escreveu trava a
Etapa 6**, independentemente da métrica 1. Não é um percentual a negociar: é a
única classe de erro que não se anuncia, e a
[ADR-0034](0034-valor-so-vale-se-a-pessoa-escreveu-digito.md) foi a terceira
correção da mesma coisa em duas semanas.

### 3. Taxa de fricção — quanto o produto pergunta

Fração das mensagens que viraram pergunta, necessária ou não, reportada
separando as duas. **Sem portão**: é diagnóstico, não critério de continuidade.
É o número que a [ADR-0029](0029-intencao-adiada-e-precedencia-de-mensagem-nova.md)
já pede no gatilho de revisão dela ("se passar de um quinto, o limiar `high`
está baixo demais"), e é o que separa dois diagnósticos que uma métrica só
confunde: fricção alta **com** acerto alto é limiar mal calibrado
([decisão aberta #7](../DECISOES-ABERTAS.md)); fricção alta **com** acerto baixo
é interpretador ruim ([decisão aberta #3](../DECISOES-ABERTAS.md)).

### O que esta ADR exige e não decide

Medir qualquer um dos três exige saber **qual prompt e qual modelo** produziram
cada interpretação, e hoje `inbound_message` grava `intent_json` e `confidence` e
mais nada disso. Exige também onde o gabarito anotado é guardado, e por quanto
tempo a mensagem original fica ([decisão aberta #4](../DECISOES-ABERTAS.md),
escopo LGPD). Nenhuma das duas é decidida aqui — esta ADR define **o que** se
mede; o instrumento é decisão separada, e ela é o que bloqueia começar a medir.

> **Resolvido no mesmo dia pela [ADR-0036](0036-instrumento-de-medicao-da-etapa-5.md).**
> Proveniência em duas colunas de `inbound_message`, `prompt_version` como hash
> da parte estática do prompt, métrica calculada **por** versão em vez de prompt
> congelado, gabarito versionado em `docs/04-qualidade/nlu-eval/`, e retenção
> mantida durante a validação com decisão na Etapa 6. O parágrafo acima fica como
> estava: era verdade quando esta ADR foi aceita, e é o que explica por que a
> 0036 existe.

## Alternativas consideradas

### A. Pergunta fica fora do denominador
Foi a proposta inicial do autor, e o raciocínio dela está certo: perguntar não é
errar. Descartada por ser **gameável pela configuração**: bastaria subir
`novoapp.conversation.confidence.high` de 0,8 para 0,99 para o bot perguntar em
quase tudo, quase toda mensagem sair da conta, e a taxa ir a ~100% com o produto
pior. Um portão que melhora quando o produto piora não mede nada — e o ajuste
que mais o infla é exatamente o que a ADR-0004 chama de morte do produto ("se
cada mensagem gera uma pergunta de confirmação, o produto perde a razão de
existir"). O que a proposta queria proteger — não punir a pergunta legítima —
está preservado: pergunta esperada conta como acerto.

### B. Uma métrica só, com pergunta contando como erro
Descartada pelo motivo oposto: trata "perguntou o valor de `mercado`" e "gravou
R$ 50,00 que ninguém escreveu" como o mesmo evento, quando o primeiro é o
comportamento correto e o segundo é o pior erro que o produto sabe cometer.
Uma média entre coisas de gravidade oposta não é diagnóstico de nada, e
esconderia justamente a métrica 2, que é a que precisa aparecer sozinha.

### C. Taxa de acerto por tool, como a `estrategia-de-testes.md` escreve hoje
Descartada como métrica principal, mantida como recorte. "Qual tool o modelo
escolheu" era a pergunta certa quando havia uma tool; com seis disputando a
escolha, a matriz de confusão por tool continua útil para decidir se o cardápio
precisa encolher (gatilho de revisão do `sdd-modulo-nlu.md`) — mas ela não
enxerga o caso que mais dói, que é a tool certa com o campo errado:
`registrarDespesa` com valor inventado acerta a tool e destrói a confiança no
saldo.

### D. Adiar a definição para quando a Etapa 5 começar de fato
Descartada: a Etapa 5 já começou. A família usa o produto em produção desde
2026-09-05, e quatro dias de uso real (2026-09-17 a 2026-09-19) produziram duas
ADRs e sete commits. Definir a métrica depois de acumular o dado significa
definir a métrica olhando o dado que ela vai julgar, que é o jeito mais confiável
de escolher o número que dá o resultado desejado.

## Consequências

### Positivas
- O portão de 90% passa a ter fórmula, e deixa de subir quando a config afrouxa.
- A classe de erro mais grave do produto — dinheiro que ninguém escreveu — ganha
  métrica e portão próprios, que hoje não tinha nenhum dos dois.
- Fricção e imprecisão deixam de ser o mesmo número, o que é o que torna as
  decisões abertas #3 (trocar de provedor) e #7 (limiar) distinguíveis: hoje as
  duas seriam respondidas pelo mesmo dado, e são problemas diferentes.
- Anotar desfecho em vez de tool torna anotável o caso em que a resposta certa
  **é** perguntar — que com a tool como gabarito não tinha como ser expresso.

### Negativas
- **Anotar desfecho esperado é julgamento humano, mensagem a mensagem**, e é mais
  caro do que anotar a intenção. O alívio é que "o que o bot deveria ter feito
  com essa mensagem?" é mais fácil de responder do que "qual tool era a certa?",
  mas o custo é real e recai sobre uma pessoa só.
- **A fronteira entre pergunta necessária e desnecessária é julgamento, não
  regra.** `Pet shop 80` é claramente necessária (criar categoria é irreversível
  pelo chat); uma ambiguidade entre "Mercado" e "Mercado livre" quase sempre é;
  mas haverá casos de borda, e quem anota é a mesma pessoa que escreveu o
  produto. Mitigação possível, não decidida aqui: anotar antes de ver o que o
  sistema respondeu.
- **Três números é mais difícil de comunicar que um.** O risco é o portão virar
  conversa sobre qual métrica olhar. Contra isso, só a métrica 1 e a regra de
  dinheiro inventado da métrica 2 travam alguma coisa; a 3 nunca trava nada.
- **Nenhuma das três é calculável hoje**, e esta ADR não conserta isso: sem saber
  qual prompt e qual modelo produziram cada linha, o log de 2026-09-05 até aqui
  não é comparável consigo mesmo. Todo dia de uso real sem esse dado é um dia
  que não entra na calibração.

## Gatilhos de revisão

- **Ao decidir o instrumento** (o que grava prompt e modelo, onde mora o
  gabarito, retenção da mensagem): se o custo de anotar desfecho se mostrar
  impraticável para uma pessoa só, é aqui que se reavalia amostragem em vez de
  anotação completa — nunca tirar pergunta do denominador, que é o que esta ADR
  existe para impedir.
- **Etapa 5, ao fechar**: se a métrica 1 passar de 90% e a 3 mostrar que mais de
  um quinto das mensagens vira pergunta, o produto passou no portão e continua
  ruim de usar. Isso é sinal de que o portão precisa de um segundo critério, não
  de que a métrica 1 está errada.
- **Se o provedor mudar** ([decisão aberta #3](../DECISOES-ABERTAS.md)): as três
  métricas são comparáveis entre modelos só se o gabarito não mudar junto.
  Recalcular o baseline com o gabarito congelado antes de comparar.
