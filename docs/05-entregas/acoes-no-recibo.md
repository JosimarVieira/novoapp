---
tipo: entrega
etapa: acoes-no-recibo
status: entregue
data: 2026-11-03
modulos:
  - channel
  - conversation
  - finance
  - shopping
  - banco
adrs:
  - ADR-0041
---

# Desfazer e trocar categoria pelo recibo

**Critério de pronto do [ROADMAP](../../ROADMAP.md)**: a troca aparece no recibo,
fica no histórico com quem trocou, e o próximo fechamento pelo carrinho já
pergunta o valor na categoria corrigida. **Atingido nos testes.** Os 18 cenários
de [`financas-acoes-no-recibo.feature`](../03-specs/features/financas-acoes-no-recibo.feature)
verdes, e a suíte inteira: **249 testes, 102 cenários de aceitação, zero
falhas.** A tag `em-construcao` saiu de `AcoesNoReciboAcceptanceTest`, que passou
a integrar o portão do CI.

**Não verificado no Telegram de verdade.** Quem implementou não tinha acesso ao
bot nem a um cliente do Telegram — a mesma situação da entrega dos botões na
lista, cujo primeiro toque em produção falhou por configuração, e não por código.
O adaptador de canal é exatamente a parte que o stub de aceitação substitui. O
roteiro está em [Verificação no Telegram](#verificação-no-telegram).

Nenhuma ADR nova. `staticFingerprint()` de `MistralMessageInterpreter` deu
`5eb5b298b145` antes e depois: nada de botão vazou para o prompt, e
`prompt_version` não mudou.

## O que foi construído

Um commit por passo do ROADMAP, com o placar subindo como o plano dizia: 0 de 18
com o runner ligado, 7 com o desfazer, 18 com a troca. Entre o passo 1 e o 2, um
commit de correção com os achados da revisão do desenho (abaixo).

| Onde | O quê |
|---|---|
| `common/message/ChatAction` | o id de item virou uma referência tipada (item ou lançamento) e, na troca, a categoria de destino; cinco tipos novos: `desfazer`, `estornar`, `trocar`, `voltar`, `categoria` |
| `channel/inbound/TelegramActionCodec` | `dz`, `es`, `tr`, `vt`, `ct`; referência de lançamento e de categoria em base64url (22 caracteres, só a escrita canônica); o maior botão, a troca, em 48 bytes. Os códigos da lista continuam textuais |
| `finance` | `reverse` (estorno determinado, com trava de escrita), `changeCategory` (desfecho selado `CategoryChange`), a leitura `ExpenseView`; o carimbo do estorno, comum com `reverseLatest`, virou `UPDATE` condicional; entidade e repositório de `TransactionEdit` |
| `shopping` | `CheckoutResult.Closed` com o `transactionId`; a leitura `itemsClosedBy`, para redesenhar o recibo de fechamento. Nenhuma linha para o estorno |
| `conversation` | `action/ReceiptView` (os estados da mensagem de recibo) e `action/ReceiptActionHandler` (os cinco toques); os dois recibos de lançamento do orquestrador saem com os botões |
| `V11__transaction_edit.sql` | `transaction_edit`, com `household_id`, RLS ativa e `FORCE`, só `SELECT` e `INSERT` para o papel de domínio |
| `messages_pt_BR.properties` | rótulos, o teclado, a confirmação, o estado estornado e os avisos curtos (ADR-0015) |
| `application.properties` | `novoapp.conversation.receipt.undo-window=PT24H` (decisão aberta #30) |
| `server/scripts/reset-household-*.sql` | apagam `transaction_edit` antes de `transaction` — sem isso, a FK nova abortava o reset de qualquer família que tivesse trocado uma categoria |

Testes novos fora da aceitação: `ReversalConcurrencyTest` (três pares de escritas
concorrentes no mesmo lançamento, com duas threads), cinco casos em
`TenantIsolationTest` (histórico isolado, lançamento e categoria de outro
household vindos do botão, `WITH CHECK` da tabela nova, append-only) e quatro em
`TelegramActionCodecTest` (botão de lista já enviado, forma compacta, maior
botão, referência malformada).

## Decisões tomadas ao implementar

Nenhuma contradiz ADR aceita; todas estão nos SDDs de
[`conversation`](../02-arquitetura/sdd-modulo-conversation.md),
[`finance`](../02-arquitetura/sdd-modulo-finance.md),
[`channel`](../02-arquitetura/sdd-modulo-channel.md) e
[`shopping`](../02-arquitetura/sdd-modulo-shopping.md).

- **Os toques do recibo têm tratador próprio**, `ReceiptActionHandler`, para o
  qual o `ChatActionHandler` despacha. Agem sobre um lançamento, e não sobre a
  lista, e nenhum mexe em pendência.
- **Todo toque relê o lançamento antes de agir.** É o que faz o botão de um
  lançamento estornado por qualquer caminho virar aviso e redesenho.
- **O redesenho do recibo de fechamento traz os itens**, lidos em `shopping`.
  Sem eles, "voltar redesenha o recibo como estava" (ADR-0041, item 3) não valeria
  para o fechamento.
- **O "Ainda falta" redesenhado é o de agora**, e não o do momento do
  fechamento. É o único ponto em que o redesenho não é o recibo "como estava";
  para a despesa, o texto volta idêntico, e o cenário de `voltar` confere a
  igualdade.
- **Lançamento não encontrado é o único toque que não redesenha**: só o aviso,
  porque não há o que desenhar no lugar. Para quem tem mais de uma família, o
  aviso nomeia onde o bot procurou (ADR-0007).
- **O registro do clique** para a Etapa 5 usa a chave `transaction_id`, a mesma
  do `desfazer` digitado, e a categoria nova na troca.

### Achados da revisão do desenho

O desenho passou por uma revisão independente antes do código da troca, em
quatro lentes; duas voltaram (integridade e conversa) e duas pararam no limite de
gasto da organização (fidelidade à ADR e cola de teste). O que mudou por causa
dela:

- **Uma corrida real entre o `desfazer` digitado e os botões.** O digitado lê o
  alvo sem trava, e o Hibernate regravava a linha inteira: concorrente com a
  troca de categoria, o lançamento saía de cena na categoria **antiga**; com o
  desfazer pelo botão, era carimbado e anunciado duas vezes. O carimbo virou
  `UPDATE` condicional só nas colunas do estorno, e o evento só sai se uma linha
  mudou. `ReversalConcurrencyTest` confere que a segunda escrita de fato esperou
  — sem isso o teste passaria em série e não provaria nada — e, com o carimbo
  antigo, o par do digitado fica vermelho.
- **"Já estornado" ganhou texto próprio.** Com o "Estornei" do recibo de estorno,
  o estorno feito pelo outro telefone aparecia como se tivesse sido este toque, e
  o fio de quem digitou `desfazer` terminava com duas mensagens iguais.
- **Categoria que saiu do teclado vira aviso e o teclado de agora**, e não recibo
  de erro; e `changeCategory` olha o lançamento antes da categoria.
- **A confirmação ecoa a descrição e, num fechamento, os itens que voltam para a
  lista.**
- **Os scripts de reset** e os casos de isolamento para ids forjados, que o
  desenho não previa.

## O que ficou de fora, e por quê

- **Trocar a conta pelo recibo** — decisão aberta #31: hoje toda família tem uma
  conta só.
- **A pergunta do primeiro fechamento em botões** — gatilho da ADR-0041, e mudaria
  o item 4 da ADR-0040.
- **Refazer estorno, prazo próprio para o `Estornar`, paginação do teclado e o
  adaptador do WhatsApp** — fora do escopo, como o prompt da implementação manda.
- **A inversão da política de confiança** — é o próximo item do ROADMAP.

## Lacunas

- **Verificação no Telegram, não feita** (roteiro abaixo). Em particular, a
  premissa de que `editMessageText` **sem** `reply_markup` tira os botões — é
  como o estado estornado aparece — não tem como ser conferida pelo stub.
- **O cenário do toque duplo não pega sozinho a regressão para `reverseLatest`.**
  Conferido trocando, no tratador, o estorno determinado por `reverseLatest`: só
  "Desfazer pelo botão estorna o lançamento do próprio recibo" fica vermelho. No
  toque duplo, o segundo toque para na releitura (o lançamento já está estornado)
  antes de chegar à escrita. A regressão continua barrada pela suíte, mas pelo
  outro cenário — vale saber disso antes de mexer em qualquer um dos dois.
- **A concorrência não está nos cenários.** A aceitação roda síncrona
  (`novoapp.channel.async=false`); quem cobre as corridas é
  `ReversalConcurrencyTest`, na camada de `finance`.
- **A frase "`shopping` não ganha nenhuma linha"** (ADR-0041, Consequências)
  vale para o caminho do estorno, que é o que ela descreve. `shopping` ganhou duas
  linhas de leitura — o `transactionId` em `Closed`, que o próprio prompt da
  implementação pedia, e `itemsClosedBy`. Se e como ajustar o texto da ADR fica
  com o autor.
- **Uma confirmação deixada aberta no chat continua sendo um estorno a um
  toque**, como a ADR declara. Nada mudou aqui.
- **Teclado longo** sem paginação, como a ADR deixou: dezenas de categorias com
  subcategoria devem bater no limite do teclado inline `[a verificar contra a Bot
  API]`.

## Verificação no Telegram

No bot de desenvolvimento, com a janela curta para ver a confirmação sem esperar
um dia:

```bash
export NOVOAPP_CONVERSATION_RECEIPT_UNDO_WINDOW=PT2M
mvn quarkus:dev
```

Confira antes que o webhook está registrado com
`allowed_updates=["message","callback_query"]` (`server/README.md`) — sem o
`callback_query`, o botão gira e nada acontece, sem erro em log.

1. **Recibo com os dois botões.** Mande `farmácia 30`. O recibo chega com
   `↩ Desfazer` e `Trocar categoria`, e a dica "Se eu errei, responda: desfazer"
   continua no texto.
2. **O alvo é o do recibo.** Do outro telefone, mande `mercado 50`. No primeiro
   telefone, toque `↩ Desfazer` no recibo de `farmácia 30`: ele vira "Estornei:
   Farmácia R$ 30,00…" **sem botões**, e o `mercado 50` continua de pé.
3. **Toque duplo.** Num recibo novo, toque duas vezes rápido em `↩ Desfazer`: um
   estorno só, nenhuma mensagem nova empilhada.
4. **Já estornado.** Mande `farmácia 20`, depois `desfazer` digitado, e então
   toque `↩ Desfazer` no recibo de `farmácia 20`: aviso curto "Esse lançamento já
   foi estornado" e o recibo vira "Estornado em …".
5. **Fora da janela.** Mande `farmácia 15`, espere mais de 2 minutos e toque
   `↩ Desfazer`: a mensagem vira "Quer mesmo estornar este lançamento de …?" com
   `Estornar` e `Voltar`. `Voltar` devolve o recibo como estava; `↩ Desfazer` de
   novo e `Estornar` estornam.
6. **Pergunta aberta.** Mande `mercado` (pergunta o valor), toque `↩ Desfazer`
   num recibo anterior, e responda `50`: a despesa de R$ 50,00 em Mercado é
   registrada — a pergunta sobreviveu ao toque.
7. **Trocar categoria.** Num recibo de despesa, `Trocar categoria`: o teclado
   mostra as categorias de despesa, duas por linha, a atual com `✅`, e `Voltar`.
   Escolha outra: o recibo volta mostrando a nova, com os dois botões.
8. **O elo.** Com itens na lista, feche pelo carrinho, troque a categoria do
   recibo do fechamento para outra, e feche de novo pelo carrinho: a pergunta do
   valor já cita a categoria corrigida. Toque `↩ Desfazer` no recibo do primeiro
   fechamento: os itens dele voltam para a lista.
9. **Banco.** `SELECT * FROM transaction_edit` mostra uma linha por troca, com
   quem trocou e `{"category_id": [de, para]}`; escolher a categoria atual não
   grava linha.

## Alterações em documento já existente

- SDDs de [`conversation`](../02-arquitetura/sdd-modulo-conversation.md),
  [`finance`](../02-arquitetura/sdd-modulo-finance.md) e
  [`channel`](../02-arquitetura/sdd-modulo-channel.md): de "ainda não
  implementado" para implementado, com as decisões acima. O de
  [`shopping`](../02-arquitetura/sdd-modulo-shopping.md) ganhou a ADR-0041 no
  frontmatter e a seção das duas leituras.
- [Modelo de dados](../02-arquitetura/modelo-de-dados.md): `V11` em "O que existe
  no banco hoje", e `transaction_edit` saiu da lista do que só está modelado.
- [ROADMAP](../../ROADMAP.md): item 4 da ordem feito, com a verificação no
  Telegram pendente declarada; a inversão da política de confiança passa a ser o
  próximo.
- **Passos de aceitação existentes**: `que os itens ... estão pendentes, pedidos
  por` ganhou a forma de dois itens, e `que o household ... tem as categorias de
  despesa` a de três. O stub de canal ganhou a leitura de uma mensagem pelo id,
  para tocar num recibo que já não é a mensagem mais recente do fio.
