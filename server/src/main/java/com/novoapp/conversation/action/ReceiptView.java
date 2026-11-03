package com.novoapp.conversation.action;

import com.novoapp.common.message.ChatAction;
import com.novoapp.common.message.ChatReply;
import com.novoapp.common.message.ChatReply.ReplyAction;
import com.novoapp.conversation.ReceiptFormatter;
import com.novoapp.finance.CategoryQueryService;
import com.novoapp.finance.CategoryView;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * O recibo de lancamento como resposta com acoes (ADR-0041): os estados da
 * mensagem de recibo, que os botoes dela redesenham.
 *
 * <p>O recibo, com os botoes; o teclado de categorias; a confirmacao de fora da
 * janela do desfazer; e o
 * estornado, sem botao nenhum -- o recibo de estorno, quando foi o toque que
 * estornou, ou o estado estornado, quando ja estava. Vale para o recibo de
 * despesa e para o de fechamento, venha o lancamento de onde vier.
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
    CategoryQueryService categories;

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
     * O teclado de troca (ADR-0041, item 4): as categorias de despesa da familia,
     * a mesma lista e na mesma forma da pergunta do primeiro fechamento
     * (ADR-0040, item 4), a atual marcada, duas por linha, e <code>voltar</code>.
     * Cada botao leva o lancamento e a categoria de destino -- o estado de
     * destino, nunca "a proxima". Sem paginacao: familia com mais categorias do
     * que o teclado comporta e gatilho de revisao da ADR, nao escopo.
     */
    public ChatReply categories(ExpenseView expense, Locale locale) {
        UUID transactionId = expense.transactionId();
        List<List<ReplyAction>> rows = new ArrayList<>();
        List<ReplyAction> row = new ArrayList<>();
        for (CategoryView category : categories.listExpenseCategories()) {
            row.add(new ReplyAction(
                    receipts.categoryButton(locale, category.displayName(), category.id().equals(expense.categoryId())),
                    ChatAction.setCategory(transactionId, category.id())));
            if (row.size() == 2) {
                rows.add(row);
                row = new ArrayList<>();
            }
        }
        if (!row.isEmpty()) {
            rows.add(row);
        }
        rows.add(List.of(new ReplyAction(receipts.backButton(locale),
                ChatAction.onTransaction(ChatAction.Type.BACK, transactionId))));
        return ChatReply.of(receipts.askNewCategory(locale, expense.amountCents(), expense.categoryDisplayName()),
                rows);
    }

    /**
     * Fora da janela (ADR-0041, item 3): o que vai sumir, e o segundo toque.
     * <code>Estornar</code> nao olha a janela; <code>voltar</code> devolve o
     * recibo.
     */
    public ChatReply confirmation(ExpenseView expense, ResolvedContext context) {
        Locale locale = context.locale();
        UUID transactionId = expense.transactionId();
        List<String> closedItems = shopping.itemsClosedBy(context.householdId(), transactionId);
        return ChatReply.of(receipts.confirmReversal(locale, expense.occurredOn(), expense.categoryDisplayName(),
                        expense.amountCents(), expense.description(), closedItems),
                List.of(List.of(
                        new ReplyAction(receipts.reverseButton(locale),
                                ChatAction.onTransaction(ChatAction.Type.REVERSE, transactionId)),
                        new ReplyAction(receipts.backButton(locale),
                                ChatAction.onTransaction(ChatAction.Type.BACK, transactionId)))));
    }

    /**
     * O lancamento que este toque acabou de estornar: o recibo de estorno, sem
     * botao (ADR-0041, item 2). Sem botao nenhum porque nao ha o que desfazer num
     * lancamento estornado, e estorno nao tem refazer. O que voltou para a lista
     * vai junto, como no recibo do <code>desfazer</code> digitado (ADR-0032).
     */
    public ChatReply reversed(ReversedExpense reversed, ResolvedContext context) {
        String createdBy = members.nameOf(reversed.createdByMemberId()).orElse(null);
        List<String> reopened = shopping.reopenedItemsOf(context.householdId(), reversed.transactionId());
        return ChatReply.text(receipts.reversalReceipt(reversed.categoryDisplayName(), reversed.amountCents(),
                createdBy, reopened, context));
    }

    /**
     * O lancamento que ja estava estornado quando o botao foi tocado, por
     * qualquer caminho: o estado estornado, sem botao, dizendo quando -- e por
     * quem, se nao foi quem tocou.
     */
    public ChatReply alreadyReversed(ExpenseView expense, ResolvedContext context) {
        String reversedBy = expense.reversedByMemberId() == null
                || expense.reversedByMemberId().equals(context.memberId())
                ? null
                : members.nameOf(expense.reversedByMemberId()).orElse(null);
        List<String> reopened = shopping.reopenedItemsOf(context.householdId(), expense.transactionId());
        return ChatReply.text(receipts.reversedState(expense.reversedAt(), expense.categoryDisplayName(),
                expense.amountCents(), reversedBy, reopened, context.locale()));
    }

    /**
     * Uma linha com os dois botoes do recibo (ADR-0041, item 1), que levam o
     * lancamento <b>deste</b> recibo -- e nao "o mais recente", como o
     * <code>desfazer</code> digitado (item 2). A dica de desfazer digitado
     * continua no texto.
     */
    private ChatReply withButtons(String text, UUID transactionId, Locale locale) {
        return ChatReply.of(text, List.of(List.of(
                new ReplyAction(receipts.undoButton(locale),
                        ChatAction.onTransaction(ChatAction.Type.UNDO, transactionId)),
                new ReplyAction(receipts.changeCategoryButton(locale),
                        ChatAction.onTransaction(ChatAction.Type.CHANGE_CATEGORY, transactionId)))));
    }
}
