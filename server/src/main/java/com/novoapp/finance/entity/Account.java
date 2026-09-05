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

/** Origem ou destino do dinheiro (glossario). */
@Entity
@Table(name = "account")
public class Account extends PanacheEntityBase {

    /** Nome da conta implicita criada com o household (ADR-0011). */
    public static final String IMPLICIT_WALLET_NAME = "Carteira";

    @Id
    @GeneratedValue
    public UUID id;

    @Column(name = "household_id", nullable = false)
    public UUID householdId;

    @Column(nullable = false)
    public String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    public AccountType type;

    @Column(name = "closing_day")
    public Integer closingDay;

    @Column(name = "due_day")
    public Integer dueDay;

    @Column(name = "credit_limit_cents")
    public Long creditLimitCents;

    @Column(name = "archived_at")
    public Instant archivedAt;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();
}
