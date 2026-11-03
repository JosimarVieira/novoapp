# Prompt para implementar desfazer e trocar categoria pelo recibo (ADR-0041)

Contexto: a [ADR-0041](docs/01-adr/0041-botoes-de-desfazer-e-trocar-categoria-no-recibo.md)
foi aceita em 2026-10-31, e a documentação inteira já está na `main` (PR #23):
ADR, 18 cenários Gherkin, SDDs de `channel`, `conversation` e `finance`,
glossário, decisões abertas #29 (encerrada), #30 e #31, ROADMAP e o runner
`AcoesNoReciboAcceptanceTest` com `@Disabled`. **Nenhuma linha de código de
produção existe ainda.** Este prompt é para uma sessão nova do Claude Code fazer
a implementação no mesmo repositório.

## Antes de qualquer código

1. Crie a branch `feat/acoes-no-recibo` a partir da `main` atualizada.
2. Confirme que o CI do PR #23 passou. Se não houver registro, rode
   `DocumentationCoherenceTest`: os documentos foram conferidos por um script que
   replica as regras, não pelo build. Se quebrar, o problema está nos
   documentos, não no teste.
3. Rode a suíte inteira e confirme que está verde. É a linha de base: daqui em
   diante, vermelho fora do runner novo é regressão. **Os 15 cenários de
   `@acoes-no-chat` estão no portão do CI** e esta mudança mexe no contrato que
   eles usam.

## Leitura obrigatória

1. `CLAUDE.md` inteiro, com atenção às regras 4, 5, 6 e 7.
2. A ADR-0041 inteira, inclusive as alternativas. Três desenhos parecem mais
   simples e **estão recusados**: troca de categoria por texto (A), `desfazer
   <número>` (B) e desfazer que reverte a troca (E).
3. `docs/03-specs/features/financas-acoes-no-recibo.feature`, comentário do topo
   incluído.
4. `ROADMAP.md`, seção **"Desfazer e trocar categoria pelo recibo (planejado em
   2026-10-31)"**: os dois passos e o entregável de cada um.
5. As seções marcadas "ADR-0041, aceita em 2026-10-31, ainda não implementado"
   em `docs/02-arquitetura/sdd-modulo-conversation.md`, `sdd-modulo-finance.md` e
   `sdd-modulo-channel.md`.
6. ADR-0012 (schema de `transaction_edit`) e ADR-0032 (o evento que reabre o
   fechamento).

**Não use lista curada de ADRs.** As que governam esta mudança estão no
`depende_de` da 0041 e nos links dos SDDs; siga de lá.

Antes do primeiro grep, rode `graphify query` (regra do `CLAUDE.md`) se o
`graphify` estiver instalado.

## Ordem de execução

### Passo 0 — ligar o runner

No primeiro commit de código, tire o `@Disabled` de `AcoesNoReciboAcceptanceTest`
e ponha `@Tag("em-construcao")`. O CI já trata essa tag: fica fora do portão e
roda num passo informativo. Os 18 cenários vão falhar por passo indefinido, e é
assim que o placar sobe. **Não recoloque o `@Disabled` nem afrouxe cenário para
ver verde.**

### Passo 1 — desfazer pelo botão (7 cenários)

Comece por **"Desfazer pelo botão estorna o lançamento do próprio recibo"**. Ele
força a cadeia inteira: recibo com botão → clique → codec → `ChatAction` com
referência de lançamento → `ChatActionHandler` → estorno determinado em
`finance` → redesenho como estornado.

Depois, nesta ordem: "Desfazer pelo botão no recibo do fechamento devolve os
itens", "Toque duplo no desfazer estorna uma vez só", "Reentrega do mesmo clique
de desfazer", "Desfazer pelo botão não cancela a pergunta aberta", "Botão de um
recibo cujo lançamento já foi estornado" e "Fora da janela, o desfazer pede
confirmação".

Neste passo o recibo ganha **só** o botão de desfazer. Os dois cenários "traz os
botões" e "Voltar da confirmação" conferem também o de trocar categoria e ficam
verdes no passo 2. Não ponha no recibo um botão sem tratador: se este passo for
para produção sozinho, a família tocaria num botão que não faz nada.

Sem migration neste passo.

