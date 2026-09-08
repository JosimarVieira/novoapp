---
tipo: sdd
modulo: nlu
status: escrito
atualizado_em: 2026-09-07
adrs:
  - ADR-0004
  - ADR-0009
  - ADR-0020
  - ADR-0023
  - ADR-0024
  - ADR-0026
---

# SDD — Módulo `nlu`

## Responsabilidade

Montar o contexto do household (categorias existentes, contas), chamar o LLM
com function calling ([ADR-0004](../01-adr/0004-interpretacao-por-function-calling-com-politica-de-confianca.md), provedor Mistral na validação, [ADR-0009](../01-adr/0009-mistral-ai-como-provedor-de-llm-na-validacao.md)) e
devolver `Intent` + confiança. Não executa nada, não persiste dado de
domínio.

## Escopo desta versão (Etapa 2a)

Cinco tools no contexto de uma mensagem comum — `registrarDespesa`,
`adicionarItemLista`, `marcarItemComprado`, `consultarLista`,
`convidarMembro` — mais uma sexta, `confirmarCategoriaSugerida`, declarada
sozinha e só no momento de ler a correção livre de uma pendência de categoria
([ADR-0026](../01-adr/0026-hierarquia-na-criacao-de-categoria-por-chat.md)).

**`convidarMembro` não está na lista de tools que a ADR-0004 enumera.** Aquela
lista descreve o mecanismo com os fluxos de domínio conhecidos em 2026-08-31, e
a [ADR-0020](../01-adr/0020-convite-de-membro.md) é posterior: ela exige que o
`OWNER` peça o convite ao bot, e esse comando parte de número já vinculado —
atravessa o pipeline de interpretação como qualquer outra mensagem, logo precisa
de tool. Não é contradição com a ADR-0004; é um fluxo que ela não conhecia.

**Não cobre ainda**: `registrarReceita`, `consultarSaldo`, `criarTarefa` e
`fecharCompra` — as quatro tools restantes da ADR-0004. As três primeiras não
têm `.feature` nesta etapa; `fecharCompra` é a Etapa 3.

## Depende de

- `finance` — leitura das categorias de despesa do household, com a hierarquia,
  pra montar o enum do parâmetro da tool.
- `shopping` — leitura dos itens pendentes da lista, pro modelo casar "comprei o
  arroz" com o item "Arroz".

As duas são **só leitura**, e nenhuma estava desenhada no `sdd-visao-geral.md`:
a de `finance` entrou na Etapa 1 e a de `shopping` na Etapa 2a, sempre pelo
mesmo motivo — a ADR-0004 manda dar ao modelo "as categorias e listas reais do
household como contexto", e isso obriga `nlu` a ler onde esses dados moram. A
regra de dependência do `sdd-visao-geral.md` foi corrigida junto, e o teste
ArchUnit que a trava também.

## Estrutura interna proposta

```
nlu/
  NluService                 -- interpret(...) -> Intent; interpretCategoryCorrection(...)
  ContextBuilder             -- categorias (finance) e itens pendentes (shopping)
  Intent                     -- interface selada: uma variante por acao possivel
  tools/
    RegisterExpenseTool          -- categoria (enum) | categoria_sugerida, valor_cents,
                                    descricao, conta, confianca
    AddListItemTool              -- itens[] (nome, quantidade, unidade), confianca
    MarkItemPurchasedTool        -- item, confianca
    QueryListTool                -- confianca
    InviteMemberTool             -- nome, telefone, confianca (ADR-0020)
    ConfirmSuggestedCategoryTool -- nome, categoria_pai, confianca (ADR-0026)
  spi/
    MessageInterpreter         -- fronteira com o provedor de LLM
    InterpretationRequest      -- texto + contexto real do household + proposito
    ToolCall                   -- a chamada escolhida, crua (nome + argumentos)
  MistralMessageInterpreter  -- implementação LangChain4j (ADR-0009)
```

Duas decisões de 2026-09-05, ao implementar a Etapa 1:

