package com.novoapp.channel.inbound;

import com.novoapp.common.message.ChatAction;
import com.novoapp.identity.Channel;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

/** Update do Telegram -> {@link NormalizedInbound}. */
@ApplicationScoped
public class InboundMessageNormalizer {

    /**
     * Prefixo da chave de idempotencia de um clique (ADR-0040). O id do
     * <code>callback_query</code> ja e unico globalmente; o prefixo so existe
     * para nunca colidir com <code>chat_id:message_id</code>, que e o formato
     * das mensagens de texto.
     */
    static final String CALLBACK_PREFIX = "cb:";

    public Optional<NormalizedInbound> normalize(TelegramUpdate update) {
        if (update == null) {
            return Optional.empty();
        }
        if (update.callbackQuery() != null) {
            return normalizeClick(update.callbackQuery());
        }
        if (update.message() == null) {
            // Update que nao e mensagem nem clique (edicao, entrada em grupo etc).
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
     * Toque num botao (ADR-0040), sob as mesmas condicoes da mensagem de texto:
     * chat privado e remetente presente (ADR-0008).
     *
     * <p>Toque duplo gera dois <code>callback_query</code> distintos e <b>nao</b>
     * e deduplicado aqui -- quem o torna inofensivo e o clique carregar o estado
     * de destino. Aqui so cai a reentrega do mesmo clique pelo provedor, pela
     * mesma chave da ADR-0005.
     *
     * <p><code>callback_data</code> que nao casa com codigo nenhum e descartado
     * como {@code Update} desconhecido: sem linha em <code>inbound_message</code>
     * e sem resposta ao clique. So aparece com botao de uma versao antiga do bot
     * ou com dado forjado, e responder exigiria uma chamada ao Telegram dentro do
     * webhook, que tem 3s (regra 3).
     */
    private Optional<NormalizedInbound> normalizeClick(TelegramUpdate.CallbackQuery click) {
        TelegramUpdate.Message message = click.message();
        if (click.id() == null || click.from() == null || message == null
                || message.chat() == null || message.messageId() == null || !message.chat().isPrivate()) {
            return Optional.empty();
        }
        Optional<ChatAction> action = TelegramActionCodec.decode(click.data());
        if (action.isEmpty()) {
            return Optional.empty();
        }

        TelegramClickOrigin origin = new TelegramClickOrigin(click.id(), message.chat().id(), message.messageId());
        return Optional.of(new NormalizedInbound(
                Channel.TELEGRAM,
                String.valueOf(click.from().id()),
                click.from().firstName(),
                CALLBACK_PREFIX + click.id(),
                click.data(),
                null,
                action.get(),
                origin.encode()));
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
