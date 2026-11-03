package com.novoapp.tenancy;

import com.novoapp.common.tenancy.TenantContext;
import com.novoapp.conversation.PendingActionService;
import com.novoapp.conversation.PendingIntent;
import com.novoapp.conversation.cart.ChatCartService;
import com.novoapp.finance.CategoryChange;
import com.novoapp.finance.FinanceService;
import com.novoapp.identity.ContextResolution.ResolvedContext;
import com.novoapp.shopping.ItemDraft;
import com.novoapp.shopping.ShoppingService;
import com.novoapp.support.Fixtures;
import com.novoapp.support.PostgresTestResource;
import com.novoapp.support.TenantProbe;
import io.quarkus.test.common.TestResourceScope;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O teste mais importante do projeto (estrategia-de-testes.md): dois households
 * ao mesmo tempo, e nenhuma operacao de um pode alcancar dado do outro.
 *
 * <p>Vazamento entre households nao e bug de severidade alta -- e evento de
 * encerramento do negocio (ADR-0003).
 */
@QuarkusTest
@WithTestResource(value = PostgresTestResource.class, scope = TestResourceScope.GLOBAL)
class TenantIsolationTest {

    @Inject
    Fixtures fixtures;

    @Inject
    TenantProbe probe;

    @Inject
    FinanceService finance;

    @Inject
    ShoppingService shopping;

    @Inject
    PendingActionService pendingActions;

    @Inject
    ChatCartService cart;

    private UUID householdA;
    private UUID householdB;
    private UUID memberA;
    private UUID memberB;
    private UUID categoryA;
    private UUID categoryB;
    private UUID identityA;

    @BeforeEach
    void setUp() {
        fixtures.truncateAll();

        householdA = fixtures.insertHousehold("Silva");
        memberA = fixtures.insertMember("Ana", "+5511900000001");
        fixtures.insertMembership(householdA, memberA, "OWNER");
        fixtures.insertWallet(householdA);
        categoryA = fixtures.insertExpenseCategory(householdA, "Mercado");
        identityA = fixtures.insertChannelIdentity(memberA, "TELEGRAM", "700000001", householdA);

        householdB = fixtures.insertHousehold("Costa");
        memberB = fixtures.insertMember("Bruno", "+5511900000002");
        fixtures.insertMembership(householdB, memberB, "OWNER");
        fixtures.insertWallet(householdB);
        categoryB = fixtures.insertExpenseCategory(householdB, "Farmacia");
        fixtures.insertChannelIdentity(memberB, "TELEGRAM", "700000002", householdB);
    }

    @Test
    @DisplayName("household A nunca ve lancamento, categoria ou conta de household B")
    void doesNotLeakBetweenHouseholds() {
        TenantContext.withHousehold(householdA,
                () -> finance.registerExpense(householdA, memberA, categoryA, 5_000L, null, null));
        TenantContext.withHousehold(householdB,
                () -> finance.registerExpense(householdB, memberB, categoryB, 8_000L, null, null));

        // Duas linhas existem de verdade -- a fixture ve as duas, sem RLS.
        assertThat(fixtures.count("SELECT count(*) FROM transaction")).isEqualTo(2);

        TenantContext.withHousehold(householdA, () -> {
            assertThat(probe.countTransactions()).isEqualTo(1);
            assertThat(probe.countCategories()).isEqualTo(1);
            assertThat(probe.countAccounts()).isEqualTo(1);
            return null;
        });

        TenantContext.withHousehold(householdB, () -> {
            assertThat(probe.countTransactions()).isEqualTo(1);
            assertThat(probe.countCategories()).isEqualTo(1);
            assertThat(probe.countAccounts()).isEqualTo(1);
            return null;
        });
    }

