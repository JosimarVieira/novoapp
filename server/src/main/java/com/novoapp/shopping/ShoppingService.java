package com.novoapp.shopping;

import com.novoapp.common.tenancy.HouseholdScoped;
import com.novoapp.shopping.entity.ListItem;
import com.novoapp.shopping.entity.ListItemStatus;
import com.novoapp.shopping.entity.ShoppingList;
import com.novoapp.shopping.entity.ShoppingListStatus;
import com.novoapp.shopping.repository.ListItemRepository;
import com.novoapp.shopping.repository.ShoppingListRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lista de compras do household (sdd-modulo-shopping.md).
 *
 * <p>Nao cria lancamento nenhum: marcar item comprado nao mexe em dinheiro. O
 * elo lista -> despesa e <code>fecharCompra</code>, Etapa 3.
 *
 * <p>Nao pergunta nada. Quando o item nao esta na lista, devolve
 * {@link MarkPurchasedResult.NotOnTheList} e quem transforma isso em pergunta e
 * <code>conversation</code> (ADR-0018).
 */
@ApplicationScoped
public class ShoppingService {

    @Inject
    ShoppingListRepository lists;

    @Inject
    ListItemRepository items;

    @Inject
    Clock clock;

    /**
     * Adiciona um ou varios itens numa chamada -- "acabou arroz, leite e cafe" e
     * uma mensagem so, e o recibo tambem e um so.
     *
     * <p>Item que ja esta faltando nao duplica: volta marcado como
     * {@code alreadyPending}, com quem tinha pedido antes.
     */
    @Transactional
    @HouseholdScoped
    public List<AddedItem> addItems(UUID householdId, UUID memberId,
                                    List<ItemDraft> drafts, UUID sourceMessageId) {
        ShoppingList list = activeListOrCreate(householdId);
        List<AddedItem> result = new ArrayList<>();

        for (ItemDraft draft : drafts) {
            Optional<ListItem> pending = items.findPendingByName(list.id, draft.name());
            if (pending.isPresent()) {
                ListItem existing = pending.get();
                result.add(new AddedItem(existing.id, existing.name, true, existing.requestedByMemberId));
                continue;
            }

            ListItem item = new ListItem();
            item.householdId = householdId;
            item.shoppingListId = list.id;
            item.name = draft.name().trim();
            item.quantity = draft.quantity();
            item.unit = draft.unit();
            item.status = ListItemStatus.PENDING;
            item.requestedByMemberId = memberId;
            item.sourceMessageId = sourceMessageId;
            items.persist(item);
            result.add(new AddedItem(item.id, item.name, false, memberId));
        }

        // Flush dentro do escopo: no commit o SET LOCAL ROLE ja teria voltado pro
        // papel de fora, que nao tem permissao nestas tabelas.
        items.flush();
        return result;
    }

    @Transactional
    @HouseholdScoped
    public MarkPurchasedResult markPurchased(UUID householdId, UUID memberId, String itemName) {
        Optional<ShoppingList> list = lists.findActive();
        if (list.isEmpty()) {
            return new MarkPurchasedResult.NotOnTheList(itemName);
        }

        Optional<ListItem> found = items.findPendingByName(list.get().id, itemName);
        if (found.isEmpty()) {
            return new MarkPurchasedResult.NotOnTheList(itemName);
        }

        ListItem item = found.get();
        item.status = ListItemStatus.PURCHASED;
        item.purchasedByMemberId = memberId;
        item.purchasedAt = Instant.now(clock);
        items.flush();
        return new MarkPurchasedResult.Purchased(item.name);
    }

    /**
     * Registra como ja comprado um item que nunca esteve na lista -- o "sim" da
     * pergunta do cenario "Item mencionado nao existe na lista". Entra direto
     * como {@code PURCHASED}: nunca passou por {@code PENDING} porque ninguem
     * chegou a pedir.
     */
    @Transactional
    @HouseholdScoped
    public MarkPurchasedResult.Purchased addAlreadyPurchased(UUID householdId, UUID memberId,
                                                             String itemName, UUID sourceMessageId) {
        ShoppingList list = activeListOrCreate(householdId);

        ListItem item = new ListItem();
        item.householdId = householdId;
        item.shoppingListId = list.id;
        item.name = itemName.trim();
        item.status = ListItemStatus.PURCHASED;
        item.requestedByMemberId = memberId;
        item.purchasedByMemberId = memberId;
        item.purchasedAt = Instant.now(clock);
        item.sourceMessageId = sourceMessageId;
        items.persist(item);
        items.flush();
        return new MarkPurchasedResult.Purchased(item.name);
    }

    /**
     * O que esta faltando. Household sem lista nenhuma devolve vazio, e nao erro
     * -- a diferenca entre "sem lista" e "lista sem item" nao e observavel pelo
     * usuario, e nao deve ser (sdd-modulo-shopping.md).
     */
    @Transactional
    @HouseholdScoped
    public List<ListItemView> pendingItems(UUID householdId) {
        return lists.findActive()
                .map(list -> items.listPending(list.id).stream()
                        .map(item -> new ListItemView(item.name, item.quantity, item.unit))
                        .toList())
                .orElseGet(List::of);
    }

    /** Cria a lista ativa no primeiro item, em vez de no onboarding (ADR-0011 vale so pra conta). */
    private ShoppingList activeListOrCreate(UUID householdId) {
        return lists.findActive().orElseGet(() -> {
            ShoppingList created = new ShoppingList();
            created.householdId = householdId;
            created.name = ShoppingList.DEFAULT_NAME;
            created.status = ShoppingListStatus.ACTIVE;
            lists.persist(created);
            lists.flush();
            return created;
        });
    }
}
