package com.novoapp.acceptance;

import com.novoapp.channel.inbound.TelegramActionCodec;
import com.novoapp.common.message.ChatAction;
import com.novoapp.common.message.ChatReply.ReplyAction;
import com.novoapp.common.text.Normalization;
import com.novoapp.support.Fixtures;
import com.novoapp.support.StubOutboundMessagePort;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private static final AtomicLong CLICK_IDS = new AtomicLong(900_000_000L);

    @Inject
    Fixtures fixtures;

    @Inject
    StubOutboundMessagePort outbound;

    @Inject
    com.novoapp.support.StubMessageInterpreter interpreter;

    /**
     * O cenario de falha da ADR-0031 liga a indisponibilidade de
     * <code>finance</code>; sem desligar aqui, ela vazaria para o proximo
     * cenario e o tornaria vermelho por um motivo que ninguem escreveu.
     */
    @Inject
    com.novoapp.support.UnavailableExpenseRegistration finance;

    private final Map<String, String> externalIds = new HashMap<>();
    private final Map<String, String> displayNames = new HashMap<>();

    final Map<String, UUID> households = new LinkedHashMap<>();
    final Map<String, UUID> members = new LinkedHashMap<>();
    final Map<String, UUID> categories = new LinkedHashMap<>();
    final Map<String, String> inviteTokens = new LinkedHashMap<>();
    /** A carteira implicita de cada household, pra montar lancamento por fixture. */
    final Map<String, UUID> wallets = new LinkedHashMap<>();
    final Map<String, UUID> shoppingLists = new LinkedHashMap<>();

    /** Quem falou por ultimo. E a "pessoa" dos passos que nao repetem o nome. */
    String currentActor;

    /**
     * A ultima categoria que um passo conferiu como criada. E o que da sentido a
     * "uma despesa e registrada <b>nessa</b> categoria", que aparece logo depois
     * em varios cenarios de criacao.
     */
    String lastCategoryChecked;

    /**
     * O item de que o cenario esta falando, quando ha varios na lista: o ultimo
     * em que alguem tocou. E o que da sentido a "o item fica registrado como
     * solicitado por", que nasceu para uma lista de um item so.
     */
    String itemInFocus;

    /**
     * Quantos itens ja tinham saido da lista antes do ultimo toque. "Nenhum item
     * sai da lista" fala do que o toque fez, e numa lista desatualizada (ADR-0040)
     * alguem ja tinha removido um item antes dele.
     */
    long removedBeforeLastTap;

    /** A mensagem onde estava o ultimo botao tocado: e ela que tem de ser substituida. */
    long lastTappedMessageId;

    /**
     * Lancamentos e fechamentos plantados como historico do household -- "o
     * ultimo fechamento foi lancado em Mercado" (ADR-0040). Os passos que contam
     * "a despesa registrada" e "o fechamento criado" falam do que o cenario fez,
     * e nao do passado que ele montou.
     */
    final Set<UUID> plantedTransactions = new HashSet<>();
    final Set<UUID> plantedCheckouts = new HashSet<>();

    /**
     * As opcoes da ultima pergunta numerada que um passo conferiu, e a que a
     * pessoa escolheu respondendo com o numero. E o que da sentido a "na
     * categoria escolhida".
     */
    List<String> numberedOptions;
    String chosenOption;

    /**
     * Um recibo de lancamento que a pessoa recebeu (ADR-0041): a mensagem onde ele
     * esta, o lancamento dele e o texto com que chegou -- e esse texto que
     * <code>voltar</code> tem de devolver.
     */
    record Receipt(long messageId, UUID transactionId, String text) {
    }

    /** Os recibos pela frase que os gerou: "o recibo de farmácia 30". */
    final Map<String, Receipt> receipts = new LinkedHashMap<>();

    /** O recibo de que o cenario esta falando: o ultimo recebido ou tocado. */
    Receipt receiptInFocus;

    void reset() {
        fixtures.truncateAll();
        outbound.clear();
        interpreter.resetCallCount();
        finance.makeAvailable();
        externalIds.clear();
        displayNames.clear();
        households.clear();
        members.clear();
        categories.clear();
        inviteTokens.clear();
        wallets.clear();
        shoppingLists.clear();
        currentActor = null;
        lastCategoryChecked = null;
        itemInFocus = null;
        removedBeforeLastTap = 0;
        lastTappedMessageId = 0;
        plantedTransactions.clear();
        plantedCheckouts.clear();
        numberedOptions = null;
        chosenOption = null;
        receipts.clear();
        receiptInFocus = null;
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
        if (numberedOptions != null && text.matches("\\d{1,2}")) {
            int chosen = Integer.parseInt(text);
            chosenOption = chosen >= 1 && chosen <= numberedOptions.size() ? numberedOptions.get(chosen - 1) : null;
            numberedOptions = null;
        }
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

    // ------------------------------------------------------------------
    // Botoes (ADR-0040)
    // ------------------------------------------------------------------

    /**
     * Um botao que a pessoa tem na tela agora: a mensagem onde ele esta, e a
     * acao que ele devolve ao ser tocado.
     */
    record Button(long messageId, ReplyAction action) {
    }

    /**
     * O botao deste tipo, do item com este nome, na mensagem mais recente da
     * pessoa que o tenha -- como estiver agora, depois das substituicoes. O item
     * e achado pelo id que o botao carrega, e nao pelo rotulo: o botao de
     * remover nem tem o nome escrito.
     *
     * @param itemName nulo para o botao que nao e de item (fechar compra)
     * @param types o botao de carrinho e de um tipo ou de outro conforme o item
     *        ja esteja nele -- quem toca nao escolhe, toca no que esta la
     */
    Button buttonFor(String actor, String itemName, ChatAction.Type... types) {
        Set<ChatAction.Type> accepted = Set.of(types);
        Set<UUID> ids = new HashSet<>();
        if (itemName != null) {
            for (List<Object> row : fixtures.query("SELECT id FROM list_item WHERE name_normalized = ?",
                    Normalization.of(itemName))) {
                ids.add((UUID) row.get(0));
            }
        }
        for (StubOutboundMessagePort.Sent message : outbound.currentMessagesTo(externalIdFor(actor))) {
            for (List<ReplyAction> row : message.actions()) {
                for (ReplyAction button : row) {
                    ChatAction action = button.action();
                    if (accepted.contains(action.type()) && (itemName == null || ids.contains(action.itemId()))) {
                        return new Button(message.messageId(), button);
                    }
                }
            }
        }
        throw new AssertionError("%s nao tem na tela um botao %s de %s".formatted(actor, accepted, itemName));
    }

    /** Toca no botao: um <code>callback_query</code> novo, como o Telegram manda a cada toque. */
    void tap(String actor, Button button) {
        tapAs(actor, button, String.valueOf(CLICK_IDS.incrementAndGet()));
    }

    /**
     * O mesmo clique entregue duas vezes pelo provedor: o id do
     * <code>callback_query</code> repetido e o que caracteriza reentrega
     * (ADR-0005), do mesmo jeito que o <code>message_id</code> na mensagem de
     * texto.
     */
    void tapDeliveredTwice(String actor, Button button) {
        String clickId = String.valueOf(CLICK_IDS.incrementAndGet());
        tapAs(actor, button, clickId);
        tapAs(actor, button, clickId);
    }

    private void tapAs(String actor, Button button, String clickId) {
        currentActor = actor;
        lastTappedMessageId = button.messageId();
        removedBeforeLastTap = fixtures.count("SELECT count(*) FROM list_item WHERE status = 'REMOVED'");
        String externalId = externalIdFor(actor);
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("message_id", button.messageId());
        message.put("chat", Map.of("id", Long.parseLong(externalId), "type", "private"));

        Map<String, Object> click = new LinkedHashMap<>();
        click.put("id", clickId);
        click.put("from", Map.of("id", Long.parseLong(externalId), "first_name", displayNameOf(actor)));
        click.put("message", message);
        // O codec de producao, e nao uma string montada aqui: o que o cenario
        // testa e o caminho inteiro, do callback_data ao tratador.
        click.put("data", TelegramActionCodec.encode(button.action().action()));

        given().contentType(ContentType.JSON)
                .body(Map.of("update_id", MESSAGE_IDS.incrementAndGet(), "callback_query", click))
                .when().post("/webhook/telegram")
                .then().statusCode(200);
    }

    Optional<StubOutboundMessagePort.Sent> lastShownTo(String actor) {
        return outbound.lastTo(externalIdFor(actor));
    }

    List<StubOutboundMessagePort.ClickAnswer> clickAnswersTo(String actor) {
        return outbound.clickAnswersTo(externalIdFor(actor));
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
