package com.novoapp.finance;

import java.time.LocalDate;
import java.util.UUID;

/**
 * O que <code>conversation</code> precisa saber pra montar o recibo.
 *
 * @param categoryDisplayName ja no formato que o recibo usa: "Restaurantes
 *        (dentro de Alimentacao)" quando ha pai, so o nome quando e raiz. O
 *        recibo ecoar a hierarquia criada e exigencia da ADR-0026
 * @param description o que sobrou da mensagem (ADR-0023). Nula quando nao
 *        sobrou nada -- "mercado 50" nunca vira "compra de mercado"
 */
public record RegisteredExpense(UUID transactionId,
                                long amountCents,
                                String categoryDisplayName,
                                String accountName,
                                String description,
                                LocalDate occurredOn) {
}
