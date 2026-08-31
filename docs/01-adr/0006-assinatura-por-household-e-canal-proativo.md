# ADR-0006 — Assinatura por household e escolha do canal proativo

- **Status**: Proposta
- **Data**: 2026-08-31
- **Impacta**: billing, `channel`, features de lembrete

## Contexto

No WhatsApp, mensagem iniciada pelo negócio (template) é paga por mensagem;
resposta dentro da janela iniciada pelo usuário, não. `[a verificar]` os
valores e a mecânica atuais junto à Meta antes de fechar preço.

O fluxo reativo (usuário manda, sistema responde) tem custo marginal quase
zero. Mas as features que geram retenção em app de finanças — lembrete de
vencimento, resumo semanal, alerta de lista — são todas proativas. Uma família
de quatro pessoas recebendo dois avisos semanais gera dezenas de templates por
mês, e o custo escala com o número de membros.

## Decisão

Cobrar por household, com membros ilimitados dentro de um limite razoável.
Nunca cobrar por usuário.

Quando um membro tiver Telegram e WhatsApp vinculados, mensagens proativas vão
preferencialmente pelo Telegram, onde não há custo por mensagem. WhatsApp
proativo é reservado para o que o usuário explicitamente pedir lá.

Toda feature proativa nasce com um teto de frequência configurável pelo
household.

## Alternativas consideradas

### A. Cobrança por usuário
Descartada: custo de mensagem cresce com membros, receita também — mas a
disposição a pagar de uma família não cresce linearmente com o número de
pessoas. Além disso, cria incentivo para a família compartilhar um login,
quebrando a atribuição de "quem lançou".

### B. Todas as proativas por WhatsApp
Descartada: coloca uma variável de custo controlada por terceiro no meio da
margem.

## Consequências

### Positivas
- Margem previsível e desacoplada do tamanho da família.
- Preço simples de comunicar.

### Negativas
- Família grande e ativa consome mais que família pequena pelo mesmo preço.
  Exige monitorar custo por household desde o primeiro cliente pago.
- Preferir Telegram para proativas cria experiência inconsistente entre canais,
  e pode confundir quem espera receber tudo no WhatsApp.

## Gatilhos de revisão

Se o custo médio de mensagem por household passar de um patamar definido da
mensalidade, introduzir limite explícito de proativas por plano.
