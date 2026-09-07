---
tipo: adr
numero: 25
status: aceita
data: 2026-09-07
modulos:
  - conversation
  - finance
depende_de:
  - ADR-0012
  - ADR-0018
supera: []
superada_por:
corrigida_em:
---

# ADR-0025 — Desfazer: precedência sobre pendência e escopo do estorno

- **Impacta**: `conversation` (resolução do comando `desfazer`), `finance`
  (alvo do estorno); resolve colisão entre
  [ADR-0018](0018-central-de-pendencias.md) e a regra 7 de `CLAUDE.md`; Etapa
  2a do roadmap

## Contexto

`desfazer` aparece em dois papéis que nenhum documento ordena entre si.

CLAUDE.md regra 6 lista `desfazer` entre as palavras resolvidas por
curto-circuito determinístico — no contexto da regra, isso significa resolver
uma `PendingAction` aberta, o mesmo mecanismo de `sim`/`não`/número descrito
pela [ADR-0018](0018-central-de-pendencias.md). CLAUDE.md regra 7 diz "todo
lançamento é reversível por `desfazer`", e a
[ADR-0012](0012-edicao-de-lancamento-entre-membros.md) explicita que qualquer
membro do household — não só quem lançou — pode estornar qualquer lançamento
de qualquer outro membro. O cenário `@etapa2` já escrito
(`Cenário: Desfazer um lançamento recém-criado`) testa só o caso mais simples:
um remetente, uma transação recente, nenhuma pendência aberta ao mesmo tempo.

Nenhum documento decide o que acontece quando os dois casos coincidem: se há
uma `PendingAction` aberta para o household (por exemplo, a confirmação de
categoria nova da ADR-0024) e ao mesmo tempo existe uma transação recente
ainda não estornada, `desfazer` pode significar "cancela essa pergunta" ou
"estorna aquele lançamento" — e nada aponta qual vence.

Mesmo isolando o caso de estorno puro, falta escopo: a
[ADR-0012](0012-edicao-de-lancamento-entre-membros.md) permite estorno
cross-member, então se Bruno lança uma despesa logo depois de Ana, o
`desfazer` de Ana é ambíguo entre "o último lançamento que eu fiz" e "o
último lançamento do household" — que já não é o dela.

## Decisão

**Precedência.** Se existe uma `PendingAction` aberta (não expirada, não
resolvida) para o household ou membro que enviou `desfazer`, ela tem
precedência absoluta: `desfazer` resolve essa pendência como rejeição,
equivalente a responder `não`. Só quando não há `PendingAction` aberta,
`desfazer` cai no fluxo de estorno da regra 7. Isso mantém `desfazer`
coerente com os outros atalhos de curto-circuito da regra 6 — todos eles só
fazem sentido quando há algo pendente para resolver.

**Escopo do estorno.** Quando `desfazer` estorna um lançamento, o alvo é a
`transaction` mais recente do **household** (qualquer membro, conforme a
[ADR-0012](0012-edicao-de-lancamento-entre-membros.md)) com `reversed_at`
nulo — não a mais recente do remetente. Esta ADR não decide algo novo aqui;
formaliza dentro do comando `desfazer` uma permissão que a ADR-0012 já
concedeu, para que a implementação não escolha em silêncio um escopo mais
restrito (por remetente) que contradiria a ADR-0012.

**Sem janela de tempo.** `desfazer` estorna a transação mais recente não
estornada, sem limite de quanto tempo faz que ela foi criada. Um número
qualquer aqui seria tão inventado quanto o limiar de confiança que a própria
ADR-0004 recusou fixar sem dado real — fica para calibrar na Etapa 5, se o
uso real mostrar necessidade.

## Alternativas consideradas

### A. Escopo por remetente ("meu último lançamento", não do household)
Descartada — contradiz diretamente a ADR-0012, que decidiu deliberadamente
que qualquer membro pode estornar lançamento de qualquer outro. Restringir o
escopo de `desfazer` ao remetente seria a implementação decidindo, por conta
própria, um comportamento mais restrito que a ADR-0012 já rejeitou.

### B. Janela de tempo fixa (ex.: só desfaz lançamento dos últimos 10 minutos)
Descartada pela mesma razão que a ADR-0004 recusou fixar limiar de confiança
sem dado real: qualquer número escolhido agora é palpite, não decisão
informada. Fica como gatilho de revisão, não como valor definido aqui.

### C. Tratar todo estorno como resolução de pendência (nunca estorno direto fora de uma `PendingAction`)
Descartada — a maioria dos lançamentos de alta confiança nunca gera
`PendingAction` (é o caminho feliz da ADR-0004: executa e responde recibo
direto). Exigir uma pendência aberta para todo `desfazer` mudaria a UX de
"responde `desfazer` e pronto" para "seria preciso ter sido perguntado antes",
o que a ADR-0004 já rejeitou ao decidir não confirmar toda mensagem.

## Consequências

### Positivas
- Remove a ambiguidade que o próprio cenário `@etapa2` mais complexo (pendência
  aberta simultânea a lançamento recente) exporia como comportamento não
  definido.
- Consistente com a ADR-0012 (escopo household-wide) e com a regra 6
  (curto-circuito de pendência tem prioridade sobre qualquer outra
  interpretação de texto).

### Negativas
- **Se os dois casos coincidirem no tempo, quem manda `desfazer` esperando
  estornar um lançamento pode só cancelar uma pergunta pendente sem perceber**
  — a mitigação é a resposta deixar explícito o que foi desfeito ("pergunta
  cancelada" vs. "lançamento de R$ X estornado"), o que precisa ser escrito em
  `sdd-modulo-conversation.md`, não é automático por esta ADR.
- Estorno sem escopo por conta ou categoria, do household inteiro, pode
  surpreender em household com mais de um membro lançando ao mesmo tempo — a
  ADR-0012 já aceitou esse risco ao permitir estorno cross-member; esta ADR
  só formaliza o alvo do comando dentro do risco que ela já assumiu.
- Sem janela de tempo, um `desfazer` dias depois do último lançamento (se
  ninguém lançou mais nada nesse meio tempo) ainda estorna essa transação
  antiga — pode ser inesperado; sem dado real hoje para decidir se isso
  importa na prática.

## Gatilhos de revisão

Se dado real da Etapa 5 mostrar confusão frequente entre "cancelei uma
pergunta" e "estornei um lançamento", revisar o texto da resposta ou passar a
perguntar explicitamente qual das duas coisas a pessoa quer quando os dois
casos coincidirem. Se usuários reportarem como surpresa o estorno de
lançamento feito por outro membro, é a ADR-0012 que precisa ser reaberta, não
esta.
