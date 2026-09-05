package com.novoapp.acceptance;

import com.novoapp.support.Fixtures;
import com.novoapp.support.StubOutboundMessagePort;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static io.restassured.RestAssured.given;

/**
 * Estado compartilhado entre os passos de um cenario, e o unico lugar que sabe
 * como "mandar uma mensagem" de verdade: um POST no webhook, exatamente como o
 * Telegram faz.
 *
 * <p>Os cenarios falam em nomes ("Ana") e telefones ("+5511900000001"); o
 * Telegram fala em user id numerico. A traducao entre os dois mora aqui, e nao
 * nos passos, pra que o Gherkin continue lendo como produto.
 */
// @Singleton, e nao @ApplicationScoped: os passos leem campos direto (world.currentActor,
// world.households). Bean de escopo normal e injetado por proxy, e acesso a campo pelo proxy
// nao chega no bean de verdade -- o estado ficaria num objeto e o reset noutro.
@Singleton
public class AcceptanceWorld {

    private static final AtomicLong TELEGRAM_IDS = new AtomicLong(700_000_000L);
    private static final AtomicLong MESSAGE_IDS = new AtomicLong(1L);

    @Inject
    Fixtures fixtures;

    @Inject
    StubOutboundMessagePort outbound;

    private final Map<String, String> externalIds = new HashMap<>();
    private final Map<String, String> displayNames = new HashMap<>();

    final Map<String, UUID> households = new LinkedHashMap<>();
    final Map<String, UUID> members = new LinkedHashMap<>();
    final Map<String, UUID> categories = new LinkedHashMap<>();
    final Map<String, String> inviteTokens = new LinkedHashMap<>();

    /** Quem falou por ultimo. E a "pessoa" dos passos que nao repetem o nome. */
    String currentActor;

    void reset() {
        fixtures.truncateAll();
        outbound.clear();
        externalIds.clear();
        displayNames.clear();
        households.clear();
        members.clear();
        categories.clear();
        inviteTokens.clear();
        currentActor = null;
    }

    String externalIdFor(String actor) {
        return externalIds.computeIfAbsent(actor, key -> String.valueOf(TELEGRAM_IDS.incrementAndGet()));
    }

    /** Faz dois rotulos do Gherkin ("Carla" e o telefone dela) apontarem pra mesma conversa. */
    void alias(String actor, String sameAs) {
        externalIds.put(actor, externalIdFor(sameAs));
        displayNames.put(actor, displayNames.getOrDefault(sameAs, sameAs));
        displayNames.put(sameAs, displayNames.getOrDefault(actor, actor));
    }

    void nameOf(String actor, String displayName) {
        displayNames.put(actor, displayName);
    }

    String displayNameOf(String actor) {
        return displayNames.getOrDefault(actor, actor);
    }

    void send(String actor, String text) {
        deliver(actor, text, null, MESSAGE_IDS.incrementAndGet());
    }

    void shareContact(String actor, String phoneNumber) {
        deliver(actor, null, phoneNumber, MESSAGE_IDS.incrementAndGet());
    }

    /**
     * Mesma mensagem entregue duas vezes pelo provedor: o
     * <code>message_id</code> repetido e o que caracteriza reentrega (ADR-0005).
     */
    void sendTwice(String actor, String text) {
        long messageId = MESSAGE_IDS.incrementAndGet();
        deliver(actor, text, null, messageId);
        deliver(actor, text, null, messageId);
    }

    private void deliver(String actor, String text, String phoneNumber, long messageId) {
        currentActor = actor;
        String externalId = externalIdFor(actor);

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("message_id", messageId);
        message.put("chat", Map.of("id", Long.parseLong(externalId), "type", "private"));
        message.put("from", Map.of("id", Long.parseLong(externalId), "first_name", displayNameOf(actor)));
        if (text != null) {
            message.put("text", text);
        }
        if (phoneNumber != null) {
            message.put("contact", Map.of("phone_number", phoneNumber));
        }

        given().contentType(ContentType.JSON)
                .body(Map.of("update_id", messageId, "message", message))
                .when().post("/webhook/telegram")
                .then().statusCode(200);
    }

    java.util.List<StubOutboundMessagePort.Sent> repliesTo(String actor) {
        return outbound.to(externalIdFor(actor));
    }

    String lastReplyTo(String actor) {
        return outbound.lastTextTo(externalIdFor(actor));
    }

    String lastReplyToCurrentActor() {
        return lastReplyTo(currentActor);
    }
}
