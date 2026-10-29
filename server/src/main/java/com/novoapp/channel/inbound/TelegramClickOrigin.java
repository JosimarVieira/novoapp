package com.novoapp.channel.inbound;

import java.util.Optional;

/**
 * O identificador de origem de um clique no Telegram (ADR-0040): o que o
 * adaptador precisa para responder a ele.
 *
 * <p>Atravessa <code>conversation</code> como string opaca
 * ({@code InboundMessage.origin}) e volta na resposta. Dentro dele ha duas
 * coisas, e nao so a mensagem onde o botao estava, como o SDD desenhou:
 * <code>answerCallbackQuery</code> precisa do id do <code>callback_query</code>,
 * e nao ha outro caminho para ele chegar ao adaptador de saida sem que o resto
 * do sistema o carregue com nome de canal. O formato continua so daqui.
 *
 * @param callbackQueryId o que <code>answerCallbackQuery</code> responde
 * @param chatId e {@code messageId}: a mensagem que <code>editMessageText</code>
 *        substitui
 */
public record TelegramClickOrigin(String callbackQueryId, long chatId, long messageId) {

    private static final char SEPARATOR = '|';

    public String encode() {
        return callbackQueryId + SEPARATOR + chatId + ":" + messageId;
    }

    /** Vazio para o que nao foi produzido por {@link #encode()}. */
    public static Optional<TelegramClickOrigin> decode(String origin) {
        if (origin == null) {
            return Optional.empty();
        }
        int separator = origin.lastIndexOf(SEPARATOR);
        int colon = origin.lastIndexOf(':');
        if (separator <= 0 || colon < separator) {
            return Optional.empty();
        }
        try {
            return Optional.of(new TelegramClickOrigin(origin.substring(0, separator),
                    Long.parseLong(origin.substring(separator + 1, colon)),
                    Long.parseLong(origin.substring(colon + 1))));
        } catch (NumberFormatException malformed) {
            return Optional.empty();
        }
    }
}
