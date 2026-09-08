# Glossário (linguagem ubíqua)

Termo que não está aqui não pode aparecer em código, Gherkin ou SDD.
Adicione primeiro, use depois. Um conceito = um nome, sempre.

## Núcleo

| Termo (pt) | Código (en) | Definição |
|---|---|---|
| Household | `Household` | A família. Unidade de isolamento de dados e de cobrança. Todo dado de domínio pertence a exatamente um household. |
| Membro | `Member` | Pessoa. Independente de household — pode pertencer a nenhum, um ou vários ([ADR-0007](../01-adr/0007-pessoa-em-multiplos-households.md)), um Vínculo com o household por família a que pertence. |
| Vínculo com o household | `HouseholdMembership` | Relação entre um membro e um household, com papel `OWNER` ou `MEMBER`. O papel é por vínculo, não da pessoa em geral — o mesmo membro pode ser `OWNER` numa família e `MEMBER` noutra. |
| Identidade de canal | `ChannelIdentity` | Vínculo entre um identificador externo (telefone, telegram user id) e um membro. É por aqui que uma mensagem vira "de quem". |
| Household ativo | `activeHouseholdId` | Household para o qual as mensagens de uma identidade de canal são resolvidas. Só relevante para quem tem mais de um Vínculo com o household; trocado por comando explícito no chat, nunca perguntado a cada mensagem ([ADR-0007](../01-adr/0007-pessoa-em-multiplos-households.md)). |
| Canal | `Channel` | Meio de entrada da mensagem: `TELEGRAM`, `WHATSAPP`, `WEB`. |
| Idioma preferido | `Member.preferredLocale` | Idioma em que a pessoa recebe as respostas do bot, tag BCP 47 (`pt-BR`). É do membro e não do household: uma mesma família pode ter gente com preferência diferente. Só afeta a **saída** — o `nlu` interpreta mensagem em qualquer idioma sem trabalho extra. Não confundir com moeda, que continua em reais e fora do escopo da [ADR-0015](../01-adr/0015-internacionalizacao.md). |
| Mensagem recebida | `InboundMessage` | Mensagem já normalizada, sem traço do canal de origem. |
| Intenção | `Intent` | Resultado da interpretação: qual ação o usuário quis, com quais parâmetros e com que confiança. |
| Confiança | `confidence` | Score de 0 a 1 atribuído à interpretação. Define se executa direto ou se pergunta. |
| Ação pendente | `PendingAction` | Pergunta que o bot fez e ainda espera resposta, com TTL. Pertence ao fio de quem foi perguntado ([ADR-0008](../01-adr/0008-interacao-1-1-por-membro-nunca-em-grupo.md)), não ao household: `desfazer` de outro membro não cancela pergunta que ele nunca viu. Passado o TTL ela não morre — só deixa de aceitar atalho no chat e passa a se resolver pela Central de pendências ([ADR-0018](../01-adr/0018-central-de-pendencias.md)). |
| Curto-circuito | — (regra 6 do `CLAUDE.md`) | Resolver `sim`, `não`, `desfazer` ou um número sem chamar o modelo. Única exceção: a Correção livre, abaixo. |
| Correção livre | `ConfirmSuggestedCategoryTool` | Resposta a uma pergunta de criação de categoria que não é `sim`, `não` nem `desfazer` — "restaurante dentro de alimentação". Não é erro nem repetição da pergunta: é a terceira via, que corrige nome e hierarquia de uma vez e pode criar categoria-pai e subcategoria juntas. Único caminho de resolução de pendência que gasta uma segunda chamada ao modelo ([ADR-0026](../01-adr/0026-hierarquia-na-criacao-de-categoria-por-chat.md)). |
| Recibo | `Receipt` | Resposta curta no chat confirmando o que foi gravado, sempre com a saída de `desfazer`. |
| Central de pendências | — (tela, não entidade) | Tela do app (Etapa 4) que lista toda `PendingAction` com `resolution` nula — inclusive as que expiraram no chat sem resposta. Ver [ADR-0018](../01-adr/0018-central-de-pendencias.md). |
| Convite | `HouseholdInvite` | Convite de um `OWNER` para um telefone específico entrar num household existente. Token único, expira em 7 dias, só aceito pelo telefone-alvo. Não é entregue pelo sistema — o `OWNER` repassa o link por fora (limitação da Telegram Bot API). Ver [ADR-0020](../01-adr/0020-convite-de-membro.md). |

## Finanças

