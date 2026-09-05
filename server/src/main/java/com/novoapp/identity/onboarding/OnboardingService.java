package com.novoapp.identity.onboarding;

import com.novoapp.common.tenancy.IdentityScoped;
import com.novoapp.identity.IncomingContact;
import com.novoapp.identity.entity.Household;
import com.novoapp.identity.entity.OnboardingSession;
import com.novoapp.identity.entity.OnboardingState;
import com.novoapp.identity.repository.OnboardingSessionRepository;
import com.novoapp.identity.spi.OutboundMessagePort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Onboarding deterministico (ADR-0020): nenhuma mensagem daqui passa por LLM nem
 * por politica de confianca. Implementa `vinculo-de-identidade.feature`.
 *
 * <p>Roda sob o papel pre-tenant (ADR-0022) -- quem esta aqui ainda nao tem
 * household, que e justamente o que este fluxo vai criar ou descobrir.
 */
@ApplicationScoped
public class OnboardingService {

    private static final String START_COMMAND = "/start";

    @Inject
    OnboardingSessionRepository sessions;

    @Inject
    HouseholdSelfServiceFlow selfService;

    @Inject
    InviteFlow inviteFlow;

    @Inject
    OutboundMessagePort outbound;

    @Transactional
    @IdentityScoped
    public void handle(IncomingContact contact) {
        Optional<OnboardingSession> open = sessions.findOpen(contact.channel(), contact.externalId());

        // Link de convite ganha de qualquer conversa em andamento: a pessoa
        // clicou num convite, e isso e uma intencao explicita e nova.
        String token = inviteToken(contact.text());
        if (token != null) {
            InviteFlow.InviteReply reply = inviteFlow.open(token);
            if (reply.awaitingContact()) {
                remember(contact, OnboardingState.AWAITING_SHARED_CONTACT, token);
            }
            reply(contact, reply.text());
            return;
        }

        if (contact.sharedPhoneNumber() != null) {
            String sessionToken = open.map(session -> session.inviteToken).orElse(null);
            InviteFlow.InviteReply reply = inviteFlow.acceptSharedContact(contact, sessionToken);
            if (!reply.awaitingContact()) {
                sessions.close(contact.channel(), contact.externalId());
            }
            reply(contact, reply.text());
            return;
        }

        if (open.isEmpty()) {
            // Primeira mensagem de um numero desconhecido, seja ela "/start",
            // "mercado 50" ou "quero entrar na familia do Silva".
            remember(contact, OnboardingState.AWAITING_CREATE_CONFIRMATION, null);
            reply(contact, OnboardingMessages.WELCOME);
            return;
        }

        OnboardingSession session = open.get();
        switch (session.state) {
            case AWAITING_CREATE_CONFIRMATION -> confirmCreation(contact, session);
            case AWAITING_HOUSEHOLD_NAME -> createHousehold(contact, session);
            case AWAITING_SETUP_CHANNEL_CHOICE -> chooseSetupChannel(contact, session);
            // Sem contato compartilhado nesta mensagem: repete o pedido em vez
            // de deixar a pessoa sem resposta.
            case AWAITING_SHARED_CONTACT -> reply(contact, inviteFlow.open(session.inviteToken).text());
        }
    }

    private void confirmCreation(IncomingContact contact, OnboardingSession session) {
        if (Answers.isAffirmative(contact.text())) {
            advance(session, OnboardingState.AWAITING_HOUSEHOLD_NAME);
            reply(contact, OnboardingMessages.ASK_HOUSEHOLD_NAME);
            return;
        }
        if (Answers.isNegative(contact.text())) {
            sessions.close(contact.channel(), contact.externalId());
            reply(contact, OnboardingMessages.DECLINED);
            return;
        }
        reply(contact, OnboardingMessages.WELCOME);
    }

    private void createHousehold(IncomingContact contact, OnboardingSession session) {
        String householdName = contact.text().trim();
        if (householdName.isEmpty()) {
            reply(contact, OnboardingMessages.ASK_HOUSEHOLD_NAME);
            return;
        }
        Household household = selfService.create(contact, householdName);
        advance(session, OnboardingState.AWAITING_SETUP_CHANNEL_CHOICE);
        reply(contact, OnboardingMessages.householdCreated(household.name));
    }

    private void chooseSetupChannel(IncomingContact contact, OnboardingSession session) {
        if (Answers.prefersApp(contact.text())) {
            // A Etapa 4 e que entrega o PWA (ADR-0021). Ate la, so o chat.
            reply(contact, OnboardingMessages.APP_NOT_AVAILABLE_YET);
            return;
        }
        sessions.close(contact.channel(), contact.externalId());
        reply(contact, OnboardingMessages.CONTINUING_BY_CHAT);
    }

    /** Extrai o token do <code>/start &lt;token&gt;</code> do link de convite. */
    private String inviteToken(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (!trimmed.toLowerCase(java.util.Locale.ROOT).startsWith(START_COMMAND)) {
            return null;
        }
        String remainder = trimmed.substring(START_COMMAND.length()).trim();
        return remainder.isEmpty() ? null : remainder;
    }

    private void remember(IncomingContact contact, OnboardingState state, String token) {
        sessions.save(contact.channel(), contact.externalId(), state, token);
    }

    private void advance(OnboardingSession session, OnboardingState state) {
        session.state = state;
        session.updatedAt = Instant.now();
        sessions.flush();
    }

    private void reply(IncomingContact contact, String text) {
        outbound.send(contact.channel(), contact.externalId(), text);
    }
}
