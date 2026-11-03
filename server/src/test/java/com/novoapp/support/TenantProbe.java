package com.novoapp.support;

import com.novoapp.common.tenancy.HouseholdScoped;
import com.novoapp.conversation.cart.repository.ChatCartItemRepository;
import com.novoapp.conversation.repository.PendingActionRepository;
import com.novoapp.finance.repository.AccountRepository;
import com.novoapp.finance.repository.CategoryRepository;
import com.novoapp.finance.repository.TransactionEditRepository;
import com.novoapp.finance.repository.TransactionRepository;
import com.novoapp.shopping.repository.ListItemRepository;
import com.novoapp.shopping.repository.ShoppingListRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.UUID;

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
    PendingActionRepository pendingActions;

    @Inject
    ShoppingListRepository shoppingLists;

    @Inject
    ListItemRepository listItems;

    @Inject
    ChatCartItemRepository cartItems;

    @Inject
    TransactionEditRepository transactionEdits;

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

    @Transactional
    @HouseholdScoped
    public long countPendingActions() {
        return pendingActions.count();
    }

    @Transactional
    @HouseholdScoped
    public long countShoppingLists() {
        return shoppingLists.count();
    }

    @Transactional
    @HouseholdScoped
    public long countListItems() {
        return listItems.count();
    }

    @Transactional
    @HouseholdScoped
    public long countCartItems() {
        return cartItems.count();
    }

    @Transactional
    @HouseholdScoped
    public long countTransactionEdits() {
        return transactionEdits.count();
    }

    /**
     * Linha de historico escrita direto, sem passar por <code>finance</code>:
     * prova que a policy recusa o household errado mesmo quando o servico nao
     * esta no caminho.
     */
    @Transactional
    @HouseholdScoped
    public void insertTransactionEdit(UUID householdId, UUID transactionId, UUID memberId) {
        entityManager.createNativeQuery("""
                        INSERT INTO transaction_edit (household_id, transaction_id, edited_by_member_id, changed_fields_json)
                        VALUES (?1, ?2, ?3, '{}')""")
                .setParameter(1, householdId)
                .setParameter(2, transactionId)
                .setParameter(3, memberId)
                .executeUpdate();
    }

    /** O historico e append-only (ADR-0012): o papel de dominio nao tem grant para isto. */
    @Transactional
    @HouseholdScoped
    public int rewriteTransactionEdits() {
        return entityManager.createNativeQuery("UPDATE transaction_edit SET changed_fields_json = '{}'")
                .executeUpdate();
    }

    @Transactional
    @HouseholdScoped
    public int deleteTransactionEdits() {
        return entityManager.createNativeQuery("DELETE FROM transaction_edit").executeUpdate();
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