    /**
     * As tabelas que a Etapa 2a criou. A estrategia-de-testes.md exige o teste de
     * isolamento cobrindo toda tabela de dado de usuario que a etapa toca -- e
     * <code>pending_action</code> em especial guarda o texto de uma conversa,
     * que e o tipo de dado cujo vazamento nao teria conserto.
     */
    @Test
    @DisplayName("household A nunca ve pendencia, lista nem item de household B")
    void doesNotLeakPendingActionsOrShoppingLists() {
        TenantContext.withHousehold(householdA, () -> {
            shopping.addItems(householdA, memberA, List.of(ItemDraft.of("Arroz")), null);
            pendingActions.open(contextOf(householdA, "Silva", memberA, "Ana", identityA),
                    PendingIntent.createCategory("Pet shop", 8_000L, null, null),
                    "Criar a categoria Pet shop?", List.of());
            return null;
        });
        TenantContext.withHousehold(householdB, () -> {
            shopping.addItems(householdB, memberB, List.of(ItemDraft.of("Feijao"), ItemDraft.of("Cafe")), null);
            return null;
        });

        // As linhas existem de verdade -- a fixture ve todas, sem RLS.
        assertThat(fixtures.count("SELECT count(*) FROM list_item")).isEqualTo(3);
        assertThat(fixtures.count("SELECT count(*) FROM pending_action")).isEqualTo(1);

        TenantContext.withHousehold(householdA, () -> {
            assertThat(probe.countShoppingLists()).isEqualTo(1);
            assertThat(probe.countListItems()).isEqualTo(1);
            assertThat(probe.countPendingActions()).isEqualTo(1);
            return null;
        });

        TenantContext.withHousehold(householdB, () -> {
            assertThat(probe.countShoppingLists()).isEqualTo(1);
            assertThat(probe.countListItems()).isEqualTo(2);
            // A pendencia de Silva nao existe para Costa.
            assertThat(probe.countPendingActions()).isZero();
            return null;
        });
    }

