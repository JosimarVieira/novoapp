package com.novoapp.channel.outbound;

import com.novoapp.identity.Channel;
import com.novoapp.identity.spi.OutboundMessagePort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

/**
 * Implementacao Telegram do {@link OutboundMessagePort} (sdd-modulo-channel.md).
 *
 * <p>A interface vive em <code>identity</code> e a implementacao aqui: e o que
 * mantem <code>channel</code> como o modulo mais externo, do qual ninguem
 * depende.
 */
@ApplicationScoped
public class TelegramMessageSender implements OutboundMessagePort {

    private static final Logger LOG = Logger.getLogger(TelegramMessageSender.class);

    @Inject
    @RestClient
    TelegramApi telegramApi;

    @ConfigProperty(name = "novoapp.channel.telegram.bot-token")
    String botToken;

    @Override
    public void send(Channel channel, String externalId, String text) {
        if (channel != Channel.TELEGRAM) {
            // WhatsApp entra na Etapa 7 como outro adaptador, mesma interface.
            throw new UnsupportedOperationException("Canal ainda sem adaptador de envio: " + channel);
        }
        try {
            // Interacao e sempre 1:1 (ADR-0008), entao o chat de destino e a
            // propria pessoa: o external_id ja e o chat.
            telegramApi.sendMessage(botToken, new TelegramApi.SendMessageRequest(externalId, text));
        } catch (RuntimeException e) {
            // Nao propaga: o lancamento ja foi gravado, e derrubar o
            // processamento por falha de envio nao desfaz nada -- so faria o
            // provedor reentregar e duplicar.
            LOG.errorf(e, "Falha ao enviar mensagem para %s", externalId);
        }
    }
}
