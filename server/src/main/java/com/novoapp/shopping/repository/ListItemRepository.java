package com.novoapp.shopping.repository;

import com.novoapp.common.text.Normalization;
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
     * Comparacao pela forma normalizada -- minuscula e sem acento (ADR-0030) --,
     * a mesma do indice unico parcial que sustenta "item repetido nao duplica"
     * (sdd-modulo-shopping.md).
     *
     * <p>Ate 2026-09-16 os dois eram <code>lower(name)</code>, e entao "acabou
     * cafe" com "Café" ja pendente inseria um segundo item: a consulta nao
     * achava o primeiro e o indice nao barrava o segundo. Sem pergunta, sem
     * aviso -- o pior desfecho possivel dos dois.
     */
    public Optional<ListItem> findPendingByName(UUID shoppingListId, String name) {
        return find("shoppingListId = ?1 and status = ?2 and nameNormalized = ?3",
                shoppingListId, ListItemStatus.PENDING, Normalization.of(name))
                .firstResultOptional();
    }
}
