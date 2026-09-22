---
tipo: adr
numero: 37
status: aceita
data: 2026-09-21
modulos:
  - nlu
  - shopping
  - conversation
depende_de: [ADR-0004, ADR-0013, ADR-0024, ADR-0031, ADR-0034]
supera: []
superada_por:
corrigida_em:
---

# ADR-0037 — A categoria do fechamento sai do cardápio da família, e fechamento sem categoria não acontece

- **Impacta**: a Etapa 3 do [ROADMAP](../../ROADMAP.md) — é o primeiro cenário do
  elo, e sem esta decisão a tool não tem como ser escrita —, `nlu` (a tool
  `fecharCompra` e seus parâmetros), `shopping`
  ([`checkout`](../02-arquitetura/sdd-modulo-shopping.md) já recebe `categoryId`
  e nunca soube de onde ele vem) e `conversation` (o desfecho quando não vem
  nenhum)

## Contexto

`comprei tudo, 180` não nomeia categoria nenhuma. O cenário
`Fechar a lista inteira com valor`
([elo-fechamento-de-compra.feature](../03-specs/features/elo-fechamento-de-compra.feature))
exige que a despesa entre em `"Mercado"`, e o `Contexto` do arquivo declara essa
categoria como já existente na família. Ou seja: o cenário pede que o sistema
escolha uma categoria que a pessoa não escreveu.

`ShoppingService.checkout(...)` já existe desde 2026-09-21 e recebe `categoryId`
pronto ([ADR-0031](0031-atomicidade-do-fechamento-de-compra.md)). Quem resolve
esse id nunca foi decidido — o método foi escrito sem chamador justamente para
não inventar a resposta.

Três fatos limitam o espaço:

- A [ADR-0024](0024-categoria-sugerida-por-texto-livre.md) decidiu que categoria
  que não existe **sempre** vira pergunta antes de ser criada, e que o parâmetro
  de categoria é um enum das categorias reais do household — é o que impede o
  modelo de inventar uma ([ADR-0004](0004-interpretacao-por-function-calling-com-politica-de-confianca.md)).
- A [ADR-0013](0013-household-novo-comeca-sem-categorias.md) manda household novo
  nascer sem categoria nenhuma. Nesse estado, `registrarDespesa` sai do ar como
  enum e só `categoria_sugerida` resta.
- A [ADR-0034](0034-valor-so-vale-se-a-pessoa-escreveu-digito.md) acabou de ser
  escrita porque o modelo completou um valor que ninguém digitou. Completar
  categoria tem a mesma forma, e o modelo já demonstrou que faz isso.

Nenhum dos nove cenários do elo cobre "fechar compra numa família que não tem
categoria onde a compra caiba".

## Decisão

A tool `fecharCompra` declara **um só** parâmetro de categoria: o mesmo enum das
categorias de despesa reais do household que `registrarDespesa` usa. O modelo
escolhe uma delas; a descrição do parâmetro diz explicitamente que é a categoria
onde a compra de mercado da família costuma entrar.

**Não há `categoria_sugerida` em `fecharCompra`.** Quando o modelo chama a tool
sem categoria resolvível — porque a família não tem nenhuma, ou porque ele
escreveu um nome que não está no enum —, a interpretação vira
`Intent.Unknown`, exatamente como `registrarDespesa` sem categoria nenhuma já
vira hoje em `NluService`. O desfecho é "não entendi essa", e a pessoa refaz a
compra pelo caminho que tem cenário escrito: `registrarDespesa`, que cria
categoria com confirmação.

## Alternativas consideradas

### A. `fecharCompra` também tem `categoria_sugerida`, como `registrarDespesa`
Descartada por escopo e por risco, nessa ordem.

Por escopo: exigiria que a pendência de criação de categoria soubesse terminar
em fechamento e não em despesa — `afterCategoryCreated` hoje desemboca direto em
`registerOrAskAmount` — e nenhum dos nove cenários descreve esse fluxo.
Construir máquina de confirmação sem cenário que a descreva é a ficção que o
`CLAUDE.md` proíbe para as etapas não documentadas, aplicada dentro de uma
documentada.

