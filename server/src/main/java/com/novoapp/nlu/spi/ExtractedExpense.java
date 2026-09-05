package com.novoapp.nlu.spi;

/**
 * O que o modelo devolveu na chamada da tool <code>registrarDespesa</code>,
 * ainda cru -- categoria pelo nome, sem id resolvido.
 *
 * @param accountName parametro opcional da tool, sem uso na Etapa 1
 *        (sdd-modulo-finance.md)
 */
public record ExtractedExpense(String categoryName, long amountCents, String accountName) {
}
