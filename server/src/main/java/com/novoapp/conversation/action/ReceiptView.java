package com.novoapp.conversation.action;

import com.novoapp.common.message.ChatAction;
import com.novoapp.common.message.ChatReply;
import com.novoapp.common.message.ChatReply.ReplyAction;
import com.novoapp.conversation.ReceiptFormatter;
import com.novoapp.finance.ExpenseView;
import com.novoapp.finance.RegisteredExpense;
import com.novoapp.finance.ReversedExpense;
import com.novoapp.identity.ContextResolution.ResolvedContext;
import com.novoapp.identity.MemberDirectory;
import com.novoapp.shopping.CheckoutResult;
import com.novoapp.shopping.ListItemView;
import com.novoapp.shopping.ShoppingService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * O recibo de lancamento como resposta com acoes (ADR-0041): os estados da
 * mensagem de recibo, que os botoes dela redesenham.
 *
 * <p>O recibo, com os botoes; a confirmacao de fora da janela do desfazer; e o
 * estornado, sem botao nenhum. Vale para o recibo de despesa e para o de
 * fechamento, venha o lancamento de onde vier.
 *
 * <p><b>Redesenho sempre relido do banco</b>, como a lista em
 * {@link ShoppingListView}: a mensagem onde o botao estava nunca e fonte de
 * verdade. O fio e 1:1 (ADR-0008), e o lancamento pode ter sido estornado ou
 * mudado pelo outro telefone desde que o recibo chegou.
 *
 * <p>Nao sabe de teclado inline nem de edicao de mensagem: monta
 * {@link ChatReply}, e o adaptador de canal decide como ela aparece (regra 5). O
 * texto vem de {@link ReceiptFormatter}, como todo texto deste modulo
 * (ADR-0015).
 */
@ApplicationScoped
public class ReceiptView {

    @Inject
    ShoppingService shopping;

    @Inject
    MemberDirectory members;

    @Inject
    ReceiptFormatter receipts;

    /** O recibo da despesa que acabou de ser gravada, ja com os botoes. */
    public ChatReply expense(RegisteredExpense expense, ResolvedContext context) {
        return withButtons(receipts.expenseReceipt(expense, context), expense.transactionId(), context.locale());
    }

    /** O recibo do fechamento que acabou de acontecer, pelo texto ou pelo carrinho. */
    public ChatReply checkout(Locale locale, CheckoutResult.Closed closed, List<ListItemView> stillPending) {
        return withButtons(receipts.checkoutReceipt(locale, closed, stillPending), closed.transactionId(), locale);
    }

    /**
     * O recibo como o lancamento esta agora: o que <code>voltar</code> desenha.
     * Lancamento que veio de fechamento volta como recibo de fechamento, com os
     * itens -- o recibo do elo diz as duas coisas, e um redesenho que so falasse
     * do dinheiro esconderia metade do efeito.
     */
    public ChatReply receipt(ExpenseView expense, ResolvedContext context) {
        Locale locale = context.locale();
        List<String> closedItems = shopping.itemsClosedBy(context.householdId(), expense.transactionId());
        String text = closedItems.isEmpty()
                ? receipts.expenseReceipt(expense.categoryDisplayName(), expense.amountCents(),
                        expense.description(), context)
                : receipts.checkoutReceipt(locale, expense.amountCents(), expense.categoryDisplayName(),
                        closedItems, shopping.pendingItems(context.householdId()));
        return withButtons(text, expense.transactionId(), locale);
    }

    /**
     * Fora da janela (ADR-0041, item 3): o que vai sumir, e o segundo toque.
     * <code>Estornar</code> nao olha a janela; <code>voltar</code> devolve o
     * recibo.
     */
    public ChatReply confirmation(ExpenseView expense, Locale locale) {
        UUID transactionId = expense.transactionId();
        return ChatReply.of(receipts.confirmReversal(locale, expense.occurredOn(), expense.categoryDisplayName(),
                        expense.amountCents()),
                List.of(List.of(
                        new ReplyAction(receipts.reverseButton(locale),
                                ChatAction.onTransaction(ChatAction.Type.REVERSE, transactionId)),
                        new ReplyAction(receipts.backButton(locale),
                                ChatAction.onTransaction(ChatAction.Type.BACK, transactionId)))));
    }

    /** O lancamento que o botao acabou de estornar: o recibo de estorno, sem botao. */
    public ChatReply reversed(ReversedExpense reversed, ResolvedContext context) {
        return reversed(reversed.transactionId(), reversed.categoryDisplayName(), reversed.amountCents(),
                reversed.createdByMemberId(), context);
    }

    /** O lancamento que ja estava estornado, por qualquer caminho: redesenhado igual. */
    public ChatReply reversed(ExpenseView expense, ResolvedContext context) {
        return reversed(expense.transactionId(), expense.categoryDisplayName(), expense.amountCents(),
                expense.createdByMemberId(), context);
    }

    /**
     * Sem botao nenhum: nao ha o que desfazer num lancamento estornado, e estorno
     * nao tem refazer. O que voltou para a lista vai junto, como no recibo do
     * <code>desfazer</code> digitado (ADR-0032).
     */
    private ChatReply reversed(UUID transactionId, String categoryDisplayName, long amountCents,
                               UUID createdByMemberId, ResolvedContext context) {
        String createdBy = members.nameOf(createdByMemberId).orElse(null);
        List<String> reopened = shopping.reopenedItemsOf(context.householdId(), transactionId);
        return ChatReply.text(receipts.reversalReceipt(categoryDisplayName, amountCents, createdBy, reopened,
                context));
    }

    /**
     * Uma linha com o botao de desfazer, que leva o lancamento <b>deste</b>
     * recibo -- e nao "o mais recente", como o <code>desfazer</code> digitado
     * (ADR-0041, item 2). A dica de desfazer digitado continua no texto.
     */
    private ChatReply withButtons(String text, UUID transactionId, Locale locale) {
        return ChatReply.of(text, List.of(List.of(
                new ReplyAction(receipts.undoButton(locale),
                        ChatAction.onTransaction(ChatAction.Type.UNDO, transactionId)))));
    }
}
