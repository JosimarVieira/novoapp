# Glossário (linguagem ubíqua)

Termo que não está aqui não pode aparecer em código, Gherkin ou SDD.
Adicione primeiro, use depois. Um conceito = um nome, sempre.

## Núcleo

| Termo (pt) | Código (en) | Definição |
|---|---|---|
| Household | `Household` | A família. Unidade de isolamento de dados e de cobrança. Todo dado pertence a exatamente um household. |
| Membro | `Member` | Pessoa dentro de um household. Pode ter papel `OWNER` ou `MEMBER`. |
| Identidade de canal | `ChannelIdentity` | Vínculo entre um identificador externo (telefone, telegram user id) e um membro. É por aqui que uma mensagem vira "de quem". |
| Canal | `Channel` | Meio de entrada da mensagem: `TELEGRAM`, `WHATSAPP`, `WEB`. |
| Mensagem recebida | `InboundMessage` | Mensagem já normalizada, sem traço do canal de origem. |
| Intenção | `Intent` | Resultado da interpretação: qual ação o usuário quis, com quais parâmetros e com que confiança. |
| Confiança | `confidence` | Score de 0 a 1 atribuído à interpretação. Define se executa direto ou se pergunta. |
| Ação pendente | `PendingAction` | Intenção aguardando confirmação do usuário, com TTL. |
| Recibo | `Receipt` | Resposta curta no chat confirmando o que foi gravado, sempre com a saída de `desfazer`. |

## Finanças

| Termo | Código | Definição |
|---|---|---|
| Lançamento | `Transaction` | Movimento financeiro. Tem tipo `EXPENSE` ou `INCOME`. Nunca use "transação" no sentido de banco de dados junto deste termo. |
| Categoria | `Category` | Classificação de lançamento, criada pelo household. Tem tipo (despesa ou receita). |
| Conta | `Account` | Origem ou destino do dinheiro (carteira, banco, cartão). |

## Mercado

| Termo | Código | Definição |
|---|---|---|
| Lista de compras | `ShoppingList` | Lista ativa do household. Um household tem no máximo uma lista ativa por vez. |
| Item da lista | `ListItem` | Item com status `PENDING` ou `PURCHASED`, com quem pediu e quem comprou. |
| Fechamento de compra | `ListCheckout` | Ato de marcar itens como comprados e gerar o lançamento correspondente. É o **elo** entre mercado e finanças. |

## Tarefas

| Termo | Código | Definição |
|---|---|---|
| Tarefa | `Task` | Item com responsável opcional, prazo opcional e recorrência opcional. |
| Responsável | `assignee` | Membro encarregado. Ausência de responsável significa "qualquer um do household". |

## Termos proibidos

| Não use | Use |
|---|---|
| "usuário" para quem paga | Household (a assinatura é do household, não da pessoa) |
| "transação" para movimento financeiro em texto de domínio | Lançamento |
| "compra" isolado | Diga se é `ListItem` (o que falta) ou `Transaction` (o dinheiro que saiu) |
| "grupo" | Household |