**Existe uma interface entre `nlu` e o LangChain4j** (`ExpenseExtractor`), e
nenhum tipo da biblioteca a atravessa. Dois motivos: a [ADR-0009](../01-adr/0009-mistral-ai-como-provedor-de-llm-na-validacao.md) exige que trocar
de provedor na Etapa 5 seja configuração e não reescrita; e a
`estrategia-de-testes.md` manda stubbar o LLM em todo teste de aceitação — sem
essa fronteira, o stub teria que imitar a API do LangChain4j em vez de imitar o
resultado.

**O nome da tool e dos parâmetros continua em português** (`registrarDespesa`,
`categoria`, `valor_cents`) enquanto os identificadores Java viraram inglês
(`RegisterExpenseTool`, `interpret`). Não é inconsistência: o nome da tool é
dado enviado ao modelo, exatamente como a [ADR-0004](../01-adr/0004-interpretacao-por-function-calling-com-politica-de-confianca.md) o escreveu, e o modelo
interpreta mensagem em português.

Cinco de 2026-09-07, ao implementar a Etapa 2a.

**`ExpenseExtractor` virou `MessageInterpreter`, e o que atravessa a fronteira é
a chamada de função crua.** A interface antiga só sabia falar de despesa. Com
mercado e convite no mesmo pipeline há mais de uma tool possível para a mesma
mensagem, e quem escolhe entre elas é o modelo — então o que cruza a fronteira
passou a ser "a chamada que ele escolheu" (`ToolCall`: nome + mapa de
argumentos), e não "a despesa que ele extraiu". Continua sem nenhum tipo do
LangChain4j atravessando, que é o que a [ADR-0009](../01-adr/0009-mistral-ai-como-provedor-de-llm-na-validacao.md)
exige. Um mapa, e não um record por tool, de propósito: se a fronteira soubesse
quais tools existem, cada tool nova exigiria mexer no adaptador do provedor.

**A confiança vem do modelo, num parâmetro `confianca` declarado em toda tool.**
É o desenho que a própria ADR-0004 pressupõe ao registrar como fraqueza central
que "`confidence` reportado por LLM é mal calibrado por natureza" e que o limiar
terá de ser calibrado empiricamente — não haveria o que calibrar se o número
fosse calculado em código. `nlu` só valida a faixa 0-1 e trata ausência como
zero; quem compara com limiar é `conversation`.

**As alternativas da pergunta de ambiguidade são montadas aqui, por semelhança
de nome.** Quando a confiança é média, a pergunta precisa de opções numeradas, e
o modelo devolve uma categoria só. `nlu` monta a lista com a escolhida na frente
e as categorias que compartilham a primeira palavra com ela ("Mercado" ↔
"Mercado livre"). É heurística sobre os **nomes das categorias**, nunca sobre o
texto da pessoa — quem julgou a mensagem ambígua foi o modelo, ao devolver
confiança média; aqui só se monta o cardápio. Se essa heurística se mostrar
pobre na Etapa 5, o conserto é declarar as candidatas na própria tool, que é a
alternativa registrada como não escolhida agora.

**`valor_cents` saiu de `required`.** "paguei o mercado" precisa ser expressável
como despesa sem valor. Com o parâmetro obrigatório, o modelo não chamaria tool
nenhuma, e o bot perderia a categoria que já tinha reconhecido — sem ela não há
como fazer a "pergunta curta pedindo o valor" que o cenário exige. O único
parâmetro obrigatório de `registrarDespesa` hoje é `confianca`.

**Se o modelo preencher `categoria` e `categoria_sugerida` juntos, a categoria
existente vence.** A ADR-0024 deixa essa regra defensiva explicitamente para a
implementação. O critério: criar categoria é o caminho caro e irreversível dos
dois, então na dúvida não se cria. Chamada sem nenhum dos dois vira confiança
baixa — não dá para registrar sem categoria, nem para oferecer criar o que não
tem nome.

## Fluxo

1. `conversation` chama `NluService.interpret(householdId, texto)`.
2. `ContextBuilder` busca as categorias de despesa (em `finance`) e os itens
   pendentes da lista (em `shopping`). RLS já ativa pelo contexto de tenant
   resolvido por `identity`.
