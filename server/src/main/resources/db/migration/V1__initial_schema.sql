-- Etapa 1 do ROADMAP: identidade, ingestao de mensagem e financas minimas.
--
-- Regra que atravessa tudo (ADR-0003): household_id NOT NULL em toda tabela de
-- dado de usuario, com isolamento aplicado por Row Level Security -- nunca por
-- filtro escrito a mao na query.
--
-- Duas excecoes deliberadas, ambas nomeadas em ADR:
--   * member          -- identidade de pessoa, nao dado de household (ADR-0007);
--   * inbound_message -- household_id nulo enquanto a identidade nao resolve
--                        (ADR-0003, unica excecao a obrigatoriedade da coluna).
--
-- E a excecao decidida na ADR-0022: as tabelas de identidade sao lidas ANTES de
-- existir tenant (o tenant e justamente o que a resolucao vai descobrir), entao
-- vivem sob um papel de banco pre-tenant proprio, nunca sob o papel de dominio.

-- ---------------------------------------------------------------------------
-- Papeis de banco (ADR-0003, ADR-0022)
-- ---------------------------------------------------------------------------
-- novoapp_app      -- papel de dominio: so enxerga o household do contexto atual.
-- novoapp_identity -- papel pre-tenant: resolucao de identidade e ingestao.
-- novoapp_runtime  -- unico papel com LOGIN. NOINHERIT de proposito: sem um
--                     SET ROLE explicito ele nao tem privilegio nenhum, entao
--                     esquecer a anotacao de tenancy quebra na hora, em vez de
--                     vazar dado em silencio.

DO $roles$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'novoapp_app') THEN
    CREATE ROLE novoapp_app NOLOGIN;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'novoapp_identity') THEN
    CREATE ROLE novoapp_identity NOLOGIN;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'novoapp_runtime') THEN
    CREATE ROLE novoapp_runtime LOGIN NOINHERIT PASSWORD '${runtimepwd}';
  END IF;
END
$roles$;

GRANT novoapp_app, novoapp_identity TO novoapp_runtime;
GRANT USAGE ON SCHEMA public TO novoapp_app, novoapp_identity;

-- Household do contexto da transacao atual. Nulo quando nenhum foi setado --
-- e o que faz toda policy de tenant negar por padrao (falha fechada).
CREATE OR REPLACE FUNCTION app_current_household() RETURNS uuid
LANGUAGE sql STABLE AS $fn$
  SELECT nullif(current_setting('app.household_id', true), '')::uuid
$fn$;

-- ---------------------------------------------------------------------------
-- Identidade (ADR-0007, ADR-0020, ADR-0021, ADR-0022)
-- ---------------------------------------------------------------------------

CREATE TABLE household (
    id         uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    name       text        NOT NULL,
    plan       text        NOT NULL DEFAULT 'FREE',
    created_at timestamptz NOT NULL DEFAULT now()
);

