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

/**
 * Convite de um OWNER para um telefone especifico entrar num household
 * existente (ADR-0020). Uso unico, expira em 7 dias, so aceito pelo telefone
 * pra qual foi gerado.
 */
@Entity
@Table(name = "household_invite")
public class HouseholdInvite extends PanacheEntityBase {

    /** Prazo fixo da ADR-0020. */
    public static final int VALIDITY_DAYS = 7;

    @Id
    @GeneratedValue
    public UUID id;

    @Column(name = "household_id", nullable = false)
    public UUID householdId;

    @Column(name = "invited_by_member_id", nullable = false)
    public UUID invitedByMemberId;

    /** E.164, alvo do convite. So este numero aceita. */
    @Column(name = "phone_number", nullable = false)
    public String phoneNumber;

    @Column(name = "token", nullable = false)
    public String token;

    /**
     * So PENDING ou ACCEPTED chegam ao banco -- ha CHECK constraint garantindo
     * isso. Pra ler a situacao real use {@link #currentStatus(Instant)}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    public InviteStatus status = InviteStatus.PENDING;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    public Instant expiresAt;

    @Column(name = "accepted_at")
    public Instant acceptedAt;

    @Column(name = "accepted_by_member_id")
    public UUID acceptedByMemberId;

    /**
     * Situacao calculada: convite pendente que passou do prazo ja conta como
     * expirado, sem ninguem ter rodado nada pra marcar isso.
     */
    public InviteStatus currentStatus(Instant now) {
        if (status == InviteStatus.ACCEPTED) {
            return InviteStatus.ACCEPTED;
        }
        return now.isAfter(expiresAt) ? InviteStatus.EXPIRED : InviteStatus.PENDING;
    }
}
