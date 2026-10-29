package com.novoapp.channel.inbound;

import com.novoapp.common.i18n.MessageKey;
import com.novoapp.common.i18n.Messages;
import com.novoapp.common.message.ChatReply;
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
            // O callback_data cru nao e texto da pessoa: nao pode ser lido como
            // link de convite nem como resposta de onboarding.
            IncomingContact contact = new IncomingContact(inbound.channel(), inbound.externalId(),
                    inbound.senderName(), inbound.isAction() ? null : inbound.rawText(),
                    inbound.sharedPhoneNumber());

            switch (identityResolution.resolveContext(contact)) {
                case ContextResolution.ResolvedContext context -> execute(messageId, inbound, context);
                case ContextResolution.ChooseHousehold choose -> {
                    send(inbound, OnboardingMessages.chooseHousehold(choose.householdNames()));
                    log.markIgnored(messageId);
                }
                case ContextResolution.OnboardingStep ignored -> {
                    if (inbound.isAction()) {
                        // Botao so existe para quem ja tem household; clique de
                        // quem esta em onboarding e de mensagem que ja nao vale.
                        // Responde ao clique, para o botao nao ficar carregando,
                        // e nao entra no fluxo de onboarding.
                        outbound.send(inbound.channel(), inbound.externalId(),
                                ChatReply.text(null).inReplyTo(inbound.origin()));
                    } else {
                        onboarding.handle(contact);
                    }
                    // Nenhum household pra atribuir: a mensagem fica com
                    // household_id nulo, que e a excecao nomeada da ADR-0003.
                    log.markIgnored(messageId);
                }
            }
        } catch (RuntimeException e) {
            // Achado em 2026-09-18 lendo o codigo, e nao em producao: o log de
            // ingestao mostrou depois que a mensagem que parecia sem resposta
            // tinha sido EXECUTED, e o silencio era do recorte do transcript.
            // O furo continua: o orquestrador responde as falhas dele proprio e
            // o InboundDispatcher confiava nisso, mas nada respondia por quem
            // estoura ANTES dele -- resolucao de contexto, onboarding,
            // attachHousehold. Silencio e o unico desfecho que o
            // sdd-visao-geral.md proibe: quem manda mensagem e nao recebe nada
            // reenvia, e pode duplicar o que ja gravou.
            LOG.errorf(e, "Falha antes do orquestrador na mensagem %s", messageId);
            failed(messageId, inbound);
        } finally {
            TenantContext.clear();
        }
    }

    private void execute(UUID messageId, NormalizedInbound inbound, ContextResolution.ResolvedContext context) {
        TenantContext.withHousehold(context.householdId(), () -> {
            log.attachHousehold(messageId, context.householdId());
            InboundMessage message = new InboundMessage(messageId, inbound.rawText(), Instant.now(),
                    inbound.action(), inbound.origin());
            ProcessingOutcome outcome = conversation.process(message, context,
                    inbound.channel(), inbound.externalId());
            // O recibo do usuario ja saiu. Falhar aqui e problema de
            // observabilidade, e nao dele -- se deixasse estourar, o catch de
            // cima mandaria "nao consegui" depois de um "Anotado:" que esta
            // gravado.
            try {
                log.recordOutcome(messageId, outcome);
            } catch (RuntimeException e) {
                LOG.errorf(e, "Desfecho da mensagem %s nao foi gravado", messageId);
            }
            LOG.debugf("Mensagem %s processada: %s", messageId, outcome.result());
        });
    }

    /**
     * Recibo de erro no chat quando ninguem mais vai dar um. Cada passo no seu
     * try: se o envio falhar, o status ainda e gravado, e vice-versa.
     */
    private void failed(UUID messageId, NormalizedInbound inbound) {
        try {
            send(inbound, Messages.get(Messages.DEFAULT, MessageKey.FAILURE));
        } catch (RuntimeException e) {
            LOG.errorf(e, "Recibo de erro da mensagem %s nao foi entregue", messageId);
        }
        try {
            log.markFailed(messageId);
        } catch (RuntimeException e) {
            LOG.errorf(e, "Status FAILED da mensagem %s nao foi gravado", messageId);
        }
    }

    /**
     * Texto em resposta a mensagem que nao chegou a <code>conversation</code>.
     * Num clique, vai como resposta a ele (ADR-0040): todo clique e respondido,
     * inclusive quando o que se tem a dizer e um erro.
     */
    private void send(NormalizedInbound inbound, String text) {
        if (inbound.isAction()) {
            outbound.send(inbound.channel(), inbound.externalId(), ChatReply.text(text).inReplyTo(inbound.origin()));
        } else {
            outbound.send(inbound.channel(), inbound.externalId(), text);
        }
    }
}
