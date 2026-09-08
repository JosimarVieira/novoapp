package com.novoapp.finance;

import java.util.UUID;

/**
 * O lancamento que o <code>desfazer</code> estornou (ADR-0025).
 *
 * <p>Carrega quem tinha lancado, e nao so o valor: o alvo do estorno e o
 * lancamento mais recente do household inteiro, de qualquer membro (ADR-0012),
 * entao quem mandou <code>desfazer</code> pode estar estornando lancamento de
 * outra pessoa -- e o recibo precisa poder dizer isso.
 */
public record ReversedExpense(UUID transactionId,
                              long amountCents,
                              String categoryDisplayName,
                              UUID createdByMemberId) {
}
