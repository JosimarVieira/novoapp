package com.novoapp.conversation.action;

import com.novoapp.common.message.ChatAction;
import com.novoapp.common.message.ChatReply;
import com.novoapp.common.message.ChatReply.ReplyAction;
import com.novoapp.conversation.ReceiptFormatter;
import com.novoapp.conversation.cart.ChatCartService;
import com.novoapp.identity.ContextResolution.ResolvedContext;
import com.novoapp.shopping.ListItemSnapshot;
import com.novoapp.shopping.ListItemView;
import com.novoapp.shopping.ShoppingService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * A lista pendente como resposta com acoes (ADR-0040): cada item uma linha com
 * dois botoes, carrinho e remover.
 *
 * <p><b>Sempre relida do banco.</b> A mensagem onde o botao estava nunca e fonte
 * de verdade: o fio e 1:1 (ADR-0008), e a lista que a Ana recebeu de manha nao
 * sabe o que o Bruno fez depois. Por isso nao ha "atualizar a lista que estava
 * na mensagem" -- todo clique termina desenhando a lista de novo.
 *
 * <p>Nao sabe de teclado inline nem de edicao de mensagem: monta
 * {@link ChatReply}, e o adaptador de canal decide como ela aparece (regra 5).
 * O texto vem de {@link ReceiptFormatter}, como todo texto deste modulo
 * (ADR-0015).
 */
@ApplicationScoped
public class ShoppingListView {

    @Inject
    ShoppingService shopping;

    @Inject
    ReceiptFormatter receipts;

    @Inject
    ChatCartService cart;

    /**
     * @param restorable o item que acabou de ser removido pelo botao, que ganha o
     *        rodape "devolver" -- a volta do toque errado, no mesmo lugar onde ele
     *        aconteceu (ADR-0040, item 5). Nulo no caso comum
     * @return so texto quando nao ha item nem nada a devolver: lista vazia nao
     *         tem botao
     */
    public ChatReply render(ResolvedContext context, Locale locale, ListItemSnapshot restorable) {
        List<ListItemView> pending = shopping.pendingItems(context.householdId());
        Set<UUID> inCart = cartOf(context, pending);

        List<List<ReplyAction>> rows = new ArrayList<>();
        for (ListItemView item : pending) {
            boolean carted = inCart.contains(item.id());
            // O botao carrega o estado de destino, nunca "alternar" (ADR-0040,
            // item 6): o que esta no carrinho oferece tirar, e vice-versa.
            ChatAction cart = new ChatAction(carted ? ChatAction.Type.REMOVE_FROM_CART : ChatAction.Type.ADD_TO_CART,
                    item.id());
            rows.add(List.of(
                    new ReplyAction(receipts.cartButton(locale, item.name(), carted), cart),
                    new ReplyAction(receipts.removeButton(locale),
                            new ChatAction(ChatAction.Type.REMOVE_ITEM, item.id()))));
        }
        if (restorable != null) {
            rows.add(List.of(new ReplyAction(receipts.restoreButton(locale, restorable.name()),
                    new ChatAction(ChatAction.Type.RESTORE_ITEM, restorable.id()))));
        }
        if (!inCart.isEmpty()) {
            rows.add(List.of(new ReplyAction(receipts.closeCartButton(locale, inCart.size()),
                    ChatAction.closeCart())));
        }
        return ChatReply.of(receipts.pendingList(locale, pending, inCart), rows);
    }

    /**
     * O carrinho de quem le, cruzado com o que ainda esta pendente: item que
     * outra pessoa fechou ou removeu sai do carrinho em silencio, porque ja nao
     * esta na lista (ADR-0040, item 3).
     */
    private Set<UUID> cartOf(ResolvedContext context, List<ListItemView> pending) {
        Set<UUID> carted = cart.itemIds(context);
        Set<UUID> inCart = new LinkedHashSet<>();
        pending.stream().map(ListItemView::id).filter(carted::contains).forEach(inCart::add);
        return inCart;
    }
}
