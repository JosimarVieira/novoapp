-- Etapa 3: remover item da lista (ADR-0039, decisao aberta #25).
--
-- `status = 'REMOVED'` existe desde a V3 e nunca foi escrito: ate 2026-09-19
-- nao havia ferramenta de remover, e "remover chocolate" voltava do modelo como
-- marcarItemComprado com confianca 0,9 -- o item pedido para sair da lista
-- ficava com o status que a Etapa 3 transforma em despesa.
--
-- A remocao marca, nao apaga, pelo mesmo motivo que o estorno nao apaga o
-- lancamento: em lista compartilhada "sumiu" e pior que "foi removido"
-- (modelo-de-dados.md). Estas duas colunas sao o "por quem" e o "quando" que o
-- status sozinho nao guarda -- a ADR-0039 decide que ninguem e avisado pelo
-- chat, e que a informacao aparece na tela da Etapa 4. Sem elas, a tela nao
-- teria o que mostrar.

ALTER TABLE list_item
    ADD COLUMN removed_by_member_id uuid REFERENCES member (id),
    ADD COLUMN removed_at           timestamptz;
