# Roadmap

Cada etapa termina em algo que dá para ver funcionando. Nenhuma etapa entrega
só plano.

## Etapa 0 — Fundação documental (fechada em 2026-09-04)

**Entregável**: todas as ADRs aceitas no fechamento da etapa (escopo cresceu
muito além das 0001-0006 originalmente previstas aqui — quais e quantas em
`docs/adr.base`, filtro `status: aceita`; faixa fixa neste parágrafo já
desatualizou duas vezes), glossário fechado, features das Etapas 1-3
escritas. Decisões abertas 1, 6, 9, 10, 11 e o #17 original
(fechamento de fatura) já resolvidas por virarem ADR.

Critério de saída: nenhum item marcado `[a verificar]` nas ADRs aceitas —
cumprido. A feature de vínculo de identidade (onboarding) está escrita
(`03-specs/features/vinculo-de-identidade.feature`), com a ADR-0020
(convite de membro, Aceita em 2026-09-05) registrando o schema e o fluxo.
Nada documentado bloqueia mais abrir o editor pra Etapa 1.

## Etapa 1 — Bot Telegram + despesa (fechada em 2026-09-05)

Webhook, adaptador de canal, `identity`, idempotência, uma tool no LLM,
Flyway com schema mínimo já multi-tenant e RLS ativa.

Entregue. O relato completo — o que foi construído, o que ficou de fora, as
lacunas conhecidas dentro do que foi entregue, e as decisões tomadas ao
implementar — está em
[`docs/05-entregas/etapa-1-bot-telegram-e-despesa.md`](docs/05-entregas/etapa-1-bot-telegram-e-despesa.md).
Uma decisão estrutural virou ADR nova ([ADR-0022](docs/01-adr/0022-papel-de-banco-pre-tenant-para-identidade.md), papel de banco pré-tenant
para a resolução de identidade); as demais ficaram registradas nos SDDs de
módulo.

Escopo decidido em 2026-09-05, corrigindo uma imprecisão de escopo: esta
etapa toca `channel`, `identity`, `nlu`, `conversation` e `finance` — não só
os dois primeiros. Mas cobre só o esqueleto andante do
`financas-lancamento-por-chat.feature`: cenários `@etapa1` (categoria já
reconhecida, sem ambiguidade, reentrega, identidade não vinculada). Os
cenários `@etapa2` (ambiguidade, criar categoria, valor ausente, desfazer)
exigem `PendingAction` e a política de confiança média/baixa da ADR-0004
inteira — ficam pra Etapa 2, junto com mercado/tarefas.

**Entregável**: você manda `mercado 50` no Telegram e vê a linha no Postgres,
com recibo no chat. Teste de vazamento de tenant verde. — **Cumprido**, com uma
ressalva operacional: household novo nasce sem categoria ([ADR-0013](docs/01-adr/0013-household-novo-comeca-sem-categorias.md)) e criar
categoria por chat é Etapa 2, então as categorias da família são semeadas por
SQL na validação (passo documentado em [`server/README.md`](server/README.md)).

## Nota sobre a ordem das etapas 2 e 3

A Etapa 2 original — mercado, tarefas e consultas em um bloco de ~2 semanas —
foi dividida em 2026-09-05. Tarefas não está no caminho crítico do elo, que é o
diferencial do produto (`CLAUDE.md`) e o que a Etapa 3 demonstra. Manter as três
coisas juntas colocava a demonstração que vende o produto atrás de duas semanas
de trabalho que ela não usa.

A ordem de execução é **2a → 3 → 2b**, e é nessa ordem que as seções abaixo
estão. Os números não foram reatribuídos de propósito: as tags `@etapa1`,
`@etapa2` e `@etapa3` nos `.feature`, as referências a "Etapa 5" nas decisões
abertas e as citações a etapa em várias ADRs estão ancoradas neles.
Renumerar custaria uma varredura por toda a documentação em troca de nada.

O custo da divisão, declarado: os seis fluxos do glossário deixam de sair
juntos. Quem olhar o produto entre a 3 e a 2b vê finanças e mercado completos e
tarefas ausente.

## Etapa 2a — Mercado, ambiguidade e convite por chat (fechada em 2026-09-07)

Entregue. O relato completo — o que foi construído, o que ficou de fora, as
lacunas conhecidas dentro do que foi entregue, as decisões tomadas ao
implementar e as três contradições entre documentos aceitos que apareceram no
caminho — está em
[`docs/05-entregas/etapa-2a-mercado-ambiguidade-e-convite.md`](docs/05-entregas/etapa-2a-mercado-ambiguidade-e-convite.md).

Nenhuma ADR nova foi necessária: as 0024-0026, aceitas horas antes de a etapa
começar, cobriram o que precisava de decisão. As demais decisões ficaram
registradas nos SDDs de módulo, incluindo o [SDD de `shopping`](docs/02-arquitetura/sdd-modulo-shopping.md),
escrito nesta etapa porque o módulo não tinha nenhum.

