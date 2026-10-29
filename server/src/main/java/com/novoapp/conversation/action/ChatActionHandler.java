package com.novoapp.conversation.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novoapp.common.message.ChatAction;
import com.novoapp.common.message.InboundMessage;
import com.novoapp.conversation.ProcessingOutcome;
import com.novoapp.conversation.ProcessingOutcome.Result;
import com.novoapp.conversation.ReceiptFormatter;
import com.novoapp.conversation.Reply;
import com.novoapp.conversation.cart.ChatCartService;
import com.novoapp.identity.ContextResolution.ResolvedContext;
import com.novoapp.shopping.ItemDraft;
import com.novoapp.shopping.ListItemSnapshot;
import com.novoapp.shopping.RemoveItemResult;
import com.novoapp.shopping.ShoppingService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolve uma Acao de botao sem modelo (ADR-0040, regra 6 do CLAUDE.md).
 *
 * <p>Entra no fluxo <b>antes</b> da busca por pendencia, e por isso um toque
 * nunca e lido como resposta a pergunta aberta: o botao foi desenhado pelo
 * proprio bot, e nao ha o que interpretar. Clique nao resolve nem supera
 * pendencia (sdd-modulo-conversation.md).
 *
 * <p>Toda acao e conferida contra o banco antes de escrever. Item que ja nao
 * esta pendente nao e erro: quem tocou recebe um aviso curto e a lista
 * redesenhada com o estado atual -- e o que torna inofensivo o clique numa lista
 * que outro membro mudou desde que ela foi enviada (ADR-0008).
 *
 * <p>Remover e devolver chamam os mesmos metodos de <code>shopping</code> que o
 * texto chama, com o nome atual do item: o botao carrega o id, e este tratador o
 * traduz. Um segundo caminho de escrita, por id, seria um segundo lugar onde a
 * regra pode divergir (sdd-modulo-shopping.md).
 *
 * <p>Nenhum desfecho daqui carrega proveniencia: clique nao gasta modelo, e
 * <code>prompt_version</code> e <code>model_name</code> ficam nulos (ADR-0036).
 */
@ApplicationScoped
public class ChatActionHandler {

    private static final Logger LOG = Logger.getLogger(ChatActionHandler.class);

    @Inject
    ShoppingService shopping;

    @Inject
    ShoppingListView listView;

    @Inject
    ChatCartService cart;

    @Inject
    ReceiptFormatter receipts;

    @Inject
    ObjectMapper objectMapper;

    public ProcessingOutcome handle(InboundMessage message, ResolvedContext context, Reply reply) {
        ChatAction action = message.action();
        return switch (action.type()) {
            case ADD_TO_CART -> addToCart(action.itemId(), context, reply);
            case REMOVE_FROM_CART -> removeFromCart(action.itemId(), context, reply);
            case REMOVE_ITEM -> remove(action.itemId(), context, reply);
            case RESTORE_ITEM -> restore(action.itemId(), message.id(), context, reply);
            // O fechamento entra no passo 3 da ADR-0040. Ate la o toque so
            // redesenha: responder ao clique e obrigatorio de qualquer jeito.
            case CLOSE_CART -> {
                redraw(context, reply, null, null);
                yield new ProcessingOutcome(Result.INTERPRETED, null, null);
            }
        };
    }

    /**
     * "Peguei" (ADR-0040, item 2): o item vai para o carrinho de quem tocou, e
     * <b>so</b> para ele. Nao toca <code>list_item</code> -- para a familia o
     * item continua pendente, e continua ao alcance do fechamento.
     */
    private ProcessingOutcome addToCart(UUID itemId, ResolvedContext context, Reply reply) {
        Optional<ListItemSnapshot> item = shopping.item(context.householdId(), itemId);
        if (item.isEmpty() || !item.get().pending()) {
            return alreadyGone(item, context, reply);
        }
        cart.put(context, itemId);
        redraw(context, reply, null, null);
        return executed("carrinho+", item.get().name());
    }

