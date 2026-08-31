# Modelo de dados

Escopo: Etapas 1 a 3. Tarefas está esboçado; agenda/recorrência fica fora até
a Etapa 5 provar que o domínio é usado.

Regra que atravessa tudo: **`household_id NOT NULL` em toda tabela abaixo de
`household`**, com RLS ativa (ADR-0003).

## Identidade

```
household
  id, name, plan, created_at

member
  id, household_id, name, role (OWNER|MEMBER), created_at

channel_identity
  id, household_id, member_id,
  channel (TELEGRAM|WHATSAPP),
  external_id,            -- telegram user id ou telefone E.164
  verified_at
  UNIQUE (channel, external_id)
```

`channel_identity` é o ponto de entrada de todo o sistema: é o que transforma
"chegou uma mensagem" em "fulano do household X disse".

`UNIQUE (channel, external_id)` sem `household_id` é proposital: o mesmo
telefone não pode pertencer a duas famílias. **Isso proíbe o caso real de uma
pessoa em dois households** (ex.: pais separados). Ver DECISOES-ABERTAS.

## Ingestão

```
inbound_message
  id, household_id (nullable), channel,
  provider_message_id, external_id_from,
  raw_text, received_at, processed_at,
  status (RECEIVED|INTERPRETED|EXECUTED|FAILED|IGNORED),
  intent_json, confidence
  UNIQUE (channel, provider_message_id)     -- ADR-0005
```

`household_id` é nulo quando a identidade não é reconhecida (mensagem de
número desconhecido). É a única exceção à regra de obrigatoriedade, e por isso
esta tabela precisa de tratamento explícito na política de RLS.

```
pending_action
  id, household_id, member_id, channel_identity_id,
  intent_json, question_asked, options_json,
  expires_at, resolved_at, resolution (CONFIRMED|REJECTED|EXPIRED)
```

TTL sugerido de 10 minutos, a calibrar na Etapa 5.

## Finanças

```
account
  id, household_id, name, type (WALLET|BANK|CARD), archived_at

category
  id, household_id, name, kind (EXPENSE|INCOME),
  created_by_member_id, archived_at
  UNIQUE (household_id, lower(name), kind)

transaction
  id, household_id, account_id, category_id,
  kind (EXPENSE|INCOME), amount_cents, occurred_on,
  description, created_by_member_id,
  source (CHAT|WEB), source_message_id,
  reversed_at, reversal_of_id
```

Três decisões embutidas:

- **`amount_cents` inteiro.** Nunca ponto flutuante para dinheiro.
- **`source` e `source_message_id`** permitem rastrear todo lançamento até a
  mensagem que o originou. É o que torna a Etapa 5 mensurável.
- **Estorno em vez de delete.** `desfazer` marca `reversed_at`, não apaga.
  Histórico auditável importa em finanças compartilhadas — e quando duas
  pessoas mexem no mesmo dado, "sumiu" é pior que "foi estornado por fulano".

## Mercado

```
shopping_list
  id, household_id, name, status (ACTIVE|CLOSED),
  opened_at, closed_at

list_item
  id, household_id, shopping_list_id, name,
  quantity, unit,
  status (PENDING|PURCHASED|REMOVED),
  requested_by_member_id, purchased_by_member_id, purchased_at,
  source_message_id

list_checkout                     -- o elo
  id, household_id, shopping_list_id,
  transaction_id,
  items_purchased_count,
  performed_by_member_id, performed_at
```

`list_checkout` materializa o diferencial do produto: liga o fechamento da
lista ao lançamento financeiro. Ter tabela própria (em vez de só uma FK em
`transaction`) permite fechar parcialmente a lista mais de uma vez.

## Tarefas (esboço)

```
task
  id, household_id, title, notes,
  assignee_member_id (nullable), due_at (nullable),
  status (OPEN|DONE|CANCELLED),
  created_by_member_id, completed_by_member_id, completed_at,
  source_message_id
```

Recorrência deliberadamente ausente. Recorrência mal modelada é dívida cara;
só entra depois que a Etapa 5 mostrar que tarefas são usadas de verdade.

## Índices que já se sabe necessários

- `inbound_message (channel, provider_message_id)` — unicidade, ADR-0005
- `channel_identity (channel, external_id)` — resolução em toda mensagem
- `transaction (household_id, occurred_on desc)` — consulta de extrato
- `list_item (shopping_list_id, status)` — "o que está faltando"
- `pending_action (channel_identity_id, expires_at)` — resolução de confirmação