    @Test
    @DisplayName("nao da pra lancar no proprio household usando categoria de outro")
    void cannotUseAnotherHouseholdsCategory() {
        assertThatThrownBy(() -> TenantContext.withHousehold(householdA,
                () -> finance.registerExpense(householdA, memberA, categoryB, 5_000L, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Categoria inexistente");

        assertThat(fixtures.count("SELECT count(*) FROM transaction")).isZero();
    }

    @Test
    @DisplayName("gravar com household do contexto diferente do household do dado e recusado pelo banco")
    void cannotWriteIntoAnotherHouseholdWhileScopedToOwn() {
        // Simula o bug classico: o servico recebeu um householdId que nao e o do
        // contexto resolvido. A policy tem WITH CHECK justamente pra isso.
        assertThatThrownBy(() -> TenantContext.withHousehold(householdA,
                () -> finance.registerExpense(householdB, memberB, categoryA, 5_000L, null, null)))
                .isNotNull();

        assertThat(fixtures.count("SELECT count(*) FROM transaction")).isZero();
    }

    @Test
    @DisplayName("sem escopo de tenancy a conexao nao le nada: falha alto, nao volta vazio")
    void failsLoudlyWithoutAnyScope() {
        TenantContext.withHousehold(householdA,
                () -> finance.registerExpense(householdA, memberA, categoryA, 5_000L, null, null));

        // NOINHERIT no papel de login: sem SET ROLE nao ha privilegio nenhum.
        // E o oposto do modo de falha que a ADR-0003 registra como risco
        // ("retorna vazio em vez de erro").
        assertThatThrownBy(() -> probe.countTransactionsWithoutScope()).isNotNull();
    }

    @Test
    @DisplayName("o papel de dominio nao alcanca as tabelas pre-tenant")
    void domainRoleCannotReachPreTenantTables() {
        assertThatThrownBy(() -> TenantContext.withHousehold(householdA,
                () -> probe.countChannelIdentities())).isNotNull();
    }

    /**
     * O Carrinho (ADR-0040) e a tabela de dominio mais nova, e a unica em que o
     * papel de dominio apaga linha. Mesmo teste das outras: as linhas existem de
     * verdade, e cada household so ve as dele.
     */
    @Test
    @DisplayName("household A nunca ve o carrinho de household B")
    void doesNotLeakCartItems() {
        ResolvedContext silva = contextOf(householdA, "Silva", memberA, "Ana", identityA);
        TenantContext.withHousehold(householdA, () -> {
            UUID arroz = shopping.addItems(householdA, memberA, List.of(ItemDraft.of("Arroz")), null).get(0).itemId();
            cart.put(silva, arroz);
            return null;
        });

        assertThat(fixtures.count("SELECT count(*) FROM chat_cart_item")).isEqualTo(1);

        TenantContext.withHousehold(householdA, () -> {
            assertThat(probe.countCartItems()).isEqualTo(1);
            return null;
        });
        TenantContext.withHousehold(householdB, () -> {
            assertThat(probe.countCartItems()).isZero();
            return null;
        });
    }

    /**
     * O historico de edicao (ADR-0012), que a troca de categoria pelo recibo
     * criou (ADR-0041): cada household so ve as proprias correcoes.
     */
    @Test
    @DisplayName("household A nunca ve o historico de edicao de household B")
    void doesNotLeakTransactionEdits() {
        UUID otherCategoryA = fixtures.insertExpenseCategory(householdA, "Farmacia");
        UUID expenseA = TenantContext.withHousehold(householdA,
                () -> finance.registerExpense(householdA, memberA, categoryA, 5_000L, null, null).transactionId());
        TenantContext.withHousehold(householdA,
                () -> finance.changeCategory(householdA, memberA, expenseA, otherCategoryA));

        assertThat(fixtures.count("SELECT count(*) FROM transaction_edit")).isEqualTo(1);
        TenantContext.withHousehold(householdA, () -> assertThat(probe.countTransactionEdits()).isEqualTo(1));
        TenantContext.withHousehold(householdB, () -> assertThat(probe.countTransactionEdits()).isZero());
    }

    /**
     * Os ids dos botoes do recibo vem do cliente. Lancamento de outro household
     * nao e encontrado -- nem para ler, nem para estornar, nem para trocar -- e a
     * linha dele fica intocada.
     */
    @Test
    @DisplayName("botao com lancamento de outro household nao le, nao estorna e nao troca nada")
    void receiptButtonsCannotReachAnotherHouseholdsEntry() {
        UUID expenseB = TenantContext.withHousehold(householdB,
                () -> finance.registerExpense(householdB, memberB, categoryB, 8_000L, null, null).transactionId());

        TenantContext.withHousehold(householdA, () -> {
            assertThat(finance.expense(expenseB)).isEmpty();
            assertThat(finance.reverse(householdA, memberA, expenseB)).isEmpty();
            assertThat(finance.changeCategory(householdA, memberA, expenseB, categoryA))
                    .isInstanceOf(CategoryChange.NotFound.class);
        });

        assertThat(fixtures.query("SELECT reversed_at, category_id FROM transaction WHERE id = ?", expenseB).get(0))
                .containsExactly(null, categoryB);
        assertThat(fixtures.count("SELECT count(*) FROM transaction_edit")).isZero();
    }

    /**
     * A categoria de destino tambem vem do botao. A FK de
     * <code>transaction.category_id</code> nao segura isto -- checagem de FK
     * ignora RLS --, entao quem segura e a leitura sob RLS em
     * <code>finance</code>.
     */
    @Test
    @DisplayName("nao da pra trocar o proprio lancamento para a categoria de outro household")
    void cannotChangeIntoAnotherHouseholdsCategory() {
        UUID expenseA = TenantContext.withHousehold(householdA,
                () -> finance.registerExpense(householdA, memberA, categoryA, 5_000L, null, null).transactionId());

        CategoryChange change = TenantContext.withHousehold(householdA,
                () -> finance.changeCategory(householdA, memberA, expenseA, categoryB));

        assertThat(change).isInstanceOf(CategoryChange.CategoryUnavailable.class);
        assertThat(fixtures.query("SELECT category_id FROM transaction WHERE id = ?", expenseA).get(0).get(0))
                .isEqualTo(categoryA);
        assertThat(fixtures.count("SELECT count(*) FROM transaction_edit")).isZero();
    }

    @Test
    @DisplayName("historico de edicao com household de outro e recusado pelo banco")
    void cannotWriteAnEditIntoAnotherHousehold() {
        UUID expenseA = TenantContext.withHousehold(householdA,
                () -> finance.registerExpense(householdA, memberA, categoryA, 5_000L, null, null).transactionId());

        assertThatThrownBy(() -> TenantContext.withHousehold(householdA,
                () -> probe.insertTransactionEdit(householdB, expenseA, memberA)))
                .hasStackTraceContaining("row-level security");
    }

    /** Append-only (ADR-0012): correcao errada se corrige com outra linha, nunca reescrevendo. */
    @Test
    @DisplayName("o papel de dominio nao reescreve nem apaga historico de edicao")
    void transactionEditsAreAppendOnly() {
        UUID otherCategoryA = fixtures.insertExpenseCategory(householdA, "Farmacia");
        UUID expenseA = TenantContext.withHousehold(householdA,
                () -> finance.registerExpense(householdA, memberA, categoryA, 5_000L, null, null).transactionId());
        TenantContext.withHousehold(householdA,
                () -> finance.changeCategory(householdA, memberA, expenseA, otherCategoryA));

        assertThatThrownBy(() -> TenantContext.withHousehold(householdA, () -> probe.rewriteTransactionEdits()))
                .hasStackTraceContaining("permission denied");
        assertThatThrownBy(() -> TenantContext.withHousehold(householdA, () -> probe.deleteTransactionEdits()))
                .hasStackTraceContaining("permission denied");
        assertThat(fixtures.count("SELECT count(*) FROM transaction_edit")).isEqualTo(1);
    }

    private ResolvedContext contextOf(UUID householdId, String householdName,
                                      UUID memberId, String memberName, UUID channelIdentityId) {
        return new ResolvedContext(householdId, householdName, memberId, memberName, channelIdentityId,
                com.novoapp.common.i18n.Messages.DEFAULT, false);
    }
}
