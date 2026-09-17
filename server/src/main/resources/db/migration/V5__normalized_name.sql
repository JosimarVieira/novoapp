-- ADR-0030: nome de categoria e de item de lista passam a ser comparados pela
-- forma normalizada -- minuscula e sem acento -- e nao mais so por lower().
--
-- O furo que isto fecha: "acabou cafe" com "Café" ja pendente inseria um SEGUNDO
-- item, sem pergunta e sem aviso, porque o indice unico que sustenta "item
-- repetido nao duplica" tambem era lower(). O mesmo valia para categoria-pai na
-- correcao livre: "dentro de alimentacao" criava uma raiz homonima ao lado de
-- "Alimentação".
--
-- Coluna persistida, e nao unaccent() no indice: unaccent e STABLE e nao
-- IMMUTABLE, entao o Postgres a recusa em indice -- sairia wrapper IMMUTABLE
-- mais extensao. A coluna dispensa a extensao e deixa a normalizacao num lugar
-- so do lado Java (common/text/Normalization), que e quem grava.

ALTER TABLE category  ADD COLUMN name_normalized text;
ALTER TABLE list_item ADD COLUMN name_normalized text;

-- Backfill. translate() cobre o conjunto latino-1 que o portugues usa, que e o
-- mesmo efeito do NFD + descarte de marcas do lado Java para este alfabeto.
-- Vale so aqui, uma vez: daqui pra frente quem grava a coluna e a aplicacao.
--
-- O regexp_replace nao e enfeite: Normalization.of colapsa espaco interno, e sem
-- ele o backfill produziria "pet  shop" onde a aplicacao produz "pet shop" --
-- duas implementacoes divergentes da mesma coisa, que e exatamente o risco que a
-- ADR-0030 existe para eliminar. Uma consulta feita pela aplicacao nunca acharia
-- a linha backfilada.
UPDATE category SET name_normalized = lower(regexp_replace(translate(trim(name),
    'ÁÀÂÃÄáàâãäÉÈÊËéèêëÍÌÎÏíìîïÓÒÔÕÖóòôõöÚÙÛÜúùûüÇçÑñ',
    'AAAAAaaaaaEEEEeeeeIIIIiiiiOOOOOoooooUUUUuuuuCcNn'), '\s+', ' ', 'g'));

UPDATE list_item SET name_normalized = lower(regexp_replace(translate(trim(name),
    'ÁÀÂÃÄáàâãäÉÈÊËéèêëÍÌÎÏíìîïÓÒÔÕÖóòôõöÚÙÛÜúùûüÇçÑñ',
    'AAAAAaaaaaEEEEeeeeIIIIiiiiOOOOOoooooUUUUuuuuCcNn'), '\s+', ' ', 'g'));

ALTER TABLE category  ALTER COLUMN name_normalized SET NOT NULL;
ALTER TABLE list_item ALTER COLUMN name_normalized SET NOT NULL;

-- O indice novo e mais estrito que o antigo: linhas que so conviviam por causa
-- do acento passam a colidir. Se o banco ja tiver alguma, a criacao do indice
-- falharia com "could not create unique index" e a mensagem nao diria qual
-- linha. Falhar aqui, dizendo o que fazer, e a diferenca entre cinco minutos e
-- uma tarde.
DO $duplicates$
DECLARE
    offending text;
BEGIN
    -- Sem filtro de archived_at, de proposito: o indice unico de category nao
    -- tem clausula WHERE -- nao tinha na V1 e nao tem aqui --, entao arquivar
    -- uma categoria nao libera o nome dela. Filtrar aqui faria esta checagem
    -- passar e o CREATE UNIQUE INDEX falhar logo abaixo, com a mensagem
    -- justamente que ela existe para evitar. A checagem tem de enxergar o mesmo
    -- conjunto que o indice.
    SELECT string_agg(format('category: household=%s pai=%s nome=%s', household_id,
                             coalesce(parent_category_id::text, '-'), name_normalized), E'\n')
      INTO offending
      FROM (SELECT household_id, parent_category_id, name_normalized, kind
              FROM category
             GROUP BY household_id, parent_category_id, name_normalized, kind
            HAVING count(*) > 1) AS duplicated;

    IF offending IS NOT NULL THEN
        RAISE EXCEPTION E'Categorias que so nao colidiam por causa do acento:\n%\n\n'
            'Resolva a mao (arquive ou renomeie uma das duas, e reaponte os lancamentos '
            'da arquivada) e rode a migracao de novo.', offending;
    END IF;

    SELECT string_agg(format('list_item: lista=%s nome=%s', shopping_list_id, name_normalized), E'\n')
      INTO offending
      FROM (SELECT shopping_list_id, name_normalized
              FROM list_item
             WHERE status = 'PENDING'
             GROUP BY shopping_list_id, name_normalized
            HAVING count(*) > 1) AS duplicated;

    IF offending IS NOT NULL THEN
        RAISE EXCEPTION E'Itens pendentes que so nao colidiam por causa do acento:\n%\n\n'
            'Resolva a mao (marque um deles como REMOVED) e rode a migracao de novo.', offending;
    END IF;
END
$duplicates$;

-- Os indices antigos ficam obsoletos, e nao apenas redundantes: sempre que
-- lower() colide, a forma normalizada tambem colide -- o contrario nao vale.
DROP INDEX category_unique_sibling_name;
DROP INDEX list_item_unique_pending_name;

CREATE UNIQUE INDEX category_unique_sibling_name
    ON category (household_id, parent_category_id, name_normalized, kind) NULLS NOT DISTINCT;

CREATE UNIQUE INDEX list_item_unique_pending_name
    ON list_item (shopping_list_id, name_normalized) WHERE status = 'PENDING';
