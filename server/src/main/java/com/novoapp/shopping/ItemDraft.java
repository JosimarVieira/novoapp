package com.novoapp.shopping;

import java.math.BigDecimal;

/**
 * Item como saiu da interpretacao, antes de existir na lista.
 *
 * @param name     ja normalizado por <code>nlu</code> ("acabou o arroz" -> "Arroz")
 * @param quantity nula no caso comum. Ausencia nunca vira pergunta
 *                 (sdd-modulo-shopping.md)
 * @param unit     "kg", "l", "un" -- o que a pessoa disse, nulo quando nao disse
 */
public record ItemDraft(String name, BigDecimal quantity, String unit) {

    public static ItemDraft of(String name) {
        return new ItemDraft(name, null, null);
    }
}
