---
tipo: adr
numero: 26
status: aceita
data: 2026-09-07
modulos:
  - nlu
  - conversation
  - finance
depende_de:
  - ADR-0016
  - ADR-0018
  - ADR-0024
supera: []
superada_por:
corrigida_em:
---

# ADR-0026 — Hierarquia na criação de categoria por chat, com correção por texto livre

- **Impacta**: `nlu` (nova tool de resolução de pendência),
  `conversation` (terceira via de resposta a `PendingAction`), `finance`
  (criação de categoria com pai); estende
  [ADR-0024](0024-categoria-sugerida-por-texto-livre.md) e
  [ADR-0018](0018-central-de-pendencias.md) sem contradizê-las; respeita o
  limite de um nível da [ADR-0016](0016-subcategoria.md); Etapa 2a do
  roadmap

## Contexto

A [ADR-0024](0024-categoria-sugerida-por-texto-livre.md) resolveu criar
categoria por chat, mas só pensou em categoria solta: `categoria_sugerida`
é uma string sem noção de pai, a `PendingAction` que ela gera é um sim/não
("criar categoria 'Pet shop'?"), e o próprio código que monta o contexto do
modelo confirma que hoje não existe hierarquia nenhuma disponível pra
`nlu` — `CategoryView` é só `(id, name)`, sem `parentCategoryId`, e
`CategoryRepository.listExpenseCategories()` traz raiz e subcategoria juntas
na mesma lista achatada, sem marcar qual é qual.

Isso não é problema enquanto a categoria sugerida faz sentido como raiz. Mas
o caso mais comum do dia a dia é o oposto: "restaurante eu e esposa 90"
deveria virar a subcategoria "Restaurantes" dentro de "Alimentação"
([ADR-0016](0016-subcategoria.md): `category.parent_category_id`, um nível),
não uma categoria solta chamada "Restaurantes". A ADR-0024, do jeito que
foi aceita, cria exatamente isso — uma raiz solta — porque nunca perguntou
nem decidiu pai nenhum. Pior: se nem "Restaurantes" nem "Alimentação"
existirem ainda no household, a criação de uma categoria com hierarquia
correta ("Alimentação" como raiz, "Restaurantes" como filha) não tem
caminho nenhum hoje — só criação avulsa.

Some ainda a isso a pergunta de resposta: a `PendingAction` de criação de
categoria, herdada da [ADR-0018](0018-central-de-pendencias.md), tem hoje só
duas saídas por curto-circuito determinístico (regra 6 do CLAUDE.md): `sim`
confirma, `não`/`desfazer` (ADR-0025) rejeita/cancela. Nenhuma das duas
permite o usuário corrigir a sugestão — dizer, por exemplo, "restaurante
dentro de alimentação" em resposta à pergunta "criar categoria
'Restaurante'?". Isso não é `sim`, não é `não`, não é `desfazer`: hoje não
tem tratamento nenhum, cairia no fallback genérico de "não entendi".

## Decisão

**Extração continua sem inventar hierarquia.** Na chamada original de
`registrarDespesa`, `nlu` extrai só o nome literal mencionado
("restaurante"), nunca infere a categoria-pai por conhecimento de mundo —
mesma disciplina de "só o resíduo" que a
[ADR-0023](0023-descricao-de-lancamento-extraida-pelo-llm.md) e a
[ADR-0024](0024-categoria-sugerida-por-texto-livre.md) já adotaram. Se a
mensagem original não menciona "alimentação", o modelo não deve adivinhar
isso na extração — a alucinação de hierarquia é o mesmo risco que essas
duas ADRs já fecharam para nome de categoria e descrição.

**A confirmação da ADR-0024 ganha uma terceira via: correção por texto
livre.** Qualquer resposta à `PendingAction` de criação de categoria que não
seja `sim`, `não` ou `desfazer` é tratada como correção da estrutura
pretendida, nunca como erro nem repetição cega da pergunta. Essa resposta
não é resolvida por curto-circuito — dispara uma nova chamada ao modelo,
usando uma tool dedicada só a esse momento, `confirmarCategoriaSugerida`
(parâmetros `nome`, obrigatório, e `categoria_pai`, opcional, ambos texto
livre), com a pergunta original (`question_asked`/`intent_json` da
`pending_action`, campos já existentes na ADR-0018) como contexto da
chamada. É a primeira pendência cujo terceiro caminho de resolução precisa
de LLM — a ADR-0018 previu o mecanismo como genérico por tipo de pendência,
mas nunca cobriu resposta livre reprocessada; fica registrado aqui.