**Entregável**: no Telegram de verdade, tocar no desfazer de um recibo mais
antigo estorna aquele lançamento, mesmo com outro lançado depois, e o recibo
vira estornado sem botões. Para ver a confirmação sem esperar 24 horas, suba o
bot de desenvolvimento com a janela curta
(`NOVOAPP_CONVERSATION_RECEIPT_UNDO_WINDOW=PT2M`).

### Passo 2 — trocar categoria (11 cenários)

Migration `V11`: `transaction_edit`, com o schema da ADR-0012, `household_id`,
RLS ativa e `FORCE`, no padrão das tabelas novas da `V7` e da `V10`. **Grants só
de `SELECT` e `INSERT`**: a tabela é append-only por decisão da ADR-0012, então
`UPDATE` e `DELETE` não são concedidos ao papel de runtime.

Comece por **"Trocar a categoria pelo recibo"**, depois "Trocar categoria
mostra as categorias da família", e só então o resto.

**Entregável**: a troca aparece no recibo, fica em `transaction_edit` com quem
trocou, e o próximo fechamento pelo carrinho pergunta o valor na categoria
corrigida. Os 18 cenários verdes.

## Armadilhas concretas

**O recibo do fechamento não sabe o id do lançamento.** `CheckoutResult.Closed`
carrega itens, valor e categoria, mas não `transactionId`, e o botão precisa
dele. `ShoppingService` já tem o id na mão quando grava o `list_checkout`
(`checkout.transactionId = expense.transactionId()`). Acrescente o campo ao
`Closed`. É o `[a verificar ao implementar]` do SDD de `conversation`.

**Só há dois pontos que montam recibo de lançamento**:
`receipts.expenseReceipt` e `receipts.checkoutReceipt` em
`ConversationOrchestrator`. O de fechamento cobre o texto e o carrinho, porque o
fechamento pelo carrinho termina em `executeDeferred`. Os dois passam a enviar
`ChatReply` com ações. Recibo de estorno, de item anotado e de pergunta não
mudam. A dica "Se eu errei, responda: desfazer" continua no texto.

**O estorno determinado é irmão de `reverseLatest`, não cópia.** Extraia o miolo
(carimbar `reversed_at` e `reversed_by_member_id`, disparar `ExpenseReversed`
dentro da transação) e use nos dois. O fechamento reabre pelo
`ExpenseReversedListener`, que já existe. **`shopping` não ganha nenhuma linha
para o desfazer.** Se você se pegar editando `shopping` por causa do estorno,
pare e releia a ADR-0032.

**O tratador do botão nunca chama `reverseLatest`.** É isso que torna o toque
duplo inofensivo: o segundo toque encontra o lançamento já estornado e
redesenha, em vez de estornar o próximo da fila. O cenário de toque duplo existe
para pegar exatamente essa regressão.

**O desfazer pelo botão não toca a pendência.** A ação já entra antes de
`PendingActionService.findOpen` (passo 0 do orquestrador). Diferente do
`CLOSE_CART`, que supera a pergunta aberta, `desfazer`, `estornar`, `trocar`,
`voltar` e `categoria` não chamam `supersedeOpenQuestion`.

**A janela é config, lida no toque.** `novoapp.conversation.receipt.undo-window`,
`Duration`, padrão `PT24H`, no mesmo molde de `novoapp.conversation.cart.ttl`
(`ChatCartService`). Compare com o `created_at` do lançamento usando o `Clock`
injetado. `conversation` não lê entidade de `finance`: exponha o que o recibo
precisa (id, valor, categoria, data, `created_at`, se está estornado) numa
leitura de `finance` que devolve um record, como `categoryOfStandingExpense` já
faz. `estornar` não consulta a janela.

**O passo de glue "fora da janela do desfazer" não pode assumir 24 horas.** Leia
a janela da config e recue o `created_at` além dela, no padrão de
`CheckoutSteps` (`UPDATE transaction SET created_at = now() - ...`). Hardcode de
24h no teste cristaliza o número que a decisão aberta #30 deixou provisório.

**O contrato de `ChatAction` muda, e os 15 cenários da lista têm de continuar
verdes.** `itemId` vira uma referência tipada (item ou lançamento) mais uma
categoria opcional. Os construtores e a validação do record mudam; `ChatActionHandler`,
o codec e os testes de unidade deles quebram em lugares conhecidos.

