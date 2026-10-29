-- ADR-0040: o Carrinho (glossario) -- os itens que um membro separou no proprio
-- fio durante a compra, tocando no botao de carrinho da lista.
--
-- Estado do fio, como pending_action: chaveado por membro dentro do household,
-- e nao por mensagem nem por household. Nao e pending_action (ADR-0040,
-- alternativa C): nao e pergunta, nao e superado por mensagem nova (ADR-0029)
-- e nao aparece na central de pendencias (ADR-0018). Por isso tabela propria.
--
-- NAO guarda status. O status do item e de list_item; o carrinho so diz que
-- alguem o separou. Toda leitura cruza com o status atual, e item que deixou de
-- estar PENDING simplesmente nao aparece -- ninguem precisa limpa-lo quando outra
-- pessoa o fecha ou remove.
--
-- NAO tem coluna de expiracao. O prazo e a config novoapp.conversation.cart.ttl
-- (decisao aberta #28), avaliada na leitura e sem job -- o mesmo raciocinio da
-- ADR-0018 para a pendencia. added_at e tudo de que o prazo precisa.

CREATE TABLE chat_cart_item (
    household_id uuid        NOT NULL REFERENCES household (id),
    member_id    uuid        NOT NULL REFERENCES member (id),
    -- ON DELETE CASCADE: o produto nunca apaga item da lista (marca REMOVED),
    -- mas scripts operacionais de limpeza apagam, e um carrinho apontando para
    -- item que nao existe mais nao tem o que dizer a ninguem.
    list_item_id uuid        NOT NULL REFERENCES list_item (id) ON DELETE CASCADE,
    added_at     timestamptz NOT NULL DEFAULT now(),
    -- A chave e o que torna carrinho+ idempotente: tocar duas vezes no mesmo
    -- botao e INSERT ... ON CONFLICT, nunca uma segunda linha.
    PRIMARY KEY (household_id, member_id, list_item_id)
);

ALTER TABLE chat_cart_item ENABLE ROW LEVEL SECURITY;
ALTER TABLE chat_cart_item FORCE  ROW LEVEL SECURITY;
CREATE POLICY chat_cart_item_tenant ON chat_cart_item TO novoapp_app
    USING (household_id = app_current_household())
    WITH CHECK (household_id = app_current_household());

-- DELETE, diferente das outras tabelas de dominio: o carrinho e estado efemero
-- do fio, e tirar um item dele nao e fato que alguem precise auditar depois --
-- o que importa (o fechamento, a remocao) fica em list_item e list_checkout.
GRANT SELECT, INSERT, UPDATE, DELETE ON chat_cart_item TO novoapp_app;
