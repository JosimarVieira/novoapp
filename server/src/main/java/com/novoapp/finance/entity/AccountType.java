package com.novoapp.finance.entity;

/**
 * Tipo de conta (ADR-0011). Cartao e uma <code>account</code> com campos extras,
 * nao entidade propria.
 */
public enum AccountType {
    WALLET,
    BANK,
    /** Ganha fatura, dia de fechamento e limite. Sem uso na Etapa 1. */
    CARD
}
