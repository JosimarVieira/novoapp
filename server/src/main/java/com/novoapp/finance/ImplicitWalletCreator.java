package com.novoapp.finance;

import com.novoapp.common.tenancy.HouseholdScoped;
import com.novoapp.finance.entity.Account;
import com.novoapp.finance.entity.AccountType;
import com.novoapp.finance.repository.AccountRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.UUID;

/**
 * Cria a conta WALLET implicita do household (ADR-0011).
 *
 * <p>Bean separado do observador de proposito: interceptor CDI nao pega chamada
 * de um metodo pra outro dentro do mesmo objeto, e sem o interceptor a sessao
 * de banco nunca entraria no papel de dominio.
 */
@ApplicationScoped
public class ImplicitWalletCreator {

    @Inject
    AccountRepository accounts;

    @HouseholdScoped
    public void create(UUID householdId) {
        Account wallet = new Account();
        wallet.householdId = householdId;
        wallet.name = Account.IMPLICIT_WALLET_NAME;
        wallet.type = AccountType.WALLET;
        accounts.persist(wallet);
        // Flush dentro do escopo: no commit, o SET LOCAL ROLE ja teria voltado
        // pro papel de quem chamou, que nao tem permissao nesta tabela.
        accounts.flush();
    }
}
