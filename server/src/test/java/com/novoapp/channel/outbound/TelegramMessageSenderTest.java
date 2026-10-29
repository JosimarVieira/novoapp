package com.novoapp.channel.outbound;

import com.novoapp.channel.inbound.TelegramClickOrigin;
import com.novoapp.common.message.ChatAction;
import com.novoapp.common.message.ChatReply;
import com.novoapp.common.message.ChatReply.ReplyAction;
import com.novoapp.identity.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A traducao da resposta com acoes para a Bot API (ADR-0040,
 * sdd-modulo-channel.md).
 *
 * <p>Unidade, com a API do Telegram falsa: o que se trava aqui sao os dois
 * desfechos de edicao que nao sao erro, e a regra de que todo clique e
 * respondido. Nenhum dos dois aparece num cenario de aceitacao, porque o stub de
 * canal substitui este adaptador inteiro.
 */
class TelegramMessageSenderTest {

    private static final String CLICK = new TelegramClickOrigin("cb-1", 4242L, 55L).encode();
    private static final UUID ITEM = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e");

    private FakeTelegramApi api;
    private TelegramMessageSender sender;

    @BeforeEach
    void setUp() {
        api = new FakeTelegramApi();
        sender = new TelegramMessageSender();
        sender.telegramApi = api;
        sender.botToken = "token";
    }

    @Test
    @DisplayName("resposta a clique: responde ao clique e edita a mensagem de origem, sem mandar nova")
    void clickReplyAnswersAndEdits() {
        sender.send(Channel.TELEGRAM, "4242", list().inReplyTo(CLICK).replacingOrigin().withNotice("Cafe ja saiu"));

        assertThat(api.calls).containsExactly("answer cb-1 Cafe ja saiu", "edit 4242:55");
        assertThat(api.lastKeyboard.inlineKeyboard().get(0).get(0).callbackData()).isEqualTo("c+:" + ITEM);
    }

    @Test
    @DisplayName("'message is not modified' e sucesso: nao vira recibo de erro nem lista repetida")
    void notModifiedIsSuccess() {
        api.editFailure = new TelegramApiException(400,
                "{\"ok\":false,\"error_code\":400,\"description\":\"Bad Request: message is not modified: "
                        + "specified new message content and reply markup are exactly the same\"}");

        sender.send(Channel.TELEGRAM, "4242", list().inReplyTo(CLICK).replacingOrigin());

        assertThat(api.calls).containsExactly("answer cb-1 null", "edit 4242:55");
    }

    @Test
    @DisplayName("mensagem que nao pode mais ser editada: a resposta vai como mensagem nova")
    void uneditableFallsBackToNewMessage() {
        api.editFailure = new TelegramApiException(400,
                "{\"ok\":false,\"error_code\":400,\"description\":\"Bad Request: message can't be edited\"}");

        sender.send(Channel.TELEGRAM, "4242", list().inReplyTo(CLICK).replacingOrigin());

        assertThat(api.calls).containsExactly("answer cb-1 null", "edit 4242:55", "send 4242");
    }

    @Test
    @DisplayName("clique sem nada a dizer e respondido do mesmo jeito, e nenhuma mensagem sai")
    void acknowledgeOnly() {
        sender.send(Channel.TELEGRAM, "4242", ChatReply.text(null).inReplyTo(CLICK));

        assertThat(api.calls).containsExactly("answer cb-1 null");
    }

    @Test
    @DisplayName("lista pedida por texto: mensagem nova com teclado, e nenhum clique a responder")
    void textRequestedListIsANewMessage() {
        sender.send(Channel.TELEGRAM, "4242", list());

        assertThat(api.calls).containsExactly("send 4242");
        assertThat(api.lastKeyboard).isNotNull();
    }

    @Test
    @DisplayName("texto puro continua sem teclado")
    void plainTextHasNoKeyboard() {
        sender.send(Channel.TELEGRAM, "4242", "Anotado");

        assertThat(api.calls).containsExactly("send 4242");
        assertThat(api.lastKeyboard).isNull();
    }

    private ChatReply list() {
        return ChatReply.of("Esta faltando:\n- Cafe",
                List.of(List.of(new ReplyAction("Cafe", new ChatAction(ChatAction.Type.ADD_TO_CART, ITEM)))));
    }

    private static final class FakeTelegramApi implements TelegramApi {

        final List<String> calls = new ArrayList<>();
        TelegramApiException editFailure;
        InlineKeyboardMarkup lastKeyboard;

        @Override
        public void sendMessage(String token, SendMessageRequest request) {
            calls.add("send " + request.chatId());
            lastKeyboard = request.replyMarkup();
        }

        @Override
        public void editMessageText(String token, EditMessageTextRequest request) {
            calls.add("edit " + request.chatId() + ":" + request.messageId());
            lastKeyboard = request.replyMarkup();
            if (editFailure != null) {
                throw editFailure;
            }
        }

        @Override
        public void answerCallbackQuery(String token, AnswerCallbackQueryRequest request) {
            calls.add("answer " + request.callbackQueryId() + " " + request.text());
        }
    }
}
