package com.novoapp.finance.entity;

/**
 * De onde veio o lancamento. Junto com <code>source_message_id</code>, e o que
 * torna a Etapa 5 mensuravel: da pra rastrear todo lancamento ate a mensagem
 * que o originou.
 */
public enum TransactionSource {
    CHAT,
    /** Etapa 4 (ADR-0021). */
    WEB
}
