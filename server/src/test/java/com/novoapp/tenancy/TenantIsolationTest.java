package com.novoapp.tenancy;

import com.novoapp.common.tenancy.TenantContext;
import com.novoapp.finance.FinanceService;
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

    private UUID householdA;
    private UUID householdB;
    private UUID memberA;
    private UUID memberB;
    private UUID categoryA;
    private UUID categoryB;

    @BeforeEach
    void setUp() {
        fixtures.truncateAll();

        householdA = fixtures.insertHousehold("Silva");
        memberA = fixtures.insertMember("Ana", "+5511900000001");
        fixtures.insertMembership(householdA, memberA, "OWNER");
        fixtures.insertWallet(householdA);
        categoryA = fixtures.insertExpenseCategory(householdA, "Mercado");

        householdB = fixtures.insertHousehold("Costa");
        memberB = fixtures.insertMember("Bruno", "+5511900000002");
        fixtures.insertMembership(householdB, memberB, "OWNER");
        fixtures.insertWallet(householdB);
        categoryB = fixtures.insertExpenseCategory(householdB, "Farmacia");
    }

    @Test
    @DisplayName("household A nunca ve lancamento, categoria ou conta de household B")
    void doesNotLeakBetweenHouseholds() {
        TenantContext.withHousehold(householdA,
                () -> finance.registerExpense(householdA, memberA, categoryA, 5_000L, null));
        TenantContext.withHousehold(householdB,
                () -> finance.registerExpense(householdB, memberB, categoryB, 8_000L, null));

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

    @Test
    @DisplayName("nao da pra lancar no proprio household usando categoria de outro")
    void cannotUseAnotherHouseholdsCategory() {
        assertThatThrownBy(() -> TenantContext.withHousehold(householdA,
                () -> finance.registerExpense(householdA, memberA, categoryB, 5_000L, null)))
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
                () -> finance.registerExpense(householdB, memberB, categoryA, 5_000L, null)))
                .isNotNull();

        assertThat(fixtures.count("SELECT count(*) FROM transaction")).isZero();
    }

    @Test
    @DisplayName("sem escopo de tenancy a conexao nao le nada: falha alto, nao volta vazio")
    void failsLoudlyWithoutAnyScope() {
        TenantContext.withHousehold(householdA,
                () -> finance.registerExpense(householdA, memberA, categoryA, 5_000L, null));

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
}
