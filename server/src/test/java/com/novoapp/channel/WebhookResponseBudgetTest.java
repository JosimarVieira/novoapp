package com.novoapp.channel;

import com.novoapp.support.Fixtures;
import com.novoapp.support.PostgresTestResource;
import com.novoapp.support.StubExpenseExtractor;
import io.quarkus.test.common.TestResourceScope;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Orcamento de resposta do webhook (regra nao negociavel 3 do CLAUDE.md), um dos
 * quatro testes obrigatorios da estrategia-de-testes.md.
 *
 * <p>Unico teste que roda com o pipeline assincrono ligado, como em producao. Os
 * demais rodam sincrono pra nao virar espera com relogio -- mas entao nenhum
 * deles prova que a interpretacao esta mesmo fora do ciclo de request. Este
 * prova: o interpretador demora 6s de proposito, e o 200 tem que sair antes de
 * 3s assim mesmo.
 */
@QuarkusTest
@TestProfile(WebhookResponseBudgetTest.AsyncPipelineProfile.class)
@WithTestResource(value = PostgresTestResource.class, scope = TestResourceScope.GLOBAL)
class WebhookResponseBudgetTest {

    private static final String EXTERNAL_ID = "700000997";
    private static final Duration INTERPRETATION_DELAY = Duration.ofSeconds(6);
    private static final Duration RESPONSE_BUDGET = Duration.ofSeconds(3);

    public static class AsyncPipelineProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("novoapp.channel.async", "true");
        }
    }

    @Inject
    Fixtures fixtures;

    @Inject
    StubExpenseExtractor extractor;

    @BeforeEach
    void setUp() {
        fixtures.truncateAll();
        UUID householdId = fixtures.insertHousehold("Silva");
        fixtures.insertWallet(householdId);
        fixtures.insertExpenseCategory(householdId, "Mercado");
        UUID memberId = fixtures.insertMember("Ana", null);
        fixtures.insertMembership(householdId, memberId, "OWNER");
        fixtures.insertChannelIdentity(memberId, "TELEGRAM", EXTERNAL_ID, householdId);

        extractor.delayEachCallBy(INTERPRETATION_DELAY);
    }

    @AfterEach
    void tearDown() {
        extractor.delayEachCallBy(Duration.ZERO);
    }

    @Test
    @DisplayName("200 sai em menos de 3s mesmo com o interpretador levando 6s, e o lancamento acontece depois")
    void respondsWithinBudgetAndFinishesLater() {
        long startedAt = System.nanoTime();

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("message_id", 9001L);
        message.put("chat", Map.of("id", Long.parseLong(EXTERNAL_ID), "type", "private"));
        message.put("from", Map.of("id", Long.parseLong(EXTERNAL_ID), "first_name", "Ana"));
        message.put("text", "mercado 50");

        given().contentType(ContentType.JSON)
                .body(Map.of("update_id", 9001L, "message", message))
                .when().post("/webhook/telegram")
                .then().statusCode(200);

        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);
        assertThat(elapsed).isLessThan(RESPONSE_BUDGET);

        // A mensagem ja esta persistida quando o 200 sai: e o que torna a
        // reentrega detectavel (ADR-0005).
        assertThat(fixtures.count("SELECT count(*) FROM inbound_message")).isEqualTo(1);

        // Espera o estado final, e nao so o lancamento: o status do log e gravado
        // numa transacao propria, depois da que gravou a despesa.
        Awaitility.await().atMost(INTERPRETATION_DELAY.plusSeconds(20)).untilAsserted(() -> {
            assertThat(fixtures.count("SELECT count(*) FROM transaction")).isEqualTo(1);
            assertThat(fixtures.query("SELECT status FROM inbound_message").get(0).get(0)).isEqualTo("EXECUTED");
        });
    }
}