Por risco: seria a primeira vez que criar categoria e fechar lista aconteceriam
na mesma confirmação. O "sim" passaria a significar três coisas ao mesmo tempo —
crie a categoria, feche estes itens, lance este valor — e a
[ADR-0024](0024-categoria-sugerida-por-texto-livre.md) pede confirmação
justamente porque criar categoria é o caminho caro e irreversível.

### B. Categoria fixa por convenção: o fechamento sempre entra em "Mercado"
Descartada: o nome é da família, não nosso. A
[ADR-0013](0013-household-novo-comeca-sem-categorias.md) existe precisamente
porque impor vocabulário de categoria foi recusado como decisão de produto — uma
família chama de "Supermercado", outra de "Feira", outra de "Casa". Casar por
nome literal criaria uma categoria com nome nosso no primeiro fechamento, e a
família passaria a ter duas coisas para a mesma despesa.

### C. Coluna nova: `shopping_list.default_category_id`
Descartada agora, e é a que mais tem futuro. Resolveria de vez — a lista saberia
onde suas compras caem, sem depender do modelo. Mas não há como preenchê-la sem
perguntar, e perguntar exige tela (Etapa 4) ou um comando de configuração por
chat que não existe e não tem cenário. Schema antes do comportamento que o usa é
exatamente o que a `V3` evitou ao deixar `list_checkout` de fora.

### D. Perguntar a categoria a cada fechamento
Descartada: o elo é uma mensagem, um recibo. `comprei tudo, 180` virar uma
pergunta antes de qualquer efeito destrói o argumento do produto — a
demonstração que vende é "uma mensagem fecha a lista e lança a despesa"
([ROADMAP](../../ROADMAP.md), Etapa 3). Quem tem a categoria no cardápio não
deve pagar pela família que não tem.

## Consequências

### Positivas

- A tool nova não inventa categoria: o enum é a mesma defesa que a ADR-0004
  montou, reusada sem exceção nova.
- `checkout` continua recebendo `categoryId` pronto e não ganha regra de
  resolução de nome — a fronteira que a ADR-0031 desenhou fica intacta.
- Zero máquina nova em `conversation` para um caminho que nenhum cenário
  descreve.

### Negativas

- **Família sem categoria de mercado recebe "não entendi essa" numa mensagem que
  o bot entendeu perfeitamente.** É o mesmo defeito que a
  [ADR-0033](0033-valor-ausente-com-categoria-conhecida-pergunta-o-valor.md)
  consertou para despesa, aceito aqui de propósito enquanto não há cenário — e
  ele é registrado como decisão aberta, não como limitação natural.
- A escolha da categoria fica a cargo do modelo em uma mensagem que não a
  contém. É julgamento, não extração, e vai aparecer na matriz de confusão da
  Etapa 5 como erro de `fecharCompra` quando a família tiver duas categorias
  plausíveis ("Mercado" e "Feira").
- Some a simetria entre as duas tools que lançam dinheiro: `registrarDespesa`
  cria categoria, `fecharCompra` não. Quem ler as duas lado a lado vai estranhar,
  e é por isso que está escrito aqui.

## Gatilhos de revisão

- **Ao escrever o cenário de "fechar compra sem categoria onde ela caiba"**: é a
  decisão aberta #26. Escrito o cenário, a alternativa A deixa de ser ficção e
  passa a ser a implementação óbvia.
- **Etapa 4**: com tela, a alternativa C fica barata — a família escolhe a
  categoria da lista uma vez e o modelo para de julgar. Se a Etapa 5 mostrar
  erro de categoria em fechamento, é esta alternativa que volta à mesa.
- **[Decisão aberta #15](../DECISOES-ABERTAS.md)** (múltiplas listas: mercado,
  farmácia, feira): decidida, "a categoria da lista" deixa de ser uma pergunta
  sobre a família e passa a ser um atributo de cada lista — e a alternativa C
  vira praticamente obrigatória.
