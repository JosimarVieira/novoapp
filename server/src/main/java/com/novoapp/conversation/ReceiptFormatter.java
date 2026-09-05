package com.novoapp.conversation;

import com.novoapp.finance.RegisteredExpense;
import com.novoapp.identity.ContextResolution.ResolvedContext;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.math.RoundingMode;

import java.util.Locale;

/**
 * Recibo: resposta curta confirmando o que foi gravado, sempre com a saida de
 * <code>desfazer</code> (glossario).
 */
@ApplicationScoped
public class ReceiptFormatter {

    private static final Locale BRAZIL = Locale.of("pt", "BR");

    /**
     * Cenario @etapa1 "Despesa com categoria reconhecida": valor, categoria e
     * como desfazer.
     *
     * <p>O texto ja oferece <code>desfazer</code> embora o estorno so chegue na
     * Etapa 2 -- decisao registrada no sdd-modulo-conversation.md.
     *
     * <p>Nao nomeia a conta usada. A ADR-0019 afirma de passagem que "recibo
     * sempre nomear a conta usada" seria "regra existente", mas essa regra nao
     * aparece no glossario, no .feature nem no SDD deste modulo -- e nesta etapa
     * so existe uma conta por household, entao nomear so acrescentaria ruido.
     * Quando houver mais de uma conta, isso vira decisao explicita.
     *
     * <p>Nomeia o household so quando a pessoa tem mais de um vinculo: e o que a
     * ADR-0007 exige pra que erro de contexto fique visivel, e o que ela proibe
     * de aparecer pra quem tem uma familia so.
     */
    public String expenseReceipt(RegisteredExpense expense, ResolvedContext context) {
        StringBuilder receipt = new StringBuilder("Anotado: %s %s"
                .formatted(expense.categoryName(), formatAmount(expense.amountCents())));
        if (context.multipleHouseholds()) {
            receipt.append(" na família \"").append(context.householdName()).append("\"");
        }
        receipt.append("\nSe eu errei, responda: desfazer");
        return receipt.toString();
    }

    /**
     * Qualquer coisa diferente de confianca alta vira este texto nesta etapa.
     * Pergunta especifica (ambiguidade, categoria inexistente, valor ausente)
     * exige {@code PendingAction} -- Etapa 2.
     */
    public String notUnderstood() {
        return """
                Não entendi essa. Tente algo como: mercado 50""";
    }

    public String failure() {
        return """
                Deu erro aqui ao registrar e não gravei nada. Pode mandar de novo?""";
    }

    /**
     * Formatado a mao em vez de {@code NumberFormat.getCurrencyInstance}: a
     * biblioteca usa espaco nao separavel entre o simbolo e o numero, e o
     * Gherkin fala em "R$ 50,00" com espaco comum.
     */
    private String formatAmount(long amountCents) {
        BigDecimal amount = BigDecimal.valueOf(amountCents).divide(BigDecimal.valueOf(100), 2, RoundingMode.UNNECESSARY);
        return "R$ " + String.format(BRAZIL, "%,.2f", amount);
    }
}
