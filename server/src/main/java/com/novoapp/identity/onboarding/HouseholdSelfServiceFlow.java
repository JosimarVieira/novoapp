package com.novoapp.identity.onboarding;

import com.novoapp.identity.IncomingContact;
import com.novoapp.identity.entity.ChannelIdentity;
import com.novoapp.identity.entity.Household;
import com.novoapp.identity.entity.HouseholdMembership;
import com.novoapp.identity.entity.Member;
import com.novoapp.identity.entity.MembershipRole;
import com.novoapp.identity.repository.ChannelIdentityRepository;
import com.novoapp.identity.repository.HouseholdMembershipRepository;
import com.novoapp.identity.repository.HouseholdRepository;
import com.novoapp.identity.repository.MemberRepository;
import com.novoapp.identity.spi.HouseholdCreated;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import java.time.Instant;

/**
 * Cria a familia do primeiro membro (ADR-0020: e a unica coisa self-service --
 * entrar numa familia que ja existe exige convite).
 */
@ApplicationScoped
public class HouseholdSelfServiceFlow {

    @Inject
    HouseholdRepository households;

    @Inject
    MemberRepository members;

    @Inject
    HouseholdMembershipRepository memberships;

    @Inject
    ChannelIdentityRepository channelIdentities;

    @Inject
    Event<HouseholdCreated> householdCreated;

    /**
     * Roda dentro da transacao e do escopo pre-tenant de quem chamou
     * (OnboardingService), incluindo o observador de {@link HouseholdCreated}
     * que cria a conta WALLET: household sem conta seria um estado que
     * {@code AccountResolver} nao sabe tratar.
     */
    public Household create(IncomingContact contact, String householdName) {
        Household household = new Household();
        household.name = householdName;
        households.persist(household);
        households.flush();

        Member member = new Member();
        member.name = contact.senderName();
        members.persist(member);
        members.flush();

        HouseholdMembership membership = new HouseholdMembership();
        membership.householdId = household.id;
        membership.memberId = member.id;
        membership.role = MembershipRole.OWNER;
        memberships.persist(membership);

        ChannelIdentity identity = new ChannelIdentity();
        identity.memberId = member.id;
        identity.channel = contact.channel();
        identity.externalId = contact.externalId();
        // Vinculo unico: o household ativo se resolve sozinho e a pessoa nunca
        // precisa identificar familia nenhuma (ADR-0007).
        identity.activeHouseholdId = household.id;
        identity.verifiedAt = Instant.now();
        channelIdentities.persist(identity);
        channelIdentities.flush();

        // identity nunca escreve em account: anuncia, finance observa
        // (sdd-modulo-identity.md).
        householdCreated.fire(new HouseholdCreated(household.id));

        return household;
    }
}
