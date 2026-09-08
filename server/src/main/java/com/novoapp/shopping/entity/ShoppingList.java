package com.novoapp.shopping.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Lista de compras ativa do household (glossario). */
@Entity
@Table(name = "shopping_list")
public class ShoppingList extends PanacheEntityBase {

    /**
     * Nome da lista criada sob demanda no primeiro item. A familia nunca escolhe
     * este nome nesta etapa -- so existe uma lista ativa, e nomear varias e a
     * decisao aberta #15.
     */
    public static final String DEFAULT_NAME = "Compras";

    @Id
    @GeneratedValue
    public UUID id;

    @Column(name = "household_id", nullable = false)
    public UUID householdId;

    @Column(nullable = false)
    public String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    public ShoppingListStatus status;

    @Column(name = "opened_at", nullable = false)
    public Instant openedAt = Instant.now();

    @Column(name = "closed_at")
    public Instant closedAt;
}
