package com.novoapp.shopping.repository;

import com.novoapp.shopping.entity.ListItem;
import com.novoapp.shopping.entity.ListItemStatus;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ListItemRepository implements PanacheRepositoryBase<ListItem, UUID> {

    /** O que esta faltando, na ordem em que foi pedido. */
    public List<ListItem> listPending(UUID shoppingListId) {
        return list("shoppingListId = ?1 and status = ?2 order by createdAt",
                shoppingListId, ListItemStatus.PENDING);
    }

    /**
     * Comparacao sem diferenciar maiuscula/minuscula, igual ao indice unico
     * parcial que sustenta "item repetido nao duplica" (sdd-modulo-shopping.md).
     */
    public Optional<ListItem> findPendingByName(UUID shoppingListId, String name) {
        return find("shoppingListId = ?1 and status = ?2 and lower(name) = ?3",
                shoppingListId, ListItemStatus.PENDING, name.trim().toLowerCase(java.util.Locale.ROOT))
                .firstResultOptional();
    }
}
