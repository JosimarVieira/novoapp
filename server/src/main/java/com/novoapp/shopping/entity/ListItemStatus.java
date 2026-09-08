package com.novoapp.shopping.entity;

/** Situacao do item na lista (glossario). */
public enum ListItemStatus {
    /** Esta faltando. E o que "o que esta faltando?" responde. */
    PENDING,
    PURCHASED,
    /** Tirado da lista sem ter sido comprado. Sem uso na Etapa 2a. */
    REMOVED
}
