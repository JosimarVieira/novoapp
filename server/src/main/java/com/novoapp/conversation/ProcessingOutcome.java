package com.novoapp.conversation;

import com.novoapp.nlu.spi.InterpretationProvenance;

/**
 * O que aconteceu com a mensagem, para <code>channel</code> registrar no log de
 * ingestao.
 *
 * <p>E devolvido em vez de <code>conversation</code> escrever direto em
 * <code>inbound_message</code>: aquela tabela e de <code>channel</code>, e
 * <code>conversation</code> nao pode importar <code>channel</code>
 * (sdd-visao-geral.md).
 */
public record ProcessingOutcome(Result result,
                                Double confidence,
                                String intentJson,
                                String promptVersion,
                                String modelName) {

    /**
     * Desfecho sem proveniencia: mensagem que nao gastou chamada de modelo --
     * curto-circuito da regra 6, ou falha antes da interpretacao. As duas
     * colunas ficam nulas de proposito (ADR-0035).
     */
    public ProcessingOutcome(Result result, Double confidence, String intentJson) {
        this(result, confidence, intentJson, null, null);
    }

    /**
     * O mesmo desfecho, dizendo qual prompt e qual modelo o produziram
     * (ADR-0035). Aplicado num lugar so, no ponto em que o orquestrador acaba de
     * chamar <code>nlu</code> -- e nao dentro de cada um dos treze metodos que
     * constroem um desfecho, que seria treze lugares para esquecer.
     */
    public ProcessingOutcome from(InterpretationProvenance provenance) {
        return provenance == null
                ? this
                : new ProcessingOutcome(result, confidence, intentJson,
                        provenance.promptVersion(), provenance.modelName());
    }

    public enum Result {
        /** Interpretou e executou: existe lancamento gravado. */
        EXECUTED,
        /** Interpretou, mas sem confianca pra executar. */
        INTERPRETED,
        /** Quebrou no meio. O usuario recebeu recibo de erro, nunca silencio. */
        FAILED
    }
}
