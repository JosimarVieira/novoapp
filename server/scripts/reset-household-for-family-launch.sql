-- Zera a movimentacao de UM household e semeia as categorias iniciais, para
-- liberar o produto para a familia com o historico de teste limpo.
--
-- Script operacional, de uso unico e manual. NAO e migration: fica fora de
-- db/migration de proposito, senao o Flyway rodaria isto em todo boot.
--
-- USO (com o usuario administrativo, NOVOAPP_DB_ADMIN_USER):
--
--   1) Sem parametro: so lista os households e o que cada um tem. Nao altera nada.
--        psql "$ADMIN_URL" -f reset-household-for-family-launch.sql
--
--   2) Ensaio no household escolhido: faz tudo e desfaz no fim (ROLLBACK).
--        psql "$ADMIN_URL" -v household=<uuid> -f reset-household-for-family-launch.sql
--
--   3) Para valer: igual ao 2, mais -v apply=1 (COMMIT).
--        psql "$ADMIN_URL" -v household=<uuid> -v apply=1 -f reset-household-for-family-launch.sql
--
-- O QUE APAGA (so do household informado):
--   pending_action, list_item, list_checkout, shopping_list, transaction, category.
--   chat_cart_item sai junto, pela cascata da FK para list_item (V10), sem
--   DELETE proprio.
--
-- O QUE FICA, de proposito:
--   * household, member, household_membership, channel_identity, household_invite
--     -- quem ja esta vinculado continua vinculado, sem refazer onboarding;
--   * account -- a carteira implicita e a conta padrao de cada membro (ADR-0019);
--   * inbound_message -- e a chave da idempotencia (ADR-0005) e o corpus da
--     ADR-0036. As mensagens de teste ficam; a Etapa 5 corta por received_at a
--     partir da data de liberacao para a familia.
--
-- Categoria e apagada e recriada, e nao reaproveitada: as que existem hoje
-- nasceram de teste, e categoria a mais no cardapio e contexto a mais para o
-- modelo escolher errado.

\set ON_ERROR_STOP on

\if :{?household}
\else
  \echo ''
  \echo 'Nenhum household informado. Households existentes (nada foi alterado):'
  \echo ''
  BEGIN;
  SET LOCAL row_security = off;
  SELECT h.id,
         h.name,
         h.created_at::date                                                  AS criado_em,
         (SELECT string_agg(m.name || ' (' || hm.role || ')', ', ' ORDER BY hm.created_at)
            FROM household_membership hm JOIN member m ON m.id = hm.member_id
           WHERE hm.household_id = h.id)                                     AS membros,
         (SELECT count(*) FROM transaction t WHERE t.household_id = h.id)    AS lancamentos,
         (SELECT count(*) FROM category c    WHERE c.household_id = h.id)    AS categorias,
         (SELECT count(*) FROM list_item i   WHERE i.household_id = h.id)    AS itens_lista
    FROM household h
   ORDER BY h.created_at;
  ROLLBACK;
  \echo 'Rode de novo com -v household=<id> para o ensaio.'
  \quit
\endif

BEGIN;

-- Todas as tabelas tem FORCE ROW LEVEL SECURITY e as policies sao so dos papeis
-- da aplicacao. Um usuario que nao ignore RLS veria zero linhas e o DELETE
-- "funcionaria" sem apagar nada. Com row_security = off, o Postgres recusa a
-- consulta em vez de devolver vazio -- falha alto, nao em silencio.
SET LOCAL row_security = off;

SELECT set_config('reset.household_id', :'household', true);

