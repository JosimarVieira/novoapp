package com.novoapp.finance;

import com.novoapp.common.tenancy.TenantContext;
import com.novoapp.support.Fixtures;
import com.novoapp.support.PostgresTestResource;
import io.quarkus.test.common.TestResourceScope;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Duas escritas no mesmo lancamento ao mesmo tempo (ADR-0041): dois estornos,
 * ou um estorno e uma troca de categoria.
 *
 * <p>Cada mensagem e processada na propria thread em producao
 * ({@code InboundDispatcher}), entao dois toques no mesmo botao -- ou um toque e
 * um <code>desfazer</code> digitado -- podem chegar juntos. Os cenarios de
 * aceitacao rodam sincronos e nao alcancam isso: o toque duplo de la passa pela
 * releitura, nunca pela corrida.
 *
 * <p>O primeiro estorno roda numa transacao que fica aberta, com a linha
 * travada, ate o teste liberar; o segundo e disparado nesse meio tempo e tem de
 * esperar. Conferir que ele esperou e o que prova que a intercalacao aconteceu --
 * sem isso o teste passaria em serie e nao provaria nada.
 */
@QuarkusTest
@WithTestResource(value = PostgresTestResource.class, scope = TestResourceScope.GLOBAL)
class ReversalConcurrencyTest {

    /** Segura a transacao do primeiro estorno aberta: precisa de bean, pelo interceptor. */
    @ApplicationScoped
    public static class HeldReversal {

        @Inject
        FinanceService finance;

        @Transactional
        public Optional<ReversedExpense> reverseAndHold(UUID household, UUID member, UUID transactionId,
                                                        CountDownLatch done, CountDownLatch release)
                throws InterruptedException {
            Optional<ReversedExpense> reversed = finance.reverse(household, member, transactionId);
            done.countDown();
            release.await(10, TimeUnit.SECONDS);
            return reversed;
        }

        @Transactional
        public CategoryChange changeAndHold(UUID household, UUID member, UUID transactionId, UUID categoryId,
                                            CountDownLatch done, CountDownLatch release)
                throws InterruptedException {
            CategoryChange change = finance.changeCategory(household, member, transactionId, categoryId);
            done.countDown();
            release.await(10, TimeUnit.SECONDS);
            return change;
        }
    }

    /**
     * Quantas vezes o estorno foi anunciado: o observador de shopping reage a cada
     * um. {@code @Singleton}, e nao de escopo normal: o teste le o campo direto, e
     * pelo proxy de um bean de escopo normal ele leria o campo do proxy.
     */
    @Singleton
    public static class ReversalCounter {

        final AtomicInteger announced = new AtomicInteger();

        void onReversal(@Observes ExpenseReversed event) {
            announced.incrementAndGet();
        }
    }

    @Inject
    Fixtures fixtures;

    @Inject
    FinanceService finance;

    @Inject
    HeldReversal held;

    @Inject
    ReversalCounter counter;

    private final ExecutorService threads = Executors.newFixedThreadPool(2);

    private UUID household;
    private UUID ana;
    private UUID bruno;
    private UUID expense;
    private UUID pharmacy;
    private UUID grocery;

    @BeforeEach
    void setUp() {
        fixtures.truncateAll();
        household = fixtures.insertHousehold("Silva");
        ana = fixtures.insertMember("Ana", "+5511900000001");
        bruno = fixtures.insertMember("Bruno", "+5511900000002");
        fixtures.insertMembership(household, ana, "OWNER");
        fixtures.insertMembership(household, bruno, "MEMBER");
        UUID wallet = fixtures.insertWallet(household);
        pharmacy = fixtures.insertExpenseCategory(household, "Farmacia");
        grocery = fixtures.insertExpenseCategory(household, "Mercado");
        expense = fixtures.insertExpense(household, bruno, pharmacy, wallet, 3_000L, Instant.now());
        counter.announced.set(0);
    }

    @AfterEach
    void tearDown() {
        threads.shutdownNow();
    }

