-- Versao para o Query Tool do pgAdmin do reset-household-for-family-launch.sql.
-- Mesmo efeito, sem comandos do psql (\if, \set, -v), que o Query Tool nao entende.
--
-- Conecte com o usuario administrativo (NOVOAPP_DB_ADMIN_USER, normalmente
-- "postgres" na Railway). Com o usuario da aplicacao o script falha de proposito.
--
-- PASSO 1 -- selecione SO o SELECT abaixo e rode (F5). Copie o id da familia.
-- PASSO 2 -- cole o id no lugar de COLE-O-ID-AQUI, selecione tudo do BEGIN ate o
--            fim e rode. Veja o resultado na aba "Messages".
--            A ultima linha e ROLLBACK: e o ensaio, nada fica gravado.
-- PASSO 3 -- conferiu? troque a ultima linha por COMMIT e rode o passo 2 de novo.
--
-- Apaga do household: pending_action, list_item, list_checkout, shopping_list,
-- transaction, category (chat_cart_item sai pela cascata de list_item). Mantem:
-- membros, vinculo do Telegram, convites, contas e inbound_message
-- (idempotencia, ADR-0005; corpus, ADR-0036).

-- =========================== PASSO 1 ===========================
SET row_security = off;
SELECT h.id,
       h.name,
       (SELECT string_agg(m.name || ' (' || hm.role || ')', ', ' ORDER BY hm.created_at)
          FROM household_membership hm JOIN member m ON m.id = hm.member_id
         WHERE hm.household_id = h.id)                                     AS membros,
       (SELECT count(*) FROM transaction t WHERE t.household_id = h.id)    AS lancamentos,
       (SELECT count(*) FROM category c    WHERE c.household_id = h.id)    AS categorias,
       (SELECT count(*) FROM list_item i   WHERE i.household_id = h.id)    AS itens_lista
  FROM household h
 ORDER BY h.created_at;

-- =========================== PASSO 2 ===========================
BEGIN;
SET LOCAL client_encoding = 'UTF8';
-- Sem isto, um usuario sem permissao de ignorar RLS veria zero linhas e o
-- DELETE "funcionaria" sem apagar nada. Assim o Postgres recusa, em vez de
-- devolver vazio.
SET LOCAL row_security = off;

SELECT set_config('reset.household_id', 'COLE-O-ID-AQUI', true);

-- Categorias a semear. EDITE AQUI. parent NULL = raiz; subcategoria aponta para
-- o nome da raiz (um nivel so, ADR-0016). "Mercado" e obrigatoria (ADR-0037).
-- Nome unico na arvore inteira. Sem "Outros", de proposito.
CREATE TEMP TABLE seed_category (parent text, name text) ON COMMIT DROP;
INSERT INTO seed_category (parent, name) VALUES
    (NULL,          'Mercado'),
    (NULL,          'Alimentação'),
    ('Alimentação', 'Restaurante'),
    ('Alimentação', 'Delivery'),
    (NULL,          'Casa'),
    ('Casa',        'Aluguel'),
    ('Casa',        'Condomínio'),
    ('Casa',        'Energia'),
    ('Casa',        'Água'),
    ('Casa',        'Internet'),
    (NULL,          'Transporte'),
    ('Transporte',  'Combustível'),
    ('Transporte',  'Estacionamento'),
    (NULL,          'Saúde'),
    ('Saúde',       'Farmácia'),
    ('Saúde',       'Consulta'),
    (NULL,          'Pets'),
    (NULL,          'Educação'),
    (NULL,          'Lazer'),
    (NULL,          'Vestuário'),
    (NULL,          'Assinaturas');

DO $reset$
DECLARE
    target   uuid := current_setting('reset.household_id')::uuid;
    hh_name  text;
    owner_id uuid;
    dup      text;
    orphan   text;
    r        record;
