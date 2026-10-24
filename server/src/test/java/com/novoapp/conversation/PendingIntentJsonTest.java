package com.novoapp.conversation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O JSON de <code>pending_action.intent_json</code> gravado antes de
 * 2026-10-24, quando marcar, remover e registrar fora da lista guardavam um item
 * so, em {@code itemName}.
 *
 * <p>Pendencia nao resolvida nao expira de verdade: passado o TTL ela sai do
 * atalho do chat e continua na central de pendencias (ADR-0018). Uma linha
 * dessas, aberta antes do deploy, pode receber o "sim" depois dele -- e se o
 * formato novo nao a lesse, o "sim" executaria uma lista vazia.
 *
 * <p>Unidade pura, com um {@link ObjectMapper} sem configuracao: o que se testa
 * sao as anotacoes do record, e nao a configuracao do Quarkus.
 */
class PendingIntentJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final UUID SOURCE = UUID.fromString("a7cb1fd9-e556-4383-a3c0-9140de8978e9");

    @Test
    @DisplayName("confirmacao de marcar comprado gravada no formato antigo vira lista de um item")
    void legacyMarkPurchasedIsRead() throws Exception {
        PendingIntent intent = objectMapper.readValue("""
                {"type":"CONFIRM_INTENT","deferred":"MARK_PURCHASED","itemName":"Arroz",
                 "sourceMessageId":"%s"}""".formatted(SOURCE), PendingIntent.class);

        assertThat(intent.deferred()).isEqualTo(PendingIntent.DeferredAction.MARK_PURCHASED);
        assertThat(intent.itemNamesOrEmpty()).containsExactly("Arroz");
        assertThat(intent.sourceMessageId()).isEqualTo(SOURCE);
    }

    @Test
    @DisplayName("confirmacao de remover gravada no formato antigo vira lista de um item")
    void legacyRemoveIsRead() throws Exception {
        PendingIntent intent = objectMapper.readValue("""
                {"type":"CONFIRM_INTENT","deferred":"REMOVE_LIST_ITEM","itemName":"Chocolate",
                 "sourceMessageId":"%s"}""".formatted(SOURCE), PendingIntent.class);

        assertThat(intent.itemNamesOrEmpty()).containsExactly("Chocolate");
    }

    /**
     * O mais antigo dos formatos: sem {@code deferred}, de antes da ADR-0029.
     * As duas compatibilidades tem de valer juntas.
     */
    @Test
    @DisplayName("oferta de item fora da lista de antes da ADR-0029 continua lida")
    void legacyPurchaseOfferWithoutDeferredIsRead() throws Exception {
        PendingIntent intent = objectMapper.readValue("""
                {"type":"CONFIRM_PURCHASE","itemName":"Feijão","sourceMessageId":"%s"}"""
                .formatted(SOURCE), PendingIntent.class);

        assertThat(intent.deferred()).isEqualTo(PendingIntent.DeferredAction.ADD_ALREADY_PURCHASED);
        assertThat(intent.itemNamesOrEmpty()).containsExactly("Feijão");
    }

    @Test
    @DisplayName("o formato novo grava e le a lista inteira")
    void newFormatRoundTrips() throws Exception {
        PendingIntent written = PendingIntent.confirmMarkPurchased(List.of("Ovos", "Sal"), SOURCE);

        String json = objectMapper.writeValueAsString(written);
        PendingIntent read = objectMapper.readValue(json, PendingIntent.class);

        assertThat(json).contains("\"itemNames\"").doesNotContain("\"itemName\"");
        assertThat(read).isEqualTo(written);
    }

    @Test
    @DisplayName("fechamento sem itens continua significando comprei tudo")
    void checkoutWithoutItemsIsStillEverything() throws Exception {
        PendingIntent intent = objectMapper.readValue("""
                {"type":"ASK_AMOUNT","deferred":"CLOSE_PURCHASE","itemNames":[],
                 "sourceMessageId":"%s"}""".formatted(SOURCE), PendingIntent.class);

        assertThat(intent.itemNamesOrEmpty()).isEmpty();
    }
}