| Termo | Código | Definição |
|---|---|---|
| Lançamento | `Transaction` | Movimento financeiro. Tem tipo `EXPENSE` ou `INCOME`. Nunca use "transação" no sentido de banco de dados junto deste termo. |
| Estorno | `Transaction.reversedAt` | Desfazer um lançamento sem apagá-lo: a linha continua no histórico, marcada, com quem estornou. Nunca `DELETE` — em finanças compartilhadas, "sumiu" é pior que "foi estornado por fulano". Qualquer membro estorna lançamento de qualquer outro do mesmo household ([ADR-0012](../01-adr/0012-edicao-de-lancamento-entre-membros.md)); pelo chat, `desfazer` alcança o lançamento não estornado mais recente da família, sem janela de tempo — mas só quando não há pergunta pendente, que tem precedência ([ADR-0025](../01-adr/0025-desfazer-precedencia-e-escopo.md)). |
| Categoria | `Category` | Classificação de lançamento, criada pelo household. Tem tipo (despesa ou receita). |
| Conta | `Account` | Origem ou destino do dinheiro (carteira, banco, cartão). |
| Fatura | `Invoice` | Ciclo mensal de gastos de uma conta do tipo cartão, com `status` `OPEN`/`CLOSED`/`PAID`. Todo lançamento em cartão pertence a uma fatura, resolvida automaticamente pelo dia de fechamento da conta — o household nunca precisa dizer a qual fatura um gasto pertence. Pagar a fatura não é ação própria: gera dois lançamentos espelhados (despesa na conta pagadora, receita na conta do cartão). Ver [ADR-0011](../01-adr/0011-cartao-de-credito-e-fatura.md). |
| Subcategoria | `Category.parentCategoryId` | Categoria com pai, só um nível. Herda `kind` do pai. Não é entidade própria — é `Category` apontando pra outra `Category`. Ver [ADR-0016](../01-adr/0016-subcategoria.md). |
| Categoria sugerida | `RegisterExpenseTool`, parâmetro `categoria_sugerida` | Nome de categoria que o LLM extrai da mensagem quando nenhuma categoria existente bate -- nunca junto com o enum `categoria`, sempre um ou outro. Dispara uma `PendingAction` de confirmação; a resposta pode ser `sim`/`não` ou uma correção por texto livre indicando hierarquia ("restaurante dentro de alimentação"), que pode criar categoria-pai e subcategoria juntas. Ver [ADR-0024](../01-adr/0024-categoria-sugerida-por-texto-livre.md) e [ADR-0026](../01-adr/0026-hierarquia-na-criacao-de-categoria-por-chat.md). |
| Meta financeira | `FinancialGoal` | Valor-alvo com prazo, do household. Progresso nunca é armazenado — é somado dos lançamentos vinculados na hora de mostrar. Ver [ADR-0017](../01-adr/0017-meta-financeira.md). |
| Vínculo de meta | `GoalTransactionLink` | Liga um `Transaction` a uma `FinancialGoal`. N:N — um lançamento pode contar para mais de uma meta. Ver [ADR-0017](../01-adr/0017-meta-financeira.md). |
| Descrição | `Transaction.description` | Texto curto que sobra da mensagem depois de categoria, valor, conta e data — o "o quê" que dá sentido ao lançamento meses depois ("remédio do Joaquim"). Nula quando não sobra nada; nunca repete a categoria. Extraída pelo LLM na interpretação ([ADR-0023](../01-adr/0023-descricao-de-lancamento-extraida-pelo-llm.md)) e corrigível na web ([ADR-0012](../01-adr/0012-edicao-de-lancamento-entre-membros.md)). Não confundir com `inbound_message.raw_text`, que guarda a mensagem original inteira: descrição é significado, `raw_text` é prova forense. |
| Histórico de edição | `TransactionEdit` | Uma linha por correção feita em um lançamento já existente (valor, categoria, descrição), com quem editou e quando. Não cobre criação (isso já é `created_by_member_id`) nem estorno (isso já é `reversed_by_member_id`) — só correção de campo. Ver [ADR-0012](../01-adr/0012-edicao-de-lancamento-entre-membros.md). |

## Mercado

| Termo | Código | Definição |
|---|---|---|
| Lista de compras | `ShoppingList` | Lista ativa do household. Um household tem no máximo uma lista ativa por vez — garantido por índice, não por disciplina de serviço. Nasce sob demanda, no primeiro item, e não no onboarding (`sdd-modulo-shopping.md`). |
| Item da lista | `ListItem` | Item com status `PENDING` ou `PURCHASED`, com quem pediu e quem comprou. Quantidade e unidade são opcionais e nunca viram pergunta. Item que já está faltando não duplica: é reconhecido, com quem o pediu antes. |
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
| "grupo" para se referir à família | Household. Note que "grupo" também aparece como recurso de canal (grupo do Telegram/WhatsApp) — esse sentido é válido para descrever o recurso, mas o produto nunca o usa: interação é sempre 1:1, ver [ADR-0008](../01-adr/0008-interacao-1-1-por-membro-nunca-em-grupo.md) |
