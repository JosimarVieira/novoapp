package com.novoapp.identity.entity;

import com.novoapp.identity.Channel;
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
 * Ponto de entrada de todo o sistema: transforma "chegou uma mensagem" em
 * "fulano, no household ativo dele, disse".
 *
 * <p>A unicidade <code>(channel, external_id)</code> aponta pra pessoa, nao pra
 * familia (ADR-0007).
 */
@Entity
@Table(name = "channel_identity")
public class ChannelIdentity extends PanacheEntityBase {

    @Id
    @GeneratedValue
    public UUID id;

    @Column(name = "member_id", nullable = false)
    public UUID memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false)
    public Channel channel;

    /** Telegram user id, telefone E.164, ou o e-mail quando o canal e WEB. */
    @Column(name = "external_id", nullable = false)
    public String externalId;

    /**
     * Household de destino das mensagens desta conversa. Preenchido sozinho
     * quando a pessoa tem um unico vinculo; so entra em jogo, por comando
     * explicito, quando ela tem mais de um (ADR-0007).
     */
    @Column(name = "active_household_id")
    public UUID activeHouseholdId;

    @Column(name = "verified_at")
    public Instant verifiedAt;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();
}
