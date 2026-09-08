package com.novoapp.conversation.entity;

/**
 * Como a pendencia terminou. Nula enquanto ninguem resolveu -- e essa nulidade,
 * e nao um estado calculado por tempo, que a central de pendencias da Etapa 4
 * consulta (ADR-0018).
 */
public enum PendingResolution {

    CONFIRMED,

    /** Respondeu <code>nao</code>, ou cancelou com <code>desfazer</code> (ADR-0025). */
    REJECTED,

    /**
     * Existe no CHECK do banco e nunca e gravado por este codigo. A ADR-0018
     * descartou marcar EXPIRED automaticamente quando o prazo passa: viraria
     * estado terminal antes de a pessoa ter chance de resolver na web, que e o
     * oposto do que a ADR quer. Fica reservado para uma desistencia explicita
     * registrada pela central de pendencias.
     */
    EXPIRED
}