3. Monta as tools. O enum de `categoria` recebe as categorias reais; **quando o
   household não tem nenhuma, a propriedade some do schema** em vez de virar um
   enum vazio, que não é schema válido ([ADR-0024](../01-adr/0024-categoria-sugerida-por-texto-livre.md)).
   Sem ela, o único caminho que resta ao modelo é `categoria_sugerida` — é assim
   que a primeira mensagem de um household novo dispara o fluxo de criação que a
   [ADR-0013](../01-adr/0013-household-novo-comeca-sem-categorias.md) prometeu.
4. Chama o LLM (Mistral, function calling). Itens de lista vão no prompt de
   sistema e **não** viram enum: comprar algo que ninguém pediu é caso legítimo,
   com cenário próprio. Categoria vira enum porque o modelo precisa ser impedido
   de inventar uma (ADR-0004) — a assimetria é deliberada.
5. O adaptador devolve `ToolCall` cru; `NluService` traduz em `Intent` tipada,
   resolvendo nome de categoria para id.
6. Uma tool só por mensagem, mesmo que o modelo devolva várias: uma mensagem é
   uma ação e um recibo. "acabou arroz, leite e café" já é um único
   `adicionarItemLista` com três itens.

### O rótulo do enum de categoria

É o nome simples quando ele é único no household. Quando duas categorias têm o
mesmo nome em ramos diferentes — "Presente" dentro de "Educação" e dentro de
"Lazer", exemplo da própria [ADR-0016](../01-adr/0016-subcategoria.md) —, ambas
passam a se apresentar como "Pai > Filho". Enum com valor repetido seria uma
escolha que o modelo faz e o código não consegue desfazer.

### A extração não inventa hierarquia

Na chamada original, `nlu` extrai só o nome literal mencionado
("restaurante"), nunca a categoria-pai — mesma disciplina de "só o resíduo" da
[ADR-0023](../01-adr/0023-descricao-de-lancamento-extraida-pelo-llm.md) e da
ADR-0024, e exigência explícita da
[ADR-0026](../01-adr/0026-hierarquia-na-criacao-de-categoria-por-chat.md). O pai
só aparece quando a pessoa o escreve, respondendo à pergunta.

## Erros

LLM indisponível ou timeout → mesma política da tabela de falhas
transversais do `sdd-visao-geral.md`: mensagem fica `RECEIVED`, retry com
backoff, aviso no chat após a segunda falha.

**Não implementado na Etapa 1**: o retry com backoff e o aviso após a segunda
falha. Hoje a falha vira recibo de erro no chat na primeira tentativa e a
mensagem fica `FAILED`. O usuário nunca fica sem resposta, que é a regra que não
podia ser quebrada, mas a mensagem também não é retentada. Ver a seção do que
ficou de fora em [`etapa-1-bot-telegram-e-despesa`](../05-entregas/etapa-1-bot-telegram-e-despesa.md).

~~Household sem nenhuma categoria ([ADR-0013](../01-adr/0013-household-novo-comeca-sem-categorias.md)) não gasta chamada de modelo: o
enum do parâmetro ficaria vazio, que não é schema válido. Devolve confiança
baixa direto.~~ — deixou de valer em 2026-09-07: a ADR-0024 resolveu isso
omitindo a propriedade `categoria` do schema em vez de recusar a chamada.
Household novo agora gasta chamada de modelo como qualquer outro, e é justamente
nele que o fluxo de criação de categoria precisa funcionar.

## Testes

- ArchUnit: `nlu` não importa `channel`, `conversation`, `identity` nem `tasks`.
  Importa `finance` e `shopping`, só leitura.
- Cenários `@etapa1` e `@etapa2` das três features.

## Gatilhos de revisão

- **Etapa 3**: entra `fecharCompra`. O contexto já leva os itens pendentes, então
  o `ContextBuilder` não muda — só a lista de tools.
- **Etapa 5**: seis tools disputando a escolha do modelo em toda mensagem é
  bastante contexto. Se a matriz de confusão mostrar tool errada escolhida com
  frequência, é aqui que se decide reduzir o cardápio por situação — e a
  `confirmarCategoriaSugerida`, que já é declarada só no momento em que serve, é
  o precedente de como fazer isso.
- Se a exclusividade mútua entre `categoria` e `categoria_sugerida` for violada
  com frequência relevante pelo modelo, a ADR-0024 já prevê reabrir a decisão —
  inclusive voltar à tool `criarCategoria` separada que ela descartou.
