package com.novoapp.identity.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A familia. Unidade de isolamento de dados e de cobranca (ADR-0006). */
@Entity
@Table(name = "household")
public class Household extends PanacheEntityBase {

    @Id
    @GeneratedValue
    public UUID id;

    @Column(nullable = false)
    public String name;

    @Column(nullable = false)
    public String plan = "FREE";

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();
}
