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
 * Onde a conversa de onboarding parou, por identificador externo.
 *
 * <p>Tabela decidida ao escrever a Etapa 1, registrada em
 * <code>sdd-modulo-identity.md</code>: o onboarding tem mais de um passo
 * ("quer criar?" -> "qual o nome?"), e sem estado nao ha como distinguir o nome
 * da familia de qualquer outra mensagem. Some assim que o vinculo existe.
 */
@Entity
@Table(name = "onboarding_session")
public class OnboardingSession extends PanacheEntityBase {

    @Id
    @GeneratedValue
    public UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false)
    public Channel channel;

    @Column(name = "external_id", nullable = false)
    public String externalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    public OnboardingState state;

    /** Token do convite que trouxe a pessoa ate aqui, quando houve um. */
    @Column(name = "invite_token")
    public String inviteToken;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt = Instant.now();
}
