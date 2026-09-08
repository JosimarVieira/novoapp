-- Etapa 2a: a espinha da etapa (ROADMAP) -- a pergunta que o bot faz e fica
-- esperando resposta.
--
-- Schema exatamente como modelo-de-dados.md o descreve. Nenhuma coluna a mais:
-- a ADR-0018 e explicita que a central de pendencias da Etapa 4 se sustenta
-- sem mudanca de schema, e a distincao entre TIPOS de pendencia mora dentro de
-- intent_json (campo "tipo"), nao em coluna propria -- decisao registrada em
-- sdd-modulo-conversation.md.
--
-- resolution fica NULL enquanto ninguem resolveu. expires_at no passado NAO
-- muda estado nenhum sozinho (ADR-0018, alternativa B descartada: nada de job
-- nem trigger marcando EXPIRED). O que expires_at muda e so o caminho de
-- resolucao: dentro do TTL resolve por curto-circuito no chat, fora dele so
-- pela central de pendencias do app. Por isso EXPIRED existe no CHECK mas
-- nunca e gravado por este codigo -- fica reservado pra uma eventual
-- desistencia explicita registrada pela web.

CREATE TABLE pending_action (
    id                  uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    household_id        uuid        NOT NULL REFERENCES household (id),
    member_id           uuid        NOT NULL REFERENCES member (id),
    -- De qual conversa veio a pergunta. Chat e sempre 1:1 (ADR-0008), entao a
    -- pendencia pertence ao fio de quem foi perguntado: e o que permite
    -- resolver "sim"/"nao"/numero sem ambiguidade de remetente.
    channel_identity_id uuid        NOT NULL REFERENCES channel_identity (id),
    -- O que gerou a pergunta, com tudo que a execucao vai precisar quando a
    -- resposta chegar (valor, descricao, mensagem de origem). Mesmo padrao de
    -- inbound_message.intent_json.
    intent_json         text        NOT NULL,
    question_asked      text        NOT NULL,
    -- As opcoes numeradas, quando ha. Vazio (NULL) numa pendencia de sim/nao ou
    -- de pergunta aberta -- e o que faz "50" ser lido como valor, e nao como
    -- "opcao 50", numa pendencia que pede o valor.
    options_json        text,
    created_at          timestamptz NOT NULL DEFAULT now(),
    expires_at          timestamptz NOT NULL,
    resolved_at         timestamptz,
    resolution          text        CHECK (resolution IN ('CONFIRMED', 'REJECTED', 'EXPIRED'))
);

-- "Tem pergunta aberta pra esta conversa?" roda em toda mensagem que chega,
-- antes de qualquer interpretacao (indice previsto em modelo-de-dados.md).
CREATE INDEX pending_action_conversation_idx
    ON pending_action (channel_identity_id, expires_at)
    WHERE resolved_at IS NULL;

-- A consulta da central de pendencias da Etapa 4 (ADR-0018): tudo do household
-- que ninguem resolveu, inclusive o que ja expirou no chat.
CREATE INDEX pending_action_unresolved_idx
    ON pending_action (household_id, expires_at)
    WHERE resolution IS NULL;

ALTER TABLE pending_action ENABLE ROW LEVEL SECURITY;
ALTER TABLE pending_action FORCE  ROW LEVEL SECURITY;
CREATE POLICY pending_action_tenant ON pending_action TO novoapp_app
    USING (household_id = app_current_household())
    WITH CHECK (household_id = app_current_household());

GRANT SELECT, INSERT, UPDATE ON pending_action TO novoapp_app;

-- A pendencia e escrita e lida sob o papel de dominio, mas quem a encontra e o
-- passo que roda antes da interpretacao -- e esse passo precisa do
-- channel_identity_id, que so o papel pre-tenant enxerga. Em vez de dar ao
-- papel de dominio um grant sobre channel_identity (que dissolveria a
-- ADR-0022), quem resolve a identidade ja devolve esse id junto do contexto.
