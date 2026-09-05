-- Callback do Flyway: roda depois de TODA migracao, inclusive quando nao ha
-- nada a migrar. E o que diferencia isto de uma migration versionada.
--
-- Por que existe: a V1 cria novoapp_runtime com a senha vinda de
-- NOVOAPP_DB_RUNTIME_PASSWORD, mas migration versionada nao roda de novo, e o
-- CREATE ROLE dela e IF NOT EXISTS. Trocar a variavel depois deixava a
-- aplicacao tentando uma senha que o banco nao tem -- e falhando so na primeira
-- requisicao, porque o datasource de dominio e preguicoso, nunca no boot. O
-- sintoma era um 500 no webhook com a aplicacao aparentemente saudavel.
--
-- Com este callback, a variavel de ambiente passa a ser a fonte da verdade: a
-- cada start a senha do papel e reconciliada com ela.
--
-- Roda sob o usuario administrativo (o mesmo do Flyway), unico com privilegio
-- para ALTER ROLE.
DO $sync_runtime_password$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'novoapp_runtime') THEN
        -- format/%L cita o literal corretamente, em vez de concatenar na mao.
        EXECUTE format('ALTER ROLE novoapp_runtime WITH PASSWORD %L', '${runtimepwd}');
    END IF;
END
$sync_runtime_password$;
