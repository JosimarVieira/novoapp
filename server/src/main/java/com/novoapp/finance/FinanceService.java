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
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Lancamentos, categorias, contas, estorno (sdd-modulo-finance.md).
 *
 * <p>Nao fala com canal nenhum: e chamado por <code>conversation</code> e, da
 * Etapa 4 em diante, pelo REST do Vue -- a mesma camada de servico, regra 4 do
 * CLAUDE.md.
 *
 * <p>Cartao/fatura (ADR-0011), correcao de campo com historico (ADR-0012,
 * <code>transaction_edit</code>) e metas (ADR-0017) continuam fora: nenhum
 * cenario desta etapa os exercita.
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
     * @param description o residuo semantico da mensagem (ADR-0023). Nulo e o
     *        caso normal, nunca um erro: descricao ausente jamais vira pergunta
     * @param sourceMessageId mensagem que originou o lancamento -- e o que torna
     *        a Etapa 5 mensuravel
     */
    @Transactional
    @HouseholdScoped
    public RegisteredExpense registerExpense(UUID householdId,
                                             UUID memberId,
                                             UUID categoryId,
                                             long amountCents,
                                             String description,
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
        transaction.description = blankToNull(description);
        transaction.createdByMemberId = memberId;
        transaction.source = TransactionSource.CHAT;
        transaction.sourceMessageId = sourceMessageId;
        transactions.persist(transaction);
        // Flush dentro do escopo: se o INSERT so acontecesse no commit, ele
        // rodaria depois do SET LOCAL ROLE ja ter voltado, e o papel de fora
        // nao tem permissao nesta tabela.
        transactions.flush();

        return new RegisteredExpense(transaction.id, transaction.amountCents, displayNameOf(category),
                account.name, transaction.description, transaction.occurredOn);
    }

    /**
     * Estorna o lancamento alvo do <code>desfazer</code> (ADR-0025): o lancamento
     * mais recente do <b>household inteiro</b> ainda nao estornado, de qualquer
     * membro (ADR-0012), sem janela de tempo.
     *
     * <p>Marca <code>reversed_at</code> em vez de apagar. Historico auditavel
     * importa em financas compartilhadas -- quando duas pessoas mexem no mesmo
     * dado, "sumiu" e pior que "foi estornado por fulano" (modelo-de-dados.md).
     *
     * @return vazio quando nao ha nada a estornar
     */
    @Transactional
    @HouseholdScoped
    public Optional<ReversedExpense> reverseLatest(UUID householdId, UUID memberId) {
        return transactions.findLatestNotReversed().map(transaction -> {
            transaction.reversedAt = Instant.now(clock);
            transaction.reversedByMemberId = memberId;
            transactions.flush();

            Category category = categories.findById(transaction.categoryId);
            return new ReversedExpense(transaction.id, transaction.amountCents,
                    category == null ? null : displayNameOf(category), transaction.createdByMemberId);
        });
    }

    /** Formato que a ADR-0026 fixou pro recibo quando a categoria tem pai. */
    private String displayNameOf(Category category) {
        if (category.parentCategoryId == null) {
            return category.name;
        }
        Category parent = categories.findById(category.parentCategoryId);
        return parent == null ? category.name : "%s (dentro de %s)".formatted(category.name, parent.name);
    }

    private String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
