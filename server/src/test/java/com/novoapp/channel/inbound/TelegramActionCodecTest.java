package com.novoapp.channel.inbound;

import com.novoapp.common.message.ChatAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O codec do <code>callback_data</code> (ADR-0040, ADR-0041,
 * sdd-modulo-channel.md).
 *
 * <p>Unidade pura: o que se trava aqui e o contrato com o Telegram -- ida e
 * volta sem perda, e o limite de 64 bytes, que o Telegram aplica recusando a
 * mensagem inteira, e nao so o botao.
 */
class TelegramActionCodecTest {

    private static final UUID ITEM = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e");
    private static final UUID ENTRY = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");

    @Test
    @DisplayName("todo tipo de acao faz ida e volta, e nenhum callback_data passa de 64 bytes")
    void everyActionRoundTripsWithinTheLimit() {
        for (ChatAction.Type type : ChatAction.Type.values()) {
            ChatAction action = actionOf(type);

            String data = TelegramActionCodec.encode(action);

            assertThat(data.getBytes(StandardCharsets.UTF_8).length)
                    .as(data).isLessThanOrEqualTo(TelegramActionCodec.MAX_BYTES);
            assertThat(TelegramActionCodec.decode(data)).as(data).contains(action);
        }
    }

    @Test
    @DisplayName("o maior callback_data e o de item: codigo, dois-pontos e UUID")
    void largestIsThirtyNineBytes() {
        assertThat(TelegramActionCodec.encode(ChatAction.onItem(ChatAction.Type.RESTORE_ITEM, ITEM))).hasSize(39);
    }

    /**
     * Ha listas com botoes no Telegram da familia desde a ADR-0040. O formato do
     * botao de item nao pode mudar: o que esta na tela de alguem tem de
     * continuar valendo.
     */
    @Test
    @DisplayName("botao de item ja enviado, em forma textual, continua valendo")
    void listButtonAlreadySentStillDecodes() {
        assertThat(TelegramActionCodec.decode("rm:0f8fad5b-d9cb-469f-a165-70867728950e"))
                .contains(ChatAction.onItem(ChatAction.Type.REMOVE_ITEM, ITEM));
        assertThat(TelegramActionCodec.encode(ChatAction.onItem(ChatAction.Type.ADD_TO_CART, ITEM)))
                .isEqualTo("c+:" + ITEM);
    }

    @Test
    @DisplayName("a referencia de lancamento vai em base64url, 22 caracteres")
    void transactionReferenceIsCompact() {
        String data = TelegramActionCodec.encode(ChatAction.onTransaction(ChatAction.Type.UNDO, ENTRY));

        assertThat(data).matches("dz:[A-Za-z0-9_-]{22}");
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

    /**
     * A referencia de lancamento so existe na forma que o codec escreve. UUID
     * textual num codigo de recibo nunca foi enviado por bot nenhum.
     */
    @Test
    @DisplayName("referencia de lancamento fora da forma compacta e descartada")
    void malformedTransactionReferenceIsDiscarded() {
        String valid = TelegramActionCodec.encode(ChatAction.onTransaction(ChatAction.Type.UNDO, ENTRY));
        String compact = valid.substring(3);

        assertThat(TelegramActionCodec.decode("dz")).isEmpty();
        assertThat(TelegramActionCodec.decode("dz:")).isEmpty();
        assertThat(TelegramActionCodec.decode("dz:" + ENTRY)).isEmpty();
        assertThat(TelegramActionCodec.decode("dz:" + compact.substring(1))).isEmpty();
        assertThat(TelegramActionCodec.decode("dz:" + compact + "A")).isEmpty();
        assertThat(TelegramActionCodec.decode("dz:" + compact.substring(0, 21) + "!")).isEmpty();
        assertThat(TelegramActionCodec.decode(valid + ":" + compact)).isEmpty();
        // O ultimo caractere de 22 so carrega dois bits: trocar os quatro que
        // sobram daria os mesmos bytes, numa escrita que o codec nunca produz.
        assertThat(TelegramActionCodec.decode("dz:" + compact.substring(0, 21) + nonCanonical(compact.charAt(21))))
                .isEmpty();
    }

    private static ChatAction actionOf(ChatAction.Type type) {
        if (type.reference() == null) {
            return ChatAction.closeCart();
        }
        return switch (type.reference()) {
            case LIST_ITEM -> ChatAction.onItem(type, ITEM);
            case TRANSACTION -> ChatAction.onTransaction(type, ENTRY);
        };
    }

    /** Outro caractere com os mesmos dois bits altos, e portanto os mesmos bytes. */
    private static char nonCanonical(char last) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
        int value = alphabet.indexOf(last);
        return alphabet.charAt((value & 0b110000) | ((value + 1) & 0b001111));
    }
}
