package com.novoapp.shopping.entity;

/**
 * Estado da lista. Um household tem no maximo uma {@code ACTIVE} por vez
 * (glossario) -- garantido por indice unico parcial, nao por disciplina de
 * servico (sdd-modulo-shopping.md).
 */
public enum ShoppingListStatus {
    ACTIVE,
    /** Fechada por uma compra. So a Etapa 3 grava isto. */
    CLOSED
}
