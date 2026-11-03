-- ADR-0041: trocar a categoria pelo recibo -- a primeira correcao de campo de
-- lancamento que o produto faz.
--
-- A tabela e a da ADR-0012, decidida em 2026-09-04 e nunca criada: nenhuma
-- correcao existia ate aqui, e criar a tabela antes do comportamento que a usa
-- deixaria schema morto no banco (o mesmo argumento que segurou list_checkout ate
-- a V7). Uma linha por correcao, com quem corrigiu, quando, e de qual valor para
-- qual. Nao cobre criacao (transaction.created_by_member_id) nem estorno
-- (transaction.reversed_by_member_id).
--
-- changed_fields_json e text, como intent_json e options_json de pending_action:
-- {"campo": [de, para]} por campo alterado. Sem schema por campo no banco, de
-- proposito -- a ADR-0012 registra o custo (o banco nao recusa um diff incoerente)
-- e poe a protecao no servico de finance. Hoje o unico campo e category_id.
--
-- Rollback: DROP TABLE transaction_edit. Nenhuma tabela aponta para ela, e
-- transaction nao mudou -- a categoria corrigida continua so em
-- transaction.category_id.

CREATE TABLE transaction_edit (
    id                  uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    household_id        uuid        NOT NULL REFERENCES household (id),
    transaction_id      uuid        NOT NULL REFERENCES transaction (id),
    edited_by_member_id uuid        NOT NULL REFERENCES member (id),
    edited_at           timestamptz NOT NULL DEFAULT now(),
    changed_fields_json text        NOT NULL
);

-- "Mostrar o historico de correcao de um lancamento" (indice previsto em
-- modelo-de-dados.md).
CREATE INDEX transaction_edit_by_transaction ON transaction_edit (transaction_id, edited_at);

ALTER TABLE transaction_edit ENABLE ROW LEVEL SECURITY;
ALTER TABLE transaction_edit FORCE  ROW LEVEL SECURITY;
CREATE POLICY transaction_edit_tenant ON transaction_edit TO novoapp_app
    USING (household_id = app_current_household())
    WITH CHECK (household_id = app_current_household());

-- So SELECT e INSERT, diferente das outras tabelas de dominio: o historico e
-- append-only por decisao da ADR-0012. Uma correcao errada se corrige com outra
-- linha, e nunca reescrevendo ou apagando a que ja existe -- e o papel de
-- runtime nao tem como fazer isso nem por engano.
GRANT SELECT, INSERT ON transaction_edit TO novoapp_app;
