package com.novoapp.identity;

import com.novoapp.common.tenancy.IdentityScoped;
import com.novoapp.identity.ContextResolution.ChooseHousehold;
import com.novoapp.identity.ContextResolution.OnboardingStep;
import com.novoapp.identity.ContextResolution.ResolvedContext;
import com.novoapp.identity.entity.ChannelIdentity;
import com.novoapp.identity.entity.Household;
import com.novoapp.identity.entity.HouseholdMembership;
import com.novoapp.identity.entity.Member;
import com.novoapp.identity.repository.ChannelIdentityRepository;
import com.novoapp.identity.repository.HouseholdMembershipRepository;
import com.novoapp.identity.repository.HouseholdRepository;
import com.novoapp.identity.repository.MemberRepository;
import com.novoapp.identity.repository.OnboardingSessionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Resolve o contexto de tenant de toda mensagem que chega:
 * {@code ChannelIdentity -> Member -> household ativo} (ADR-0007).
 *
 * <p>Roda sob o papel pre-tenant (ADR-0022): nao ha household setado ainda --
 * descobrir qual e o household e exatamente o trabalho deste servico.
 */
@ApplicationScoped
public class IdentityResolutionService {

    @Inject
    ChannelIdentityRepository channelIdentities;

    @Inject
    MemberRepository members;

    @Inject
    HouseholdRepository households;

    @Inject
    HouseholdMembershipRepository memberships;

    @Inject
    OnboardingSessionRepository onboardingSessions;

    @Transactional
    @IdentityScoped
    public ContextResolution resolveContext(IncomingContact contact) {
        Channel channel = contact.channel();
        String externalId = contact.externalId();

        // Link de convite e contato compartilhado sao onboarding mesmo quando a
        // pessoa ja tem identidade: e o caso de quem clica de novo num convite
        // ja usado, e de quem ja e membro de outra familia e aceita um convite
        // novo (ADR-0007, ADR-0020).
        if (carriesInviteLink(contact.text()) || contact.sharedPhoneNumber() != null) {
            return new OnboardingStep();
        }

        // Conversa de onboarding em aberto ganha da identidade ja existente: e o
        // caso de quem acabou de criar a familia e ainda nao respondeu se
        // prefere seguir pelo chat ou pelo app.
        if (onboardingSessions.findOpen(channel, externalId).isPresent()) {
            return new OnboardingStep();
        }

        Optional<ChannelIdentity> identity = channelIdentities.findByChannelAndExternalId(channel, externalId);
        if (identity.isEmpty()) {
            return new OnboardingStep();
        }

        ChannelIdentity channelIdentity = identity.get();
        List<HouseholdMembership> memberHouseholds = memberships.findByMember(channelIdentity.memberId);

        if (channelIdentity.activeHouseholdId != null) {
            return resolved(channelIdentity, channelIdentity.activeHouseholdId, memberHouseholds.size() > 1);
        }

        if (memberHouseholds.size() == 1) {
            // Chegar aqui com um unico vinculo e ainda nulo e dado inconsistente
            // de um aceite de convite que nao seguiu a regra -- conserta agora.
            channelIdentity.activeHouseholdId = memberHouseholds.get(0).householdId;
            channelIdentities.persist(channelIdentity);
            return resolved(channelIdentity, channelIdentity.activeHouseholdId, false);
        }

        if (memberHouseholds.isEmpty()) {
            // Identidade sem vinculo nenhum: nao ha o que resolver, volta pro
            // onboarding em vez de tratar como erro.
            return new OnboardingStep();
        }

        return new ChooseHousehold(channelIdentity.memberId,
                memberHouseholds.stream()
                        .map(membership -> households.findById(membership.householdId).name)
                        .toList());
    }

    /** <code>/start &lt;token&gt;</code>: chegada por link de convite (ADR-0020). */
    private boolean carriesInviteLink(String text) {
        if (text == null) {
            return false;
        }
        String trimmed = text.trim();
        return trimmed.toLowerCase(java.util.Locale.ROOT).startsWith("/start")
                && !trimmed.substring("/start".length()).isBlank();
    }

    private ResolvedContext resolved(ChannelIdentity identity, java.util.UUID householdId, boolean multiple) {
        Household household = households.findById(householdId);
        Member member = members.findById(identity.memberId);
        return new ResolvedContext(householdId, household.name, member.id, member.name, multiple);
    }
}
