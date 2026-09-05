package com.novoapp.identity.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Pessoa. Nao tem <code>household_id</code>: a mesma pessoa pode ter vinculo com
 * varios households (ADR-0007), e o limite de isolamento vive em
 * {@link HouseholdMembership}.
 */
@Entity
@Table(name = "member")
public class Member extends PanacheEntityBase {

    @Id
    @GeneratedValue
    public UUID id;

    @Column(nullable = false)
    public String name;

    /** E.164. Capturado ao compartilhar contato (ADR-0020). */
    @Column(name = "phone_number")
    public String phoneNumber;

    /** ADR-0021, Etapa 4. Nulo enquanto a pessoa so usa chat. */
    @Column(name = "email")
    public String email;

    /** ADR-0021, bcrypt. */
    @Column(name = "password_hash")
    public String passwordHash;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();
}
