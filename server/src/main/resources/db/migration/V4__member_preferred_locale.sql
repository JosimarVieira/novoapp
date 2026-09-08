-- ADR-0015, a parte de arquitetura que era pra ter entrado na Etapa 1.
--
-- A ADR decidiu, para a "Etapa 1 em diante", que nenhum texto voltado ao
-- usuario e literal no codigo: tudo vem de arquivo de mensagens por idioma,
-- resolvido a partir deste atributo. Isso nao foi feito na epoca -- os dois
-- prompts de etapa navegaram por lista curada de ADRs e esta ficou fora das
-- duas. Registrado na entrega da Etapa 2a.
--
-- Vive em member, e nao em household (ADR-0015, alternativa C descartada): um
-- household pode ter membros com preferencia de idioma diferente, mesma razao
-- de fundo que poe active_household_id no membro/canal.
--
-- Conteudo continua so pt-BR durante a validacao (ADR-0015, decisao 2). O
-- default aqui e o que faz todo membro ja existente e todo membro novo cair
-- nele sem passo de onboarding a mais.
ALTER TABLE member
    ADD COLUMN preferred_locale text NOT NULL DEFAULT 'pt-BR';
