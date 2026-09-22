package com.novoapp.acceptance;

import com.novoapp.support.Fixtures;
import com.novoapp.support.UnavailableExpenseRegistration;
import io.cucumber.java.pt.Dado;
import io.cucumber.java.pt.E;
import io.cucumber.java.pt.Entao;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Passos de `elo-fechamento-de-compra.feature` -- os onze cenarios do elo, que
 * materializam o diferencial do produto (CLAUDE.md).
 *
 * <p>Arquivo proprio, e nao acrescimo a {@link ShoppingListSteps}: aquele cobre
 * a lista sozinha, este cobre a lista <b>e</b> o dinheiro na mesma operacao, e
 * quase todo passo daqui asserta as duas coisas juntas. O que os dois
 * compartilham -- montar household, mandar mensagem, contar recibos -- ja mora
 * em {@link AcceptanceWorld} e nos passos genericos de
 * {@link ExpenseByChatSteps}, que este arquivo reusa em vez de duplicar.
 */
public class CheckoutSteps {

    private static final String PENDING = "PENDING";
    private static final String PURCHASED = "PURCHASED";

    @Inject
    AcceptanceWorld world;

    @Inject
    Fixtures fixtures;

    @Inject
    UnavailableExpenseRegistration finance;

    // ------------------------------------------------------------------
    // Contexto
    // ------------------------------------------------------------------

    @Dado("^que o household \"([^\"]*)\" tem a categoria de despesa \"([^\"]*)\"$")
    public void householdHasExpenseCategory(String householdName, String categoryName) {
        world.categories.put(categoryName,
                fixtures.insertExpenseCategory(world.households.get(householdName), categoryName));
    }

    @Dado("^que a lista de compras ativa tem os itens pendentes \"([^\"]*)\", \"([^\"]*)\" e \"([^\"]*)\"$")
    public void activeListHasPendingItems(String first, String second, String third) {
        String householdName = world.households.keySet().iterator().next();
        UUID householdId = world.households.get(householdName);
        world.shoppingLists.put(householdName, fixtures.insertShoppingList(householdId));
        for (String name : List.of(first, second, third)) {
            insertPending(name);
        }
    }

    @Dado("^que o item \"([^\"]*)\" também está pendente na lista$")
    public void itemAlsoPending(String itemName) {
        insertPending(itemName);
    }

    /**
     * Quem pediu o item. O <code>Contexto</code> do arquivo declara isso para
     * que o cenario "Ana enxerga o que Bruno comprou" fale de duas pessoas de
     * verdade -- e para deixar visivel que quem pede e quem fecha nao precisam
     * ser a mesma pessoa, que e literalmente o elo que o produto vende.
     */
    @E("^que \"([^\"]*)\" solicitou o item \"([^\"]*)\"$")
    public void memberRequestedItem(String memberName, String itemName) {
        fixtures.execute("UPDATE list_item SET requested_by_member_id = ? WHERE name_normalized = ?",
                world.members.get(memberName), com.novoapp.common.text.Normalization.of(itemName));
    }

    @Dado("^que não existe lista de compras ativa no household \"([^\"]*)\"$")
    public void noActiveList(String householdName) {
        fixtures.execute("DELETE FROM list_item");
        fixtures.execute("DELETE FROM shopping_list");
        world.shoppingLists.remove(householdName);
        assertThat(fixtures.count("SELECT count(*) FROM shopping_list WHERE status = 'ACTIVE'")).isZero();
    }

    /**
     * O passo que prova a ADR-0031, e o unico dos quatro testes obrigatorios da
     * estrategia-de-testes.md que nunca existiu.
     *
     * <p>Quando ele esta ligado, <code>finance</code> estoura <b>depois</b> de
     * <code>shopping</code> ja ter marcado os itens e descarregado o
     * <code>UPDATE</code> no banco. Sem a transacao unica, os itens ficariam
     * comprados e nao haveria lancamento nenhum -- que e exatamente o estado que
     * o cenario proibe.
     */
    @Dado("^que o registro de despesas está indisponível$")
    public void expenseRegistrationUnavailable() {
        finance.makeUnavailable();
    }

    /**
     * Um fechamento anterior, montado pelo proprio fluxo e nao por
     * <code>INSERT</code>: o que o cenario do segundo fechamento parcial testa e
     * que dois fechamentos convivem na mesma lista, e isso so significa alguma
     * coisa sobre fechamentos que o codigo de verdade criou.
     */
    @Dado("^que \"([^\"]*)\" já fechou \"([^\"]*)\" e \"([^\"]*)\" por R\\$ ([\\d.,]+)$")
    public void alreadyClosedTwoItems(String actor, String first, String second, String amount) {
        world.send(actor, "comprei o %s e o %s, %s".formatted(first, second, plain(amount)));
        assertThat(statusOf(first)).isEqualTo(PURCHASED);
        assertThat(statusOf(second)).isEqualTo(PURCHASED);
    }

