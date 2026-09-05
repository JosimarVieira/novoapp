package com.novoapp.channel.inbound;

import com.novoapp.common.tenancy.IdentityScoped;
import com.novoapp.conversation.ProcessingOutcome;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Atualizacoes do log de ingestao depois que a mensagem ja foi aceita.
 *
 * <p>Tudo aqui roda sob o papel pre-tenant: e o unico papel com permissao em
 * <code>inbound_message</code> (ADR-0003).
 */
@ApplicationScoped
public class InboundMessageLog implements PanacheRepositoryBase<InboundMessageEntity, UUID> {

    /** Preenche o household assim que <code>identity</code> resolve quem falou. */
    @Transactional
    @IdentityScoped
    public void attachHousehold(UUID messageId, UUID householdId) {
        InboundMessageEntity message = findById(messageId);
        message.householdId = householdId;
        flush();
    }

    @Transactional
    @IdentityScoped
    public void recordOutcome(UUID messageId, ProcessingOutcome outcome) {
        InboundMessageEntity message = findById(messageId);
        message.status = switch (outcome.result()) {
            case EXECUTED -> InboundMessageStatus.EXECUTED;
            case INTERPRETED -> InboundMessageStatus.INTERPRETED;
            case FAILED -> InboundMessageStatus.FAILED;
        };
        message.confidence = outcome.confidence();
        message.intentJson = outcome.intentJson();
        message.processedAt = Instant.now();
        flush();
    }

    /**
     * Mensagem que nao virou acao de dominio nenhuma: onboarding, escolha de
     * household, ou update que esta etapa nao interpreta.
     */
    @Transactional
    @IdentityScoped
    public void markIgnored(UUID messageId) {
        InboundMessageEntity message = findById(messageId);
        message.status = InboundMessageStatus.IGNORED;
        message.processedAt = Instant.now();
        flush();
    }

}
