---
tipo: adr
numero: 28
status: aceita
data: 2026-09-08
modulos:
  - tasks
  - banco
  - glossario
depende_de:
  - ADR-0014
supera: []
superada_por:
corrigida_em:
---

# ADR-0028 — Recorrência de tarefa: regra separada, uma linha por ocorrência

- **Impacta**: `tasks`, banco (`task_recurrence`, `task`), glossário; resolve a
  [decisão aberta #12](../DECISOES-ABERTAS.md); Etapa 2b do roadmap

## Contexto

"Retirar o lixo" se repete todo sábado. O autor confirmou em 2026-09-08 que é
disso que se trata e que precisa ser decidido agora — não ao escrever a
`.feature` de tarefas.

A [decisão aberta #12](../DECISOES-ABERTAS.md) já dizia por quê: "tarefa que
repete toda semana é outro problema de modelagem, não um campo a mais", e
cenário Gherkin escrito sem essa decisão vira premissa silenciosa. O
[modelo de dados](../02-arquitetura/modelo-de-dados.md) esboçou `task` com
recorrência "deliberadamente ausente", e as duas fontes discordavam sobre
quando decidir: o modelo dizia "depois que a Etapa 5 mostrar que tarefas são
usadas", a decisão aberta dizia "ao escrever a feature, na Etapa 2b". Esta ADR
encerra as duas coisas.

O que está em jogo não é sintaxe de repetição — é se existe histórico. "Você não
tirou o lixo nos dias 7, 14 e 21" só é uma pergunta respondível se cada sábado
for um dado.

## Decisão

**A regra da repetição e a ocorrência são coisas separadas.**

```text
task_recurrence                     -- a regra: "toda semana, sabado"
  id, household_id, title, notes,
  assignee_member_id (nullable),
  frequency (DAILY|WEEKLY|MONTHLY),
  day_of_week (nullable),           -- so WEEKLY
  day_of_month (nullable),          -- so MONTHLY
  until (nullable),                 -- nulo = sem fim
  created_by_member_id, created_at, archived_at

task                                -- a ocorrencia: "o sabado dia 14"
  ... campos ja esbocados ...,
  recurrence_id (nullable)          -- de qual regra esta ocorrencia nasceu
```

**Só a próxima ocorrência existe por vez.** Concluir a tarefa de hoje
materializa a do próximo sábado; nada de calendário inteiro criado à frente.
Sem job e sem agendador — mesma disciplina que a
[ADR-0014](0014-fechamento-de-fatura-sob-demanda.md) adotou para fatura e a
[ADR-0020](0020-convite-de-membro.md) para convite vencido: o estado é calculado
ou criado no momento em que alguém olha, não por processo rodando no vazio.

Consequência que faz parte da decisão: **quem nunca conclui não acumula**. Se
ninguém tirar o lixo por três sábados, existe **uma** tarefa vencida do dia 7,
não três. O histórico de que os dias 14 e 21 passaram sem ninguém fazer nada
está na data de vencimento e no calendário, não em três linhas iguais. Criar
ocorrência por tempo decorrido exigiria exatamente o job que o projeto vem
evitando desde a ADR-0014.

**Concluir a última ocorrência não encerra a regra.** `task_recurrence.until`
nulo significa para sempre; parar de tirar o lixo é arquivar a regra
(`archived_at`), o que é ação explícita, do mesmo jeito que `category.archived_at`.

**Recorrência não entra por adivinhação do modelo.** "retirar o lixo sábado" cria
tarefa avulsa; "retirar o lixo todo sábado" cria regra. A diferença é textual e
explícita — o `nlu` não infere periodicidade de um pedido que não a menciona,
mesma disciplina de "só o resíduo literal" das ADRs
[0023](0023-descricao-de-lancamento-extraida-pelo-llm.md),
[0024](0024-categoria-sugerida-por-texto-livre.md) e
[0026](0026-hierarquia-na-criacao-de-categoria-por-chat.md).

## Alternativas consideradas

### A. Campos de recorrência na própria `task`, uma linha para sempre
Descartada, e é a alternativa barata: `task` ganharia `recurrence_rule` e
`recurrence_until`, e concluir empurraria `due_at` para o próximo sábado na
mesma linha. Zero tabela nova, zero materialização. O que ela custa é o
histórico inteiro: a linha só sabe o estado atual, então "quantos sábados foram
feitos no mês" e "quem tirou o lixo dia 7" ficam sem resposta — só o último
`completed_by_member_id` sobrevive, sobrescrito toda semana. Contraria a
disciplina que o resto do modelo já tem (estorno em vez de delete,
`transaction_edit` append-only): o projeto não apaga o que aconteceu.

### B. Não ter recorrência; quem quiser repete a mensagem toda semana
Descartada pelo autor. A [decisão aberta #12](../DECISOES-ABERTAS.md) permitia
explicitamente essa saída ("decidida **ou excluída explicitamente**"), e ela é
honesta — mas deixa de fora justamente o caso mais óbvio de tarefa doméstica, e
o exemplo que motivou a pergunta.

### C. Materializar um horizonte (ex.: as próximas 8 semanas de uma vez)
Descartada: transforma "quem nunca conclui não acumula" em oito tarefas vencidas
na cara do usuário, e obriga a decidir o tamanho do horizonte, que é mais um
número inventado sem dado real — o mesmo tipo de palpite que a
[ADR-0004](0004-interpretacao-por-function-calling-com-politica-de-confianca.md)
recusou fixar para o limiar de confiança.

## Consequências

### Positivas
- Histórico real: cada ocorrência guarda quem completou e quando, e furar três
  sábados é uma consulta, não uma dedução.
- Sem job, sem agendador, sem infraestrutura nova — coerente com ADR-0014 e
  ADR-0020.
- A regra é editável sem tocar no passado: mudar de sábado para domingo altera
  `task_recurrence` e as ocorrências já concluídas continuam como foram.

### Negativas
- **Duas tabelas e um conceito a mais em `tasks`**, que é o menor domínio dos
  três e o último a ser construído. É custo real pago antes de existir um só
  usuário de tarefas.
- **"Quem nunca conclui não acumula" pode surpreender.** Alguém que espera ver
  três lixos vencidos vai ver um. É a consequência de não ter job, e está
  declarada aqui em vez de descoberta depois.
- `frequency` com três valores e dois campos condicionais (`day_of_week`,
  `day_of_month`) é uma mini-gramática de recorrência. Não é RFC 5545 e não
  cobre "toda primeira segunda-feira do mês" nem "de dois em dois dias" — se
  isso for pedido, é esta ADR que se supera, com RRULE na mesa.
- Ainda não há `sdd-modulo-tasks.md`: esta ADR fica sem SDD que a reflita até a
  Etapa 2b. É exceção registrada em `DocumentationCoherenceTest`, não
  esquecimento.

## Gatilhos de revisão

- Etapa 2b, ao escrever a `.feature` de tarefas: se algum cenário precisar de
  periodicidade que a mini-gramática não expressa, RRULE entra antes do código,
  não depois.
- Etapa 5: se tarefas se mostrarem pouco usadas, a pergunta não é simplificar a
  recorrência — é se o domínio de tarefas se sustenta, que é decisão maior.
