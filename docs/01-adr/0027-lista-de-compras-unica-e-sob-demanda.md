---
tipo: adr
numero: 27
status: aceita
data: 2026-09-08
modulos:
  - shopping
  - banco
  - glossario
depende_de:
  - ADR-0011
  - ADR-0013
supera: []
superada_por:
corrigida_em:
---

# ADR-0027 — Lista de compras única por household, criada sob demanda

- **Impacta**: `shopping` (o módulo inteiro), banco (`shopping_list`,
  `list_item`), glossário; dá lastro ao
  [`sdd-modulo-shopping.md`](../02-arquitetura/sdd-modulo-shopping.md), que
  nasceu na Etapa 2a sem ADR nenhuma sustentando suas decisões de produto

## Contexto

A Etapa 2a implementou mercado inteiro — nove cenários — e escreveu o SDD do
módulo antes do código, como o CLAUDE.md manda. O que não existiu foi ADR: as
decisões de produto de mercado (quantas listas, quando a lista nasce, o que
acontece quando duas pessoas pedem a mesma coisa) foram tomadas na
implementação e registradas só no SDD.

Isso é uma inversão do processo do projeto: "decisão estrutural vira ADR antes
de virar código". O `sdd-modulo-shopping.md` declara a ausência no segundo
parágrafo, mas declarar não é lastro — o SDD passou a ser a fonte primária de
decisões que nenhuma ADR tomou, e a view "SDD sem lastro" do `sdd.base` não o
pegou porque o frontmatter lista ADR-0003/0004/0018, que sustentam RLS e a
mecânica de pergunta, não o domínio de mercado.

O autor apontou isso ao revisar a entrega da Etapa 2a: "é possível ter um SDD
órfão? acho que o correto seria criar a ADR e não permitir que isso ocorra
mais."

Esta ADR é escrita **depois** do código, o que não é o padrão e não deve virar
um. Ela registra decisões que já estão implementadas e verdes em nove cenários
de aceitação; o que muda é que passam a ter lastro e a poder ser superadas pelo
mecanismo normal, em vez de serem editadas dentro de um SDD.

## Decisão

**Um household tem no máximo uma lista de compras ativa por vez.** Não há
seleção de lista, nem nome escolhido pelo usuário, nem "adicionar à lista da
farmácia". Garantido por índice único parcial
(`shopping_list (household_id) WHERE status = 'ACTIVE'`), e não por disciplina
de serviço — é invariante de estrutura, do mesmo tipo que a unicidade de irmãos
em `category`.

**A lista nasce no primeiro item, não no onboarding.** É a diferença explícita
para a conta `WALLET`, que a [ADR-0011](0011-cartao-de-credito-e-fatura.md) cria
junto com o household: conta tem default óbvio e é pré-requisito de qualquer
lançamento, enquanto household que nunca falou de mercado não precisa carregar
uma lista vazia. `consultarLista` sem lista nenhuma responde o mesmo que lista
vazia — a diferença entre os dois estados não é observável pelo usuário, e não
deve ser.

**Item que já está faltando é reconhecido, não duplicado.** Duas pessoas
avisando que acabou o arroz produzem um item e uma resposta que diz quem pediu
antes. Garantido por índice único parcial
(`list_item (shopping_list_id, lower(name)) WHERE status = 'PENDING'`). Parcial
de propósito: comprado o arroz de hoje, ele pode faltar de novo amanhã.

**Quantidade e unidade são opcionais e nunca viram pergunta.** "acabou o arroz"
grava item sem quantidade, e esse é o caso normal — nunca "quantos quilos?".
Mesmo argumento que a [ADR-0023](0023-descricao-de-lancamento-extraida-pelo-llm.md)
usou para a descrição: perguntar por campo opcional custa a proposta de valor.
`quantity` é `numeric`, não inteiro — "meio quilo de queijo" é tão comum quanto
"2 kg de arroz", e a regra de dinheiro inteiro é sobre dinheiro.

**Marcar item comprado não mexe em dinheiro.** `marcarItemComprado` marca o item
e nada mais. Fechar a lista gerando lançamento é `fecharCompra`, o elo da Etapa
3, e é lá que a atomicidade entre os dois domínios é decidida.

## Alternativas consideradas

### A. Várias listas simultâneas (mercado, farmácia, feira)
Descartada **por ora**, e é a [decisão aberta #15](../DECISOES-ABERTAS.md), que
continua aberta. Nada no uso real pediu isso ainda, e cada lista a mais obriga
toda mensagem a dizer de qual lista fala — "acabou o arroz" viraria "acabou o
arroz na lista do mercado", ou o modelo teria de adivinhar. Se a decisão #15 for
tomada, é esta ADR que precisa ser superada, e o custo é conhecido: cai o índice
único parcial e `adicionarItemLista` ganha um parâmetro.

### B. Lista implícita criada no onboarding, espelhando a conta WALLET
Descartada: a simetria é aparente. A ADR-0011 argumenta especificamente sobre
conta — "o usuário nunca escolheria um nome diferente" — e nada equivalente foi
dito sobre lista. Household que só usa finanças ficaria com uma lista vazia para
sempre, e `consultarLista` precisaria tratar "sem lista" de qualquer jeito na
Etapa 3, quando fechar compra pode fechar a única lista existente.

### C. Item repetido vira um segundo item na lista
Descartada: é o comportamento que o cenário "Item já pendente na lista" existe
para proibir. Lista de família tem duas pessoas escrevendo nela sem combinar
antes; duplicata é o modo de falha esperado, não a exceção.

## Consequências

### Positivas
- Mercado inteiro sem nenhuma pergunta de "qual lista?" — a mensagem mais curta
  possível funciona, que é a proposta de valor do produto.
- Os dois índices parciais tornam as invariantes verificáveis no banco: violá-las
  exige alterar o schema, não esquecer uma validação.
- Household que nunca usa mercado não carrega estado de mercado.

### Negativas
- **Nome de item é comparado por `lower(name)`, sem acento nem plural.** "cafe" e
  "café" são dois itens; "ovo" e "ovos" também. É o mesmo risco que a
  [ADR-0024](0024-categoria-sugerida-por-texto-livre.md) já assumiu para nome de
  categoria, e a decisão de aproximar deveria valer para os dois ao mesmo tempo,
  não para um só.
- Uma lista só significa que fechar compra (Etapa 3) fecha tudo que está
  pendente, sem meio-termo entre "comprei o mês inteiro" e "comprei só o pão".
  O `list_checkout` já foi modelado para permitir fechamento parcial; esta ADR
  não decide como isso é pedido no chat.
- A ADR nasce depois do código. O risco disso não é teórico: uma ADR escrita
  para justificar o que já existe tende a não considerar de verdade as
  alternativas. As três acima foram escritas com esse viés em mente, mas quem
  ler daqui a seis meses deve saber que a ordem foi essa.

## Gatilhos de revisão

- Se a [decisão aberta #15](../DECISOES-ABERTAS.md) (múltiplas listas) for
  tomada, esta ADR é superada, não editada.
- Se o uso real mostrar item duplicado por acento ou plural, a correspondência
  aproximada entra aqui e na ADR-0024 juntas — é o mesmo problema em dois
  domínios.
- Etapa 3, ao desenhar `fecharCompra`: se o fechamento parcial precisar de mais
  de uma lista para ser expressável, a alternativa A volta antes do previsto.