**Botão já enviado continua valendo.** Há listas com botões no Telegram da
família agora. Os códigos da lista (`c+:`, `c-:`, `rm:`, `rt:`, `fx`) continuam em
forma textual. Só os tipos novos usam UUID em base64url (22 caracteres), que é o
que faz código, lançamento e categoria caberem em 48 bytes. Escreva um teste de
unidade com o maior botão e confira o limite de 64 bytes, que o codec já guarda.

**`transaction_edit` só recebe linha quando algo muda.** Trocar para a categoria
atual não grava nada (há cenário). `changed_fields_json` no formato
`{"category_id": [de, para]}`. Trocar só para categoria de despesa do household,
e nunca em lançamento estornado.

**A herança da categoria não deve exigir código.** `lastCheckoutCategory` lê a
categoria atual do lançamento via `categoryOfStandingExpense`, então o cenário
"A troca no fechamento vale para o próximo fechamento pelo botão" deve passar só
com a troca. Se não passar, investigue. Não crie caso especial.

**O banco é a fonte de verdade, a mensagem nunca é.** Todo toque relê o
lançamento. Estornado por qualquer caminho, inclusive pelo `desfazer` digitado,
responde com aviso curto e redesenha como estornado. Todo clique é respondido
com `answerCallbackQuery`, e *message is not modified* é sucesso.

**O `prompt_version` não pode mudar.** Nada aqui toca tool nem prompt. Confira
`staticFingerprint()` de `MistralMessageInterpreter` antes e depois.

**Rótulos e textos novos são texto para o chat**: `↩ Desfazer`, `Trocar
categoria`, `Estornar`, `Voltar`, a confirmação com data, categoria e valor, e o
aviso de já estornado. Vão para `MessageKey` e `messages_pt_BR.properties`
(ADR-0015), nunca literal no código. `MessageBundleTest` cobra os dois lados.

**O teclado de categorias é `listExpenseCategories`**, a mesma lista da pergunta
do primeiro fechamento, com a atual marcada. Duas por linha. Sem paginação: é
gatilho da ADR, não escopo.

## Decisões já tomadas — não reabra sem ADR

- O botão estorna o lançamento do próprio recibo. O `desfazer` digitado continua
  como a ADR-0025 define.
- Fora da janela, confirmação com `Estornar` e `Voltar`, e não botão morto (D).
- Trocar categoria é teclado, sem modelo, sem pendência e sem prazo (A, F).
- Desfazer estorna o lançamento, nunca a troca (E).
- Sem `desfazer <número>` e sem id curto no recibo (B).
- Janela: `novoapp.conversation.receipt.undo-window`, 24h, config provisória
  (decisão aberta #30). Nunca constante no código.

## O que NÃO construir agora

- Trocar conta pelo recibo: decisão aberta #31. Hoje toda família tem uma conta
  só.
- A pergunta do primeiro fechamento em botões: é gatilho da ADR-0041, e mudaria
  o item 4 da ADR-0040.
- Refazer estorno, prazo próprio para o botão `Estornar`, paginação do teclado,
  adaptador do WhatsApp.
- A inversão da política de confiança: é o item seguinte do ROADMAP.

## Ao fechar

1. Os 18 cenários verdes e a suíte inteira verde.
2. Tire a `@Tag("em-construcao")` de `AcoesNoReciboAcceptanceTest`, para que ele
   entre no portão do CI.
3. Os SDDs deixam de dizer "ainda não implementado", e `modelo-de-dados.md`
   ganha a `V11` em "O que existe no banco hoje".
4. Escreva a entrega em `docs/05-entregas/` no formato das anteriores: o que foi
   construído, o que ficou de fora, as lacunas e as decisões tomadas ao
   implementar, com o roteiro de verificação no Telegram.
5. Marque o item 4 da ordem no ROADMAP como feito, com data.
6. Rode `graphify update .`.

## Regra de trabalho

A de sempre: veja o vermelho antes do verde, o Gherkin é a fonte de verdade, e
decisão estrutural vira ADR antes de virar código. Se a implementação
contradisser uma ADR aceita, escreva uma ADR nova que a supere. Não edite a
antiga.