-- ---------------------------------------------------------------------------
-- Categorias a semear. EDITE AQUI.
--
-- parent NULL = categoria raiz. Subcategoria aponta para o nome da raiz, um
-- nivel so (ADR-0016).
--
-- Tres regras ao editar:
--   * "Mercado" precisa existir: e a categoria onde o fechamento da lista cai
--     (ADR-0037). Sem ela, "comprei tudo, 180" responde "nao entendi essa"
--     (decisao aberta #26).
--   * Nome unico na arvore inteira, mesmo em pais diferentes. O cardapio da
--     tool e uma lista plana de nomes; dois "Outros" seriam indistinguiveis para
--     o modelo. O script recusa duplicata.
--   * Menos e melhor. Cada nome a mais e uma opcao a mais para um modelo de 8B
--     confundir, e categoria que faltar o bot oferece criar no primeiro uso.
--     Nao ha "Outros" de proposito: vira o lugar onde o modelo joga a duvida.
-- ---------------------------------------------------------------------------
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

\echo ''
\echo 'Antes:'
SELECT 'transaction'    AS tabela, count(*) FROM transaction    WHERE household_id = current_setting('reset.household_id')::uuid
UNION ALL SELECT 'category',       count(*) FROM category       WHERE household_id = current_setting('reset.household_id')::uuid
UNION ALL SELECT 'shopping_list',  count(*) FROM shopping_list  WHERE household_id = current_setting('reset.household_id')::uuid
UNION ALL SELECT 'list_item',      count(*) FROM list_item      WHERE household_id = current_setting('reset.household_id')::uuid
UNION ALL SELECT 'list_checkout',  count(*) FROM list_checkout  WHERE household_id = current_setting('reset.household_id')::uuid
UNION ALL SELECT 'pending_action', count(*) FROM pending_action WHERE household_id = current_setting('reset.household_id')::uuid;

DO $reset$
DECLARE
    target   uuid := current_setting('reset.household_id')::uuid;
    hh_name  text;
    owner_id uuid;
    dup      text;
    orphan   text;
    n        bigint;
BEGIN
    SELECT name INTO hh_name FROM household WHERE id = target;
    IF hh_name IS NULL THEN
        RAISE EXCEPTION 'Household % nao existe. Rode sem -v household para listar.', target;
    END IF;

    -- created_by_member_id das categorias semeadas: o OWNER mais antigo.
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
        RAISE EXCEPTION 'Nome de categoria repetido na lista de semente: %', dup;
    END IF;

    SELECT string_agg(s.name || ' -> ' || s.parent, ', ') INTO orphan
      FROM seed_category s
     WHERE s.parent IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM seed_category r WHERE r.parent IS NULL AND r.name = s.parent);
    IF orphan IS NOT NULL THEN
        RAISE EXCEPTION 'Subcategoria com pai que nao e raiz na lista: %', orphan;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM seed_category WHERE name_normalized = 'mercado') THEN
        RAISE EXCEPTION 'A lista precisa ter "Mercado" (ADR-0037, decisao aberta #26).';
    END IF;

    -- Ordem de apagar segue as FKs: quem aponta sai antes de quem e apontado.
    --   list_item -> list_checkout -> transaction -> category
    --   list_item -> shopping_list
    DELETE FROM pending_action WHERE household_id = target;
    DELETE FROM list_item      WHERE household_id = target;
    DELETE FROM list_checkout  WHERE household_id = target;
    DELETE FROM shopping_list  WHERE household_id = target;
    -- Num comando so: o estorno aponta para o lancamento original
    -- (reversal_of_id), e o Postgres confere a FK no fim do comando, com os dois
    -- ja apagados.
    DELETE FROM transaction    WHERE household_id = target;
    -- Subcategoria antes da raiz, pela FK parent_category_id.
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

    GET DIAGNOSTICS n = ROW_COUNT;
    RAISE NOTICE 'Household "%": movimentacao zerada, % subcategorias semeadas.', hh_name, n;
END
$reset$;

\echo ''
\echo 'Depois -- categorias do household:'
SELECT coalesce(p.name || ' > ', '') || c.name AS categoria
  FROM category c
  LEFT JOIN category p ON p.id = c.parent_category_id
 WHERE c.household_id = current_setting('reset.household_id')::uuid
 ORDER BY coalesce(p.name, c.name), c.parent_category_id NULLS FIRST, c.name;

\if :{?apply}
  COMMIT;
  \echo 'COMMIT feito. Movimentacao zerada e categorias semeadas.'
\else
  ROLLBACK;
  \echo ''
  \echo 'ENSAIO: nada foi gravado (ROLLBACK). Para valer, rode de novo com -v apply=1.'
\endif
