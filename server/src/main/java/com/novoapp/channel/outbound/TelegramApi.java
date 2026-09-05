package com.novoapp.channel.outbound;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/** Cliente da Telegram Bot API. So o metodo que esta etapa usa. */
@Path("/bot{token}")
@RegisterRestClient(configKey = "telegram-api")
public interface TelegramApi {

    @POST
    @Path("/sendMessage")
    @Consumes(MediaType.APPLICATION_JSON)
    void sendMessage(@PathParam("token") String token, SendMessageRequest request);

    record SendMessageRequest(@JsonProperty("chat_id") String chatId, String text) {
    }
}
