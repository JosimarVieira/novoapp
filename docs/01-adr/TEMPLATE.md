---
# Metadados estruturados: é daqui que `adr.base` monta o índice.
# Nunca duplique estes campos em prosa no corpo — duplicata desatualiza.
tipo: adr
numero: NNNN
status: proposta   # proposta | aceita | superada
data: AAAA-MM-DD
# Vocabulário fechado. Termo novo entra aqui e no glossário, não improvise.
# A tag é em inglês (é identificador); entre parênteses, como a mesma coisa
# aparece na prosa em português — a auditoria usa este par para conferir se
# frontmatter e texto contam a mesma história:
#   backend | banco (banco de dados) | channel (canal) | nlu (interpretação
#   por chat) | conversation (conversa, confirmação, recibo) | finance
#   (finanças) | shopping (mercado, lista) | tasks (tarefas) | identity
#   (identidade, vínculo) | billing (assinatura, cobrança) | custo |
#   onboarding | roadmap | glossario (glossário) | web (PWA, interface web)
modulos: []
depende_de: []     # ex.: [ADR-0003, ADR-0010]
supera: []         # ex.: [ADR-0001]
superada_por:      # preenchido só quando outra ADR superar esta
corrigida_em:      # data, só se o registro estava errado (ver CLAUDE.md)
---

# ADR-NNNN — Título curto no imperativo

- **Impacta**: a narrativa, com links — módulos, etapas do roadmap, decisões
  abertas que esta ADR encerra, ADRs de que depende. O campo `modulos` do
  frontmatter é o índice filtrável disso; esta linha é o porquê.

## Contexto

O problema e as forças em jogo. Fatos, não justificativa da escolha.
Se um fato aqui for premissa não verificada, marque como `[a verificar]`.

## Decisão

O que foi decidido, em uma ou duas frases, no presente do indicativo.

## Alternativas consideradas

Mínimo duas, cada uma com por que foi descartada. Alternativa de palha
(criada só para perder) invalida a ADR.

### A. <alternativa>
Descartada porque...

### B. <alternativa>
Descartada porque...

## Consequências

### Positivas
-

### Negativas
- (obrigatório. Se está vazio, a análise está incompleta.)

## Gatilhos de revisão

O que precisa acontecer no mundo para esta decisão ser reavaliada.
Ex.: "se o custo de template do WhatsApp cair abaixo de X".
