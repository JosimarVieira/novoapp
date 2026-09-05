package com.novoapp.channel.inbound;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * O corpo que a Telegram Bot API entrega no webhook. So os campos que
 * interessam -- o resto do Update e ignorado de proposito.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramUpdate(@JsonProperty("update_id") Long updateId, Message message) {

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
}
