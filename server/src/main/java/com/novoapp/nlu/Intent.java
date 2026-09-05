package com.novoapp.nlu;

import java.util.UUID;

/**
 * Resultado da interpretacao: qual acao o usuario quis, com quais parametros e
 * com que confianca (glossario).
 *
 * <p>Na Etapa 1 so existe uma acao possivel, {@code REGISTER_EXPENSE}, e a
 * confianca e deterministica -- ver {@link #HIGH_CONFIDENCE}.
 */
public record Intent(Kind kind, UUID categoryId, String categoryName, Long amountCents, double confidence) {

    /**
     * Confianca alta nesta etapa nao vem de limiar calibrado: e o caso, descrito
     * no sdd-modulo-nlu.md, de "a categoria bateu exatamente com uma do enum e o
     * valor veio". O limiar de verdade sai da Etapa 5 com dado real (ADR-0004,
     * decisao aberta #7) -- qualquer numero escolhido agora seria inventado.
     */
    public static final double HIGH_CONFIDENCE = 1.0d;

    public static final double LOW_CONFIDENCE = 0.0d;

    public enum Kind {
        REGISTER_EXPENSE,
        /** Nenhuma tool escolhida, ou parametro fora do que a tool aceita. */
        UNKNOWN
    }

    public static Intent registerExpense(UUID categoryId, String categoryName, long amountCents) {
        return new Intent(Kind.REGISTER_EXPENSE, categoryId, categoryName, amountCents, HIGH_CONFIDENCE);
    }

    public static Intent unknown() {
        return new Intent(Kind.UNKNOWN, null, null, null, LOW_CONFIDENCE);
    }

    public boolean isHighConfidence() {
        return confidence >= HIGH_CONFIDENCE;
    }
}
