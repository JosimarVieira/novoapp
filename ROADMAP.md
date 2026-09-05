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

## Etapa 2 — Mercado, tarefas e consultas (~2 semanas)

Começa por tirar o `@Disabled` de `Etapa2AcceptanceTest`: os cenários `@etapa2`
já estão escritos e já falham por falta de implementação, que é o estado
correto.

Além do que a etapa já previa, herda três lacunas conhecidas da Etapa 1,
listadas na entrega dela: botão nativo de compartilhar contato, retry com
backoff na falha de LLM, e o comando de trocar o household ativo ([ADR-0007](docs/01-adr/0007-pessoa-em-multiplos-households.md)).

Entra também a descrição do lançamento ([ADR-0023](docs/01-adr/0023-descricao-de-lancamento-extraida-pelo-llm.md), aceita em 2026-09-05,
a partir do primeiro uso real): a tool `registrarDespesa` ganha o parâmetro
opcional `descricao`, extraído pelo LLM e mantido fora da política de
confiança — nunca vira pergunta. Três cenários `@etapa2` já estão escritos
no `financas-lancamento-por-chat.feature`. Sem migration: a coluna já existe.

**Entregável**: os seis fluxos do glossário funcionando por chat, incluindo
"o que está faltando?".

## Etapa 3 — O elo (~1 semana)

`fecharCompra` atômico, `list_checkout`, `desfazer` reversível dos dois lados.

**Entregável**: `comprei tudo, 180` fecha a lista e lança a despesa. É a
demonstração que vende o produto.

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
