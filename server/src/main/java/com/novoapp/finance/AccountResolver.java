package com.novoapp.finance;

import com.novoapp.finance.entity.Account;
import com.novoapp.finance.repository.AccountRepository;
import com.novoapp.identity.repository.HouseholdMembershipRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.UUID;

/**
 * Decide em qual conta cai um lancamento que nao nomeou nenhuma.
 *
 * <p>Ordem fixa (ADR-0019, que estende a ADR-0011 sem contradize-la):
 * <ol>
 *   <li>a conta preferida do membro <em>neste</em> household, se houver;</li>
 *   <li>senao, a WALLET implicita do household.</li>
 * </ol>
 *
 * <p>Escolher conta pelo nome dito na mensagem nao entra na Etapa 1 -- o
 * parametro <code>conta</code> da tool existe e fica sem uso
 * (sdd-modulo-finance.md).
 */
@ApplicationScoped
public class AccountResolver {

    @Inject
    AccountRepository accounts;

    @Inject
    HouseholdMembershipRepository memberships;

    public Account resolveDefault(UUID householdId, UUID memberId) {
        UUID preferred = memberships.findByHouseholdAndMember(householdId, memberId)
                .map(membership -> membership.defaultAccountId)
                .orElse(null);

        if (preferred != null) {
            Account account = accounts.findById(preferred);
            if (account != null) {
                return account;
            }
        }

        return accounts.findImplicitWallet().orElseThrow(() -> new IllegalStateException(
                "Household " + householdId + " sem conta WALLET: o evento HouseholdCreated nao criou a conta implicita"));
    }
}