BEGIN
    SELECT name INTO hh_name FROM household WHERE id = target;
    IF hh_name IS NULL THEN
        RAISE EXCEPTION 'Household % nao existe. Rode o PASSO 1 para ver os ids.', target;
    END IF;

    SELECT member_id INTO owner_id
      FROM household_membership
     WHERE household_id = target AND role = 'OWNER'
     ORDER BY created_at
     LIMIT 1;
    IF owner_id IS NULL THEN
        RAISE EXCEPTION 'Household "%" nao tem OWNER.', hh_name;
    END IF;

    -- Mesma forma normalizada que common/text/Normalization grava (ADR-0030),
    -- pela mesma expressao do backfill da V5.
    ALTER TABLE seed_category ADD COLUMN name_normalized text;
    UPDATE seed_category SET name_normalized = lower(regexp_replace(translate(trim(name),
        'ÁÀÂÃÄáàâãäÉÈÊËéèêëÍÌÎÏíìîïÓÒÔÕÖóòôõöÚÙÛÜúùûüÇçÑñ',
        'AAAAAaaaaaEEEEeeeeIIIIiiiiOOOOOoooooUUUUuuuuCcNn'), '\s+', ' ', 'g'));

    SELECT string_agg(name_normalized, ', ') INTO dup
      FROM (SELECT name_normalized FROM seed_category GROUP BY 1 HAVING count(*) > 1) d;
    IF dup IS NOT NULL THEN
        RAISE EXCEPTION 'Nome de categoria repetido na lista: %', dup;
    END IF;

    SELECT string_agg(s.name || ' -> ' || s.parent, ', ') INTO orphan
      FROM seed_category s
     WHERE s.parent IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM seed_category p WHERE p.parent IS NULL AND p.name = s.parent);
    IF orphan IS NOT NULL THEN
        RAISE EXCEPTION 'Subcategoria com pai que nao e raiz na lista: %', orphan;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM seed_category WHERE name_normalized = 'mercado') THEN
        RAISE EXCEPTION 'A lista precisa ter "Mercado" (ADR-0037, decisao aberta #26).';
    END IF;

    RAISE NOTICE 'Household "%" -- antes: % lancamentos, % categorias, % itens de lista, % pendencias',
        hh_name,
        (SELECT count(*) FROM transaction    WHERE household_id = target),
        (SELECT count(*) FROM category       WHERE household_id = target),
        (SELECT count(*) FROM list_item      WHERE household_id = target),
        (SELECT count(*) FROM pending_action WHERE household_id = target);

    -- Ordem segue as FKs: quem aponta sai antes de quem e apontado.
    DELETE FROM pending_action WHERE household_id = target;
    DELETE FROM list_item      WHERE household_id = target;
    DELETE FROM list_checkout  WHERE household_id = target;
    DELETE FROM shopping_list  WHERE household_id = target;
    -- Num comando so: o estorno aponta para o original (reversal_of_id), e a FK
    -- e conferida no fim do comando, com os dois ja apagados.
    DELETE FROM transaction    WHERE household_id = target;
    DELETE FROM category       WHERE household_id = target AND parent_category_id IS NOT NULL;
    DELETE FROM category       WHERE household_id = target;

    INSERT INTO category (household_id, parent_category_id, name, name_normalized, kind, created_by_member_id)
    SELECT target, NULL, name, name_normalized, 'EXPENSE', owner_id
      FROM seed_category WHERE parent IS NULL;

    INSERT INTO category (household_id, parent_category_id, name, name_normalized, kind, created_by_member_id)
    SELECT target, p.id, s.name, s.name_normalized, 'EXPENSE', owner_id
      FROM seed_category s
      JOIN category p ON p.household_id = target AND p.parent_category_id IS NULL AND p.name = s.parent
     WHERE s.parent IS NOT NULL;

    RAISE NOTICE 'Depois: % lancamentos, % categorias. Categorias:',
        (SELECT count(*) FROM transaction WHERE household_id = target),
        (SELECT count(*) FROM category    WHERE household_id = target);

    FOR r IN
        SELECT coalesce(p.name || ' > ', '') || c.name AS categoria
          FROM category c
          LEFT JOIN category p ON p.id = c.parent_category_id
         WHERE c.household_id = target
         ORDER BY coalesce(p.name, c.name), c.parent_category_id NULLS FIRST, c.name
    LOOP
        RAISE NOTICE '  %', r.categoria;
    END LOOP;
END
$reset$;

-- ENSAIO: nada fica gravado. Conferiu a aba Messages? Troque por COMMIT.
ROLLBACK;
