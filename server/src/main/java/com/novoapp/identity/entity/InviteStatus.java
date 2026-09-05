package com.novoapp.identity.entity;

/**
 * Situacao do convite (ADR-0020).
 *
 * <p>So PENDING e ACCEPTED sao gravados. EXPIRED e calculado sob demanda a
 * partir de <code>expires_at</code>, sem job de limpeza -- mesmo padrao que a
 * ADR-0014 estabeleceu pro fechamento de fatura.
 */
public enum InviteStatus {
    PENDING,
    ACCEPTED,
    EXPIRED
}