    /**
     * Tirar do carrinho vale mesmo para item que ja saiu da lista -- e so
     * arrumacao do fio. O aviso continua: quem tocou ainda acha que o item esta
     * la.
     */
    private ProcessingOutcome removeFromCart(UUID itemId, ResolvedContext context, Reply reply) {
        Optional<ListItemSnapshot> item = shopping.item(context.householdId(), itemId);
        cart.take(context, itemId);
        if (item.isEmpty() || !item.get().pending()) {
            return alreadyGone(item, context, reply);
        }
        redraw(context, reply, null, null);
        return executed("carrinho-", item.get().name());
    }

    /**
     * Remover pelo botao e o mesmo <code>removeItems</code> do texto -- e, como
     * no texto, fica fora do <code>desfazer</code> (ADR-0039). A volta e o
     * rodape "devolver", no mesmo lugar onde o toque errado aconteceu.
     */
    private ProcessingOutcome remove(UUID itemId, ResolvedContext context, Reply reply) {
        Optional<ListItemSnapshot> item = shopping.item(context.householdId(), itemId);
        if (item.isEmpty() || !item.get().pending()) {
            return alreadyGone(item, context, reply);
        }

        String name = item.get().name();
        RemoveItemResult result = shopping.removeItems(context.householdId(), context.memberId(), List.of(name))
                .get(0);
        if (result instanceof RemoveItemResult.NotOnTheList) {
            // Outro membro tirou o item entre a leitura e a escrita.
            return alreadyGone(item, context, reply);
        }
        redraw(context, reply, item.get(), null);
        return executed("remover", name);
    }

    /**
     * Devolver e um <code>addItems</code> comum -- o mesmo caminho de "acabou o
     * chocolate" depois de uma remocao, que a ADR-0039 ja apontava como a volta.
     * O item volta pedido por quem tocou, e nao por quem tinha avisado que
     * faltava: e o custo declarado na ADR-0040.
     *
     * <p>Item que ja voltou por outro caminho nao duplica: <code>addItems</code>
     * o reconhece como ja pendente, e o toque so redesenha.
     */
    private ProcessingOutcome restore(UUID itemId, UUID sourceMessageId, ResolvedContext context, Reply reply) {
        Optional<ListItemSnapshot> item = shopping.item(context.householdId(), itemId);
        if (item.isEmpty()) {
            return alreadyGone(item, context, reply);
        }

        String name = item.get().name();
        shopping.addItems(context.householdId(), context.memberId(), List.of(ItemDraft.of(name)), sourceMessageId);
        redraw(context, reply, null, null);
        return executed("devolver", name);
    }

    /** O banco disse que o clique ja nao vale. Aviso curto, e a lista como ela esta agora. */
    private ProcessingOutcome alreadyGone(Optional<ListItemSnapshot> item, ResolvedContext context, Reply reply) {
        redraw(context, reply, null, receipts.itemAlreadyGone(reply.locale(),
                item.map(ListItemSnapshot::name).orElse(null)));
        return new ProcessingOutcome(Result.INTERPRETED, null, null);
    }

    /** Todo clique termina aqui, ou numa pergunta: a mensagem de origem e substituida pela lista relida. */
    private void redraw(ResolvedContext context, Reply reply, ListItemSnapshot restorable, String notice) {
        reply.send(listView.render(context, reply.locale(), restorable).replacingOrigin().withNotice(notice));
    }

    /**
     * O registro do clique para a Etapa 5, no vocabulario do glossario
     * (<code>remover</code>, <code>devolver</code>...). A coluna
     * <code>kind</code> ja separa clique de texto; isto diz qual clique foi.
     */
    private ProcessingOutcome executed(String action, String itemName) {
        Map<String, Object> intent = new LinkedHashMap<>();
        intent.put("acao", action);
        intent.put("item", itemName);
        return new ProcessingOutcome(Result.EXECUTED, null, json(intent));
    }

    private String json(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            LOG.warnf(e, "Nao foi possivel serializar o intent_json de %s", value);
            return null;
        }
    }
}