**A resolução cria o que faltar da hierarquia, respeitando o limite de um
nível.** Ao confirmar — seja `sim` simples (sem pai, comportamento já
decidido pela ADR-0024) ou correção livre com `categoria_pai` preenchido —
`finance`: procura `categoria_pai` por nome entre as categorias-raiz do
household (comparação sem diferenciar maiúscula/minúscula); cria como raiz
se não existir; cria `nome` como filha dessa raiz. Se o nome resolvido para
`categoria_pai` já for, ele mesmo, uma subcategoria (já tem
`parent_category_id` próprio), a criação é recusada — dois níveis violaria
a [ADR-0016](0016-subcategoria.md) — e o bot volta a perguntar, tratando
como confiança baixa em vez de criar hierarquia inválida.

O recibo final ecoa a hierarquia criada ("Restaurantes (dentro de
Alimentação) — R$ 90,00. Responda \"desfazer\"."), pelo mesmo argumento já
usado na ADR-0023: tornar a extração visível no instante em que acontece é
o que sustenta ser agressivo na criação automática.

## Alternativas consideradas

### A. Inferir a categoria-pai já na extração original, por conhecimento de mundo do modelo
Descartada — contraria a disciplina de "só o resíduo literal" que a
ADR-0023 e a ADR-0024 já adotaram. O modelo poderia inventar qualquer
hierarquia plausível para a família errada, sem controle nenhum, reabrindo
exatamente o risco de alucinação que essas duas ADRs fecharam.

### B. Toda categoria sugerida nasce como raiz; hierarquia só se ajusta depois, pela web (Etapa 4)
Descartada — é o status quo da ADR-0024 sozinha, e é exatamente o que
motivou esta ADR: força esperar a PWA existir para corrigir toda categoria
criada por chat com hierarquia, quando subcategoria de gasto do dia a dia é
o caso mais comum desde a primeira mensagem, não uma exceção.

### C. Pedir hierarquia por opções numeradas (categorias-raiz existentes) em vez de texto livre
Descartada como via única — funciona bem quando a categoria-pai já existe
("1. Alimentação  2. Nenhuma, criar avulsa"), mas não cobre o caso de as
duas serem novas, nem deixa o usuário corrigir o nome sugerido em si (ex.
"Restaurante" para "Restaurantes"). Texto livre resolve os dois casos com
uma pergunta só, ao custo de uma chamada a mais ao modelo para interpretar
a resposta — trade-off aceito aqui; oferecer as duas vias juntas
(número quando aplicável, texto sempre disponível) fica como refinamento
possível, não decidido nesta ADR.

## Consequências

### Positivas
- Cobre o caso de uso que motivou esta ADR: "restaurante eu e esposa 90"
  termina com hierarquia correta mesmo quando nada existe ainda no
  household.
- Reaproveita a `pending_action` genérica da ADR-0018 sem mudança de
  schema — `intent_json`/`question_asked` já guardam contexto suficiente
  para a segunda chamada.
- Mantém a disciplina de não inventar hierarquia sem base textual, movendo
  a inferência para o momento em que o humano já está confirmando, nunca
  para a extração original.

### Negativas
- **A correção livre gasta uma segunda chamada ao modelo, exatamente o que
  a regra 6 queria evitar** ("confirmações não gastam LLM"). Esta ADR abre
  uma exceção explícita só para esse tipo de pendência — custo real de
  latência e superfície de erro, para fechar um caso que pode ser raro na
  prática (a maioria das correções talvez seja só ajuste de nome, sem
  pai). Não medido ainda.
- Caminho mais complicado vira três idas e vindas (mensagem → pergunta →
  correção → confirmação implícita na criação) contra uma do caminho feliz
  da ADR-0004 — se acontecer com frequência, vale revisitar a alternativa C
  como atalho para o caso comum.
- O limite de um nível (ADR-0016) ganha um segundo ponto de enforcement
  (chat, além da eventual tela web) — dois lugares para manter a mesma
  regra em sincronia; o teste de unidade que a ADR-0016 já previu precisa
  cobrir os dois caminhos de entrada, não só a criação futura pela web.
- `categoria_pai` por correspondência textual tem o mesmo risco de
  duplicação por variação que a ADR-0024 já assumiu para
  `categoria_sugerida` — "Alimentação" sem acento pode criar uma raiz nova
  em vez de reconhecer a existente.

## Gatilhos de revisão

Se a amostra da Etapa 5 mostrar que a maioria das correções é só ajuste de
nome, sem hierarquia, considerar simplificar: tratar texto livre como novo
nome por padrão (sem segunda chamada ao modelo), reservando a chamada extra
só para quando a resposta contiver marcador textual de hierarquia
("dentro de", "em", similar) — heurística mais barata que sempre chamar o
modelo.
