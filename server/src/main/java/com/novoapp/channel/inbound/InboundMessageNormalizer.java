package com.novoapp.channel.inbound;

import com.novoapp.identity.Channel;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

/** Update do Telegram -> {@link NormalizedInbound}. */
@ApplicationScoped
public class InboundMessageNormalizer {

    public Optional<NormalizedInbound> normalize(TelegramUpdate update) {
        if (update == null || update.message() == null) {
            // Update que nao e mensagem (edicao, callback de botao, etc).
            return Optional.empty();
        }
        TelegramUpdate.Message message = update.message();
        if (message.from() == null || message.chat() == null || !message.chat().isPrivate()) {
            return Optional.empty();
        }

        String phoneNumber = message.contact() == null ? null : message.contact().phoneNumber();
        if (message.text() == null && phoneNumber == null) {
            // Foto, sticker, audio: nada que esta etapa saiba interpretar.
            return Optional.empty();
        }

        return Optional.of(new NormalizedInbound(
                Channel.TELEGRAM,
                String.valueOf(message.from().id()),
                message.from().firstName(),
                providerMessageId(message),
                message.text(),
                normalizePhoneNumber(phoneNumber)));
    }

    /**
     * O <code>message_id</code> do Telegram e unico por conversa, nao global --
     * duas pessoas diferentes recebem o mesmo numero. Compor com o id do chat e
     * o que faz a chave <code>(channel, provider_message_id)</code> da ADR-0005
     * realmente identificar uma mensagem.
     */
    private String providerMessageId(TelegramUpdate.Message message) {
        return message.chat().id() + ":" + message.messageId();
    }

    /** O Telegram entrega o telefone ora com "+", ora sem. E.164 sempre com. */
    private String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null) {
            return null;
        }
        String digits = phoneNumber.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? null : "+" + digits;
    }
}
