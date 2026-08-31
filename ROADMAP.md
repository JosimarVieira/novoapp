# Roadmap

Cada etapa termina em algo que dá para ver funcionando. Nenhuma etapa entrega
só plano.

## Etapa 0 — Fundação documental (em curso)

**Entregável**: ADRs 0001-0006 aceitas, glossário fechado, features das Etapas
1-3 escritas, decisões abertas 1 e 6 resolvidas.

Critério de saída: nenhum item marcado `[a verificar]` nas ADRs aceitas.

## Etapa 1 — Bot Telegram + despesa (~1 semana)

Webhook, adaptador de canal, `identity`, idempotência, uma tool no LLM,
Flyway com schema mínimo já multi-tenant e RLS ativa.

**Entregável**: você manda `mercado 50` no Telegram e vê a linha no Postgres,
com recibo no chat. Teste de vazamento de tenant verde.

## Etapa 2 — Mercado, tarefas e consultas (~2 semanas)

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

**Entregável**: instalável no celular, mostra o que veio do chat, permite
corrigir e recategorizar.

## Etapa 5 — Uso real na família (4 semanas)

Sem feature nova. Só uso e medição.

**Entregável**: taxa de acerto por tool, matriz de confusão, lista dos erros
reais, limiar de confiança calibrado. Decisões abertas 3, 7, 8 resolvidas.

Critério de continuidade: se a taxa de acerto de despesa ficar abaixo de 90%,
a Etapa 6 não começa. Precisão do interpretador é o produto.

## Etapa 6 — Verticalização comercial

Signup, onboarding de household, verificação de telefone, convite de membro,
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
