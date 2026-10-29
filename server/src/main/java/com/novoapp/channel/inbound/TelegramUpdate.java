package com.novoapp.channel.inbound;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * O corpo que a Telegram Bot API entrega no webhook. So os campos que
 * interessam -- o resto do Update e ignorado de proposito.
 *
 * <p>{@code callbackQuery} e o toque num botao (ADR-0040). Mensagem e clique
 * chegam pelo mesmo webhook, e no maximo um dos dois vem preenchido.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramUpdate(@JsonProperty("update_id") Long updateId,
                             Message message,
                             @JsonProperty("callback_query") CallbackQuery callbackQuery) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(@JsonProperty("message_id") Long messageId,
                          Chat chat,
                          From from,
                          String text,
                          Contact contact) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Chat(Long id, String type) {

        /**
         * Interacao e sempre 1:1 (ADR-0008). Grupo nao e limitacao tecnica --
         * e decisao de produto, e por isso a mensagem e descartada aqui.
         */
        public boolean isPrivate() {
            return "private".equals(type);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record From(Long id, @JsonProperty("first_name") String firstName) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Contact(@JsonProperty("phone_number") String phoneNumber,
                          @JsonProperty("user_id") Long userId) {
    }

    /**
     * O toque num botao de teclado inline.
     *
     * @param id unico globalmente, ao contrario do <code>message_id</code> -- e o
     *        que vira a chave de idempotencia do clique (ADR-0005)
     * @param message a mensagem onde o botao estava. Em mensagem muito antiga o
     *        Telegram manda so chat e id, sem o conteudo, e e so isso que se usa
     * @param data o <code>callback_data</code> do botao, ate 64 bytes
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CallbackQuery(String id, From from, Message message, String data) {
    }
}
