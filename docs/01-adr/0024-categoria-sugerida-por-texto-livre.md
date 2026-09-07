---
tipo: adr
numero: 24
status: aceita
data: 2026-09-07
modulos:
  - nlu
  - conversation
  - finance
depende_de:
  - ADR-0004
  - ADR-0013
supera: []
superada_por:
corrigida_em:
---

# ADR-0024 — Categoria sugerida por texto livre, fora do enum

- **Impacta**: `nlu` (schema da tool `registrarDespesa`), `conversation` (nova
  confirmação de criação de categoria), `finance` (criação de categoria a
  partir de texto do chat); fecha a contradição entre
  [ADR-0004](0004-interpretacao-por-function-calling-com-politica-de-confianca.md)
  e [ADR-0013](0013-household-novo-comeca-sem-categorias.md); Etapa 2a do
  roadmap

## Contexto

`RegisterExpenseTool.java` declara `categoria` como `JsonEnumSchema` montado
com `categoryNames` — as categorias que já existem no household. É decisão
deliberada da [ADR-0004](0004-interpretacao-por-function-calling-com-politica-de-confianca.md):
"categorias reais no contexto reduzem alucinação de categoria inexistente".
Só que um enum fechado não tem como expressar um valor que não está nele. Se
Ana escreve "pet shop 80" e "Pet shop" não existe no household, o modelo não
tem parâmetro onde colocar essa palavra — a tool inteira fica inutilizável
para essa mensagem, e `NluService` cai em `Intent.unknown()`.

A [ADR-0013](0013-household-novo-comeca-sem-categorias.md) decidiu o oposto
do que esse desenho permite: "household novo começa sem nenhuma categoria
pré-criada. A primeira menção a uma categoria inexistente, em qualquer canal,
dispara o fluxo de criação." O cenário `@etapa2` já escrito em
`financas-lancamento-por-chat.feature` descreve exatamente esse fluxo:

> Quando "Ana" envia "pet shop 80"
> Então nenhuma despesa é registrada ainda
> E "Ana" recebe uma única pergunta oferecendo criar a categoria "Pet shop"
> Quando "Ana" responde "sim"
> Então a categoria de despesa "Pet shop" é criada no household "Silva"
> E uma despesa de R$ 80,00 é registrada nessa categoria

