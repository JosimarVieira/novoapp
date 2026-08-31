# ADR-0007 — Pessoa pode pertencer a múltiplos households

- **Status**: Proposta
- **Data**: 2026-08-31
- **Impacta**: banco (`member`, `channel_identity`), `channel` (resolução de
  contexto), billing (ADR-0006)

## Contexto

O modelo de dados atual (`docs/02-arquitetura/modelo-de-dados.md`) declara
`member` com `household_id NOT NULL` e `channel_identity` com
`UNIQUE (channel, external_id)`. Isso amarra a identidade de canal — e por
consequência a pessoa — a um único household: o mesmo número de telefone não
pode existir em duas famílias.

Há casos reais que essa restrição não cobre: pais separados, cada um com sua
própria casa mas conversando com o mesmo filho; filho adulto administrando a
casa dos pais além da própria; república dividindo despesas à parte da família
de origem. Nesses casos a pessoa é uma só, mas o household de destino da
mensagem muda conforme o contexto da conversa.

Mudar esse modelo depois de dado financeiro em produção é migration cara —
mover histórico de `transaction`, `list_item` e `task` de um `member`
duplicado para um `member` único, sem perder rastreabilidade de "quem lançou".
Decidir agora, antes da Etapa 1, custa uma tabela de junção a mais. Decidir
depois custa reescrever `channel_identity` e todo o histórico que referencia
`member_id` sob dado real de cliente pagante.

## Decisão

`member` deixa de ter `household_id`. A pessoa é uma entidade independente de
família.

```text
member
  id, name, created_at

household_membership
  id, household_id, member_id, role (OWNER|MEMBER), created_at
  UNIQUE (household_id, member_id)

channel_identity
  id, member_id,
  channel (TELEGRAM|WHATSAPP),
  external_id,
  active_household_id,     -- household de destino das mensagens desta conversa
  verified_at
  UNIQUE (channel, external_id)
```

`UNIQUE (channel, external_id)` continua existindo, mas agora aponta para a
pessoa (via `member_id`), não para a família — o mesmo telefone segue
identificando uma única pessoa, e uma pessoa pode ter `household_membership`
em quantas famílias fizer sentido.

`active_household_id` é o household para o qual as mensagens daquela conversa
são resolvidas. Troca por comando explícito no chat (ex.: "usar casa dos
pais"), persistida até a próxima troca — não é perguntada a cada mensagem.
Quando `active_household_id` referencia um household onde a pessoa tem mais
de um `household_membership` possível, todo recibo nomeia explicitamente qual
household recebeu o lançamento, para que o erro de contexto seja visível
mesmo quando o usuário não pergunta.

Isso é uma exceção deliberada, não uma violação, à regra geral da ADR-0003
("`household_id` obrigatório em toda tabela de dado de usuário"): `member` é
identidade de pessoa, não dado de household — o limite de isolamento
multi-tenant continua vivendo em `household_membership` e em toda tabela de
domínio abaixo dela, que mantêm `household_id` e RLS normalmente.

## Alternativas consideradas

### A. `member` duplicado por household (um registro por família)
A mesma pessoa física vira duas linhas de `member` — uma por household em que
participa. Descartada: duplica identidade em vez de modelar o vínculo. Quebra
"quem lançou" de forma unificada (a mesma pessoa aparece como dois autores
diferentes em relatórios cross-household que um dia possam existir), e não
resolve o problema real — `channel_identity` ainda precisaria escolher qual
`member` duplicado vincular a cada mensagem, reintroduzindo a mesma
ambiguidade que a duplicação tentou evitar.

### B. Sem `active_household_id` persistido — perguntar o household a cada mensagem ambígua
Mantém `channel_identity` unívoco por pessoa, mas sem contexto persistido:
toda mensagem de alguém com mais de um household dispara uma pergunta
("mercado 50 — em qual casa?"). Descartada: contraria a política de
confirmação do projeto (execução de alta confiança deve gerar recibo curto,
não pergunta) e o caso de uso mais comum — a mesma pessoa manda várias
mensagens seguidas para o mesmo household, não uma por vez. O atrito recorrente
mataria justamente o caso que a ADR existe para viabilizar.

## Consequências

### Positivas
- Cobre os casos reais de família dividida sem exigir que a pessoa gerencie
  múltiplos números de telefone ou contas de Telegram.
- `household_membership` extra é assinatura extra com custo de aquisição zero
  (ADR-0006 cobra por household), o que torna o caso de uso comercialmente
  positivo, não só uma concessão de UX.
- A mudança troca uma migration futura cara (dado financeiro em produção) por
  uma tabela de junção barata agora, antes de existir dado real.

### Negativas
- Lançamento no household errado é um risco novo que não existia com
  unicidade estrita: a pessoa esquece de trocar `active_household_id` e
  `mercado 50` cai na casa errada. Mitigado por recibo nomeando o household e
  por `desfazer` (regra 7 do CLAUDE.md), mas o risco é aceito, não eliminado.
- Resolução de contexto (`channel_identity` → `member` → household) deixa de
  ser um join direto e passa a depender de um campo mutável
  (`active_household_id`) mais o conjunto de `household_membership` — mais um
  lugar onde um bug de resolução de contexto pode silenciosamente enviar dado
  para o household errado.
- `docs/02-arquitetura/modelo-de-dados.md` fica desatualizado em relação a
  esta ADR até ser revisado — o schema documentado hoje ainda mostra `member`
  com `household_id` e `channel_identity` sem `active_household_id`.

## Gatilhos de revisão

Se, na prática, a maioria das pessoas nunca usar um segundo household, avaliar
se a complexidade de `active_household_id` vale a pena manter ou se um comando
de "confirmar household a cada N dias" reduziria o risco de lançamento errado
sem reintroduzir pergunta por mensagem.
