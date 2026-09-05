package com.novoapp.channel.inbound;

import com.novoapp.common.message.InboundMessage;
import com.novoapp.common.tenancy.TenantContext;
import com.novoapp.conversation.ConversationOrchestrator;
import com.novoapp.conversation.ProcessingOutcome;
import com.novoapp.identity.ContextResolution;
import com.novoapp.identity.IdentityResolutionService;
import com.novoapp.identity.IncomingContact;
import com.novoapp.identity.onboarding.OnboardingMessages;
import com.novoapp.identity.onboarding.OnboardingService;
import com.novoapp.identity.spi.OutboundMessagePort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.UUID;

/**
 * O que acontece com a mensagem depois do 200 do webhook.
 *
 * <p>Este e o unico lugar de <code>channel</code> que decide pra onde a mensagem
 * vai -- e a decisao e sempre de <code>identity</code>, nunca daqui:
 * <code>channel</code> so pergunta e obedece (sdd-modulo-channel.md).
 */
@ApplicationScoped
public class InboundPipeline {

    private static final Logger LOG = Logger.getLogger(InboundPipeline.class);

    @Inject
    IdentityResolutionService identityResolution;

    @Inject
    OnboardingService onboarding;

    @Inject
    ConversationOrchestrator conversation;

    @Inject
    InboundMessageLog log;

    @Inject
    OutboundMessagePort outbound;

    public void process(UUID messageId, NormalizedInbound inbound) {
        try {
            IncomingContact contact = new IncomingContact(inbound.channel(), inbound.externalId(),
                    inbound.senderName(), inbound.rawText(), inbound.sharedPhoneNumber());

            switch (identityResolution.resolveContext(contact)) {
                case ContextResolution.ResolvedContext context -> execute(messageId, inbound, context);
                case ContextResolution.ChooseHousehold choose -> {
                    outbound.send(inbound.channel(), inbound.externalId(),
                            OnboardingMessages.chooseHousehold(choose.householdNames()));
                    log.markIgnored(messageId);
                }
                case ContextResolution.OnboardingStep ignored -> {
                    onboarding.handle(contact);
                    // Nenhum household pra atribuir: a mensagem fica com
                    // household_id nulo, que e a excecao nomeada da ADR-0003.
                    log.markIgnored(messageId);
                }
            }
        } finally {
            TenantContext.clear();
        }
    }

    private void execute(UUID messageId, NormalizedInbound inbound, ContextResolution.ResolvedContext context) {
        TenantContext.withHousehold(context.householdId(), () -> {
            log.attachHousehold(messageId, context.householdId());
            InboundMessage message = new InboundMessage(messageId, inbound.rawText(), Instant.now());
            ProcessingOutcome outcome = conversation.process(message, context,
                    inbound.channel(), inbound.externalId());
            log.recordOutcome(messageId, outcome);
            LOG.debugf("Mensagem %s processada: %s", messageId, outcome.result());
        });
    }
}
