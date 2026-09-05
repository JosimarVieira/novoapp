package com.novoapp.conversation;

/**
 * O que aconteceu com a mensagem, para <code>channel</code> registrar no log de
 * ingestao.
 *
 * <p>E devolvido em vez de <code>conversation</code> escrever direto em
 * <code>inbound_message</code>: aquela tabela e de <code>channel</code>, e
 * <code>conversation</code> nao pode importar <code>channel</code>
 * (sdd-visao-geral.md).
 */
public record ProcessingOutcome(Result result, Double confidence, String intentJson) {

    public enum Result {
        /** Interpretou e executou: existe lancamento gravado. */
        EXECUTED,
        /** Interpretou, mas sem confianca pra executar. */
        INTERPRETED,
        /** Quebrou no meio. O usuario recebeu recibo de erro, nunca silencio. */
        FAILED
    }
}
