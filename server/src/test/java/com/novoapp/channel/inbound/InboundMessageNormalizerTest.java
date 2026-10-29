package com.novoapp.channel.inbound;

import com.novoapp.common.message.ChatAction;
import com.novoapp.identity.Channel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O clique entra pelo mesmo normalizador que a mensagem (ADR-0040), sob as
 * mesmas condicoes: chat privado e remetente presente (ADR-0008).
 */
class InboundMessageNormalizerTest {

    private static final UUID ITEM = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e");

    private final InboundMessageNormalizer normalizer = new InboundMessageNormalizer();

    @Test
    @DisplayName("callback_query de chat privado vira acao, com provider_message_id prefixado")
    void privateClickBecomesAnAction() {
        NormalizedInbound inbound = normalizer.normalize(click("private", "rm:" + ITEM)).orElseThrow();

        assertThat(inbound.channel()).isEqualTo(Channel.TELEGRAM);
        assertThat(inbound.externalId()).isEqualTo("4242");
        assertThat(inbound.providerMessageId()).isEqualTo("cb:987654321");
        assertThat(inbound.rawText()).isEqualTo("rm:" + ITEM);
        assertThat(inbound.action()).isEqualTo(new ChatAction(ChatAction.Type.REMOVE_ITEM, ITEM));
        assertThat(inbound.kind()).isEqualTo(InboundMessageKind.ACTION);
        assertThat(TelegramClickOrigin.decode(inbound.origin()))
                .contains(new TelegramClickOrigin("987654321", 4242L, 55L));
    }

    @Test
    @DisplayName("clique de grupo e descartado, como mensagem de grupo")
    void groupClickIsDiscarded() {
        assertThat(normalizer.normalize(click("group", "rm:" + ITEM))).isEmpty();
    }

    @Test
    @DisplayName("callback_data desconhecido e descartado, como Update desconhecido")
    void unknownClickIsDiscarded() {
        assertThat(normalizer.normalize(click("private", "botao-antigo"))).isEmpty();
    }

    @Test
    @DisplayName("mensagem de texto nao muda: chave chat_id:message_id, tipo TEXT, sem acao")
    void textIsUnchanged() {
        TelegramUpdate.Message message = new TelegramUpdate.Message(55L, new TelegramUpdate.Chat(4242L, "private"),
                new TelegramUpdate.From(4242L, "Ana"), "mercado 50", null);

        NormalizedInbound inbound = normalizer.normalize(new TelegramUpdate(1L, message, null)).orElseThrow();

        assertThat(inbound.providerMessageId()).isEqualTo("4242:55");
        assertThat(inbound.kind()).isEqualTo(InboundMessageKind.TEXT);
        assertThat(inbound.action()).isNull();
        assertThat(inbound.origin()).isNull();
    }

    private TelegramUpdate click(String chatType, String data) {
        TelegramUpdate.Message message = new TelegramUpdate.Message(55L, new TelegramUpdate.Chat(4242L, chatType),
                null, null, null);
        TelegramUpdate.CallbackQuery click = new TelegramUpdate.CallbackQuery("987654321",
                new TelegramUpdate.From(4242L, "Ana"), message, data);
        return new TelegramUpdate(1L, null, click);
    }

    @Test
    @DisplayName("a origem so decodifica o que foi codificado aqui")
    void originRoundTrips() {
        TelegramClickOrigin origin = new TelegramClickOrigin("4405", -1001L, 7L);

        assertThat(TelegramClickOrigin.decode(origin.encode())).contains(origin);
        assertThat(TelegramClickOrigin.decode("qualquer coisa")).isEqualTo(Optional.empty());
        assertThat(TelegramClickOrigin.decode(null)).isEmpty();
    }
}