    @Dado("^que \"([^\"]*)\" fechou a compra de R\\$ ([\\d.,]+) há (\\d+) minutos?$")
    public void closedThePurchase(String actor, String amount, int minutes) {
        world.send(actor, "comprei tudo, " + plain(amount));
        assertThat(fixtures.count("SELECT count(*) FROM list_checkout")).isEqualTo(1);
        // O "ha N minutos" e cor do cenario, nao regra: o alvo do desfazer e o
        // lancamento mais recente nao estornado, sem janela de tempo (ADR-0025).
        fixtures.execute("UPDATE transaction SET created_at = now() - (? || ' minutes')::interval",
                String.valueOf(minutes));
    }

    // ------------------------------------------------------------------
    // Itens
    // ------------------------------------------------------------------

    @Entao("^os itens \"([^\"]*)\", \"([^\"]*)\" e \"([^\"]*)\" ficam com status comprado$")
    public void threeItemsPurchased(String first, String second, String third) {
        for (String name : List.of(first, second, third)) {
            assertThat(statusOf(name)).as(name).isEqualTo(PURCHASED);
        }
    }

    @Entao("^os itens \"([^\"]*)\" e \"([^\"]*)\" ficam com status comprado$")
    public void twoItemsPurchased(String first, String second) {
        assertThat(statusOf(first)).as(first).isEqualTo(PURCHASED);
        assertThat(statusOf(second)).as(second).isEqualTo(PURCHASED);
    }

    @Entao("^o item \"([^\"]*)\" fica com status comprado$")
    public void itemPurchased(String itemName) {
        assertThat(statusOf(itemName)).isEqualTo(PURCHASED);
    }

    @Entao("^os três itens ficam com status comprado$")
    public void threePendingItemsPurchased() {
        assertThat(fixtures.count("SELECT count(*) FROM list_item WHERE status = ?", PURCHASED))
                .isEqualTo(3);
        assertThat(fixtures.count("SELECT count(*) FROM list_item WHERE status = ?", PENDING)).isZero();
    }

