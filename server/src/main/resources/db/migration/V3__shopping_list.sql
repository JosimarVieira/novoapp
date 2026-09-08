-- Etapa 2a: lista de compras compartilhada (mercado-lista-de-compras.feature).
--
-- Schema como modelo-de-dados.md o descreve. list_checkout -- o elo -- NAO
-- entra aqui: e Etapa 3, e criar a tabela antes do comportamento que a usa
-- deixaria schema morto no banco.

CREATE TABLE shopping_list (
    id           uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    household_id uuid        NOT NULL REFERENCES household (id),
    name         text        NOT NULL,
    status       text        NOT NULL CHECK (status IN ('ACTIVE', 'CLOSED')),
    opened_at    timestamptz NOT NULL DEFAULT now(),
    closed_at    timestamptz
);
-- "Um household tem no maximo uma lista ativa por vez" (glossario). E invariante
-- de estrutura, entao vive no banco, e nao so na disciplina do servico -- mesmo
-- argumento do indice de irmaos de category.
CREATE UNIQUE INDEX shopping_list_single_active
    ON shopping_list (household_id) WHERE status = 'ACTIVE';

CREATE TABLE list_item (
    id                      uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    household_id            uuid          NOT NULL REFERENCES household (id),
    shopping_list_id        uuid          NOT NULL REFERENCES shopping_list (id),
    name                    text          NOT NULL,
    -- Fracionario de proposito: "meio quilo de queijo" e pedido tao comum
    -- quanto "2 kg de arroz". Nao e dinheiro, entao numeric aqui nao viola a
    -- regra de amount_cents inteiro.
    quantity                numeric(12,3),
    unit                    text,
    status                  text          NOT NULL CHECK (status IN ('PENDING', 'PURCHASED', 'REMOVED')),
    requested_by_member_id  uuid          NOT NULL REFERENCES member (id),
    purchased_by_member_id  uuid          REFERENCES member (id),
    purchased_at            timestamptz,
    -- Rastreia o item ate a mensagem que o pediu, igual a transaction: e o que
    -- torna a Etapa 5 mensuravel tambem no dominio de mercado.
    source_message_id       uuid          REFERENCES inbound_message (id),
    created_at              timestamptz   NOT NULL DEFAULT now()
);
-- "O que esta faltando" (indice previsto em modelo-de-dados.md).
CREATE INDEX list_item_status_idx ON list_item (shopping_list_id, status);
-- Duas pessoas avisando que acabou o arroz nao viram dois arrozes pendentes. So
-- vale enquanto PENDING: comprado o arroz de hoje, ele pode faltar de novo
-- amanha.
CREATE UNIQUE INDEX list_item_unique_pending_name
    ON list_item (shopping_list_id, lower(name)) WHERE status = 'PENDING';

ALTER TABLE shopping_list ENABLE ROW LEVEL SECURITY;
ALTER TABLE shopping_list FORCE  ROW LEVEL SECURITY;
CREATE POLICY shopping_list_tenant ON shopping_list TO novoapp_app
    USING (household_id = app_current_household())
    WITH CHECK (household_id = app_current_household());

ALTER TABLE list_item ENABLE ROW LEVEL SECURITY;
ALTER TABLE list_item FORCE  ROW LEVEL SECURITY;
CREATE POLICY list_item_tenant ON list_item TO novoapp_app
    USING (household_id = app_current_household())
    WITH CHECK (household_id = app_current_household());

GRANT SELECT, INSERT, UPDATE ON shopping_list, list_item TO novoapp_app;
