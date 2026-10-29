package com.novoapp.acceptance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novoapp.common.i18n.MessageKey;
import com.novoapp.common.i18n.Messages;
import com.novoapp.common.message.ChatAction;
import com.novoapp.common.message.ChatReply.ReplyAction;
import com.novoapp.common.text.Normalization;
import com.novoapp.support.Fixtures;
import com.novoapp.support.StubMessageInterpreter;
import com.novoapp.support.StubOutboundMessagePort;
import io.cucumber.java.pt.Dado;
import io.cucumber.java.pt.E;
import io.cucumber.java.pt.Entao;
import io.cucumber.java.pt.Quando;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Passos de `mercado-botoes-na-lista.feature` (ADR-0040).
 *
 * <p>Arquivo proprio pelo mesmo motivo do proprio {@code .feature}: os passos
 * daqui falam de botao, carrinho e mensagem substituida, que nenhum outro
 * arquivo de passos conhece. O que e comum -- montar household, mandar
 * mensagem, conferir status de item e despesa -- continua nos arquivos de la, e
 * este reusa em vez de duplicar.
 *
 * <p><b>O clique entra pelo webhook</b>, com um {@code Update} que tem
 * <code>callback_query</code>, exatamente como a mensagem de texto entra
 * ({@link AcceptanceWorld#tap}). Atalho direto no orquestrador faria "Reentrega
 * do mesmo clique" passar sem exercitar a ADR-0005, e o codec e o normalizador
 * ficariam de fora de todo cenario.
 *
 * <p>O botao tocado e sempre um que a pessoa tem na tela -- lido do que o bot de
 * fato mandou, e nao montado aqui. "Toca no botao de carrinho" toca no que
 * estiver la, do jeito que estiver.
 */
public class AcoesNoChatSteps {

    private static final String PENDING = "PENDING";
    private static final String REMOVED = "REMOVED";

    @Inject
    AcceptanceWorld world;

    @Inject
    Fixtures fixtures;

    @Inject
    StubMessageInterpreter interpreter;

    @Inject
    ObjectMapper objectMapper;

    // ------------------------------------------------------------------
    // Contexto
    // ------------------------------------------------------------------

    @Dado("^que os itens \"([^\"]*)\", \"([^\"]*)\" e \"([^\"]*)\" estão pendentes, pedidos por \"([^\"]*)\"$")
    public void itemsPendingRequestedBy(String first, String second, String third, String requester) {
        String householdName = world.households.keySet().iterator().next();
        for (String name : List.of(first, second, third)) {
            fixtures.insertListItem(world.households.get(householdName), world.shoppingLists.get(householdName),
                    name, PENDING, world.members.get(requester));
        }
    }

    /**
     * A lista chega pelo caminho de verdade, perguntando. A chamada ao modelo que
     * isso custa e da montagem, e nao do que o cenario testa: a contagem da
     * regra 6 recomeca aqui, para "nenhuma chamada ao modelo e feita" falar so
     * do toque.
     */
    @Dado("^que \"([^\"]*)\" recebeu a lista com botões$")
    public void receivedListWithButtons(String actor) {
        world.send(actor, "o que está faltando?");
        assertThat(world.lastShownTo(actor)).hasValueSatisfying(message ->
                assertThat(message.hasActions()).as("a lista chegou com botoes").isTrue());
        interpreter.resetCallCount();
    }

    @Dado("^que \"([^\"]*)\" removeu o item \"([^\"]*)\" pelo botão$")
    public void removedByButton(String actor, String itemName) {
        receivedListWithButtons(actor);
        world.tap(actor, world.buttonFor(actor, itemName, ChatAction.Type.REMOVE_ITEM));
        assertThat(statusOf(itemName)).isEqualTo(REMOVED);
        interpreter.resetCallCount();
    }

    @Dado("^que \"([^\"]*)\" pôs o item \"([^\"]*)\" no carrinho$")
    public void putInCart(String actor, String itemName) {
        putInCart(actor, List.of(itemName));
    }

    @Dado("^que \"([^\"]*)\" pôs os itens \"([^\"]*)\" e \"([^\"]*)\" no carrinho$")
    public void putTwoInCart(String actor, String first, String second) {
        putInCart(actor, List.of(first, second));
    }

    /**
     * Historico plantado, e nao feito pelo chat: o cenario fala do fechamento
     * <b>pelo botao</b>, e um fechamento por texto aqui gastaria itens da lista
     * que o cenario cita. Fica marcado como plantado para os passos que contam
     * "a despesa registrada" nao o confundirem com a do cenario.
     */
    @Dado("^que o último fechamento do household \"([^\"]*)\" foi lançado em \"([^\"]*)\"$")
    public void lastCheckoutWasIn(String householdName, String categoryName) {
        UUID householdId = world.households.get(householdName);
        UUID memberId = world.members.values().iterator().next();
        Instant yesterday = Instant.now().minus(1, ChronoUnit.DAYS);
        UUID transactionId = fixtures.insertExpense(householdId, memberId, world.categories.get(categoryName),
                world.wallets.get(householdName), 12_345L, yesterday);
        world.plantedTransactions.add(transactionId);
        world.plantedCheckouts.add(fixtures.insertListCheckout(householdId, world.shoppingLists.get(householdName),
                transactionId, memberId, yesterday));
    }

    @Dado("^que o household \"([^\"]*)\" nunca fechou uma compra$")
    public void neverClosedAPurchase(String householdName) {
        assertThat(fixtures.count("SELECT count(*) FROM list_checkout WHERE household_id = ?",
                world.households.get(householdName))).isZero();
    }

    /**
     * O fechamento pelo proprio fluxo, inteiro: carrinho, fechar, e -- sendo o
     * primeiro da familia -- a escolha da categoria. O que o cenario do
     * <code>desfazer</code> prova e que o botao gera o mesmo fechamento do texto,
     * e isso so significa alguma coisa sobre um fechamento que o codigo de
     * verdade criou.
     */
    @Dado("^que \"([^\"]*)\" fechou pelo carrinho os itens \"([^\"]*)\" e \"([^\"]*)\" por R\\$ ([\\d.,]+)$")
    public void closedByCart(String actor, String first, String second, String amount) {
        putInCart(actor, List.of(first, second));
        world.tap(actor, world.buttonFor(actor, null, ChatAction.Type.CLOSE_CART));
        if (!openPendingOptions().isEmpty()) {
            // Primeiro fechamento da familia: a pergunta da categoria vem antes.
            world.send(actor, "1");
        }
        world.send(actor, plain(amount));
        assertThat(fixtures.count("SELECT count(*) FROM list_checkout")).isEqualTo(1);
    }

    @Dado("^que \"([^\"]*)\" enviou \"([^\"]*)\"$")
    public void hadSent(String actor, String text) {
        world.send(actor, text);
    }

    // ------------------------------------------------------------------
    // Toques
    // ------------------------------------------------------------------

    @Quando("^\"([^\"]*)\" toca no botão de remover do item \"([^\"]*)\"$")
    public void tapsRemove(String actor, String itemName) {
        world.itemInFocus = itemName;
        world.tap(actor, world.buttonFor(actor, itemName, ChatAction.Type.REMOVE_ITEM));
    }

    @Quando("^\"([^\"]*)\" toca no botão de devolver \"([^\"]*)\"$")
    public void tapsRestore(String actor, String itemName) {
        world.itemInFocus = itemName;
        world.tap(actor, world.buttonFor(actor, itemName, ChatAction.Type.RESTORE_ITEM));
    }

    @Quando("^\"([^\"]*)\" toca no botão de carrinho do item \"([^\"]*)\"$")
    public void tapsCart(String actor, String itemName) {
        world.itemInFocus = itemName;
        world.tap(actor, cartButton(actor, itemName));
    }

    /**
     * O mesmo botao, lido uma vez so: o segundo toque e no botao que a pessoa
     * tinha na tela antes de a mensagem se redesenhar. E o caso que o "estado de
     * destino" da ADR-0040 (item 6) existe para tornar inofensivo -- um
     * "alternar" tiraria o item que o primeiro toque pos.
     */
    @Quando("^\"([^\"]*)\" toca duas vezes no mesmo botão de carrinho do item \"([^\"]*)\"$")
    public void tapsCartTwice(String actor, String itemName) {
        world.itemInFocus = itemName;
        AcceptanceWorld.Button button = cartButton(actor, itemName);
        world.tap(actor, button);
        world.tap(actor, button);
    }

    @Quando("^\"([^\"]*)\" toca no botão de fechar compra$")
    public void tapsClose(String actor) {
        world.tap(actor, world.buttonFor(actor, null, ChatAction.Type.CLOSE_CART));
    }

    @Quando("^o provedor entrega duas vezes o mesmo clique de \"([^\"]*)\" no botão de remover do item \"([^\"]*)\"$")
    public void providerDeliversClickTwice(String actor, String itemName) {
        world.itemInFocus = itemName;
        world.tapDeliveredTwice(actor, world.buttonFor(actor, itemName, ChatAction.Type.REMOVE_ITEM));
    }

    // ------------------------------------------------------------------
    // A lista com botoes
    // ------------------------------------------------------------------

    @Entao("^\"([^\"]*)\" recebe uma lista contendo \"([^\"]*)\", \"([^\"]*)\" e \"([^\"]*)\"$")
    public void receivesListContainingThree(String actor, String first, String second, String third) {
        assertThat(world.lastReplyTo(actor)).contains(first).contains(second).contains(third);
    }

    /** Todo item pendente, e so eles: um par de botoes por linha. */
    @E("^cada item da lista tem um botão de carrinho e um botão de remover$")
    public void everyItemHasCartAndRemove() {
        StubOutboundMessagePort.Sent list = lastShown(world.currentActor);
        Set<UUID> pending = pendingIds();
        assertThat(itemsWithButton(list, ChatAction.Type.ADD_TO_CART, ChatAction.Type.REMOVE_FROM_CART))
                .isEqualTo(pending);
        assertThat(itemsWithButton(list, ChatAction.Type.REMOVE_ITEM)).isEqualTo(pending);
        for (List<ReplyAction> row : list.actions()) {
            if (row.stream().anyMatch(button -> button.action().type() == ChatAction.Type.REMOVE_ITEM)) {
                // Os dois botoes do item na mesma linha (ADR-0040, item 1).
                assertThat(row).hasSize(2);
                assertThat(row.get(0).action().itemId()).isEqualTo(row.get(1).action().itemId());
            }
        }
    }

    @E("^a lista não tem botão de fechar compra$")
    public void noCloseButton() {
        assertThat(lastShown(world.currentActor).actions().stream().flatMap(List::stream)
                .noneMatch(button -> button.action().type() == ChatAction.Type.CLOSE_CART)).isTrue();
    }

    @E("^a resposta não tem botões$")
    public void replyHasNoButtons() {
        assertThat(lastShown(world.currentActor).hasActions()).isFalse();
    }

    @E("^a mesma mensagem de \"([^\"]*)\" é atualizada mostrando \"([^\"]*)\" e \"([^\"]*)\"$")
    public void sameMessageUpdatedShowingTwo(String actor, String first, String second) {
        sameMessageUpdatedShowing(actor, List.of(first, second));
    }

    @E("^a mesma mensagem de \"([^\"]*)\" é atualizada mostrando \"([^\"]*)\", \"([^\"]*)\" e \"([^\"]*)\"$")
    public void sameMessageUpdatedShowingThree(String actor, String first, String second, String third) {
        sameMessageUpdatedShowing(actor, List.of(first, second, third));
    }

    @E("^a mesma mensagem de \"([^\"]*)\" é atualizada mostrando \"([^\"]*)\" no carrinho$")
    public void sameMessageUpdatedShowingInCart(String actor, String itemName) {
        StubOutboundMessagePort.Sent message = sameMessageUpdated(actor);
        assertShowsInCart(message, itemName);
    }

    @E("^a mensagem oferece um botão para devolver \"([^\"]*)\" à lista$")
    public void offersRestore(String itemName) {
        StubOutboundMessagePort.Sent message = lastShown(world.currentActor);
        UUID removed = idOf(itemName, REMOVED);
        assertThat(message.actions().stream().flatMap(List::stream)
                .filter(button -> button.action().type() == ChatAction.Type.RESTORE_ITEM)
                .filter(button -> removed.equals(button.action().itemId()))
                .map(ReplyAction::label))
                .singleElement().asString().contains(itemName);
    }

    @E("^a mensagem tem um botão de fechar compra com (\\d+) (?:item|itens)$")
    public void hasCloseButtonWith(int count) {
        String expected = count == 1
                ? Messages.get(Messages.DEFAULT, MessageKey.LIST_BUTTON_CLOSE_ONE)
                : Messages.get(Messages.DEFAULT, MessageKey.LIST_BUTTON_CLOSE_MANY, count);
        assertThat(lastShown(world.currentActor).actions().stream().flatMap(List::stream)
                .filter(button -> button.action().type() == ChatAction.Type.CLOSE_CART)
                .map(ReplyAction::label))
                .containsExactly(expected);
    }

    // ------------------------------------------------------------------
    // Carrinho
    // ------------------------------------------------------------------

    /**
     * Na lista que a outra pessoa recebe, nada indica carrinho: nem o botao de
     * tirar, nem o de fechar, nem a marca no texto. O carrinho e do fio
     * (ADR-0040, item 2).
     */
    @E("^nenhum item aparece no carrinho na lista de \"([^\"]*)\"$")
    public void nothingInCartFor(String actor) {
        StubOutboundMessagePort.Sent list = lastShown(actor);
        assertThat(itemsWithButton(list, ChatAction.Type.REMOVE_FROM_CART)).isEmpty();
        assertThat(list.actions().stream().flatMap(List::stream)
                .noneMatch(button -> button.action().type() == ChatAction.Type.CLOSE_CART)).isTrue();
        for (List<Object> row : fixtures.query("SELECT name FROM list_item WHERE status = ?", PENDING)) {
            assertThat(list.text()).doesNotContain(inCartMark((String) row.get(0)));
        }
    }

    /** O carrinho que a pessoa enxerga: o que ela separou e ainda esta pendente. */
    @Entao("^o carrinho de \"([^\"]*)\" contém apenas \"([^\"]*)\"$")
    public void cartContainsOnly(String actor, String itemName) {
        List<List<Object>> rows = fixtures.query("""
                SELECT li.name FROM chat_cart_item c JOIN list_item li ON li.id = c.list_item_id
                 WHERE c.member_id = ? AND li.status = ?""", world.members.get(actor), PENDING);
        assertThat(rows).extracting(row -> row.get(0)).containsExactly(itemName);
        assertShowsInCart(lastShown(actor), itemName);
    }

    @Entao("^a lista de \"([^\"]*)\" mostra \"([^\"]*)\" no carrinho$")
    public void listShowsInCart(String actor, String itemName) {
        StubOutboundMessagePort.Sent list = lastShown(actor);
        assertThat(list.replaced()).as("lista nova, pedida por texto").isFalse();
        assertShowsInCart(list, itemName);
    }

    /**
     * Esvaziado no banco, e nao so escondido: o que fechou sai do carrinho de
     * quem fechou (sdd-modulo-conversation.md).
     */
    @E("^o carrinho de \"([^\"]*)\" fica vazio$")
    public void cartIsEmpty(String actor) {
        assertThat(fixtures.count("SELECT count(*) FROM chat_cart_item WHERE member_id = ?",
                world.members.get(actor))).isZero();
    }

    // ------------------------------------------------------------------
    // Fechar
    // ------------------------------------------------------------------

    @E("^\"([^\"]*)\" recebe uma pergunta curta pedindo o valor, que menciona a categoria \"([^\"]*)\"$")
    public void receivesAmountQuestionNaming(String actor, String categoryName) {
        String question = world.lastReplyTo(actor);
        assertThat(fold(question)).contains("quanto foi");
        assertThat(question).contains(categoryName);
        assertThat(fixtures.count("SELECT count(*) FROM pending_action WHERE resolved_at IS NULL"))
                .isEqualTo(1);
    }

    @Entao("^a pergunta do valor menciona apenas \"([^\"]*)\"$")
    public void amountQuestionMentionsOnly(String itemName) {
        String question = world.lastReplyToCurrentActor();
        assertThat(fold(question)).contains("quanto foi");
        assertThat(question).contains(itemName);
        for (List<Object> row : fixtures.query("SELECT name FROM list_item WHERE lower(name) <> ?",
                itemName.toLowerCase(Locale.ROOT))) {
            assertThat(question).doesNotContain((String) row.get(0));
        }
    }

    /**
     * Guarda as opcoes que a pergunta numerou, na ordem em que foram
     * oferecidas: e o que da sentido a "na categoria escolhida" depois do
     * numero.
     */
    @Entao("^\"([^\"]*)\" recebe uma pergunta com as categorias de despesa numeradas$")
    public void receivesNumberedCategories(String actor) {
        String question = world.lastReplyTo(actor);
        List<String> options = openPendingOptions();
        assertThat(options).containsExactlyInAnyOrderElementsOf(fixtures.query(
                "SELECT name FROM category WHERE kind = 'EXPENSE'").stream().map(row -> (String) row.get(0)).toList());
        for (int index = 0; index < options.size(); index++) {
            assertThat(question).contains(Messages.get(Messages.DEFAULT, MessageKey.OPTION_LINE,
                    index + 1, options.get(index)));
        }
        world.numberedOptions = options;
    }

    @Entao("^uma despesa de R\\$ ([\\d.,]+) é registrada na categoria escolhida$")
    public void expenseInChosenCategory(String amount) {
        assertThat(world.chosenOption).as("a pessoa respondeu a pergunta numerada").isNotNull();
        List<List<Object>> rows = fixtures.query("""
                SELECT t.id, t.amount_cents, c.name
                FROM transaction t JOIN category c ON c.id = t.category_id
                WHERE t.reversed_at IS NULL""").stream()
                .filter(row -> !world.plantedTransactions.contains(row.get(0)))
                .toList();
        assertThat(rows).hasSize(1);
        assertThat(((Number) rows.get(0).get(1)).longValue()).isEqualTo(cents(amount));
        assertThat(rows.get(0).get(2)).isEqualTo(world.chosenOption);
    }

    @E("^o fechamento registra (\\d+) (?:item|itens)$")
    public void checkoutRegistersItems(int count) {
        List<List<Object>> rows = fixtures.query("SELECT id, items_purchased_count FROM list_checkout").stream()
                .filter(row -> !world.plantedCheckouts.contains(row.get(0)))
                .toList();
        assertThat(rows).hasSize(1);
        assertThat(((Number) rows.get(0).get(1)).intValue()).isEqualTo(count);
        assertThat(fixtures.count("SELECT count(*) FROM list_item WHERE list_checkout_id = ?", rows.get(0).get(0)))
                .isEqualTo(count);
    }

    @Entao("^os itens \"([^\"]*)\" e \"([^\"]*)\" voltam a ficar pendentes$")
    public void twoItemsBackToPending(String first, String second) {
        for (String name : List.of(first, second)) {
            assertThat(statusOf(name)).as(name).isEqualTo(PENDING);
        }
        // Voltou a ser algo que falta: quem comprou esta no historico do
        // lancamento estornado, e nao no item (ADR-0032).
        assertThat(fixtures.count("""
                SELECT count(*) FROM list_item
                 WHERE status = ? AND purchased_by_member_id IS NOT NULL""", PENDING)).isZero();
    }

    // ------------------------------------------------------------------
    // Estado velho e reentrega
    // ------------------------------------------------------------------

    @E("^\"([^\"]*)\" é avisada de que \"([^\"]*)\" já saiu da lista$")
    public void toldItemAlreadyGone(String actor, String itemName) {
        List<StubOutboundMessagePort.ClickAnswer> answers = world.clickAnswersTo(actor);
        assertThat(answers).isNotEmpty();
        assertThat(answers.get(answers.size() - 1).notice())
                .isEqualTo(Messages.get(Messages.DEFAULT, MessageKey.LIST_NOTICE_GONE, itemName));
    }

    /**
     * Uma linha em <code>inbound_message</code>, um efeito, uma resposta ao
     * clique: a segunda entrega parou no webhook, como a reentrega de texto
     * (ADR-0005).
     */
    @Entao("^o clique é processado uma única vez$")
    public void clickProcessedOnce() {
        assertThat(fixtures.count("SELECT count(*) FROM inbound_message WHERE kind = 'ACTION'")).isEqualTo(1);
        assertThat(fixtures.count("SELECT count(*) FROM list_item WHERE status = ?", REMOVED)).isEqualTo(1);
        assertThat(world.clickAnswersTo(world.currentActor)).hasSize(1);
    }

    @E("^\"([^\"]*)\" não recebe aviso de que \"([^\"]*)\" já saiu da lista$")
    public void notToldItemAlreadyGone(String actor, String itemName) {
        String gone = Messages.get(Messages.DEFAULT, MessageKey.LIST_NOTICE_GONE, itemName);
        assertThat(world.clickAnswersTo(actor)).noneMatch(answer -> gone.equals(answer.notice()));
    }

    // ------------------------------------------------------------------

    private void putInCart(String actor, List<String> itemNames) {
        receivedListWithButtons(actor);
        for (String itemName : itemNames) {
            world.tap(actor, world.buttonFor(actor, itemName, ChatAction.Type.ADD_TO_CART));
        }
        assertThat(fixtures.count("SELECT count(*) FROM chat_cart_item WHERE member_id = ?",
                world.members.get(actor))).isEqualTo(itemNames.size());
        interpreter.resetCallCount();
    }

    private AcceptanceWorld.Button cartButton(String actor, String itemName) {
        return world.buttonFor(actor, itemName, ChatAction.Type.ADD_TO_CART, ChatAction.Type.REMOVE_FROM_CART);
    }

    /** A substituicao, e nao uma mensagem nova: e a mesma mensagem onde estava o botao tocado. */
    private StubOutboundMessagePort.Sent sameMessageUpdated(String actor) {
        StubOutboundMessagePort.Sent message = lastShown(actor);
        assertThat(message.replaced()).as("a mensagem de origem foi substituida, e nao reenviada").isTrue();
        assertThat(message.messageId()).isEqualTo(world.lastTappedMessageId);
        return message;
    }

    /**
     * "Mostrando" estes itens e so eles como pendentes: cada um com o par de
     * botoes, e nenhum outro. O texto pode citar outro item -- o botao de
     * devolver cita o removido --, os botoes de item nao.
     */
    private void sameMessageUpdatedShowing(String actor, List<String> itemNames) {
        StubOutboundMessagePort.Sent message = sameMessageUpdated(actor);
        Set<UUID> expected = new HashSet<>();
        for (String itemName : itemNames) {
            assertThat(message.text()).contains(itemName);
            expected.add(idOf(itemName, PENDING));
        }
        assertThat(itemsWithButton(message, ChatAction.Type.REMOVE_ITEM)).isEqualTo(expected);
    }

    private void assertShowsInCart(StubOutboundMessagePort.Sent message, String itemName) {
        assertThat(message.text()).contains(inCartMark(itemName));
        assertThat(itemsWithButton(message, ChatAction.Type.REMOVE_FROM_CART)).contains(idOf(itemName, PENDING));
    }

    private String inCartMark(String itemName) {
        return Messages.get(Messages.DEFAULT, MessageKey.LIST_ITEM_IN_CART, itemName);
    }

    private StubOutboundMessagePort.Sent lastShown(String actor) {
        return world.lastShownTo(actor).orElseThrow(() -> new AssertionError(actor + " nao recebeu nada"));
    }

    private Set<UUID> itemsWithButton(StubOutboundMessagePort.Sent message, ChatAction.Type... types) {
        Set<ChatAction.Type> accepted = Set.of(types);
        Set<UUID> ids = new HashSet<>();
        message.actions().stream().flatMap(List::stream)
                .filter(button -> accepted.contains(button.action().type()))
                .filter(button -> button.action().itemId() != null)
                .forEach(button -> ids.add(button.action().itemId()));
        return ids;
    }

    private Set<UUID> pendingIds() {
        Set<UUID> ids = new HashSet<>();
        for (List<Object> row : fixtures.query("SELECT id FROM list_item WHERE status = ?", PENDING)) {
            ids.add((UUID) row.get(0));
        }
        return ids;
    }

    private UUID idOf(String itemName, String status) {
        List<List<Object>> rows = fixtures.query("SELECT id FROM list_item WHERE name_normalized = ? AND status = ?",
                Normalization.of(itemName), status);
        assertThat(rows).as("%s %s", itemName, status).hasSize(1);
        return (UUID) rows.get(0).get(0);
    }

    private String statusOf(String itemName) {
        List<List<Object>> rows = fixtures.query("SELECT status FROM list_item WHERE name_normalized = ?",
                Normalization.of(itemName));
        assertThat(rows).as(itemName).hasSize(1);
        return (String) rows.get(0).get(0);
    }

    /** Os rotulos da pergunta numerada em aberto, na ordem em que foram oferecidos. */
    private List<String> openPendingOptions() {
        List<List<Object>> rows = fixtures.query(
                "SELECT options_json FROM pending_action WHERE resolved_at IS NULL AND options_json IS NOT NULL");
        if (rows.isEmpty()) {
            return List.of();
        }
        try {
            return objectMapper.readValue((String) rows.get(0).get(0), new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** "60,00" do Gherkin vira "60" na mensagem que a pessoa digitaria. */
    private String plain(String amount) {
        return amount.replace(".", "").replaceAll(",00$", "").replace(',', '.');
    }

    private long cents(String amount) {
        return new BigDecimal(amount.replace(".", "").replace(',', '.')).movePointRight(2).longValueExact();
    }

    private String fold(String text) {
        return Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }
}
