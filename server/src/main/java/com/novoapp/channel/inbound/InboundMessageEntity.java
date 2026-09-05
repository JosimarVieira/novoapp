package com.novoapp.channel.inbound;

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
 * Log de ingestao. Toda mensagem recebida vira uma linha aqui antes de qualquer
 * processamento (ADR-0005) -- inclusive as que falham depois: e log de
 * ingestao, nao efeito colateral do sucesso.
 *
 * <p>Nao confundir com {@code common.message.InboundMessage}, o record
 * normalizado que segue pra baixo. Esta entidade sabe de canal e de id do
 * provedor; aquele record nao, por causa da regra nao negociavel 5.
 */
@Entity
@Table(name = "inbound_message")
public class InboundMessageEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue
    public UUID id;

    /** Nulo enquanto a identidade nao resolveu (ADR-0003). */
    @Column(name = "household_id")
    public UUID householdId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false)
    public Channel channel;

    @Column(name = "provider_message_id", nullable = false)
    public String providerMessageId;

    @Column(name = "external_id_from", nullable = false)
    public String externalIdFrom;

    @Column(name = "raw_text")
    public String rawText;

    @Column(name = "received_at", nullable = false)
    public Instant receivedAt = Instant.now();

    @Column(name = "processed_at")
    public Instant processedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    public InboundMessageStatus status = InboundMessageStatus.RECEIVED;

    /** Intencao escolhida, pra calibrar o interpretador na Etapa 5. */
    @Column(name = "intent_json")
    public String intentJson;

    @Column(name = "confidence")
    public Double confidence;
}
