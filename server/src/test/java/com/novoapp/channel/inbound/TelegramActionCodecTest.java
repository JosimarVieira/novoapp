package com.novoapp.channel.inbound;

import com.novoapp.common.message.ChatAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O codec do <code>callback_data</code> (ADR-0040, sdd-modulo-channel.md).
 *
 * <p>Unidade pura: o que se trava aqui e o contrato com o Telegram -- ida e
 * volta sem perda, e o limite de 64 bytes, que o Telegram aplica recusando a
 * mensagem inteira, e nao so o botao.
 */
class TelegramActionCodecTest {

    private static final UUID ITEM = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e");

    @Test
    @DisplayName("todo tipo de acao faz ida e volta, e nenhum callback_data passa de 64 bytes")
    void everyActionRoundTripsWithinTheLimit() {
        for (ChatAction.Type type : ChatAction.Type.values()) {
            ChatAction action = type.needsItem() ? new ChatAction(type, ITEM) : ChatAction.closeCart();

            String data = TelegramActionCodec.encode(action);

            assertThat(data.getBytes(StandardCharsets.UTF_8).length)
                    .as(data).isLessThanOrEqualTo(TelegramActionCodec.MAX_BYTES);
            assertThat(TelegramActionCodec.decode(data)).as(data).contains(action);
        }
    }

    @Test
    @DisplayName("o maior callback_data e o de item: codigo, dois-pontos e UUID")
    void largestIsThirtyNineBytes() {
        assertThat(TelegramActionCodec.encode(new ChatAction(ChatAction.Type.RESTORE_ITEM, ITEM))).hasSize(39);
    }

    @Test
    @DisplayName("o que nao casa com codigo nenhum e descartado, e nao vira acao")
    void unknownDataIsDiscarded() {
        assertThat(TelegramActionCodec.decode(null)).isEmpty();
        assertThat(TelegramActionCodec.decode("")).isEmpty();
        assertThat(TelegramActionCodec.decode("xx:" + ITEM)).isEmpty();
        assertThat(TelegramActionCodec.decode("rm:nao-e-uuid")).isEmpty();
        // Acao de item sem item, e fechar com item: as duas formas sao forjadas.
        assertThat(TelegramActionCodec.decode("rm")).isEmpty();
        assertThat(TelegramActionCodec.decode("fx:" + ITEM)).isEmpty();
    }
}