**Três lacunas herdadas da Etapa 1 continuam abertas** — botão nativo de
compartilhar contato, retry com backoff na falha de LLM, e o comando de trocar o
household ativo ([ADR-0007](docs/01-adr/0007-pessoa-em-multiplos-households.md)).
Nenhuma delas tem cenário `@etapa2`, e nenhuma foi fechada aqui.

O plano abaixo é o que foi escrito antes de a etapa começar, mantido como
registro. O diagnóstico central dele se confirmou na prática: a espinha era
`PendingAction`, e depois que ela existiu mercado inteiro saiu quase de graça.

Começou por tirar o `@Disabled` de `Etapa2AcceptanceTest`: os 22 cenários
`@etapa2` já estavam escritos e falhavam por passo indefinido, que é o estado
correto (recontados em 2026-09-07 ao fechar as ADRs 0024-0026, que acrescentaram
cinco cenários novos aos 17 que já existiam).

**A espinha é `PendingAction` e a política de confiança média da [ADR-0004](docs/01-adr/0004-interpretacao-por-function-calling-com-politica-de-confianca.md)**, não
mercado. Cinco cenários espalhados por três features são a mesma mecânica com
conteúdo diferente — uma pergunta com opções numeradas e uma resposta que
resolve: categoria inexistente, ambiguidade entre categorias parecidas, valor
ausente, item mencionado que não está na lista, e (já na Etapa 3) fechar compra
sem informar valor. Construir mercado antes do mecanismo significa construí-lo
duas vezes.

