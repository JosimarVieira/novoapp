package com.novoapp.acceptance;

import com.novoapp.support.Fixtures;
import io.cucumber.java.Before;
import io.cucumber.java.pt.Dado;
import io.cucumber.java.pt.E;
import io.cucumber.java.pt.Entao;
import io.cucumber.java.pt.Quando;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Passos de `financas-lancamento-por-chat.feature`, cenarios @etapa1. */
public class ExpenseByChatSteps {

    @Inject
    AcceptanceWorld world;

    @Inject
    Fixtures fixtures;

    @Before
    public void resetBetweenScenarios() {
        world.reset();
    }

    @Dado("^que existe o household \"([^\"]*)\"$")
    public void householdExists(String householdName) {
        UUID householdId = fixtures.insertHousehold(householdName);
        world.households.put(householdName, householdId);
        // Toda familia nasce com a carteira implicita (ADR-0011); aqui ela vem
        // da fixture porque a familia nao passou pelo onboarding.
        fixtures.insertWallet(householdId);
    }

    @Dado("^que \"([^\"]*)\" é membro do household \"([^\"]*)\" com o Telegram vinculado$")
    public void memberWithTelegramLinked(String memberName, String householdName) {
        UUID householdId = world.households.get(householdName);
        UUID memberId = fixtures.insertMember(memberName, null);
        fixtures.insertMembership(householdId, memberId, "OWNER");
        fixtures.insertChannelIdentity(memberId, "TELEGRAM", world.externalIdFor(memberName), householdId);
        world.members.put(memberName, memberId);
        world.nameOf(memberName, memberName);
    }

    /**
     * Categoria vem da fixture: household novo nasce sem nenhuma (ADR-0013) e
     * criar categoria por chat e cenario @etapa2.
     */
    @Dado("^que o household \"([^\"]*)\" tem as categorias de despesa \"([^\"]*)\" e \"([^\"]*)\"$")
    public void householdHasExpenseCategories(String householdName, String first, String second) {
        UUID householdId = world.households.get(householdName);
        world.categories.put(first, fixtures.insertExpenseCategory(householdId, first));
        world.categories.put(second, fixtures.insertExpenseCategory(householdId, second));
    }

    @Quando("^\"([^\"]*)\" envia \"([^\"]*)\"$")
    public void sends(String actor, String text) {
        world.send(actor, text);
    }

    @Quando("^o provedor entrega duas vezes a mesma mensagem \"([^\"]*)\" de \"([^\"]*)\"$")
    public void providerDeliversTwice(String text, String actor) {
        world.sendTwice(actor, text);
    }

    @Quando("^uma mensagem \"([^\"]*)\" chega de um número desconhecido$")
    public void messageFromUnknownNumber(String text) {
        world.send("desconhecido", text);
    }

    @Entao("^uma despesa de R\\$ ([\\d.,]+) é registrada na categoria \"([^\"]*)\" com data de hoje$")
    public void expenseRegisteredToday(String amount, String categoryName) {
        expenseRegistered(amount, categoryName);
        List<List<Object>> rows = fixtures.query("SELECT occurred_on FROM transaction");
        assertThat(((java.sql.Date) rows.get(0).get(0)).toLocalDate()).isEqualTo(LocalDate.now());
    }

    @Entao("^uma despesa de R\\$ ([\\d.,]+) é registrada na categoria \"([^\"]*)\"$")
    public void expenseRegistered(String amount, String categoryName) {
        List<List<Object>> rows = fixtures.query("""
                SELECT t.amount_cents, c.name, t.kind
                FROM transaction t JOIN category c ON c.id = t.category_id""");
        assertThat(rows).hasSize(1);
        assertThat(((Number) rows.get(0).get(0)).longValue()).isEqualTo(cents(amount));
        assertThat(rows.get(0).get(1)).isEqualTo(categoryName);
        assertThat(rows.get(0).get(2)).isEqualTo("EXPENSE");
    }

    @Entao("^exatamente uma despesa de R\\$ ([\\d.,]+) é registrada$")
    public void exactlyOneExpenseRegistered(String amount) {
        List<List<Object>> rows = fixtures.query("SELECT amount_cents FROM transaction");
        assertThat(rows).hasSize(1);
        assertThat(((Number) rows.get(0).get(0)).longValue()).isEqualTo(cents(amount));
    }

    @E("^o lançamento fica atribuído a \"([^\"]*)\"$")
    public void expenseAttributedTo(String memberName) {
        List<List<Object>> rows = fixtures.query("SELECT created_by_member_id, source FROM transaction");
        assertThat(rows.get(0).get(0)).isEqualTo(world.members.get(memberName));
        assertThat(rows.get(0).get(1)).isEqualTo("CHAT");
    }

    @E("^\"([^\"]*)\" recebe um recibo informando valor, categoria e como desfazer$")
    public void receivesReceipt(String actor) {
        String receipt = world.lastReplyTo(actor);
        assertThat(receipt).isNotNull();
        List<List<Object>> rows = fixtures.query("""
                SELECT t.amount_cents, c.name
                FROM transaction t JOIN category c ON c.id = t.category_id""");
        assertThat(receipt)
                .contains(String.valueOf(rows.get(0).get(1)))
                .contains("R$ 50,00")
                .contains("desfazer");
    }

    @E("^\"([^\"]*)\" recebe exatamente um recibo$")
    public void receivesExactlyOneReceipt(String actor) {
        assertThat(world.repliesTo(actor)).hasSize(1);
    }

    @Entao("^nenhuma despesa é registrada em nenhum household$")
    public void noExpenseAnywhere() {
        assertThat(fixtures.count("SELECT count(*) FROM transaction")).isZero();
    }

    @E("^o remetente recebe apenas uma orientação de como vincular o número$")
    public void senderReceivesOnlyLinkingGuidance() {
        assertThat(world.repliesTo("desconhecido")).hasSize(1);
        assertThat(world.lastReplyTo("desconhecido"))
                .contains("vinculado a uma família")
                .contains("Quer criar uma família nova?");
    }

    private long cents(String amount) {
        return new BigDecimal(amount.replace(".", "").replace(',', '.')).movePointRight(2).longValueExact();
    }
}
