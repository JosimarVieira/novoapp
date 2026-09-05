package com.novoapp.identity.entity;

/**
 * Passo em que a conversa de onboarding parou (`vinculo-de-identidade.feature`).
 *
 * <p>Isto nao e {@code PendingAction} (ADR-0018, Etapa 2): quem esta no meio do
 * onboarding ainda nao tem <code>channel_identity</code> nem household, os dois
 * NOT NULL naquela tabela. E tambem nao passa por politica de confianca --
 * arvore de decisao fixa, sem LLM nenhum no caminho.
 */
public enum OnboardingState {

    /** Ja explicamos o produto e perguntamos se quer criar uma familia. */
    AWAITING_CREATE_CONFIRMATION,

    /** Disse que sim; falta o nome da familia. */
    AWAITING_HOUSEHOLD_NAME,

    /** Familia criada; falta escolher se segue pelo chat ou pelo app. */
    AWAITING_SETUP_CHANNEL_CHOICE,

    /** Chegou por link de convite; falta compartilhar o contato (ADR-0020). */
    AWAITING_SHARED_CONTACT
}
