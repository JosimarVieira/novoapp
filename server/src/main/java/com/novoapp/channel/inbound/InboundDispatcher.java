package com.novoapp.channel.inbound;

import io.quarkus.runtime.ShutdownEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Tira o processamento do ciclo de request do webhook.
 *
 * <p>O webhook precisa responder 200 em menos de 3s (regra nao negociavel 3 do
 * CLAUDE.md), e a chamada ao LLM tem cauda de latencia imprevisivel (ADR-0005).
 * Entao a resposta HTTP sai logo depois da persistencia, e o resto roda aqui.
 */
@ApplicationScoped
public class InboundDispatcher {

    private static final Logger LOG = Logger.getLogger(InboundDispatcher.class);

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @Inject
    InboundPipeline pipeline;

    /**
     * Falso so em teste. O teste de orcamento de resposta do webhook
     * (estrategia-de-testes.md) roda com assincrono ligado, como em producao;
     * os demais rodam sincrono pra nao virar espera com relogio.
     */
    @ConfigProperty(name = "novoapp.channel.async", defaultValue = "true")
    boolean async;

    public void dispatch(UUID messageId, NormalizedInbound inbound) {
        if (!async) {
            pipeline.process(messageId, inbound);
            return;
        }
        executor.submit(() -> {
            try {
                pipeline.process(messageId, inbound);
            } catch (RuntimeException e) {
                // A mensagem fica com o status que tiver; o usuario ja recebeu
                // recibo de erro de dentro do pipeline.
                LOG.errorf(e, "Processamento assincrono da mensagem %s falhou", messageId);
            }
        });
    }

    void shutdown(@Observes ShutdownEvent event) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }
}