Isso não existe hoje em nenhum código ou documento — nem tool, nem
`conversation`, nem `finance` sabe extrair "Pet shop" de "pet shop 80".
`sdd-modulo-nlu.md` já reconhece o buraco ("tratar isso como 'oferecer criar
categoria' é lógica de `conversation` ainda não escrita") mas erra o
diagnóstico: `conversation` só recebe `confidence`, nunca o texto cru, então
não há como ele extrair um nome que ninguém extraiu antes dele.

Pior: o mesmo SDD documenta que household sem nenhuma categoria "não gasta
chamada de modelo — o enum ficaria vazio, que não é schema válido, devolve
confiança baixa direto." Ou seja: da forma como o schema está montado hoje, o
household novo — o único caso em que a ADR-0013 promete que a primeira menção
cria a categoria — é justamente o caso em que o modelo nunca chega a ser
chamado de forma útil. As duas ADRs aceitas se contradizem, e é o primeiro
cenário `@etapa2` do arquivo de finanças.

## Decisão

A tool `registrarDespesa` ganha um parâmetro opcional `categoria_sugerida`
(string livre), preenchido pelo modelo **no lugar de** `categoria` quando
nenhuma categoria existente corresponde ao que a pessoa escreveu. Os dois
parâmetros são mutuamente exclusivos por instrução na descrição de cada um —
o modelo preenche exatamente um dos dois, nunca ambos. O enum de `categoria`
continua fechado às categorias reais; a garantia anti-alucinação da
[ADR-0004](0004-interpretacao-por-function-calling-com-politica-de-confianca.md)
não muda para categoria já existente.

Quando o household tem zero categorias de despesa (caso da
[ADR-0013](0013-household-novo-comeca-sem-categorias.md)), a propriedade
`categoria` é omitida do schema construído para essa chamada — não um enum
vazio, que é schema inválido, mas a ausência da propriedade. Sem `categoria`
no schema, o único caminho que resta ao modelo para registrar despesa é
preencher `categoria_sugerida`, o que faz a primeira mensagem de um household
novo efetivamente disparar o fluxo que a ADR-0013 prometeu.

`nlu` é quem extrai o texto livre — é a mesma chamada de função que já resolve
valor e conta, não uma etapa nova. O resultado de uma chamada com
`categoria_sugerida` preenchida não cai na linha "categoria inexistente →
opções numeradas" da tabela da ADR-0004: aquela linha descreve escolher entre
alternativas existentes, não confirmar um nome novo. Esta ADR cria uma
confirmação distinta — uma `PendingAction` de sim/não perguntando "criar a
categoria 'Pet shop'?" — cujo "sim" cria a categoria e, na sequência, executa
o registro da despesa com os demais parâmetros já capturados na mesma
mensagem original. A ADR-0004 não é editada; esta ADR refina o que acontece
nessa célula específica da tabela dela.

## Alternativas consideradas

### A. Tool `criarCategoria` própria
Descartada — é a alternativa que o próprio Josimar apontou como pior: uma
tool a mais no contexto compete pela escolha do modelo em toda mensagem, não
só nas que precisam dela. Uma despesa em categoria parecida com uma já
existente ("mercado" quando já existe "Mercado e feira") passa a correr o
risco de o modelo escolher criar categoria nova em vez de usar a existente —
o oposto do que a ADR-0004 quer.

### B. `categoria` como texto livre sempre, sem enum
Descartada — perde a garantia central da ADR-0004. Sem enum, o modelo pode
inventar variações de categorias que já existem por erro de grafia ou
sinônimo ("Mercado" vs "mercado" vs "Supermercado"), fragmentando dado
financeiro em vez de resolver o caso de categoria genuinamente nova.

### C. Deixar o caso de household zero-categoria fora desta ADR
Descartada — é exatamente a contradição que motivou esta ADR. Resolver
"categoria inexistente" sem resolver "nenhuma categoria existe ainda" deixa a
ADR-0013 quebrada do jeito que está hoje.

## Consequências

### Positivas
- Fecha a contradição entre ADR-0004 e ADR-0013; a primeira menção a
  categoria inexistente passa a ter caminho real, incluindo o household novo.
- Mantém intacta a garantia anti-alucinação do enum para categoria já
  existente — a mudança é aditiva, não substitui o mecanismo aceito.
- Custo de schema baixo: um parâmetro opcional a mais, sem tool nova, sem
  migration (a criação de categoria em si já é uma operação simples em
  `finance`).

### Negativas
- **A exclusividade mútua entre `categoria` e `categoria_sugerida` não é
  garantida pelo schema**, só por instrução textual ao modelo. Se o modelo
  preencher os dois, ou nenhum, `nlu` precisa de uma regra defensiva explícita
  (qual prevalece, ou tratar como confiança baixa) que esta ADR não
  especifica em código — fica para a implementação, mas o comportamento
  precisa ser testado, não assumido.
- Texto livre é superfície nova de alucinação sem gabarito para a métrica de
  acerto da Etapa 5 — mesma classe de risco que a
  [ADR-0023](0023-descricao-de-lancamento-extraida-pelo-llm.md) aceitou para
  `descricao`, mas aqui o resultado cria dado permanente (uma categoria nova)
  em vez de um campo de texto cosmético.
- Nome sugerido pode duplicar categoria existente por variação (maiúscula,
  plural, sinônimo) sem que o modelo perceba — esta ADR não define
  normalização ou correspondência aproximada antes de criar; fica como risco
  aberto.
- Household sem nenhuma categoria faz toda primeira mensagem — mesmo uma
  claríssima como "mercado 50" — passar pelo fluxo de confirmação de criação,
  porque não há enum nenhum para o modelo escolher. Isso é esperado, mas é um
  custo real de UX nas primeiras mensagens de cada household novo, não um
  efeito colateral gratuito.

## Gatilhos de revisão

Se a amostra manual da Etapa 5 mostrar categorias duplicadas ou quase-idênticas
criadas com frequência, adicionar correspondência aproximada antes de criar.
Se o modelo preencher os dois parâmetros na mesma chamada com frequência
relevante, reavaliar o mecanismo de exclusão mútua — inclusive considerar a
tool separada (alternativa A) apesar do custo já descartado aqui, se o dado
real mostrar que o risco dela é menor do que o risco desta ausência de
garantia estrutural.
