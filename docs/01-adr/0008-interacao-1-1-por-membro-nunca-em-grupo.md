---
tipo: adr
numero: 8
status: aceita
data: 2026-08-31
modulos:
  - channel
  - onboarding
  - roadmap
depende_de: []
supera: []
superada_por:
---

# ADR-0008 — Interação 1:1 por membro, nunca em grupo

- **Impacta**: `channel`, onboarding, UX de recibo, roadmap (encerra
  [decisão aberta #1](../DECISOES-ABERTAS.md), remove risco declarado na [ADR-0002](0002-telegram-primeiro-whatsapp-depois.md))
- **Termos**: [Canal, "grupo"](../00-produto/glossario.md#termos-proibidos)

## Contexto

A [ADR-0002](0002-telegram-primeiro-whatsapp-depois.md) havia registrado 1:1 como **mitigação de risco técnico** —
incerteza sobre se a WhatsApp Cloud API opera em grupo — e recomendado
desenhar a UX como 1:1 "tratando grupo como bônus e não como premissa" só
para o caso de a resposta ser não. Esta ADR torna a pergunta irrelevante:
decidido abaixo que 1:1 é definitivo por produto, qualquer que seja a
capacidade real do canal.

Mas o diferencial do produto (ver CLAUDE.md) é o elo entre os três domínios
*dentro da família* — não a coordenação social em tempo real. Recibo em grupo
tem um custo que recibo 1:1 não tem: todo lançamento de qualquer membro vira
notificação para todos os outros, e uma família que lança várias vezes por
dia (mercado, tarefa concluída, despesa avulsa) transforma o grupo em ruído
constante. Grupo também impede um recibo que nomeie a pessoa certa de forma
natural ("Bruno comprou o arroz que **você** pediu" só faz sentido endereçado
a quem pediu, não replicado para todo mundo) e vaza informação que às vezes
é privada dentro da própria família — presente de aniversário lançado como
despesa, por exemplo.

## Decisão

Interação é 1:1 por membro, em todo canal, permanentemente — decisão de
produto, não workaround de limitação técnica. Cada membro cadastra o próprio
número de WhatsApp ou conta de Telegram e conversa com o bot em fio dedicado.
Não usaremos grupos em nenhum canal, **mesmo onde forem suportados**.

Isso encerra a [decisão aberta #1](../DECISOES-ABERTAS.md): a resposta de se a Cloud API opera em grupo
deixa de importar para a arquitetura, porque grupo nunca seria usado de
qualquer forma. Também remove o risco declarado na [ADR-0002](0002-telegram-primeiro-whatsapp-depois.md) — não há UX
desenhada em cima de grupo para redesenhar depois, porque grupo nunca foi
premissa de fato, e agora está descartado de forma definitiva em vez de
condicional.

## Alternativas consideradas

### A. Grupo único por household, todos os membros nele
O bot lê e responde no grupo da família, identificando o autor de cada
mensagem. Descartada: cada lançamento de qualquer membro notifica todo mundo,
o que transforma uso normal (vários lançamentos por dia, por vários membros)
em ruído constante — o oposto de um assistente que deveria ser discreto.
Também impede recibo endereçado a uma pessoa específica e expõe lançamento
que a família pode preferir manter privado entre duas pessoas (presente,
despesa pessoal). Depende ainda da capacidade de grupo da Cloud API, que é
incerta ([ADR-0002](0002-telegram-primeiro-whatsapp-depois.md)).

### B. Suportar os dois: 1:1 como padrão, grupo como opção
Família escolhe se quer registrar um grupo além das conversas individuais.
Descartada por ora: duplica o caminho de resolução de contexto
(`channel_identity` precisaria diferenciar "veio de conversa direta" de "veio
de grupo compartilhado", cada um com regras próprias de a quem endereçar o
recibo), aumenta a superfície de teste sem evidência de que a família valida
esse modo, e adia a Etapa 1 por um caso que não é o caminho crítico do
diferencial do produto — o elo entre domínios funciona igual em 1:1. Fica
como candidato a reavaliação, não como alternativa descartada por princípio.

## Consequências

### Positivas
- Recibo personalizado por membro ("Bruno comprou o arroz que você pediu")
  fica trivial: o bot sempre sabe exatamente para quem está falando.
- Fecha a [decisão aberta #1](../DECISOES-ABERTAS.md) sem depender de verificação externa na
  documentação da Meta — deixa de ser bloqueio de roadmap.
- Remove o risco de redesenho de UX declarado na [ADR-0002](0002-telegram-primeiro-whatsapp-depois.md): não existe UX
  validada em grupo para descartar depois.
- Resolução de contexto simplifica: uma `channel_identity` corresponde a uma
  pessoa em uma conversa, sem caso especial de "mensagem de grupo, autor
  fulano".

### Negativas
- Onboarding fica mais pesado do que "adicionar todo mundo a um grupo":
  cada membro precisa cadastrar seu próprio número ou conta individualmente.
  Membro menos engajado (criança, avô) pode nunca completar o próprio
  cadastro e ficar de fora do canal de chat, dependendo só da web.
- Visibilidade compartilhada do household ("o que a família andou lançando")
  deixa de vir de graça pela simples presença no grupo — precisa ser
  resolvida deliberadamente por resumo enviado a cada membro ou pela
  interface web, ou a família perde a sensação de coordenação coletiva.
- Coordenação espontânea que grupo permite ("faltou arroz" perguntado e
  "já comprei" respondido na hora, sem o app no meio) desaparece; toda
  coordenação passa a depender do app registrar corretamente e do recibo
  chegar a tempo.

## Gatilhos de revisão

Se a Etapa 5 mostrar demanda forte por visibilidade em tempo real que resumo
e app não resolvem, reavaliar grupo como canal **somente de notificação**
(nunca de lançamento) — sem reabrir a Alternativa A por completo.
