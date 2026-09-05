---
tipo: adr
numero: 6
status: aceita
data: 2026-08-31
modulos:
  - billing
  - channel
  - custo
depende_de: []
supera: []
superada_por:
---

# ADR-0006 — Assinatura por household e escolha do canal proativo

- **Impacta**: billing, `channel`, features de lembrete
- **Termos**: [Household, Canal](../00-produto/glossario.md#núcleo)

## Contexto

No WhatsApp, mensagem iniciada pelo negócio (template) é paga por mensagem —
isso é fato hoje, na documentação oficial da Meta, e por si só já sustenta a
decisão abaixo. Há também uma mudança **não confirmada em fonte primária**
(ver [decisão aberta #2](../DECISOES-ABERTAS.md)): fontes de mercado apontam que, a partir de 1º de
outubro de 2026, a Meta passaria a cobrar também a mensagem de resposta
dentro da janela de 24h — hoje gratuita —, ao valor de mensagem de utilidade
(≈ R$ 0,035 no Brasil), exceto na janela de 72h de anúncio "Click to
WhatsApp". Tratada aqui como hipótese, não como fato: a documentação atual
da Meta ainda descreve a janela de 24h como gratuita, sem data de fim.

Se essa hipótese se confirmar, o fluxo reativo no WhatsApp — hoje de custo
marginal quase zero — passaria a ter custo por mensagem, só a janela de 72h
de "Click to WhatsApp" continuando livre. No Telegram o custo marginal
permanece zero de qualquer forma, o que já reforça a preferência por
Telegram desta ADR **independente** de essa hipótese se confirmar ou não.
Além disso, as features que geram retenção em app de finanças — lembrete de
vencimento, resumo semanal, alerta de lista — são todas proativas (sempre
pagas por template no WhatsApp, confirmação ou não da mudança de outubro).
Uma família de quatro pessoas recebendo dois avisos semanais gera dezenas de
templates por mês, e o custo escala com o número de membros.

## Decisão

Cobrar por household, com membros ilimitados dentro de um limite razoável.
Nunca cobrar por usuário.

Quando um membro tiver Telegram e WhatsApp vinculados, mensagens proativas vão
preferencialmente pelo Telegram, onde não há custo por mensagem. WhatsApp
proativo é reservado para o que o usuário explicitamente pedir lá.

O mecanismo de limite de frequência das features proativas em si (lembrete,
resumo semanal, alerta de lista) é decisão da Etapa 8, quando essas features
forem desenhadas — não desta ADR, que fixa só cobrança e preferência de
canal. Ver Gatilhos de revisão.

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
- **Se** a mudança de 1º/10/2026 se confirmar, mensagem **reativa** no
  WhatsApp também passa a ter custo (deixa de ser quase-zero), e a margem
  por household precisaria considerar todo o tráfego do canal, não só o
  proativo, quando a Etapa 7 chegar. Não confirmado em fonte primária no
  momento em que esta ADR foi aceita — ver [decisão aberta #2](../DECISOES-ABERTAS.md). Se não se
  confirmar, esta negativa não se aplica.

## Gatilhos de revisão

Se o custo médio de mensagem por household passar de um patamar definido da
mensalidade, introduzir limite explícito de proativas por plano. Com a
cobrança de mensagem reativa a partir de outubro de 2026, avaliar se esse
limite precisa cobrir também volume de conversa reativa por WhatsApp, não só
proativa — decidir com base na tabela de tarifas por país publicada em
1º/09/2026.
