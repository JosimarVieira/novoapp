# ADR-0002 — Telegram primeiro, WhatsApp depois

- **Status**: Proposta
- **Data**: 2026-08-31
- **Impacta**: `channel`, roadmap, modelo de custo

## Contexto

WhatsApp é o canal com distribuição no Brasil e é onde o produto precisa estar
para ser comercial. Mas a Cloud API oficial exige Meta Business verificado,
número dedicado fora do WhatsApp comum, aprovação de templates, e cobra por
mensagem iniciada pelo negócio. `[a verificar]` a tabela de preços vigente e
as regras de janela de 24h — a Meta altera com frequência.

Além disso, `[a verificar com prioridade]`: a Cloud API não opera em grupos.
Se confirmado, toda interação é 1:1 com cada membro, o que muda a UX familiar
de forma relevante.

Telegram Bot API é gratuita, sem aprovação, funciona em grupo e sobe em horas.

Bibliotecas não oficiais (Baileys, wppconnect) contornam as limitações mas
implicam risco de banimento do número — inaceitável para produto pago.

## Decisão

Construir e validar o produto inteiro no Telegram. WhatsApp entra apenas na
Etapa 7, depois que o uso real provar o valor. O adaptador de canal é escrito
desde o dia um de forma agnóstica, para que a adição do WhatsApp seja um
adaptador novo e nada mais.

## Alternativas consideradas

### A. WhatsApp desde o início
Descartada: introduz dependência de aprovação de terceiro no caminho crítico
da validação. Semanas bloqueadas sem aprender nada sobre o produto.

### B. Biblioteca não oficial de WhatsApp
Descartada: risco de ban do número. Um produto comercial cujo canal principal
pode ser desligado por violação de ToS não é um produto.

## Consequências

### Positivas
- Etapa 1 entrega em dias, não semanas.
- Custo de mensagem zero durante toda a validação, inclusive nas proativas.
- Adaptador agnóstico é forçado por necessidade, não por boa intenção.

### Negativas
- A família de validação precisa usar Telegram, que não é o hábito dela.
  Enviesa a medição de adoção — o teste mede utilidade, não conveniência.
- Se a limitação de grupo do WhatsApp se confirmar, uma UX desenhada e
  validada em grupo no Telegram terá que ser redesenhada.
  Mitigação: **projetar toda interação como 1:1 desde já**, tratando grupo
  como bônus e não como premissa.

## Gatilhos de revisão

Confirmação da regra de grupos da Cloud API. Se ela suportar grupos, revisar
a UX 1:1 imposta por esta ADR.