A primeira migration da etapa foi a tabela `pending_action` (`V2`), seguida de
`shopping_list` e `list_item` (`V3`). O TTL é a
[decisão aberta #8](docs/DECISOES-ABERTAS.md) (sugerido 10 minutos, sem base):
entrou como config global do app, provisória e explícita, nunca como constante
escondida no código — junto com os dois limiares de confiança
([decisão aberta #7](docs/DECISOES-ABERTAS.md)), pelo mesmo motivo.

Criação de categoria por chat ganhou desenho próprio depois da Etapa 1 (ADRs
[0024](docs/01-adr/0024-categoria-sugerida-por-texto-livre.md),
[0025](docs/01-adr/0025-desfazer-precedencia-e-escopo.md) e
[0026](docs/01-adr/0026-hierarquia-na-criacao-de-categoria-por-chat.md), todas
aceitas em 2026-09-07) — não é só "pergunta sim/não e cria". A tool
`registrarDespesa` ganha `categoria_sugerida` (texto livre, mutuamente
exclusivo com o enum `categoria`; para household sem nenhuma categoria a
propriedade `categoria` some do schema, forçando esse caminho desde a
primeira mensagem). A `PendingAction` de confirmação ganha uma terceira via
além de sim/não: correção livre ("restaurante dentro de alimentação"), que
processa por uma segunda chamada ao modelo — exceção explícita à regra 6
("confirmações não gastam LLM"), só para esse tipo de pendência — e pode
criar categoria-pai e subcategoria juntas, respeitando o limite de um nível
da [ADR-0016](docs/01-adr/0016-subcategoria.md). E `desfazer` ganha
precedência definida: pendência aberta sempre vence sobre estorno; sem
pendência, estorna a transação não estornada mais recente do household
inteiro (qualquer membro, [ADR-0012](docs/01-adr/0012-edicao-de-lancamento-entre-membros.md)), sem janela de tempo inventada.

Herda três lacunas conhecidas da Etapa 1, listadas na entrega dela: botão
nativo de compartilhar contato, retry com backoff na falha de LLM, e o comando
de trocar o household ativo ([ADR-0007](docs/01-adr/0007-pessoa-em-multiplos-households.md)).
— Nenhuma das três foi fechada; seguem abertas, como dito no topo desta seção.

Entra também a descrição do lançamento ([ADR-0023](docs/01-adr/0023-descricao-de-lancamento-extraida-pelo-llm.md), aceita em 2026-09-05,
a partir do primeiro uso real): a tool `registrarDespesa` ganha o parâmetro
opcional `descricao`, extraído pelo LLM e mantido fora da política de
confiança — nunca vira pergunta. Três cenários `@etapa2` já estão escritos
no `financas-lancamento-por-chat.feature`. Sem migration: a coluna já existe.

Existem `ExpenseByChatSteps` e `IdentityLinkSteps`. O glue de mercado é arquivo
novo, não adaptação. O glue dos cinco cenários novos de categoria/hierarquia/
desfazer (ADRs 0024-0026) também é novo — nenhum reaproveita passo existente
de `ExpenseByChatSteps` sem revisão, porque nenhum desses fluxos existia
quando esses steps foram escritos. — Cumprido: `ShoppingListSteps` é arquivo
próprio, e os passos de finanças que foram revisados mudaram de fato
(`uma despesa é registrada` passou a ignorar lançamento estornado, que antes não
existia).

**Entregável**: `acabou o arroz` entra na lista, `o que está faltando?`
responde, `pet shop 80` oferece criar a categoria e grava depois do `sim`,
`restaurante eu e esposa 90` corrigido para "dentro de alimentação" cria a
hierarquia certa mesmo quando nada existe ainda, e `desfazer` com pergunta
pendente cancela a pergunta em vez de estornar. Os 22 cenários `@etapa2`
verdes. — **Cumprido**, sem ressalva de escopo. Semear categoria e inserir
convite por SQL, os dois passos manuais que a Etapa 1 documentava em
[`server/README.md`](server/README.md), deixaram de existir: são exatamente os
dois fluxos que esta etapa entregou.

## Etapa 3 — O elo (~1 semana)

`fecharCompra` atômico, `list_checkout`, `desfazer` reversível dos dois lados.

Os nove cenários estão escritos e marcados `@etapa3` na linha `Funcionalidade:`
do [`elo-fechamento-de-compra.feature`](docs/03-specs/features/elo-fechamento-de-compra.feature). Falta o `Etapa3AcceptanceTest` —
a tag torna os cenários selecionáveis, não cobertos. Nasce desabilitado, pelo
mesmo motivo que o da Etapa 2 nasceu: escopo que falta deve ser visível na
própria suíte.

Nenhum cenário do elo é destacável para a 2a. Todos passam por `fecharCompra`,
e o cenário de falha ("nenhum item muda de status, nenhuma despesa é
registrada") só significa algo se a atomicidade existir.

**Entregável**: `comprei tudo, 180` fecha a lista e lança a despesa. É a
demonstração que vende o produto.

## Etapa 2b — Tarefas e agenda (~0,5 semana)

Executada depois da Etapa 3. Começa **escrevendo a `.feature` de tarefas**, que
não existe — é a única feature dos três domínios ainda não escrita, e sem ela
não há código a fazer (`CLAUDE.md`: comportamento vira `.feature` antes de
virar código).

Ao escrevê-la, decidir ou excluir explicitamente a [decisão aberta #12](docs/DECISOES-ABERTAS.md)
(recorrência de tarefas — modelo de dados). Tarefa que repete toda semana é
outro problema de modelagem, não um campo a mais.

Os cenários dela **não** levam `@etapa2`: essa tag é o portão da 2a e ficaria
vermelha por escopo que ainda não começou. Tag própria, decidida ao escrever.

**Entregável**: `lembrar de pagar o IPTU sexta` vira tarefa e a consulta de
pendências responde por chat. Com isso os seis fluxos do glossário estão
completos.

## Etapa 4 — PWA Vue (~2-3 semanas)

Foco na tela de **correção** de lançamento, não na de criação. É onde o usuário
conserta o erro da IA, e é o que decide se ele confia no sistema. A tela de
criação manual é secundária — quem quer criar manualmente já tem planilha.

Dois itens novos, registrados em 2026-09-04 ao validar a visão do produto com
o autor — fazem parte do escopo desta etapa, ainda sem desenho de tela:

- **Dashboard** ao abrir o app: últimas transações, entradas/saídas por
  período (diário, semanal, mensal, anual), e outras informações relevantes
  à primeira vista. Sem ADR própria ainda — layout e métricas exatas ficam
  para quando esta etapa começar de fato.
- **Central de pendências**: lista toda `PendingAction` ainda sem resolução
  — inclusive as que expiraram no chat sem resposta — com notificação,
  resolvível ali dentro do app. Mecanismo já decidido em [ADR-0018](docs/01-adr/0018-central-de-pendencias.md); a tela em si
  é trabalho desta etapa.

**Entregável**: instalável no celular, mostra o que veio do chat, permite
corrigir e recategorizar, com dashboard inicial e central de pendências.

## Etapa 5 — Uso real na família (4 semanas)

Sem feature nova. Só uso e medição.

**Entregável**: taxa de acerto por tool, matriz de confusão, lista dos erros
reais, limiar de confiança calibrado. Decisões abertas 3, 7, 8 resolvidas.

Critério de continuidade: se a taxa de acerto de despesa ficar abaixo de 90%,
a Etapa 6 não começa. Precisão do interpretador é o produto.

## Etapa 6 — Verticalização comercial

Endurecer signup e verificação de telefone pra uso fora da família
(o mecanismo de convite em si já existe desde a Etapa 1, ADR-0020),
billing por household, LGPD (política, base legal, exclusão real).

**Entregável**: alguém de fora da sua família consegue criar conta e usar
sozinho, sem você intervir.

## Etapa 7 — WhatsApp

Meta Business verificado, número dedicado, templates aprovados, adaptador novo
em `channel`.

**Entregável**: o mesmo produto, no canal onde as famílias já estão.

## Etapa 8 — Proatividade

Lembretes, resumo semanal, alerta de lista. Preferindo Telegram por custo
(ADR-0006). Modelar custo por household antes de ligar.
