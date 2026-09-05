---
tipo: adr
numero: 22
status: aceita
data: 2026-09-05
modulos:
  - banco
  - identity
  - channel
depende_de:
  - ADR-0003
  - ADR-0007
  - ADR-0020
supera: []
superada_por:
corrigida_em:
---

# ADR-0022 — Papel de banco pré-tenant para a resolução de identidade

- **Impacta**: banco (papéis e policies de RLS), `identity`, `channel`, o pacote
  técnico `common/tenancy`; estende [ADR-0003](0003-isolamento-multi-tenant-por-household.md) e desloca uma frase dela;
  depende de [ADR-0007](0007-pessoa-em-multiplos-households.md) e [ADR-0020](0020-convite-de-membro.md)
- **Termos**: [Household, Identidade de canal, Vínculo com o household](../00-produto/glossario.md#núcleo)

## Contexto

A [ADR-0003](0003-isolamento-multi-tenant-por-household.md) decidiu RLS por `SET LOCAL app.household_id` e abriu **uma**
exceção nomeada, para `inbound_message`, fechando com: *"Nenhuma outra tabela
tem exceção equivalente."* O mesmo parágrafo, porém, diz que *"a resolução de
identidade em `channel`/`identity` — o único código que precisa enxergar essas
linhas antes de saber o household — roda sob uma policy própria e restrita para
essa tabela, aplicada somente ao papel de banco usado por esse caminho"*.

Escrever a Etapa 1 mostrou que as duas frases não podem valer ao mesmo tempo. A
resolução de contexto inteira acontece **antes de existir tenant** — descobrir o
tenant é o trabalho dela:

- `channel_identity` é buscada por `(channel, external_id)` sem household nenhum
  no contexto, e **não tem coluna `household_id`**: tem `active_household_id`
  nulável, exatamente a mesma forma de `inbound_message`;
- `household_membership` é lida por `member_id` para saber *quais* households a
  pessoa tem — é a pergunta que decide se há ambiguidade ([ADR-0007](0007-pessoa-em-multiplos-households.md));
- `household_invite` é buscada por token ou por telefone antes de se saber a que
  família o convite pertence ([ADR-0020](0020-convite-de-membro.md));
- `household` é lida e escrita no onboarding, quando o household ainda está
  sendo criado.

Com uma policy `household_id = current_setting(...)` e nada setado,
`app_current_household()` é nulo e toda comparação falha: a resolução de
identidade retornaria vazio sempre — e retorno vazio silencioso é justamente a
consequência negativa que a [ADR-0003](0003-isolamento-multi-tenant-por-household.md) registra como o modo de falha mais
difícil de diagnosticar.

## Decisão

Existem dois papéis de banco, e a frase da [ADR-0003](0003-isolamento-multi-tenant-por-household.md) que dizia "nenhuma outra
tabela tem exceção equivalente" fica deslocada por esta ADR: a exceção não é por
tabela, é por **caminho de execução**, e cobre as tabelas de identidade além de
`inbound_message`.

| Papel | Enxerga | Usado por |
|---|---|---|
| `novoapp_app` | `account`, `category`, `transaction` filtradas por `household_id = app_current_household()`; `household` e `household_membership` do tenant atual. Nenhum privilégio sobre as tabelas pré-tenant | `finance`, `shopping`, `tasks`, `nlu`, `conversation` |
| `novoapp_identity` | `channel_identity`, `household_invite`, `onboarding_session`, `member`, `household`, `household_membership` sem filtro de tenant; `inbound_message` com a policy literal da [ADR-0003](0003-isolamento-multi-tenant-por-household.md) (`household_id = tenant() OR household_id IS NULL`) | só `channel` e `identity` |

Um terceiro papel, `novoapp_runtime`, é o único com `LOGIN`, e é **`NOINHERIT`**:
sozinho não tem privilégio nenhum. Cada transação escolhe explicitamente sob qual
dos dois papéis roda, por `SET LOCAL ROLE`, no mesmo ponto de entrada onde o
`SET LOCAL app.household_id` já é aplicado.

Consequência direta e desejada do `NOINHERIT`: esquecer o escopo de tenancy
**não vaza dado, estoura permissão negada**. O modo de falha deixa de ser
"voltou vazio" e passa a ser "quebrou na hora".

O interceptor que aplica isso vive num pacote técnico próprio,
`common/tenancy`, fora dos sete módulos de domínio — `identity` descobre *qual* é
o household, aplicar isso na conexão é infraestrutura de que todo módulo
depende. (O `sdd-modulo-identity.md` registrava esse dono como "não decidido";
fica decidido aqui.)

## Alternativas consideradas

### A. RLS em `channel_identity` por `active_household_id`
Descartada por ser impossível, não por ser ruim: a resolução roda sem tenant
setado, então a policy nunca casaria — nem para a linha certa. Seria trocar
vazamento por indisponibilidade total do produto.

### B. Nenhuma RLS nas tabelas de identidade, um papel de aplicação só
Descartada por abrir mais do que esta ADR abre: com um papel único, `finance`,
`shopping` e `tasks` passariam a enxergar `household_membership` e
`household_invite` de todas as famílias. A superfície cresceria justamente nos
módulos que mais escrevem dado de usuário, para resolver um problema que só
existe em dois módulos.

### C. Um datasource por papel, em vez de `SET LOCAL ROLE`
Descartada: dois datasources são duas conexões, e portanto duas transações. O
onboarding cria o `household` sob o papel pré-tenant e, no mesmo instante,
`finance` cria a conta `WALLET` implícita sob o papel de domínio ([ADR-0011](0011-cartao-de-credito-e-fatura.md),
evento `HouseholdCreated`). Com transações separadas passaria a existir household
sem conta — estado que `AccountResolver` não sabe tratar. `SET LOCAL ROLE` troca
de papel dentro da mesma transação e mantém a atomicidade.

## Consequências

### Positivas
- A resolução de identidade funciona sem que nenhum módulo de domínio ganhe
  acesso a dado de outra família.
- `NOINHERIT` transforma o erro mais provável (esquecer a anotação de escopo) de
  vazamento silencioso em falha imediata e barulhenta.
- Os dois papéis são verificáveis: o teste de vazamento afirma explicitamente que
  o papel de domínio **não consegue** ler `channel_identity`.

### Negativas
- Mais uma peça de RLS para acertar, e a mais sutil delas: um `GRANT` a mais no
  papel errado desfaz o isolamento sem quebrar teste nenhum que já exista. É a
  mesma crítica que a [ADR-0003](0003-isolamento-multi-tenant-por-household.md) já fazia à própria exceção de
  `inbound_message`, agora sobre sete tabelas em vez de uma.
- `member` continua sem RLS possível ([ADR-0007](0007-pessoa-em-multiplos-households.md): não tem `household_id`). O que
  esta ADR faz é reduzir o alcance disso — só o papel pré-tenant recebe
  privilégio sobre `member` —, não eliminar. Quem lê `member` lê todo mundo.
- Papéis no Postgres são do cluster, não do banco: dois ambientes no mesmo
  cluster compartilham `novoapp_app`/`novoapp_identity`. Aceitável na validação,
  precisa de revisão antes de multi-ambiente.
- Rotacionar a senha de `novoapp_runtime` não é migration: a V1 cria o papel com
  a senha vinda de placeholder, e migration com checksum fixo não roda de novo.
  Rotação exige `ALTER ROLE` manual.

## Gatilhos de revisão

- Se algum módulo de domínio precisar legitimamente ler `channel_identity` ou
  `household_invite`, isso é sinal de fronteira errada — revisar o
  `sdd-visao-geral.md` antes de conceder o `GRANT`.
- Quando o WhatsApp entrar ([ADR-0002](0002-telegram-primeiro-whatsapp-depois.md), Etapa 7), nada aqui muda: o adaptador
  novo usa o mesmo caminho pré-tenant.
- Antes de rodar mais de um ambiente no mesmo cluster Postgres, decidir se os
  papéis passam a ter prefixo por ambiente.
