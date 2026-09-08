package com.novoapp.conversation;

/**
 * Que tipo de pergunta esta esperando resposta.
 *
 * <p><b>Decisao da Etapa 2a, registrada em sdd-modulo-conversation.md.</b> A
 * ADR-0018 desenhou {@code PendingAction} como mecanismo generico -- toda
 * pendencia resolve por <code>sim</code>/<code>nao</code>/numero. A ADR-0026
 * abriu uma excecao para um tipo so: na pergunta de criacao de categoria, uma
 * resposta que nao e nenhum desses atalhos vira correcao livre e gasta uma
 * segunda chamada ao modelo. Logo o codigo precisa saber qual tipo esta aberto
 * antes de decidir entre "nao entendi" e a tool de correcao.
 *
 * <p>O discriminador vive no campo <code>type</code> de
 * <code>pending_action.intent_json</code>, e nao em coluna propria nem no
 * formato de <code>options_json</code>. Coluna nova contrariaria a ADR-0018,
 * que decide explicitamente que a central de pendencias da Etapa 4 nao precisa
 * de mudanca de schema. E deduzir o tipo do formato das opcoes amarraria a
 * regra de resolucao a uma escolha de apresentacao: uma pendencia de sim/nao e
 * uma de valor ausente tem as duas <code>options_json</code> vazio, e sao
 * coisas diferentes.
 */
public enum PendingActionType {

    /**
     * "Criar a categoria 'Pet shop'?" (ADR-0024). Unico tipo com terceira via:
     * resposta que nao e atalho vira correcao de hierarquia (ADR-0026).
     */
    CREATE_CATEGORY,

    /** "Foi em qual? 1) Mercado 2) Mercado livre" -- confianca media (ADR-0004). */
    CHOOSE_CATEGORY,

    /** "Quanto foi?" -- a pessoa nomeou a categoria e nao disse o valor. */
    ASK_AMOUNT,

    /** "Nao achei 'Feijao' na lista. Registro como comprado?" */
    CONFIRM_PURCHASE
}
