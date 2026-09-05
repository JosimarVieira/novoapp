package com.novoapp.identity.entity;

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

/** Vinculo entre um membro e um household, com papel (ADR-0007). */
@Entity
@Table(name = "household_membership")
public class HouseholdMembership extends PanacheEntityBase {

    @Id
    @GeneratedValue
    public UUID id;

    @Column(name = "household_id", nullable = false)
    public UUID householdId;

    @Column(name = "member_id", nullable = false)
    public UUID memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    public MembershipRole role;

    /**
     * Conta preferida deste membro neste household (ADR-0019). Nulo cai na
     * WALLET implicita do household. Nao ha como preencher na Etapa 1 -- e a
     * Etapa 4 que da a tela pra isso.
     */
    @Column(name = "default_account_id")
    public UUID defaultAccountId;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();
}
