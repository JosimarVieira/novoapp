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