    @E("^os itens ficam registrados como comprados por \"([^\"]*)\"$")
    public void itemsPurchasedBy(String memberName) {
        List<List<Object>> rows = fixtures.query(
                "SELECT purchased_by_member_id, purchased_at FROM list_item WHERE status = ?", PURCHASED);
        assertThat(rows).isNotEmpty();
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.get(0)).isEqualTo(world.members.get(memberName));
            assertThat(row.get(1)).isNotNull();
        });
    }

    @E("^os itens \"([^\"]*)\", \"([^\"]*)\" e \"([^\"]*)\" continuam pendentes$")
    public void threeItemsStillPending(String first, String second, String third) {
        for (String name : List.of(first, second, third)) {
            assertThat(statusOf(name)).as(name).isEqualTo(PENDING);
        }
    }

    @Entao("^os itens \"([^\"]*)\", \"([^\"]*)\" e \"([^\"]*)\" voltam a ficar pendentes$")
    public void threeItemsBackToPending(String first, String second, String third) {
        threeItemsStillPending(first, second, third);
        // Voltar a PENDING nao e so o status: o item voltou a ser algo que falta,
        // e quem o tinha comprado esta no historico do lancamento estornado
        // (ADR-0032).
        assertThat(fixtures.count("""
                SELECT count(*) FROM list_item
                 WHERE status = ? AND purchased_by_member_id IS NOT NULL""", PENDING)).isZero();
    }

    @Entao("^nenhum item muda de status$")
    public void noItemChangedStatus() {
        assertThat(fixtures.count("SELECT count(*) FROM list_item WHERE status <> ?", PENDING)).isZero();
    }

    @Entao("^nenhum item muda de status ainda$")
    public void noItemChangedStatusYet() {
        noItemChangedStatus();
    }

    @Entao("^os itens \"([^\"]*)\", \"([^\"]*)\" e \"([^\"]*)\" ficam com status comprado uma única vez$")
    public void threeItemsPurchasedOnce(String first, String second, String third) {
        threeItemsPurchased(first, second, third);
        // Reentrega (ADR-0005): o efeito e um so, e e a contagem que prova --
        // status sozinho ficaria igual com dois fechamentos.
        assertThat(fixtures.count("SELECT count(*) FROM list_item")).isEqualTo(3);
        assertThat(fixtures.count("SELECT count(*) FROM list_checkout")).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // O elo
    // ------------------------------------------------------------------

    @E("^o fechamento fica ligado à despesa criada$")
    public void checkoutLinkedToExpense() {
        List<List<Object>> rows = fixtures.query("""
                SELECT c.id, c.items_purchased_count
                FROM list_checkout c JOIN transaction t ON t.id = c.transaction_id
                WHERE t.reversed_at IS NULL""");
        assertThat(rows).hasSize(1);
        // O vinculo vale nos dois sentidos: o fechamento aponta para o
        // lancamento, e cada item fechado aponta para o fechamento -- e este
        // segundo lado que o `desfazer` da ADR-0032 percorre.
        assertThat(fixtures.count("SELECT count(*) FROM list_item WHERE list_checkout_id = ?",
                rows.get(0).get(0)))
                .isEqualTo(((Number) rows.get(0).get(1)).longValue());
    }

    @E("^a lista registra dois fechamentos distintos$")
    public void twoDistinctCheckouts() {
        List<List<Object>> rows = fixtures.query(
                "SELECT id, transaction_id FROM list_checkout ORDER BY performed_at");
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get(1)).isNotEqualTo(rows.get(1).get(1));
        assertThat(fixtures.count("SELECT count(DISTINCT shopping_list_id) FROM list_checkout"))
                .isEqualTo(1);
    }

    @E("^uma segunda despesa de R\\$ ([\\d.,]+) é registrada na categoria \"([^\"]*)\"$")
    public void secondExpenseRegistered(String amount, String categoryName) {
        assertThat(fixtures.count("SELECT count(*) FROM transaction WHERE reversed_at IS NULL"))
                .isEqualTo(2);
        assertThat(fixtures.count("""
                SELECT count(*) FROM transaction t JOIN category c ON c.id = t.category_id
                 WHERE t.amount_cents = ? AND c.name = ? AND t.reversed_at IS NULL""",
                cents(amount), categoryName)).isEqualTo(1);
    }

    @E("^a despesa de R\\$ ([\\d.,]+) é estornada$")
    public void expenseReversed(String amount) {
        assertThat(fixtures.count("""
                SELECT count(*) FROM transaction
                 WHERE amount_cents = ? AND reversed_at IS NOT NULL""", cents(amount))).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // Respostas
    // ------------------------------------------------------------------

    @E("^\"([^\"]*)\" recebe um recibo com a quantidade de itens e o valor$")
    public void receivesCheckoutReceipt(String actor) {
        String receipt = world.lastReplyTo(actor);
        List<List<Object>> rows = fixtures.query("""
                SELECT c.items_purchased_count, t.amount_cents
                FROM list_checkout c JOIN transaction t ON t.id = c.transaction_id""");
        assertThat(receipt)
                .contains(String.valueOf(rows.get(0).get(0)))
                .contains(formatAmount(((Number) rows.get(0).get(1)).longValue()))
                .contains("desfazer");
    }

    @E("^\"([^\"]*)\" é avisado da falha pelo chat$")
    public void toldAboutFailure(String actor) {
        // Erro depois do 200 do webhook tem de aparecer no chat, nunca so no log
        // (sdd-visao-geral.md). O texto nao afirma "nao gravei nada": o
        // orquestrador nao e transacional, e o recibo de erro nao mente sobre
        // isso.
        String reply = world.lastReplyTo(actor);
        assertThat(reply).isNotNull();
        assertThat(fold(reply)).contains("deu erro aqui no meio do processamento");
    }

    @E("^\"([^\"]*)\" recebe uma pergunta curta pedindo o valor da compra$")
    public void receivesCheckoutAmountQuestion(String actor) {
        assertThat(fold(world.lastReplyTo(actor))).contains("quanto foi a compra");
        assertThat(fixtures.count("SELECT count(*) FROM pending_action WHERE resolved_at IS NULL"))
                .isEqualTo(1);
    }

    @Entao("^\"([^\"]*)\" recebe uma pergunta oferecendo registrar apenas a despesa$")
    public void receivesOfferOfExpenseOnly(String actor) {
        assertThat(fold(world.lastReplyTo(actor))).contains("registre so a despesa");
        assertThat(fixtures.count("SELECT count(*) FROM list_checkout")).isZero();
        assertThat(fixtures.count("SELECT count(*) FROM pending_action WHERE resolved_at IS NULL"))
                .isEqualTo(1);
    }

    // ------------------------------------------------------------------

    private void insertPending(String itemName) {
        String householdName = world.households.keySet().iterator().next();
        fixtures.insertListItem(world.households.get(householdName),
                world.shoppingLists.get(householdName), itemName, PENDING,
                world.members.values().iterator().next());
    }

    private String statusOf(String itemName) {
        List<List<Object>> rows = fixtures.query(
                "SELECT status FROM list_item WHERE name_normalized = ?",
                com.novoapp.common.text.Normalization.of(itemName));
        assertThat(rows).as(itemName).hasSize(1);
        return (String) rows.get(0).get(0);
    }

    /** "180,00" do Gherkin vira "180" na mensagem que a pessoa digitaria. */
    private String plain(String amount) {
        return amount.replace(".", "").replaceAll(",00$", "").replace(',', '.');
    }

    private String formatAmount(long amountCents) {
        return "R$ " + String.format(Locale.of("pt", "BR"), "%,.2f",
                BigDecimal.valueOf(amountCents).movePointLeft(2));
    }

    private long cents(String amount) {
        return new BigDecimal(amount.replace(".", "").replace(',', '.')).movePointRight(2).longValueExact();
    }

    private String fold(String text) {
        return Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }
}
