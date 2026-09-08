package com.novoapp.shopping.repository;

import com.novoapp.shopping.entity.ShoppingList;
import com.novoapp.shopping.entity.ShoppingListStatus;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ShoppingListRepository implements PanacheRepositoryBase<ShoppingList, UUID> {

    /**
     * A lista ativa do household. Nao filtra household_id na query de proposito:
     * quem filtra e a policy de RLS (ADR-0003).
     */
    public Optional<ShoppingList> findActive() {
        return find("status = ?1", ShoppingListStatus.ACTIVE).firstResultOptional();
    }
}
