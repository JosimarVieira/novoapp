package com.novoapp.channel.outbound;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.rest.client.reactive.ClientExceptionMapper;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

/** Cliente da Telegram Bot API. So os metodos que o produto usa. */
@Path("/bot{token}")
@RegisterRestClient(configKey = "telegram-api")
public interface TelegramApi {

    @POST
    @Path("/sendMessage")
    @Consumes(MediaType.APPLICATION_JSON)
    void sendMessage(@PathParam("token") String token, SendMessageRequest request);

    /** Substitui a mensagem onde o botao estava (ADR-0040, alternativa F descartada: nada e reenviado). */
    @POST
    @Path("/editMessageText")
    @Consumes(MediaType.APPLICATION_JSON)
    void editMessageText(@PathParam("token") String token, EditMessageTextRequest request);

    /** Responde ao clique. Sem isso o cliente deixa o botao carregando. */
    @POST
    @Path("/answerCallbackQuery")
    @Consumes(MediaType.APPLICATION_JSON)
    void answerCallbackQuery(@PathParam("token") String token, AnswerCallbackQueryRequest request);

    /**
     * Erro da Bot API com a descricao dela, e nao so o status: "message is not
     * modified" e "message can't be edited" sao os dois 400, e um e sucesso
     * enquanto o outro pede mensagem nova (sdd-modulo-channel.md).
     */
    @ClientExceptionMapper
    static TelegramApiException toException(Response response) {
        String body = null;
        try {
            body = response.hasEntity() ? response.readEntity(String.class) : null;
        } catch (RuntimeException unreadable) {
            // Sem corpo legivel, fica o status: o chamador trata como falha comum.
        }
        return new TelegramApiException(response.getStatus(), body);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record SendMessageRequest(@JsonProperty("chat_id") String chatId,
                              String text,
                              @JsonProperty("reply_markup") InlineKeyboardMarkup replyMarkup) {

        SendMessageRequest(String chatId, String text) {
            this(chatId, text, null);
        }
    }

    /**
     * Sem {@code replyMarkup}, o Telegram tira o teclado da mensagem editada --
     * e o que se quer quando o redesenho nao tem botao nenhum.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record EditMessageTextRequest(@JsonProperty("chat_id") String chatId,
                                  @JsonProperty("message_id") long messageId,
                                  String text,
                                  @JsonProperty("reply_markup") InlineKeyboardMarkup replyMarkup) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AnswerCallbackQueryRequest(@JsonProperty("callback_query_id") String callbackQueryId, String text) {
    }

    record InlineKeyboardMarkup(@JsonProperty("inline_keyboard") List<List<InlineKeyboardButton>> inlineKeyboard) {
    }

    record InlineKeyboardButton(String text, @JsonProperty("callback_data") String callbackData) {
    }
}
