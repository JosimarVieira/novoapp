package com.novoapp.finance.entity;

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

/**
 * Classificacao de lancamento, criada pelo household (glossario).
 *
 * <p>Household novo nasce sem nenhuma (ADR-0013). Criar categoria por chat e
 * Etapa 2 -- por isso nada nesta etapa escreve nesta tabela.
 */
@Entity
@Table(name = "category")
public class Category extends PanacheEntityBase {

    @Id
    @GeneratedValue
    public UUID id;

    @Column(name = "household_id", nullable = false)
    public UUID householdId;

    /** Subcategoria: so um nivel, validado aqui em finance (ADR-0016). */
    @Column(name = "parent_category_id")
    public UUID parentCategoryId;

    @Column(nullable = false)
    public String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    public EntryKind kind;

    @Column(name = "created_by_member_id")
    public UUID createdByMemberId;

    @Column(name = "archived_at")
    public Instant archivedAt;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();
}
