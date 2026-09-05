package com.novoapp.finance.repository;

import com.novoapp.finance.entity.Account;
import com.novoapp.finance.entity.AccountType;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class AccountRepository implements PanacheRepositoryBase<Account, UUID> {

    /**
     * A WALLET implicita do household. Nao filtra household_id na query de
     * proposito: quem filtra e a policy de RLS (ADR-0003) -- filtro escrito a
     * mao seria exatamente o que a ADR descartou por depender de disciplina.
     */
    public Optional<Account> findImplicitWallet() {
        return find("type = ?1 and archivedAt is null", AccountType.WALLET).firstResultOptional();
    }
}
