package com.novoapp.channel.inbound;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.Optional;
import java.util.UUID;

/**
 * Webhook do Telegram (ADR-0002).
 *
 * <p>Responde 200 assim que a mensagem esta persistida e nao antes: persistir
 * primeiro e o que torna a reentrega detectavel (ADR-0005). Responder rapido e
 * o que impede o provedor de reentregar por timeout.
 */
@Path("/webhook/telegram")
public class TelegramWebhookResource {

    private static final Logger LOG = Logger.getLogger(TelegramWebhookResource.class);

    @Inject
    InboundMessageNormalizer normalizer;

    @Inject
    InboundMessageIdempotencyGuard idempotencyGuard;

    @Inject
    InboundDispatcher dispatcher;

    /**
     * Segredo combinado com o Telegram no <code>setWebhook</code>, devolvido
     * por ele no header a cada entrega. Sem isso, qualquer um que descubra a URL
     * injeta mensagem em nome de qualquer pessoa.
     */
    @ConfigProperty(name = "novoapp.channel.telegram.webhook-secret")
    Optional<String> webhookSecret;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response receive(TelegramUpdate update,
                            @jakarta.ws.rs.HeaderParam("X-Telegram-Bot-Api-Secret-Token") String secretToken) {
        if (webhookSecret.isPresent() && !webhookSecret.get().equals(secretToken)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        Optional<NormalizedInbound> normalized = normalizer.normalize(update);
        if (normalized.isEmpty()) {
            // Update que esta etapa nao trata (grupo, foto, edicao). 200 mesmo
            // assim: 4xx faria o Telegram reentregar pra sempre.
            return Response.ok().build();
        }

        NormalizedInbound inbound = normalized.get();
        Optional<UUID> messageId = idempotencyGuard.registerIfNew(inbound);
        if (messageId.isEmpty()) {
            // Reentrega: descarte silencioso (ADR-0005). Sem recibo repetido.
            LOG.debugf("Reentrega descartada: %s", inbound.providerMessageId());
            return Response.ok().build();
        }

        dispatcher.dispatch(messageId.get(), inbound);
        return Response.ok().build();
    }
}