    @Test
    @DisplayName("dois toques no desfazer do mesmo recibo, juntos: estorna uma vez, e o segundo fica vazio")
    void twoTapsAtOnceReverseOnce() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Optional<ReversedExpense>> first = inHousehold(
                () -> uncheckedHold(bruno, done, release));
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<Optional<ReversedExpense>> second = inHousehold(
                () -> finance.reverse(household, ana, expense));
        assertWaiting(second);
        release.countDown();

        assertThat(first.get(10, TimeUnit.SECONDS)).isPresent();
        assertThat(second.get(10, TimeUnit.SECONDS)).isEmpty();
        assertThat(counter.announced).hasValue(1);
        assertThat(fixtures.query("SELECT reversed_by_member_id FROM transaction WHERE id = ?", expense)
                .get(0).get(0)).isEqualTo(bruno);
    }

    /**
     * O <code>desfazer</code> digitado le o alvo sem trava. Antes do carimbo
     * condicional, o UPDATE da linha inteira dele esperava o botao terminar e
     * estornava de novo: dois anuncios, e o autor do estorno trocado.
     */
    @Test
    @DisplayName("o desfazer digitado durante o estorno pelo botao nao estorna de novo")
    void typedUndoDuringTheButtonDoesNotReverseAgain() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Optional<ReversedExpense>> button = inHousehold(
                () -> uncheckedHold(bruno, done, release));
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<Optional<ReversedExpense>> typed = inHousehold(
                () -> finance.reverseLatest(household, ana));
        assertWaiting(typed);
        release.countDown();

        assertThat(button.get(10, TimeUnit.SECONDS)).isPresent();
        assertThat(typed.get(10, TimeUnit.SECONDS)).isEmpty();
        assertThat(counter.announced).hasValue(1);
        assertThat(fixtures.query("SELECT reversed_by_member_id FROM transaction WHERE id = ?", expense)
                .get(0).get(0)).isEqualTo(bruno);
    }

    /**
     * O caso que o carimbo condicional existe para segurar: com a linha inteira
     * regravada pelo estado lido antes da troca, o lancamento sairia de cena na
     * categoria antiga, contra o historico e contra o recibo que a pessoa viu
     * (ADR-0041, item 5).
     */
    @Test
    @DisplayName("o desfazer digitado durante a troca de categoria estorna na categoria nova")
    void typedUndoDuringTheChangeKeepsTheNewCategory() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<CategoryChange> change = inHousehold(() -> {
            try {
                return held.changeAndHold(household, bruno, expense, grocery, done, release);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        });
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<Optional<ReversedExpense>> typed = inHousehold(
                () -> finance.reverseLatest(household, ana));
        assertWaiting(typed);
        release.countDown();

        assertThat(change.get(10, TimeUnit.SECONDS)).isInstanceOf(CategoryChange.Changed.class);
        assertThat(typed.get(10, TimeUnit.SECONDS)).hasValueSatisfying(reversed ->
                assertThat(reversed.categoryDisplayName()).isEqualTo("Mercado"));
        assertThat(fixtures.query("SELECT category_id, reversed_at IS NOT NULL FROM transaction WHERE id = ?",
                expense).get(0)).containsExactly(grocery, true);
        assertThat(fixtures.count("SELECT count(*) FROM transaction_edit")).isEqualTo(1);
    }

    // ------------------------------------------------------------------

    private Optional<ReversedExpense> uncheckedHold(UUID member, CountDownLatch done, CountDownLatch release) {
        try {
            return held.reverseAndHold(household, member, expense, done, release);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private <T> CompletableFuture<T> inHousehold(Supplier<T> body) {
        return CompletableFuture.supplyAsync(() -> TenantContext.withHousehold(household, body), threads);
    }

    /** A segunda escrita esta presa na trava da primeira: a intercalacao aconteceu. */
    private void assertWaiting(CompletableFuture<?> second) throws InterruptedException {
        Thread.sleep(300);
        assertThat(second).as("a segunda escrita esperou a primeira transacao").isNotDone();
    }
}