-- Sem household_id de proposito: member e identidade de pessoa, e a mesma
-- pessoa pode ter vinculo com varios households (ADR-0007). O limite de
-- isolamento vive em household_membership, nao aqui. Consequencia aceita: nao
-- ha RLS possivel nesta tabela -- por isso so o papel pre-tenant recebe grant
-- sobre ela, e o papel de dominio nunca a enxerga.
CREATE TABLE member (
    id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    name          text        NOT NULL,
    phone_number  text,                      -- E.164, capturado ao compartilhar contato (ADR-0020)
    email         text,                      -- ADR-0021, unico quando preenchido
    password_hash text,                      -- ADR-0021, bcrypt
    created_at    timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX member_email_key ON member (lower(email)) WHERE email IS NOT NULL;
CREATE INDEX member_phone_number_idx ON member (phone_number) WHERE phone_number IS NOT NULL;

CREATE TABLE household_membership (
    id                 uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    household_id       uuid        NOT NULL REFERENCES household (id),
    member_id          uuid        NOT NULL REFERENCES member (id),
    role               text        NOT NULL CHECK (role IN ('OWNER', 'MEMBER')),
    default_account_id uuid,                 -- conta preferida do membro neste household (ADR-0019)
    created_at         timestamptz NOT NULL DEFAULT now(),
    UNIQUE (household_id, member_id)
);
-- Listar os households da pessoa ao resolver contexto e ao trocar o ativo (ADR-0007)
CREATE INDEX household_membership_member_idx ON household_membership (member_id);

-- Ponto de entrada de todo o sistema: transforma "chegou uma mensagem" em
-- "fulano, no household ativo dele, disse". Nao tem household_id -- tem
-- active_household_id nullable, que e o destino das mensagens desta conversa.
CREATE TABLE channel_identity (
    id                  uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id           uuid        NOT NULL REFERENCES member (id),
    channel             text        NOT NULL CHECK (channel IN ('TELEGRAM', 'WHATSAPP', 'WEB')),
    external_id         text        NOT NULL,
    active_household_id uuid        REFERENCES household (id),
    verified_at         timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    UNIQUE (channel, external_id)
);

-- ADR-0020. status guarda so PENDING/ACCEPTED: EXPIRED e calculado sob demanda
-- a partir de expires_at, sem job de limpeza (mesmo padrao do ADR-0014).
CREATE TABLE household_invite (
    id                    uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    household_id          uuid        NOT NULL REFERENCES household (id),
    invited_by_member_id  uuid        NOT NULL REFERENCES member (id),
    phone_number          text        NOT NULL,
    token                 text        NOT NULL UNIQUE,
    status                text        NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED')),
    created_at            timestamptz NOT NULL DEFAULT now(),
    expires_at            timestamptz NOT NULL,
    accepted_at           timestamptz,
    accepted_by_member_id uuid        REFERENCES member (id)
);
CREATE INDEX household_invite_phone_idx ON household_invite (phone_number, status);

-- Decisao registrada em sdd-modulo-identity.md, nao em ADR: o onboarding e uma
-- arvore de decisao de mais de um passo (pergunta -> confirmacao -> nome da
-- familia), e quem esta no meio dela ainda nao tem channel_identity nem
-- household -- logo nao cabe em pending_action, que exige os dois NOT NULL.
CREATE TABLE onboarding_session (
    id           uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    channel      text        NOT NULL CHECK (channel IN ('TELEGRAM', 'WHATSAPP', 'WEB')),
    external_id  text        NOT NULL,
    state        text        NOT NULL,
    invite_token text,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    UNIQUE (channel, external_id)
);

-- ---------------------------------------------------------------------------
-- Ingestao (ADR-0005)
-- ---------------------------------------------------------------------------

CREATE TABLE inbound_message (
    id                  uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    household_id        uuid        REFERENCES household (id),   -- nulo ate a identidade resolver
    channel             text        NOT NULL CHECK (channel IN ('TELEGRAM', 'WHATSAPP', 'WEB')),
    provider_message_id text        NOT NULL,
    external_id_from    text        NOT NULL,
    raw_text            text,
    received_at         timestamptz NOT NULL DEFAULT now(),
    processed_at        timestamptz,
    status              text        NOT NULL CHECK (status IN ('RECEIVED', 'INTERPRETED', 'EXECUTED', 'FAILED', 'IGNORED')),
    intent_json         text,
    confidence          double precision,
    -- A chave da idempotencia (ADR-0005): reentrega do provedor viola isso e e
    -- descartada em silencio, em vez de virar despesa duplicada.
    UNIQUE (channel, provider_message_id)
);

-- ---------------------------------------------------------------------------
-- Financas (ADR-0010, ADR-0011, ADR-0013, ADR-0019)
-- ---------------------------------------------------------------------------

CREATE TABLE account (
    id                 uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    household_id       uuid        NOT NULL REFERENCES household (id),
    name               text        NOT NULL,
    type               text        NOT NULL CHECK (type IN ('WALLET', 'BANK', 'CARD')),
    closing_day        integer,                -- so CARD (ADR-0011)
    due_day            integer,                -- so CARD
    credit_limit_cents bigint,                 -- so CARD
    archived_at        timestamptz,
    created_at         timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX account_household_idx ON account (household_id);

ALTER TABLE household_membership
    ADD CONSTRAINT household_membership_default_account_fk
    FOREIGN KEY (default_account_id) REFERENCES account (id);

-- Household novo comeca sem nenhuma categoria (ADR-0013): a primeira mencao a
-- uma categoria inexistente dispara o fluxo de criacao, que e Etapa 2.
CREATE TABLE category (
    id                   uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    household_id         uuid        NOT NULL REFERENCES household (id),
    parent_category_id   uuid        REFERENCES category (id),   -- so 1 nivel, validado em finance (ADR-0016)
    name                 text        NOT NULL,
    kind                 text        NOT NULL CHECK (kind IN ('EXPENSE', 'INCOME')),
    created_by_member_id uuid        REFERENCES member (id),
    archived_at          timestamptz,
    created_at           timestamptz NOT NULL DEFAULT now()
);
-- NULLS NOT DISTINCT: sem isso duas categorias raiz de mesmo nome passariam,
-- porque parent_category_id nulo nunca colide consigo mesmo.
CREATE UNIQUE INDEX category_unique_sibling_name
    ON category (household_id, parent_category_id, lower(name), kind) NULLS NOT DISTINCT;
CREATE INDEX category_parent_idx ON category (household_id, parent_category_id);

CREATE TABLE transaction (
    id                    uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    household_id          uuid        NOT NULL REFERENCES household (id),
    account_id            uuid        NOT NULL REFERENCES account (id),
    category_id           uuid        NOT NULL REFERENCES category (id),
    kind                  text        NOT NULL CHECK (kind IN ('EXPENSE', 'INCOME')),
    amount_cents          bigint      NOT NULL CHECK (amount_cents > 0),  -- nunca ponto flutuante para dinheiro
    occurred_on           date        NOT NULL,
    description           text,
    created_by_member_id  uuid        NOT NULL REFERENCES member (id),
    source                text        NOT NULL CHECK (source IN ('CHAT', 'WEB')),
    -- Rastreia todo lancamento ate a mensagem que o originou: e o que torna a
    -- Etapa 5 mensuravel. A FK existe so no banco, de proposito -- finance nao
    -- pode importar channel (regra de dependencia do sdd-visao-geral.md).
    source_message_id     uuid        REFERENCES inbound_message (id),
    -- Estorno em vez de delete: historico auditavel importa em financas
    -- compartilhadas. Preenchido so na Etapa 2 (ADR-0012).
    reversed_at           timestamptz,
    reversal_of_id        uuid        REFERENCES transaction (id),
    reversed_by_member_id uuid        REFERENCES member (id),
    -- Colunas ja previstas no modelo de dados e sem uso nesta etapa.
    invoice_id            uuid,                   -- preenchido quando account.type = CARD (ADR-0011)
    installment_number    integer,
    installment_count     integer,
    installment_group_id  uuid,
    split_group_id        uuid,
    created_at            timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX transaction_household_occurred_idx ON transaction (household_id, occurred_on DESC);

-- ---------------------------------------------------------------------------
-- Row Level Security
-- ---------------------------------------------------------------------------
-- FORCE em toda tabela: sem isso o dono da tabela ignora as policies, e em
-- desenvolvimento o dono costuma ser justamente quem roda tudo.

-- Tabelas de dominio: so o papel de dominio, so o household do contexto.
ALTER TABLE account     ENABLE ROW LEVEL SECURITY;
ALTER TABLE account     FORCE  ROW LEVEL SECURITY;
CREATE POLICY account_tenant ON account TO novoapp_app
    USING (household_id = app_current_household())
    WITH CHECK (household_id = app_current_household());

ALTER TABLE category    ENABLE ROW LEVEL SECURITY;
ALTER TABLE category    FORCE  ROW LEVEL SECURITY;
CREATE POLICY category_tenant ON category TO novoapp_app
    USING (household_id = app_current_household())
    WITH CHECK (household_id = app_current_household());

ALTER TABLE transaction ENABLE ROW LEVEL SECURITY;
ALTER TABLE transaction FORCE  ROW LEVEL SECURITY;
CREATE POLICY transaction_tenant ON transaction TO novoapp_app
    USING (household_id = app_current_household())
    WITH CHECK (household_id = app_current_household());

-- household e household_membership sao lidos pelos dois papeis, com regra
-- diferente em cada um: o de dominio ve so o tenant atual; o de identidade ve
-- tudo, porque ainda esta descobrindo qual e o tenant (ADR-0022).
ALTER TABLE household ENABLE ROW LEVEL SECURITY;
ALTER TABLE household FORCE  ROW LEVEL SECURITY;
CREATE POLICY household_tenant ON household TO novoapp_app
    USING (id = app_current_household())
    WITH CHECK (id = app_current_household());
CREATE POLICY household_pre_tenant ON household TO novoapp_identity
    USING (true) WITH CHECK (true);

ALTER TABLE household_membership ENABLE ROW LEVEL SECURITY;
ALTER TABLE household_membership FORCE  ROW LEVEL SECURITY;
CREATE POLICY household_membership_tenant ON household_membership TO novoapp_app
    USING (household_id = app_current_household())
    WITH CHECK (household_id = app_current_household());
CREATE POLICY household_membership_pre_tenant ON household_membership TO novoapp_identity
    USING (true) WITH CHECK (true);

-- Tabelas exclusivamente pre-tenant: o papel de dominio nao recebe grant
-- nenhum sobre elas, entao nao ha policy de dominio a escrever.
ALTER TABLE channel_identity ENABLE ROW LEVEL SECURITY;
ALTER TABLE channel_identity FORCE  ROW LEVEL SECURITY;
CREATE POLICY channel_identity_pre_tenant ON channel_identity TO novoapp_identity
    USING (true) WITH CHECK (true);

ALTER TABLE household_invite ENABLE ROW LEVEL SECURITY;
ALTER TABLE household_invite FORCE  ROW LEVEL SECURITY;
CREATE POLICY household_invite_pre_tenant ON household_invite TO novoapp_identity
    USING (true) WITH CHECK (true);

ALTER TABLE onboarding_session ENABLE ROW LEVEL SECURITY;
ALTER TABLE onboarding_session FORCE  ROW LEVEL SECURITY;
CREATE POLICY onboarding_session_pre_tenant ON onboarding_session TO novoapp_identity
    USING (true) WITH CHECK (true);

-- A policy que a ADR-0003 escreveu literalmente: a linha e visivel enquanto a
-- identidade nao resolveu (household_id nulo) ou, depois que resolveu, para o
-- household do contexto. Nenhum papel de dominio recebe grant aqui.
ALTER TABLE inbound_message ENABLE ROW LEVEL SECURITY;
ALTER TABLE inbound_message FORCE  ROW LEVEL SECURITY;
CREATE POLICY inbound_message_ingestion ON inbound_message TO novoapp_identity
    USING (household_id = app_current_household() OR household_id IS NULL)
    WITH CHECK (household_id = app_current_household() OR household_id IS NULL);

-- ---------------------------------------------------------------------------
-- Privilegios
-- ---------------------------------------------------------------------------
GRANT SELECT, INSERT, UPDATE ON account, category, transaction TO novoapp_app;
GRANT SELECT ON household, household_membership TO novoapp_app;

GRANT SELECT, INSERT, UPDATE ON household, member, household_membership,
      channel_identity, household_invite, onboarding_session, inbound_message
      TO novoapp_identity;
GRANT DELETE ON onboarding_session TO novoapp_identity;
