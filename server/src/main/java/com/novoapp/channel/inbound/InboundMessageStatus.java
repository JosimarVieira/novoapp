package com.novoapp.channel.inbound;

/** Ciclo de vida de uma mensagem no log de ingestao (modelo-de-dados.md). */
public enum InboundMessageStatus {
    RECEIVED,
    INTERPRETED,
    EXECUTED,
    FAILED,
    /** Descartada de proposito: reentrega do provedor, grupo, mensagem sem texto. */
    IGNORED
}
