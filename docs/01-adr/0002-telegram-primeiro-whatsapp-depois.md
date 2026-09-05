---
tipo: adr
numero: 2
status: aceita
data: 2026-08-31
modulos:
  - channel
  - roadmap
  - custo
depende_de: []
supera: []
superada_por:
---

# ADR-0002 — Telegram primeiro, WhatsApp depois

- **Impacta**: `channel`, roadmap, modelo de custo

## Contexto

WhatsApp é o canal com distribuição no Brasil e é onde o produto precisa estar
para ser comercial. Mas a Cloud API oficial exige Meta Business verificado,
número dedicado fora do WhatsApp comum, aprovação de templates, e cobra por
mensagem iniciada pelo negócio. A partir de 1º de outubro de 2026 a Meta
também passa a cobrar a mensagem de resposta dentro da janela de atendimento
de 24h — hoje gratuita —, com exceção da janela de 72h aberta por anúncio
"Click to WhatsApp". O valor definitivo por país e a data real de vigência
não têm fonte primária confirmada ainda — tratado como [decisão aberta #2](../DECISOES-ABERTAS.md),
não trava esta ADR porque a decisão abaixo (sequenciar Telegram → WhatsApp)
não depende do valor exato.

A pergunta "a Cloud API opera em grupo?" motivou a princípio um risco de
redesenho de UX (ver Negativas). Deixou de importar para a arquitetura:
[ADR-0008](0008-interacao-1-1-por-membro-nunca-em-grupo.md) decidiu 1:1 permanente, por produto, independente da
capacidade técnica do canal — grupo nunca seria usado mesmo que suportado.

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
- ~~Se a limitação de grupo do WhatsApp se confirmar, uma UX desenhada e
  validada em grupo no Telegram terá que ser redesenhada.~~ Resolvido por
  [ADR-0008](0008-interacao-1-1-por-membro-nunca-em-grupo.md): interação é 1:1 por decisão de produto, não por limitação
  técnica do canal. Nunca houve UX de grupo a redesenhar.

## Gatilhos de revisão

Publicação da tabela definitiva de tarifas por país (esperada 1º/09/2026,
ver [decisão aberta #2](../DECISOES-ABERTAS.md)) — revisar o custo projetado da Etapa 7, não a
decisão de sequenciamento em si. (O gatilho original, sobre suporte a grupo
na Cloud API, caiu: [ADR-0008](0008-interacao-1-1-por-membro-nunca-em-grupo.md) tornou 1:1 permanente independente disso.)
