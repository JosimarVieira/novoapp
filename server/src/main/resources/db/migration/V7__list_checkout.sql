-- Etapa 3: o elo (elo-fechamento-de-compra.feature).
--
-- A tabela que a V3 deixou de fora de proposito -- "criar a tabela antes do
-- comportamento que a usa deixaria schema morto no banco". O comportamento
-- comeca agora, entao ela entra agora.
--
-- Tabela propria, e nao uma FK em transaction: e o que permite fechar a lista
-- parcialmente mais de uma vez (modelo-de-dados.md), que e cenario escrito
-- ("Segundo fechamento parcial na mesma lista").

CREATE TABLE list_checkout (
    id                     uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    household_id           uuid        NOT NULL REFERENCES household (id),
    shopping_list_id       uuid        NOT NULL REFERENCES shopping_list (id),
    -- O elo. NOT NULL: fechamento sem lancamento nao existe -- a ADR-0031 poe
    -- as duas escritas na mesma transacao, e o cenario de falha exige que
    -- nenhuma sobreviva sozinha.
    transaction_id         uuid        NOT NULL REFERENCES transaction (id),
    items_purchased_count  integer     NOT NULL CHECK (items_purchased_count > 0),
    performed_by_member_id uuid        NOT NULL REFERENCES member (id),
    performed_at           timestamptz NOT NULL DEFAULT now()
);

-- "Este lancamento veio de um fechamento?" e a pergunta que o desfazer faz
-- (ADR-0032), uma vez por desfazer. Unico, e nao so indice: um lancamento e de
-- no maximo um fechamento, e isso e invariante de estrutura -- se um dia duas
-- linhas apontarem para a mesma transaction, o desfazer nao saberia qual
-- reverter.
CREATE UNIQUE INDEX list_checkout_transaction ON list_checkout (transaction_id);

-- Os fechamentos de uma lista, na ordem em que aconteceram: e o que sustenta
-- "a lista registra dois fechamentos distintos".
CREATE INDEX list_checkout_by_list ON list_checkout (shopping_list_id, performed_at);

-- Qual fechamento devolveu este item a PENDING (ADR-0032: "o fechamento e a
-- unidade" -- volta o que aquele fechamento fechou, nao a lista inteira).
-- Nulavel: item comprado por marcarItemComprado, fora de um fechamento, nao tem
-- checkout nenhum -- e e justamente por isso que ele nao e desfazivel pelo chat,
-- limite declarado na ADR-0032.
ALTER TABLE list_item
    ADD COLUMN list_checkout_id uuid REFERENCES list_checkout (id);

CREATE INDEX list_item_by_checkout ON list_item (list_checkout_id);

ALTER TABLE list_checkout ENABLE ROW LEVEL SECURITY;
ALTER TABLE list_checkout FORCE  ROW LEVEL SECURITY;
CREATE POLICY list_checkout_tenant ON list_checkout TO novoapp_app
    USING (household_id = app_current_household())
    WITH CHECK (household_id = app_current_household());

GRANT SELECT, INSERT, UPDATE ON list_checkout TO novoapp_app;
