package com.novoapp.identity.onboarding;

import com.novoapp.common.tenancy.IdentityScoped;
import com.novoapp.identity.Channel;
import com.novoapp.identity.entity.HouseholdInvite;
import com.novoapp.identity.entity.InviteStatus;
import com.novoapp.identity.entity.MembershipRole;
import com.novoapp.identity.repository.HouseholdInviteRepository;
import com.novoapp.identity.repository.HouseholdMembershipRepository;
import com.novoapp.identity.spi.InviteLinkPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * A emissao do convite pelo OWNER (ADR-0020) -- o lado que a Etapa 1 deixou de
 * fora.
 *
 * <p>Bean separado de {@link InviteFlow}, que cuida do aceite: os dois lados
 * partem de situacoes opostas. O aceite chega de numero desconhecido, sem
 * household nenhum; a emissao parte de numero ja vinculado e atravessa o
 * pipeline de interpretacao como qualquer outra mensagem.
 *
 * <p><b>O sistema nao entrega o convite.</b> Ele devolve o link para quem
 * convidou repassar por fora. Nao e escolha de produto: a Telegram Bot API nao
 * deixa um bot iniciar conversa com quem nunca falou com ele (ADR-0020).
 */
@ApplicationScoped
public class InviteIssuer {

    @Inject
    HouseholdInviteRepository invites;

    @Inject
    HouseholdMembershipRepository memberships;

    @Inject
    InviteLinkPort inviteLinks;

    @Inject
    Clock clock;

    /**
     * @param link o que o OWNER repassa. Montado por <code>channel</code> atraves
     *        de {@link InviteLinkPort} -- <code>identity</code> nunca sabe que
     *        formato de URL e esse (regra 5 do CLAUDE.md)
     */
    public record IssuedInvite(UUID inviteId, String invitedName, String phoneNumber,
                               String link, Instant expiresAt) {
    }

    /** Recusa nomeada, e nao excecao: quem chama precisa escolher o texto da resposta. */
    public sealed interface IssueResult {

        record Issued(IssuedInvite invite) implements IssueResult {
        }

        /** So OWNER convida (ADR-0020). */
        record NotOwner() implements IssueResult {
        }

        record AlreadyInvited(String phoneNumber) implements IssueResult {
        }
    }

    @Transactional
    @IdentityScoped
    public IssueResult issue(UUID householdId, UUID invitedByMemberId, Channel channel,
                             String invitedName, String phoneNumber) {
        boolean owner = memberships.findByHouseholdAndMember(householdId, invitedByMemberId)
                .map(membership -> membership.role == MembershipRole.OWNER)
                .orElse(false);
        if (!owner) {
            return new IssueResult.NotOwner();
        }

        Instant now = Instant.now(clock);
        // Convite pendente e nao vencido pro mesmo numero nao vira um segundo:
        // dois links validos pro mesmo telefone e confusao sem ganho, e a
        // ADR-0020 nao decide reenvio.
        var existing = invites.findPendingByPhoneNumber(phoneNumber);
        if (existing.isPresent() && existing.get().currentStatus(now) == InviteStatus.PENDING) {
            return new IssueResult.AlreadyInvited(phoneNumber);
        }

        HouseholdInvite invite = new HouseholdInvite();
        invite.householdId = householdId;
        invite.invitedByMemberId = invitedByMemberId;
        invite.phoneNumber = phoneNumber;
        invite.token = UUID.randomUUID().toString().replace("-", "");
        invite.status = InviteStatus.PENDING;
        invite.createdAt = now;
        invite.expiresAt = now.plus(HouseholdInvite.VALIDITY_DAYS, ChronoUnit.DAYS);
        invites.persist(invite);
        invites.flush();

        return new IssueResult.Issued(new IssuedInvite(invite.id, invitedName, phoneNumber,
                inviteLinks.linkFor(channel, invite.token), invite.expiresAt));
    }
}
