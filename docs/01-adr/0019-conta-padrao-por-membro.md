---
tipo: adr
numero: 19
status: aceita
data: 2026-09-04
modulos:
  - banco
  - finance
  - nlu
depende_de:
  - ADR-0011
supera: []
superada_por:
---

# ADR-0019 — Conta padrão por membro, opcional, sobre o padrão do household

- **Impacta**: banco (`household_membership`), `finance`, interpretação por
  chat (resolução de conta); estende [ADR-0011](0011-cartao-de-credito-e-fatura.md) sem contradizê-la

## Contexto

A [ADR-0011](0011-cartao-de-credito-e-fatura.md) decidiu uma `account` `WALLET` implícita por household, criada no
onboarding, como o destino padrão de `mercado 50` quando nenhuma conta é
nomeada. Isso cobre o caso comum, mas não cobre um caso real que o autor
trouxe ao validar a visão do produto (2026-09-04): membros do mesmo
household podem ter contas pessoais diferentes (cartão do marido, cartão da
esposa) e querem que "mercado 50" caia na própria conta por padrão, não
necessariamente na carteira compartilhada.

Isso não contradiz a ADR-0011 — o household continua tendo um default
(fallback), e nada aqui muda o comportamento de quem nunca configurar uma
conta preferida.

## Decisão

`household_membership` ganha `default_account_id` (nullable, FK `account`).
Resolução de conta quando a mensagem não especifica:

1. Se `household_membership.default_account_id` do membro que enviou a
   mensagem estiver preenchido, usa essa conta.
2. Senão, usa a `WALLET` implícita do household ([ADR-0011](0011-cartao-de-credito-e-fatura.md)) — comportamento
   inalterado para quem nunca configurou preferência própria.

Fica em `household_membership`, não em `member`: a preferência é por
vínculo com um household específico, não da pessoa em geral — a mesma
pessoa em dois households ([ADR-0007](0007-pessoa-em-multiplos-households.md)) pode preferir contas diferentes em
cada um. Cada membro configura a própria preferência; não é ação que outro
membro faz por ele (diferente de editar lançamento já feito, [ADR-0012](0012-edicao-de-lancamento-entre-membros.md), que é
sobre dado já existente, não sobre preferência pessoal de resolução).

## Alternativas consideradas

### A. Manter só o default do household (ADR-0011 como estava)
Descartada agora que o caso de uso real foi confirmado pelo autor: cartão
pessoal por membro é comum o suficiente para justificar o campo, e o custo
de adicionar é uma coluna nullable, não um redesenho.

### B. `default_account_id` em `member`, não em `household_membership`
Descartada: `member` é identidade de pessoa independente de household
([ADR-0007](0007-pessoa-em-multiplos-households.md)) — conta preferida só faz sentido dentro de um household
específico. Colocar em `member` obrigaria a mesma preferência em todo
household que a pessoa participa, o que não faz sentido para quem tem mais
de um vínculo.

## Consequências

### Positivas
- Cobre o caso real (contas pessoais dentro de finanças compartilhadas) sem
  quebrar o caso comum (household sem preferência configurada continua
  caindo na `WALLET`).
- Custo de schema mínimo: uma coluna nullable, sem tabela nova.

### Negativas
- Mais um passo na resolução de contexto de toda mensagem de despesa/receita
  — depois de resolver `household` ativo ([ADR-0007](0007-pessoa-em-multiplos-households.md)), agora também resolve
  `default_account_id` do vínculo. Mitigado por recibo sempre nomear a conta
  usada (já é regra existente), então erro de resolução é visível na hora.
- Um membro pode esquecer que configurou uma preferência e estranhar o
  lançamento caindo num cartão que não o esperado — mesmo tipo de risco já
  aceito em `active_household_id` ([ADR-0007](0007-pessoa-em-multiplos-households.md)), mesma mitigação (recibo explícito).

## Gatilhos de revisão

Se, na Etapa 5, quase ninguém configurar conta preferida (todo mundo usa a
`WALLET` do household o tempo todo), avaliar se o campo compensa manter ou
se é complexidade sem uso real.
