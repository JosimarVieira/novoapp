package com.novoapp.identity.onboarding;

import com.novoapp.identity.IncomingContact;
import com.novoapp.identity.entity.ChannelIdentity;
import com.novoapp.identity.entity.Household;
import com.novoapp.identity.entity.HouseholdInvite;
import com.novoapp.identity.entity.HouseholdMembership;
import com.novoapp.identity.entity.InviteStatus;
import com.novoapp.identity.entity.Member;
import com.novoapp.identity.entity.MembershipRole;
import com.novoapp.identity.repository.ChannelIdentityRepository;
import com.novoapp.identity.repository.HouseholdInviteRepository;
import com.novoapp.identity.repository.HouseholdMembershipRepository;
import com.novoapp.identity.repository.HouseholdRepository;
import com.novoapp.identity.repository.MemberRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Aceite de convite (ADR-0020): so o telefone alvo entra, uso unico, 7 dias.
 *
 * <p>A criacao do convite pelo OWNER (comando no chat) nao entra na Etapa 1 --
 * parte de numero ja vinculado, e passaria pelo pipeline de interpretacao, que
 * nesta etapa so conhece a tool <code>registrarDespesa</code>
 * (<code>sdd-modulo-nlu.md</code>). Ate la o convite e inserido direto no banco;
 * ver README.
 */
@ApplicationScoped
public class InviteFlow {

    @Inject
    HouseholdInviteRepository invites;

    @Inject
    HouseholdRepository households;

    @Inject
    MemberRepository members;

    @Inject
    HouseholdMembershipRepository memberships;

    @Inject
    ChannelIdentityRepository channelIdentities;

    /** O que responder, e se a conversa deve ficar esperando o contato. */
    public record InviteReply(String text, boolean awaitingContact) {
    }

    /** Chegada pelo link: <code>/start &lt;token&gt;</code>. */
    public InviteReply open(String token) {
        Optional<HouseholdInvite> found = invites.findByToken(token);
        if (found.isEmpty()) {
            return new InviteReply(OnboardingMessages.INVITE_NOT_FOUND, false);
        }
        HouseholdInvite invite = found.get();
        return switch (invite.currentStatus(Instant.now())) {
            case ACCEPTED -> new InviteReply(OnboardingMessages.INVITE_ALREADY_USED, false);
            case EXPIRED -> new InviteReply(OnboardingMessages.INVITE_EXPIRED, false);
            case PENDING -> new InviteReply(
                    OnboardingMessages.inviteAskContact(households.findById(invite.householdId).name), true);
        };
    }

    /**
     * Contato compartilhado. O convite so e aceito se o telefone bater com o
     * alvo -- tentativa errada nao consome nem expira o convite (ADR-0020).
     *
     * @param token token do link, quando a pessoa chegou por ele. Nulo faz cair
     *              na busca por telefone, que e o caminho de quem compartilhou o
     *              contato sem ter aberto o link nesta conversa.
     */
    public InviteReply acceptSharedContact(IncomingContact contact, String token) {
        Optional<HouseholdInvite> found = token != null
                ? invites.findByToken(token)
                : invites.findPendingByPhoneNumber(contact.sharedPhoneNumber());

        if (found.isEmpty()) {
            return new InviteReply(OnboardingMessages.INVITE_NOT_FOUND, false);
        }
        HouseholdInvite invite = found.get();

        switch (invite.currentStatus(Instant.now())) {
            case ACCEPTED -> {
                return new InviteReply(OnboardingMessages.INVITE_ALREADY_USED, false);
            }
            case EXPIRED -> {
                return new InviteReply(OnboardingMessages.INVITE_EXPIRED, false);
            }
            default -> {
                // segue pro aceite
            }
        }

        if (!invite.phoneNumber.equals(contact.sharedPhoneNumber())) {
            // Continua PENDING de proposito: numero errado nao queima o convite.
            return new InviteReply(OnboardingMessages.INVITE_PHONE_MISMATCH, true);
        }

        return new InviteReply(accept(invite, contact), false);
    }

    private String accept(HouseholdInvite invite, IncomingContact contact) {
        // Mesma pessoa, familia nova: reaproveita o member em vez de duplicar
        // identidade (ADR-0007).
        Member member = members.findByPhoneNumber(contact.sharedPhoneNumber())
                .orElseGet(() -> {
                    Member created = new Member();
                    created.name = contact.senderName();
                    created.phoneNumber = contact.sharedPhoneNumber();
                    members.persist(created);
                    members.flush();
                    return created;
                });
        if (member.phoneNumber == null) {
            member.phoneNumber = contact.sharedPhoneNumber();
        }

        List<HouseholdMembership> existing = memberships.findByMember(member.id);

        HouseholdMembership membership = new HouseholdMembership();
        membership.householdId = invite.householdId;
        membership.memberId = member.id;
        membership.role = MembershipRole.MEMBER;
        memberships.persist(membership);

        ChannelIdentity identity = channelIdentities
                .findByChannelAndExternalId(contact.channel(), contact.externalId())
                .orElseGet(() -> {
                    ChannelIdentity created = new ChannelIdentity();
                    created.memberId = member.id;
                    created.channel = contact.channel();
                    created.externalId = contact.externalId();
                    channelIdentities.persist(created);
                    return created;
                });
        identity.verifiedAt = Instant.now();

        invite.status = InviteStatus.ACCEPTED;
        invite.acceptedAt = Instant.now();
        invite.acceptedByMemberId = member.id;
        invites.flush();

        Household household = households.findById(invite.householdId);

        if (existing.isEmpty()) {
            // Vinculo unico: household ativo resolvido agora e nunca mais tocado.
            identity.activeHouseholdId = invite.householdId;
            channelIdentities.flush();
            return OnboardingMessages.inviteAccepted(household.name);
        }

        // Segundo vinculo em diante: o ativo nao muda sozinho, troca-se por
        // comando explicito (ADR-0007).
        channelIdentities.flush();
        String activeName = identity.activeHouseholdId != null
                ? households.findById(identity.activeHouseholdId).name
                : households.findById(existing.get(0).householdId).name;
        return OnboardingMessages.inviteAcceptedWithOtherHouseholds(household.name, activeName);
    }
}
