---
tipo: adr
numero: 15
status: aceita
data: 2026-08-31
modulos:
  - channel
  - conversation
  - banco
  - glossario
  - roadmap
depende_de: []
supera: []
superada_por:
---

# ADR-0015 — Internacionalização: arquitetura desde já, conteúdo por demanda real

- **Impacta**: `channel`, `conversation` (recibo e mensagens), banco (`member`),
  glossário, roadmap
- **Termos**: [Idioma preferido — termo novo, adicionar ao glossário antes de
  codar]

## Contexto

Pedido: aplicação com internacionalização, começando por português do
Brasil, inglês e espanhol.

Isso esbarra num padrão que se repete em quase toda ADR aceita até aqui
(Telegram antes de WhatsApp, RLS antes de multi-tenant real, Mistral antes
de provedor comercial): **decidir a arquitetura cedo é barato, decidir o
conteúdo cedo é caro e prematuro**. Hoje: validação acontece só na família
do fundador (CLAUDE.md), presumivelmente falante de português. Não existe
ainda uma única mensagem real em inglês ou espanhol para calibrar nada — a
mesma lógica que adiou WhatsApp para a Etapa 7 e o provedor comercial de LLM
para a Etapa 5 (dado real antes de decisão) se aplica aqui.

Ao mesmo tempo, **retrofit é caro**: recibo, mensagem de erro e pergunta de
confirmação hoje seriam texto literal em português espalhado pelo código de
`conversation`/`channel` se ninguém desenhar isso agora — o mesmo argumento
que fez RLS ([ADR-0003](0003-isolamento-multi-tenant-por-household.md)) e `household_membership` ([ADR-0007](0007-pessoa-em-multiplos-households.md)) entrarem
cedo, antes de dado real em produção.

Fora de escopo desta ADR: moeda. `amount_cents` já é agnóstico de moeda
([modelo-de-dados.md](../02-arquitetura/modelo-de-dados.md)); multi-moeda (household em outro país, conversão) é
problema maior e não foi pedido — trata-se aqui só de **idioma** de texto,
não de valor monetário.

## Decisão

Duas coisas decididas juntas, propositalmente separadas em custo e tempo:

1. **Arquitetura, agora (Etapa 1 em diante)**: nenhum texto voltado ao
   usuário (recibo, pergunta de confirmação, erro no chat) é literal no
   código. Todo texto vem de arquivo de mensagens por idioma (`ResourceBundle`
   ou equivalente Quarkus, ex. `messages_pt_BR.properties`,
   `messages_en.properties`, `messages_es.properties`), resolvido a partir de
   um novo atributo `member.preferred_locale` (default `pt-BR` no onboarding,
   trocável por comando, do mesmo jeito que `active_household_id` na
   [ADR-0007](0007-pessoa-em-multiplos-households.md) — decisão de pessoa, não de household: dois membros do mesmo
   household podem preferir idiomas diferentes).

2. **Conteúdo, por demanda real**: confirmado pelo autor em 2026-08-31 — só
   o arquivo `pt-BR` é escrito e mantido completo durante toda a validação
   interna na família do fundador (Etapas 1-5). `en` e `es` entram como
   tradução desse arquivo quando houver pedido real de cliente ou a Etapa 6
   (verticalização comercial) escancarar a base pra fora da família — o que
   é tradução de conteúdo, não reescrita de código, porque a arquitetura já
   existe desde o início.

O `nlu` (interpretação por LLM, [ADR-0004](0004-interpretacao-por-function-calling-com-politica-de-confianca.md)) não muda: modelo com function calling
interpreta a mensagem de entrada em qualquer idioma sem trabalho extra — o
que esta ADR resolve é o lado de **saída** (o que o bot responde), não de
entrada.

Gherkin continua em português (convenção do CLAUDE.md) mesmo depois de `en`/
`es` existirem — Gherkin descreve comportamento, não é o texto que o usuário
final vê; não é duplicado por idioma.

## Alternativas consideradas

### A. Traduzir os três idiomas agora, antes da Etapa 1
Descartada: nenhum dado real sustenta prioridade sobre inglês vs. espanhol
vs. variações regionais de português, e a família de validação não fala
nenhum dos dois idiomas novos — manter dois arquivos de mensagem sem uso real
é custo permanente (toda mensagem nova em `conversation` precisa ser escrita
três vezes) pago antes de qualquer household precisar disso.

### B. Não desenhar nada agora, resolver i18n só quando um cliente pedir
Descartada: exatamente o erro que RLS e `household_membership` evitaram —
texto literal em português vai se espalhar por `conversation`/`channel`
durante as Etapas 1-5, e extrair isso pra arquivo de mensagens depois de
dezenas de recibos e perguntas escritas é refactor mecânico, mas extenso,
sobre código que por essa altura já teria usuário real rodando.

### C. `locale` no household, não no membro
Descartada: um household pode ter membros de gerações ou origens diferentes
com preferência de idioma distinta (ex.: avô que prefere português, neto
que cresceu falando inglês) — a mesma razão de fundo que fez `active_household_id`
viver no membro/canal, não no household, na [ADR-0007](0007-pessoa-em-multiplos-households.md). `locale` por household
forçaria todo mundo no mesmo idioma sem necessidade.

## Consequências

### Positivas
- Nenhum retrofit de string hardcoded quando `en`/`es` forem realmente
  necessários — é arquivo de tradução novo, não mudança de código.
- Preferência por pessoa, não por household, cobre família multilíngue sem
  desenho adicional.
- Custo de manter conteúdo (revisão de texto, tom, recibo) fica adiado para
  quando há usuário real que o justifique — mesmo racional de custo que
  adiou WhatsApp e o provedor comercial de LLM.

### Negativas
- Mais uma camada de indireção em todo texto de `conversation`/`channel`
  desde o dia um (chave de mensagem em vez de string direta) — custo
  constante pequeno, pago mesmo enquanto só `pt-BR` existe.
- `preferred_locale` por membro, resolvido em tempo de resposta, é mais um
  ponto de contexto a carregar (junto de `active_household_id`) na resolução
  de identidade — mais um lugar onde esquecer de propagar o valor gera
  resposta no idioma errado, silenciosamente (não quebra, só erra).
- Interpretação (`nlu`) em múltiplos idiomas não foi calibrada em nenhum
  dado real — a taxa de acerto de 90% que trava a Etapa 6 ([ADR-0004](0004-interpretacao-por-function-calling-com-politica-de-confianca.md), ROADMAP)
  foi pensada e será medida em português; não há garantia de que se mantenha
  em inglês ou espanhol quando isso for testado de verdade.

## Gatilhos de revisão

Quando houver pedido real de `en` ou `es` (cliente específico, ou a Etapa 6
abrir a base pra fora da família do fundador), traduzir o arquivo de
mensagens correspondente e medir taxa de acerto do `nlu` nesse idioma
separadamente — não assumir que o número calibrado em português vale para os
outros.
