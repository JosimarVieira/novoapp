package com.novoapp.acceptance;

import com.novoapp.common.i18n.MessageKey;
import com.novoapp.common.i18n.Messages;
import com.novoapp.common.message.ChatAction;
import com.novoapp.common.message.ChatReply.ReplyAction;
import com.novoapp.support.Fixtures;
import com.novoapp.support.StubMessageInterpreter;
import com.novoapp.support.StubOutboundMessagePort;
import io.cucumber.java.pt.Dado;
import io.cucumber.java.pt.E;
import io.cucumber.java.pt.Quando;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Passos de `financas-acoes-no-recibo.feature` (ADR-0041).
 *
 * <p>Arquivo proprio pelo mesmo motivo do proprio {@code .feature}: os passos
 * daqui falam do recibo como mensagem com botoes, que nenhum outro arquivo de
 * passos conhece. Montar household, mandar mensagem e conferir despesa
 * continuam nos arquivos de la, e este reusa em vez de duplicar.
 *
 * <p><b>O recibo tocado e sempre o que a pessoa recebeu</b>, no estado em que
 * ele esta agora no fio -- lido do que o bot de fato mandou e substituiu, e nao
 * montado aqui. O toque entra pelo webhook com o codec de producao
 * ({@link AcceptanceWorld#tap}), como na lista: "Reentrega do mesmo clique"
 * exercita a ADR-0005 de verdade.
 *
 * <p>A chamada ao modelo que montar um lancamento custa e da montagem, e nao do
 * que o cenario testa: a contagem da regra 6 recomeca depois de cada passo de
 * contexto, e "nenhuma chamada ao modelo e feita" fala so do toque.
 */
public class AcoesNoReciboSteps {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Inject
    AcceptanceWorld world;

    @Inject
    Fixtures fixtures;

    @Inject
    StubMessageInterpreter interpreter;

    @Inject
    StubOutboundMessagePort outbound;

    /**
     * A janela vem da config, e nunca de um numero escrito aqui: 24 horas e valor
     * provisorio (decisao aberta #30), e um passo com o numero cristalizaria o
     * que a decisao deixou em aberto -- o mesmo motivo de o prazo do carrinho nao
     * ter cenario.
     */
    @ConfigProperty(name = "novoapp.conversation.receipt.undo-window")
    Duration undoWindow;

    // ------------------------------------------------------------------
    // Contexto
    // ------------------------------------------------------------------

    @Dado("^que \"([^\"]*)\" registrou \"([^\"]*)\" e recebeu o recibo$")
    public void registeredAndGotReceipt(String actor, String text) {
        receiptArrived(actor, text, register(actor, text));
    }

    @Dado("^que \"([^\"]*)\" registrou \"([^\"]*)\" depois e recebeu o recibo$")
    public void registeredLaterAndGotReceipt(String actor, String text) {
        UUID transactionId = register(actor, text);
        assertLatest(transactionId);
        receiptArrived(actor, text, transactionId);
    }

    @Dado("^que \"([^\"]*)\" registrou \"([^\"]*)\"$")
    public void registered(String actor, String text) {
        register(actor, text);
    }

    @Dado("^que \"([^\"]*)\" registrou \"([^\"]*)\" depois$")
    public void registeredLater(String actor, String text) {
        assertLatest(register(actor, text));
    }

    /** O fechamento pelo proprio fluxo, inteiro: e o recibo dele que o cenario toca. */
    @Dado("^que \"([^\"]*)\" enviou \"([^\"]*)\" e recebeu o recibo$")
    public void sentAndGotReceipt(String actor, String text) {
        receiptArrived(actor, text, register(actor, text));
    }

    /** A pergunta que o desfazer pelo botao nao pode cancelar (ADR-0041, item 8). */
    @Dado("^que \"([^\"]*)\" enviou \"([^\"]*)\" e recebeu uma pergunta pedindo o valor$")
    public void sentAndGotAmountQuestion(String actor, String text) {
        world.send(actor, text);
        assertThat(fold(world.lastReplyTo(actor))).contains("quanto foi");
        assertThat(openQuestions()).isEqualTo(1);
        interpreter.resetCallCount();
    }

    /**
     * Recua o lancamento para alem da janela, como um recibo de dias atras. A data
     * do lancamento recua junto: pelo chat, ela e o dia em que ele foi gravado.
     */
    @Dado("^que o lançamento de \"([^\"]*)\" está fora da janela do desfazer$")
    public void outsideTheUndoWindow(String text) {
        Instant recorded = Instant.now().minus(undoWindow).minus(1, ChronoUnit.MINUTES);
        fixtures.execute("UPDATE transaction SET created_at = ?, occurred_on = ? WHERE id = ?",
                Timestamp.from(recorded), Date.valueOf(recorded.atZone(ZoneId.systemDefault()).toLocalDate()),
                receiptOf(text).transactionId());
    }

    @Dado("^que \"([^\"]*)\" tocou no botão de desfazer do recibo de \"([^\"]*)\"$")
    public void tappedUndo(String actor, String text) {
        tapsUndo(actor, text);
    }

    // ------------------------------------------------------------------
    // Toques
    // ------------------------------------------------------------------

    @Quando("^\"([^\"]*)\" toca no botão de desfazer do recibo de \"([^\"]*)\"$")
    public void tapsUndo(String actor, String text) {
        world.tap(actor, button(receiptOf(text), ChatAction.Type.UNDO));
    }

    @Quando("^\"([^\"]*)\" toca no botão de desfazer do recibo do fechamento$")
    public void tapsUndoOnCheckoutReceipt(String actor) {
        world.tap(actor, button(checkoutReceipt(), ChatAction.Type.UNDO));
    }

    @Quando("^\"([^\"]*)\" toca no botão de desfazer do recibo$")
    public void tapsUndoOnReceiptInFocus(String actor) {
        world.tap(actor, button(focus(), ChatAction.Type.UNDO));
    }

    /**
     * O mesmo botao, lido uma vez so: o segundo toque e no botao que a pessoa
     * ainda tinha na tela antes de a mensagem se redesenhar. E o caso que o alvo
     * no botao existe para tornar inofensivo -- o desfazer digitado, no segundo
     * toque, estornaria o proximo da fila (ADR-0041, item 7).
     */
    @Quando("^\"([^\"]*)\" toca duas vezes no botão de desfazer do recibo de \"([^\"]*)\"$")
    public void tapsUndoTwice(String actor, String text) {
        AcceptanceWorld.Button undo = button(receiptOf(text), ChatAction.Type.UNDO);
        world.tap(actor, undo);
        world.tap(actor, undo);
    }

    @Quando("^o Telegram entrega duas vezes o mesmo toque de \"([^\"]*)\" no botão de desfazer do recibo de \"([^\"]*)\"$")
    public void telegramDeliversTheTapTwice(String actor, String text) {
        world.tapDeliveredTwice(actor, button(receiptOf(text), ChatAction.Type.UNDO));
    }

    @Quando("^\"([^\"]*)\" toca no botão de estornar$")
    public void tapsReverse(String actor) {
        world.tap(actor, button(focus(), ChatAction.Type.REVERSE));
    }

    @Quando("^\"([^\"]*)\" toca no botão de voltar$")
    public void tapsBack(String actor) {
        world.tap(actor, button(focus(), ChatAction.Type.BACK));
    }

    // ------------------------------------------------------------------
    // Lancamentos
    // ------------------------------------------------------------------

    @E("^a despesa de R\\$ ([\\d.,]+) continua de pé$")
    public void expenseStillStanding(String amount) {
        assertThat(fixtures.count("SELECT count(*) FROM transaction WHERE amount_cents = ? AND reversed_at IS NULL",
                cents(amount))).isEqualTo(1);
    }

    @E("^nenhum lançamento é estornado$")
    public void nothingReversed() {
        assertThat(fixtures.count("SELECT count(*) FROM transaction WHERE reversed_at IS NOT NULL")).isZero();
    }

    // ------------------------------------------------------------------
    // A mensagem de recibo
    // ------------------------------------------------------------------

    /**
     * O recibo de estorno do lancamento daquele recibo, no lugar dele, sem botao
     * nenhum (ADR-0041, item 2): nao ha o que desfazer num lancamento estornado.
     */
    @E("^a mesma mensagem de \"([^\"]*)\" é atualizada como estornada, sem botões$")
    public void sameMessageShownAsReversed(String actor) {
        StubOutboundMessagePort.Sent message = sameMessageUpdated(actor);
        assertThat(message.hasActions()).as("recibo estornado nao tem botao").isFalse();
        Entry entry = entry(focus().transactionId());
        assertThat(entry.reversed()).as("o lancamento do recibo foi estornado").isTrue();
        assertThat(message.text()).startsWith(Messages.get(Messages.DEFAULT, MessageKey.REVERSAL_RECEIPT,
                entry.categoryName(), formatAmount(entry.amountCents())));
    }

    @E("^\"([^\"]*)\" recebe um aviso curto de que o lançamento já foi estornado$")
    public void toldAlreadyReversed(String actor) {
        List<StubOutboundMessagePort.ClickAnswer> answers = world.clickAnswersTo(actor);
        assertThat(answers).isNotEmpty();
        assertThat(answers.get(answers.size() - 1).notice())
                .isEqualTo(Messages.get(Messages.DEFAULT, MessageKey.RECEIPT_NOTICE_ALREADY_REVERSED));
    }

    /**
     * A confirmacao de fora da janela (ADR-0041, item 3): a data do lancamento, a
     * categoria e o valor -- o que vai sumir --, no lugar do recibo.
     */
    @E("^a mesma mensagem de \"([^\"]*)\" é atualizada pedindo confirmação, com a data, a categoria \"([^\"]*)\" e o valor R\\$ ([\\d.,]+)$")
    public void sameMessageAsksConfirmation(String actor, String categoryName, String amount) {
        StubOutboundMessagePort.Sent message = sameMessageUpdated(actor);
        Entry entry = entry(focus().transactionId());
        assertThat(message.text()).isEqualTo(Messages.get(Messages.DEFAULT, MessageKey.RECEIPT_UNDO_CONFIRM,
                DAY.format(entry.occurredOn()), categoryName, "R$ " + amount));
    }

    @E("^a mensagem tem um botão de estornar e um botão de voltar$")
    public void messageHasReverseAndBack() {
        assertThat(buttonTypes(lastShown(world.currentActor)))
                .containsExactlyInAnyOrder(ChatAction.Type.REVERSE, ChatAction.Type.BACK);
    }

    // ------------------------------------------------------------------

    /**
     * Manda a mensagem pelo webhook e devolve o lancamento que ela criou -- um so.
     * A chamada ao modelo que isso custa e da montagem.
     */
    private UUID register(String actor, String text) {
        Set<UUID> before = transactionIds();
        world.send(actor, text);
        Set<UUID> created = transactionIds();
        created.removeAll(before);
        assertThat(created).as("\"%s\" de %s criou um lancamento", text, actor).hasSize(1);
        interpreter.resetCallCount();
        return created.iterator().next();
    }

    /**
     * "Depois" e o que da sentido ao cenario: o desfazer digitado alcanca o mais
     * recente (ADR-0025), e o botao, o do recibo. Conferido em vez de suposto.
     */
    private void assertLatest(UUID transactionId) {
        assertThat(fixtures.count("""
                SELECT count(*) FROM transaction
                 WHERE id <> ? AND created_at >= (SELECT created_at FROM transaction WHERE id = ?)""",
                transactionId, transactionId)).isZero();
    }

    /** O recibo que acabou de chegar, com o botao de desfazer do lancamento que ele confirma. */
    private void receiptArrived(String actor, String text, UUID transactionId) {
        StubOutboundMessagePort.Sent message = lastShown(actor);
        assertThat(message.replaced()).as("recibo e mensagem nova").isFalse();
        assertThat(message.actions().stream().flatMap(List::stream)
                .filter(button -> button.action().type() == ChatAction.Type.UNDO)
                .map(button -> button.action().transactionId()))
                .as("o botao de desfazer do recibo leva o lancamento dele")
                .containsExactly(transactionId);
        AcceptanceWorld.Receipt receipt = new AcceptanceWorld.Receipt(message.messageId(), transactionId,
                message.text());
        world.receipts.put(text, receipt);
        world.receiptInFocus = receipt;
    }

    private AcceptanceWorld.Receipt receiptOf(String text) {
        AcceptanceWorld.Receipt receipt = world.receipts.get(text);
        assertThat(receipt).as("ninguem recebeu o recibo de \"%s\"", text).isNotNull();
        return receipt;
    }

    /** O recibo cujo lancamento veio de um fechamento -- ha um so por cenario. */
    private AcceptanceWorld.Receipt checkoutReceipt() {
        return world.receipts.values().stream()
                .filter(receipt -> fixtures.count("SELECT count(*) FROM list_checkout WHERE transaction_id = ?",
                        receipt.transactionId()) == 1)
                .findFirst()
                .orElseThrow(() -> new AssertionError("ninguem recebeu o recibo de um fechamento"));
    }

    private AcceptanceWorld.Receipt focus() {
        assertThat(world.receiptInFocus).as("nenhum recibo em foco").isNotNull();
        return world.receiptInFocus;
    }

    /**
     * O botao deste tipo no recibo, como a mensagem esta agora no fio -- e
     * levando o lancamento daquele recibo. O recibo passa a ser o do cenario.
     */
    private AcceptanceWorld.Button button(AcceptanceWorld.Receipt receipt, ChatAction.Type type) {
        world.receiptInFocus = receipt;
        StubOutboundMessagePort.Sent message = outbound.current(receipt.messageId())
                .orElseThrow(() -> new AssertionError("a mensagem do recibo sumiu do fio"));
        for (List<ReplyAction> row : message.actions()) {
            for (ReplyAction button : row) {
                if (button.action().type() == type && receipt.transactionId().equals(button.action().transactionId())) {
                    return new AcceptanceWorld.Button(receipt.messageId(), button);
                }
            }
        }
        throw new AssertionError("o recibo nao tem na tela um botao %s: %s".formatted(type, message));
    }

    /** A substituicao, e nao uma mensagem nova: e o recibo onde estava o botao tocado. */
    private StubOutboundMessagePort.Sent sameMessageUpdated(String actor) {
        StubOutboundMessagePort.Sent message = lastShown(actor);
        assertThat(message.replaced()).as("a mensagem de origem foi substituida, e nao reenviada").isTrue();
        assertThat(message.messageId()).isEqualTo(world.lastTappedMessageId);
        assertThat(message.messageId()).isEqualTo(focus().messageId());
        return message;
    }

    private StubOutboundMessagePort.Sent lastShown(String actor) {
        return world.lastShownTo(actor).orElseThrow(() -> new AssertionError(actor + " nao recebeu nada"));
    }

    private List<ChatAction.Type> buttonTypes(StubOutboundMessagePort.Sent message) {
        List<ReplyAction> buttons = message.actions().stream().flatMap(List::stream).toList();
        assertThat(buttons).as("todo botao leva o lancamento do recibo")
                .allMatch(button -> focus().transactionId().equals(button.action().transactionId()));
        return buttons.stream().map(button -> button.action().type()).toList();
    }

    private record Entry(long amountCents, String categoryName, LocalDate occurredOn, boolean reversed) {
    }

    private Entry entry(UUID transactionId) {
        List<List<Object>> rows = fixtures.query("""
                SELECT t.amount_cents, c.name, t.occurred_on, t.reversed_at IS NOT NULL
                FROM transaction t JOIN category c ON c.id = t.category_id
                WHERE t.id = ?""", transactionId);
        assertThat(rows).hasSize(1);
        List<Object> row = rows.get(0);
        return new Entry(((Number) row.get(0)).longValue(), (String) row.get(1),
                ((Date) row.get(2)).toLocalDate(), (Boolean) row.get(3));
    }

    private Set<UUID> transactionIds() {
        Set<UUID> ids = new HashSet<>();
        for (List<Object> row : fixtures.query("SELECT id FROM transaction")) {
            ids.add((UUID) row.get(0));
        }
        return ids;
    }

    private long openQuestions() {
        return fixtures.count("SELECT count(*) FROM pending_action WHERE resolved_at IS NULL");
    }

    private String formatAmount(long amountCents) {
        return "R$ " + String.format(Locale.of("pt", "BR"), "%,.2f", BigDecimal.valueOf(amountCents).movePointLeft(2));
    }

    private long cents(String amount) {
        return new BigDecimal(amount.replace(".", "").replace(',', '.')).movePointRight(2).longValueExact();
    }

    private String fold(String text) {
        return Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }
}
