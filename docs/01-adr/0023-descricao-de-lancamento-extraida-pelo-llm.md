---
tipo: adr
numero: 23
status: aceita
data: 2026-09-05
modulos:
  - nlu
  - conversation
  - finance
depende_de:
  - ADR-0004
supera: []
superada_por:
corrigida_em:
---

# ADR-0023 — Extrair a descrição do lançamento pelo LLM, fora da política de confiança

- **Impacta**: `nlu` (schema da tool `registrarDespesa`), `conversation`
  (recibo), `finance` (`transaction.description`), Etapa 2 do roadmap;
  completa a premissa de [ADR-0012](0012-edicao-de-lancamento-entre-membros.md) (correção de descrição) e estende
  [ADR-0004](0004-interpretacao-por-function-calling-com-politica-de-confianca.md) sem contradizê-la
- **Termos**: [Descrição](../00-produto/glossario.md#finanças),
  [Lançamento](../00-produto/glossario.md#finanças)

## Contexto

`transaction.description` existe no modelo de dados desde o schema inicial
(`V1__initial_schema.sql`, `modelo-de-dados.md`) e é editável segundo a
[ADR-0012](0012-edicao-de-lancamento-entre-membros.md), que a lista entre os campos que uma correção pode alterar. Mas
nenhuma `.feature`, ADR ou SDD jamais disse de onde ela nasce. A tool
`registrarDespesa` declara `categoria`, `valor_cents` e `conta` — não há
origem possível para a descrição. Na prática a coluna só deixaria de ser nula
por uma tela de correção que ainda não existe.

Em 2026-09-05, no primeiro dia de uso real da Etapa 1, o autor enviou
`60 farmacia - remedio joaquim` sem que nada no produto pedisse isso. A
despesa foi registrada como Farmácia R$ 60,00 e o "remédio do Joaquim" foi
descartado da interpretação. É o tipo de sinal que a Etapa 5 foi desenhada
para capturar; chegou adiantado, e a Etapa 2 já vai mexer no schema da tool
(criar categoria, ambiguidade), então é o momento barato para agir.

O texto original não se perdeu: `transaction.source_message_id` aponta para
`inbound_message.raw_text`, que guarda a mensagem inteira. O que falta não é
o dado bruto — é o significado extraído dele, num campo que a tela de
correção da Etapa 4 possa mostrar e a [ADR-0012](0012-edicao-de-lancamento-entre-membros.md) possa versionar.

## Decisão

A tool `registrarDespesa` ganha o parâmetro opcional `descricao` (string,
não obrigatório), preenchido pelo LLM na mesma chamada que já resolve
categoria e valor. Nenhuma migration é necessária: a coluna já existe.

Três restrições fazem parte da decisão, não são detalhe de implementação:

1. **Só o resíduo semântico.** A descrição recebe o que sobra da mensagem
   depois de categoria, valor, conta e data. Se não sobra nada, é nula —
   `mercado 50` grava descrição nula, nunca "compra de mercado". Descrição
   que repete a categoria é ruído com aparência de informação, e é o que
   torna ilegível a lista da tela de correção (Etapa 4).

2. **Fora da política de confiança da [ADR-0004](0004-interpretacao-por-function-calling-com-politica-de-confianca.md).** Ausência ou baixa qualidade
   de descrição nunca gera pergunta, nunca reduz a confiança da intenção e
   nunca impede a execução. `farmácia 60` continua sendo recibo direto, não
   "o que você comprou?". Perguntar por um campo opcional custaria justamente
   a proposta de valor que a ADR-0004 protege — ser mais rápido que abrir o
   app.

3. **Fora da métrica de acerto da Etapa 5.** Texto livre não tem gabarito
   contra o qual comparar. A taxa de acerto por tool, e o portão de 90% que
   decide se a Etapa 6 começa, consideram categoria, valor e conta. A
   qualidade da descrição é avaliada à parte, por amostra manual.

O recibo ecoa a descrição registrada (`Farmácia R$ 60,00 — remédio do
Joaquim. Responda "desfazer".`). É o que torna a extração visível no instante
em que acontece, coerente com o argumento da [ADR-0004](0004-interpretacao-por-function-calling-com-politica-de-confianca.md) de que ser agressivo
na execução automática só se sustenta porque o erro é recuperável em um toque.

## Alternativas consideradas

### A. Não ter descrição própria; a tela de correção lê `inbound_message.raw_text`
Descartada por três motivos independentes: `raw_text` só existe para
lançamento com `source = CHAT` (lançamento criado na web, e a eventual
entrada por foto de cupom da [decisão aberta #13](../DECISOES-ABERTAS.md), ficariam sem nada);
o texto cru não é editável sem virar outra coisa; e a [ADR-0012](0012-edicao-de-lancamento-entre-membros.md) já registra
correção de descrição em `transaction_edit`, o que exige campo próprio e
versionável. Os dois convivem com papéis distintos: `description` é o
significado, `raw_text` é a prova forense.

### B. Gravar o texto cru da mensagem como descrição
Descartada: `60 farmacia - remedio joaquim` no campo repete categoria e valor,
que já são colunas próprias. Além de ruído, cria duas fontes para a mesma
informação que divergem na primeira correção — e o texto cru já está
preservado em `inbound_message.raw_text` de qualquer forma.

### C. Extrair por regra determinística (remover valor e nome da categoria, guardar o resto)
Descartada pela mesma razão que a [ADR-0004](0004-interpretacao-por-function-calling-com-politica-de-confianca.md) descartou parser por regex:
funciona em `60 farmacia - remedio joaquim` e quebra em "gastei 60 na farmácia
com o remédio do Joaquim". A chamada ao modelo já está sendo feita; o custo
marginal de um parâmetro no schema é menor que o de manter uma gramática.

## Consequências

### Positivas
- Uma linha de R$ 60,00 continua fazendo sentido três meses depois, que é
  exatamente o que a tela de correção da Etapa 4 precisa mostrar.
- A premissa da [ADR-0012](0012-edicao-de-lancamento-entre-membros.md) deixa de ser órfã: passa a existir descrição para
  corrigir.
- Custo marginal baixo — sem migration, sem tabela nova, no mesmo momento em
  que a Etapa 2 já reabre o schema da tool.

### Negativas
- **Superfície nova de alucinação num campo sem gabarito.** O modelo pode
  escrever palavra que não estava na mensagem, e nada no sistema detecta isso
  automaticamente. A única mitigação é humana: o recibo no instante do
  lançamento e a tela de correção depois. É a fraqueza central desta decisão.
- A regra "só o resíduo" é julgamento do modelo, não validação de schema.
  Cenário de aceitação cobre exemplo, não a regra inteira — `mercado 50`
  virando "compra de mercado" pode reaparecer em variação não testada.
- Por ficar fora da métrica da Etapa 5, a qualidade da descrição não terá
  número. Se degradar, isso aparece como incômodo subjetivo e tarde.
- Um parâmetro a mais no schema divide a atenção do modelo e pode degradar
  levemente a precisão dos parâmetros que importam. A medir na Etapa 5, junto
  com o resto.

## Gatilhos de revisão

Se, na Etapa 5, a amostra manual mostrar descrição majoritariamente redundante
ou inventada, voltar a descrição a nula por padrão na interpretação e mantê-la
apenas como campo de edição manual na web. Se a entrada por foto de cupom
([decisão aberta #13](../DECISOES-ABERTAS.md)) for decidida, reavaliar de onde nasce a descrição
nesse caminho — o resíduo de uma imagem não é o resíduo de uma frase.
