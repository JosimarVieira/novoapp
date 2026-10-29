-- ADR-0040: a lista de compras ganha botoes, e o clique entra pelo mesmo
-- webhook e pela mesma tabela que a mensagem de texto -- a idempotencia da
-- ADR-0005 vale para os dois.
--
-- Sem esta coluna, a calibracao da ADR-0035 teria de deduzir o que e clique
-- pelo formato de raw_text (o callback_data cru) ou pelo prefixo de
-- provider_message_id ("cb:"). As duas coisas sao escolha do adaptador de canal
-- e mudariam sem aviso. E se as mensagens de lista migrarem para o botao, a
-- taxa de acerto antes/depois so e comparavel separando por tipo da linha.
--
-- NOT NULL DEFAULT 'TEXT': e verdade para toda linha que ja existe -- antes
-- desta migracao nao havia botao nenhum.
--
-- Mesmo padrao da V6: inbound_message e exclusiva do papel pre-tenant
-- (ADR-0022), e o grant de tabela que a V1 da a novoapp_identity ja alcanca a
-- coluna nova. Nenhum papel de dominio passa a enxerga-la.
ALTER TABLE inbound_message
    ADD COLUMN kind text NOT NULL DEFAULT 'TEXT' CHECK (kind IN ('TEXT', 'ACTION'));

COMMENT ON COLUMN inbound_message.kind IS
    'TEXT ou ACTION (Acao de botao, ADR-0040). Clique nunca passa pelo modelo: prompt_version e model_name ficam nulos.';
