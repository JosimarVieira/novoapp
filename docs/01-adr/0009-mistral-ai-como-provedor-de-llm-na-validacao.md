---
tipo: adr
numero: 9
status: aceita
data: 2026-08-31
modulos:
  - nlu
  - custo
depende_de: []
supera: []
superada_por:
---

# ADR-0009 — Mistral AI como provedor de LLM na fase de validação

- **Impacta**: `nlu`, custo ([ADR-0006](0006-assinatura-por-household-e-canal-proativo.md)), [decisão aberta #3](../DECISOES-ABERTAS.md)

## Contexto

A [decisão aberta #3](../DECISOES-ABERTAS.md) previa escolher provedor de
LLM só na Etapa 5, com custo por mensagem medido em uso real — decidir antes
seria chute. Mas a Etapa 1 já precisa de um provedor funcionando para existir
o `nlu` da [ADR-0004](0004-interpretacao-por-function-calling-com-politica-de-confianca.md), e a validação inicial acontece só na família do fundador
(CLAUDE.md), sem receita: qualquer custo por mensagem nessa fase é saída de
caixa pura, no mesmo espírito que levou a [ADR-0002](0002-telegram-primeiro-whatsapp-depois.md) a escolher Telegram por
ser gratuito durante a validação.

Mistral AI oferece tier gratuito que atende o volume de uma família em teste,
com suporte a function calling — o mecanismo que a [ADR-0004](0004-interpretacao-por-function-calling-com-politica-de-confianca.md) exige.

## Decisão

Usar Mistral AI (tier gratuito) como provedor de LLM para a fase de validação
(Etapas 1-5), acessado via LangChain4j / quarkus-langchain4j (CLAUDE.md, camada
`nlu`), nunca com chamada direta à API do provedor fora dessa camada — para
que troca de provedor na Etapa 5 seja configuração, não reescrita.

A escolha é para a fase de validação, não definitiva: a [decisão aberta #3](../DECISOES-ABERTAS.md)
permanece parcialmente aberta para a escolha de provedor pago/comercial,
a ser feita na Etapa 5 com taxa de acerto e custo reais — inclusive a
possibilidade de continuar com Mistral se os dados sustentarem.

## Alternativas consideradas

### A. OpenAI (ex.: GPT-4o-mini)
Descartada para a fase de validação: cobra desde a primeira chamada, exige
cartão de crédito e billing configurado antes de existir qualquer usuário
real. Sem tier gratuito com volume relevante. Fica como candidato natural na
escolha comercial da Etapa 5, não descartada em definitivo.

### B. Modelo self-hospedado (ex.: Llama via Ollama)
Descartada: custo de infraestrutura e operação (hospedar, manter, garantir
disponibilidade de um modelo próprio) desloca esforço da Etapa 1 do
diferencial do produto — o elo entre domínios — para infraestrutura de ML.
Quebra a analogia que fez o Telegram vencer na [ADR-0002](0002-telegram-primeiro-whatsapp-depois.md): "sobe em horas".

### C. Esperar a Etapa 5 para escolher qualquer provedor
Descartada: bloquearia a Etapa 1 por completo — não existe `nlu` funcional
sem um provedor de LLM escolhido. A [decisão aberta #3](../DECISOES-ABERTAS.md) pressupunha dado real
de uso, mas o próprio dado real exige que algo já esteja rodando primeiro.

## Consequências

### Positivas
- Custo zero durante toda a validação na família do fundador, no mesmo
  racional de custo que levou à escolha do Telegram ([ADR-0002](0002-telegram-primeiro-whatsapp-depois.md)).
- Acesso via LangChain4j (quarkus-langchain4j) mantém a troca de provedor na Etapa 5
  como mudança de configuração, não reescrita de `nlu`.

### Negativas
- Tier gratuito tem limite de taxa (requisições por minuto/dia) não
  dimensionado para uso além da família fundadora — risco de throttling se o
  uso crescer antes da Etapa 5, sem aviso prévio de mudança de cota por parte
  do provedor.
- Modelo gratuito tende a ter capacidade de function calling e resolução de
  entidade inferior a modelos maiores pagos, o que pode empurrar a taxa de
  acerto para baixo do critério de continuidade do ROADMAP (90% no fluxo de
  despesa) — a escolha por custo pode comprometer justamente a métrica que
  decide se o produto segue para a Etapa 6.
- Termos e disponibilidade do tier gratuito são controlados por terceiro e
  podem mudar sem aviso, como já é o caso para preço de mensagem no WhatsApp
  ([ADR-0002](0002-telegram-primeiro-whatsapp-depois.md), [ADR-0006](0006-assinatura-por-household-e-canal-proativo.md)).

## Gatilhos de revisão

Na Etapa 5, decidir o provedor comercial com taxa de acerto e custo reais
medidos com Mistral como baseline. Se a taxa de acerto ficar abaixo de 90%
(critério do ROADMAP) e não houver calibração de prompt que resolva, trocar
de provedor antes de abrir a Etapa 6 — mesmo em meio à validação, se o
throttling do tier gratuito se tornar bloqueio observável antes disso.
