package com.novoapp.finance;

import com.novoapp.common.tenancy.HouseholdScoped;
import com.novoapp.finance.entity.Account;
import com.novoapp.finance.entity.Category;
import com.novoapp.finance.entity.EntryKind;
import com.novoapp.finance.entity.Transaction;
import com.novoapp.finance.entity.TransactionSource;
import com.novoapp.finance.repository.CategoryRepository;
import com.novoapp.finance.repository.TransactionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Lancamentos, categorias, contas, estorno (sdd-modulo-finance.md).
 *
 * <p>Escopo da Etapa 1: so registrar despesa. Estorno, edicao entre membros
 * (ADR-0012), cartao/fatura (ADR-0011) e metas (ADR-0017) nao entram aqui
 * ainda.
 *
 * <p>Nao fala com canal nenhum: e chamado por <code>conversation</code> e,
 * da Etapa 4 em diante, pelo REST do Vue -- a mesma camada de servico, regra 4
 * do CLAUDE.md.
 */
@ApplicationScoped
public class FinanceService {

    @Inject
    AccountResolver accountResolver;

    @Inject
    CategoryRepository categories;

    @Inject
    TransactionRepository transactions;

    @Inject
    Clock clock;

    /**
     * Registra uma despesa ja interpretada.
     *
     * <p>O nome do metodo e ingles, e nao <code>registrarDespesa</code> como o
     * SDD escreveu: identificador em ingles e regra sem excecao no CLAUDE.md --
     * o portugues fica no Gherkin, na ADR e nos comentarios.
     *
     * @param sourceMessageId mensagem que originou o lancamento. Nao esta na
     *        assinatura do SDD, mas o fluxo do proprio SDD exige
     *        <code>source_message_id</code> gravado -- e o que torna a Etapa 5
     *        mensuravel.
     */
    @Transactional
    @HouseholdScoped
    public RegisteredExpense registerExpense(UUID householdId,
                                             UUID memberId,
                                             UUID categoryId,
                                             long amountCents,
                                             UUID sourceMessageId) {
        if (amountCents <= 0) {
            throw new IllegalArgumentException("Valor de despesa precisa ser positivo: " + amountCents);
        }

        Category category = categories.findById(categoryId);
        if (category == null) {
            // RLS ja garante que so categoria do household atual aparece aqui:
            // nulo significa "nao existe neste household", nao "sem permissao".
            throw new IllegalArgumentException("Categoria inexistente neste household: " + categoryId);
        }

        Account account = accountResolver.resolveDefault(householdId, memberId);

        Transaction transaction = new Transaction();
        transaction.householdId = householdId;
        transaction.accountId = account.id;
        transaction.categoryId = category.id;
        transaction.kind = EntryKind.EXPENSE;
        transaction.amountCents = amountCents;
        transaction.occurredOn = LocalDate.now(clock);
        transaction.createdByMemberId = memberId;
        transaction.source = TransactionSource.CHAT;
        transaction.sourceMessageId = sourceMessageId;
        transactions.persist(transaction);
        // Flush dentro do escopo: se o INSERT so acontecesse no commit, ele
        // rodaria depois do SET LOCAL ROLE ja ter voltado, e o papel de fora
        // nao tem permissao nesta tabela.
        transactions.flush();

        return new RegisteredExpense(transaction.id, transaction.amountCents, category.name,
                account.name, transaction.occurredOn);
    }
}
