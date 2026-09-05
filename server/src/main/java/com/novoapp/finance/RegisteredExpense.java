package com.novoapp.finance;

import java.time.LocalDate;
import java.util.UUID;

/** O que <code>conversation</code> precisa saber pra montar o recibo. */
public record RegisteredExpense(UUID transactionId,
                                long amountCents,
                                String categoryName,
                                String accountName,
                                LocalDate occurredOn) {
}
