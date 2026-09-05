package com.novoapp.support;

import com.novoapp.common.tenancy.HouseholdScoped;
import com.novoapp.finance.repository.AccountRepository;
import com.novoapp.finance.repository.CategoryRepository;
import com.novoapp.finance.repository.TransactionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * Le as tabelas de dominio exatamente como a aplicacao le -- pelo papel
 * <code>novoapp_app</code>, sob RLS. O que este bean nao enxerga, o produto
 * tambem nao enxerga.
 */
@ApplicationScoped
public class TenantProbe {

    @Inject
    TransactionRepository transactions;

    @Inject
    CategoryRepository categories;

    @Inject
    AccountRepository accounts;

    @Inject
    EntityManager entityManager;

    @Transactional
    @HouseholdScoped
    public long countTransactions() {
        return transactions.count();
    }

    @Transactional
    @HouseholdScoped
    public long countCategories() {
        return categories.count();
    }

    @Transactional
    @HouseholdScoped
    public long countAccounts() {
        return accounts.count();
    }

    /**
     * Sem <code>@HouseholdScoped</code> de proposito: a conexao fica no papel de
     * login, que e NOINHERIT. Serve pra provar que esquecer a anotacao quebra em
     * vez de vazar.
     */
    @Transactional
    public long countTransactionsWithoutScope() {
        return transactions.count();
    }

    /**
     * Tabela pre-tenant lida sob o papel de dominio. Deve estourar permissao:
     * o papel de dominio nao recebe grant nenhum sobre ela (ADR-0022).
     */
    @Transactional
    @HouseholdScoped
    public long countChannelIdentities() {
        return (Long) entityManager
                .createNativeQuery("SELECT count(*) FROM channel_identity", Long.class)
                .getSingleResult();
    }
}
