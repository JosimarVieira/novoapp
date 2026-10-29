package com.novoapp.channel;

import com.novoapp.support.Fixtures;
import com.novoapp.support.PostgresTestResource;
import com.novoapp.support.StubOutboundMessagePort;
import io.quarkus.test.common.TestResourceScope;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Idempotencia (ADR-0005), um dos quatro testes obrigatorios da
 * estrategia-de-testes.md.
 *
 * <p>O cenario @etapa1 "Reentrega da mesma mensagem pelo provedor" ja cobre o
 * efeito observavel (uma despesa, um recibo). Este teste desce um nivel e olha
 * tambem o log de ingestao: a reentrega nao pode nem virar linha nova em
 * <code>inbound_message</code>, senao a calibracao da Etapa 5 conta a mesma
 * mensagem duas vezes.
 */
@QuarkusTest
@WithTestResource(value = PostgresTestResource.class, scope = TestResourceScope.GLOBAL)
class IdempotencyTest {

    private static final String EXTERNAL_ID = "700000999";

    @Inject
    Fixtures fixtures;

    @Inject
    StubOutboundMessagePort outbound;

    private UUID householdId;

    @BeforeEach
    void setUp() {
        fixtures.truncateAll();
        outbound.clear();

        householdId = fixtures.insertHousehold("Silva");
        fixtures.insertWallet(householdId);
        fixtures.insertExpenseCategory(householdId, "Mercado");
        UUID memberId = fixtures.insertMember("Ana", null);
        fixtures.insertMembership(householdId, memberId, "OWNER");
        fixtures.insertChannelIdentity(memberId, "TELEGRAM", EXTERNAL_ID, householdId);
    }

    @Test
    @DisplayName("reentrega do mesmo provider_message_id nao cria linha nova nem lancamento novo")
    void redeliveryIsDiscardedSilently() {
        deliver(4242L, "mercado 50");
        deliver(4242L, "mercado 50");

        assertThat(fixtures.count("SELECT count(*) FROM inbound_message")).isEqualTo(1);
        assertThat(fixtures.count("SELECT count(*) FROM transaction")).isEqualTo(1);
        assertThat(outbound.to(EXTERNAL_ID)).hasSize(1);
    }

    @Test
    @DisplayName("mesma mensagem enviada de novo de proposito e um lancamento novo, nao reentrega")
    void repeatingTheSameTextOnPurposeIsANewExpense() {
        // ADR-0005 descartou deduplicacao por hash de conteudo justamente por
        // isto: duas compras iguais no mesmo dia sao caso de uso legitimo.
        deliver(1L, "mercado 50");
        deliver(2L, "mercado 50");

        assertThat(fixtures.count("SELECT count(*) FROM transaction")).isEqualTo(2);
    }

    @Test
    @DisplayName("message_id do Telegram se repete entre conversas: a chave nao pode ser so ele")
    void sameTelegramMessageIdInAnotherChatIsANewMessage() {
        UUID otherMember = fixtures.insertMember("Bruno", null);
        fixtures.insertMembership(householdId, otherMember, "MEMBER");
        fixtures.insertChannelIdentity(otherMember, "TELEGRAM", "700000998", householdId);

        deliver(EXTERNAL_ID, 77L, "mercado 50");
        deliver("700000998", 77L, "mercado 50");

        assertThat(fixtures.count("SELECT count(*) FROM inbound_message")).isEqualTo(2);
        assertThat(fixtures.count("SELECT count(*) FROM transaction")).isEqualTo(2);
    }

    /**
     * O clique tem a mesma chave de idempotencia que a mensagem (ADR-0040): o id
     * do <code>callback_query</code>, prefixado. Reentrega dele nao vira linha
     * nova -- e nem um segundo efeito, que aqui seria um segundo item removido
     * ou um aviso de "ja saiu da lista" para quem tocou uma vez so.
     */
    @Test
    @DisplayName("reentrega do mesmo clique nao cria linha nova, e a linha e marcada como ACTION")
    void clickRedeliveryIsDiscardedSilently() {
        UUID listId = fixtures.insertShoppingList(householdId);
        UUID itemId = fixtures.insertListItem(householdId, listId, "Cafe", "PENDING",
                (UUID) fixtures.query("SELECT id FROM member").get(0).get(0));

        click("cb-4242", 10L, "rm:" + itemId);
        click("cb-4242", 10L, "rm:" + itemId);

        List<List<Object>> rows = fixtures.query("SELECT kind, provider_message_id, raw_text FROM inbound_message");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get(0)).isEqualTo("ACTION");
        assertThat(rows.get(0).get(1)).isEqualTo("cb:cb-4242");
        assertThat(rows.get(0).get(2)).isEqualTo("rm:" + itemId);
        assertThat(fixtures.count("SELECT count(*) FROM list_item WHERE status = 'REMOVED'")).isEqualTo(1);
        assertThat(outbound.clickAnswersTo(EXTERNAL_ID)).hasSize(1);
    }

    @Test
    @DisplayName("mensagem de texto continua TEXT")
    void textMessageIsText() {
        deliver(5L, "mercado 50");

        assertThat(fixtures.query("SELECT kind FROM inbound_message").get(0).get(0)).isEqualTo("TEXT");
    }

    private void click(String clickId, long messageId, String data) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("message_id", messageId);
        message.put("chat", Map.of("id", Long.parseLong(EXTERNAL_ID), "type", "private"));

        Map<String, Object> click = new LinkedHashMap<>();
        click.put("id", clickId);
        click.put("from", Map.of("id", Long.parseLong(EXTERNAL_ID), "first_name", "Ana"));
        click.put("message", message);
        click.put("data", data);

        given().contentType(ContentType.JSON)
                .body(Map.of("update_id", messageId, "callback_query", click))
                .when().post("/webhook/telegram")
                .then().statusCode(200);
    }

    private void deliver(long messageId, String text) {
        deliver(EXTERNAL_ID, messageId, text);
    }

    private void deliver(String externalId, long messageId, String text) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("message_id", messageId);
        message.put("chat", Map.of("id", Long.parseLong(externalId), "type", "private"));
        message.put("from", Map.of("id", Long.parseLong(externalId), "first_name", "Ana"));
        message.put("text", text);

        given().contentType(ContentType.JSON)
                .body(Map.of("update_id", messageId, "message", message))
                .when().post("/webhook/telegram")
                .then().statusCode(200);
    }
}
