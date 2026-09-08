package com.novoapp.acceptance;

import com.novoapp.support.Fixtures;
import io.cucumber.java.Before;
import io.cucumber.java.pt.Dado;
import io.cucumber.java.pt.E;
import io.cucumber.java.pt.Entao;
import io.cucumber.java.pt.Quando;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Passos de `financas-lancamento-por-chat.feature`, cenarios @etapa1 e @etapa2. */
public class ExpenseByChatSteps {

    @Inject
    AcceptanceWorld world;

    @Inject
    Fixtures fixtures;

    @Before
    public void resetBetweenScenarios() {
        world.reset();
    }

    // ------------------------------------------------------------------
    // Montagem do household
    // ------------------------------------------------------------------

    @Dado("^que existe o household \"([^\"]*)\"$")
    public void householdExists(String householdName) {
        UUID householdId = fixtures.insertHousehold(householdName);
        world.households.put(householdName, householdId);
        // Toda familia nasce com a carteira implicita (ADR-0011); aqui ela vem
        // da fixture porque a familia nao passou pelo onboarding.
        world.wallets.put(householdName, fixtures.insertWallet(householdId));
    }

    /**
     * Household sem categoria nenhuma e o estado normal de familia nova
     * (ADR-0013) -- este passo existe pra deixar isso explicito no cenario, nao
     * pra montar nada diferente.
     */
    @Dado("^que existe o household \"([^\"]*)\", sem nenhuma categoria de despesa$")
    public void householdWithoutCategories(String householdName) {
        householdExists(householdName);
        assertThat(fixtures.count("SELECT count(*) FROM category WHERE household_id = ?",
                world.households.get(householdName))).isZero();
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
     * criar categoria por chat e justamente o que varios cenarios @etapa2
     * exercitam -- semear aqui o que o cenario quer testar seria circular.
     */
    @Dado("^que o household \"([^\"]*)\" tem as categorias de despesa \"([^\"]*)\" e \"([^\"]*)\"$")
    public void householdHasExpenseCategories(String householdName, String first, String second) {
        UUID householdId = world.households.get(householdName);
        world.categories.put(first, fixtures.insertExpenseCategory(householdId, first));
        world.categories.put(second, fixtures.insertExpenseCategory(householdId, second));
    }

    @Dado("^que o household \"([^\"]*)\" também tem a categoria de despesa \"([^\"]*)\"$")
    public void householdAlsoHasExpenseCategory(String householdName, String categoryName) {
        world.categories.put(categoryName,
                fixtures.insertExpenseCategory(world.households.get(householdName), categoryName));
    }

    /** Um nivel de hierarquia ja montado, pra provar que um segundo e recusado (ADR-0016). */
    @Dado("^que o household \"([^\"]*)\" tem a categoria \"([^\"]*)\" com a subcategoria \"([^\"]*)\"$")
    public void householdHasCategoryWithSubcategory(String householdName, String parent, String child) {
        UUID householdId = world.households.get(householdName);
        UUID parentId = fixtures.insertExpenseCategory(householdId, parent);
        world.categories.put(parent, parentId);
        world.categories.put(child, fixtures.insertExpenseSubcategory(householdId, parentId, child));
    }

    @Dado("^que \"([^\"]*)\" registrou uma despesa de R\\$ ([\\d.,]+) em \"([^\"]*)\" há (\\d+) minutos?$")
    public void memberRegisteredExpenseMinutesAgo(String memberName, String amount,
                                                  String categoryName, int minutes) {
        String householdName = world.households.keySet().iterator().next();
        fixtures.insertExpense(world.households.get(householdName), world.members.get(memberName),
                world.categories.get(categoryName), world.wallets.get(householdName),
                cents(amount), Instant.now().minus(minutes, ChronoUnit.MINUTES));
    }

    /**
     * Monta a pendencia mandando a mensagem que a gera, e nao inserindo linha na
     * mao: o que o cenario de <code>desfazer</code> testa e a precedencia
     * (ADR-0025), e ela so significa alguma coisa sobre uma pendencia que o
     * proprio fluxo abriu.
     */
    @E("^que \"([^\"]*)\" tem uma pergunta pendente oferecendo criar a categoria \"([^\"]*)\"$")
    public void hasPendingCategoryQuestion(String actor, String categoryName) {
        world.send(actor, categoryName.toLowerCase(Locale.ROOT) + " 80");
        assertThat(world.lastReplyTo(actor)).contains(categoryName);
        assertThat(fixtures.count("SELECT count(*) FROM pending_action WHERE resolved_at IS NULL"))
                .isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // Mensagens
    // ------------------------------------------------------------------

    @Quando("^\"([^\"]*)\" envia \"([^\"]*)\"$")
    public void sends(String actor, String text) {
        world.send(actor, text);
    }

    @Quando("^\"([^\"]*)\" responde \"([^\"]*)\"$")
    public void answers(String actor, String text) {
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

    // ------------------------------------------------------------------
    // Lancamento
    // ------------------------------------------------------------------

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
                FROM transaction t JOIN category c ON c.id = t.category_id
                WHERE t.reversed_at IS NULL""");
        assertThat(rows).hasSize(1);
        assertThat(((Number) rows.get(0).get(0)).longValue()).isEqualTo(cents(amount));
        assertThat(rows.get(0).get(1)).isEqualTo(categoryName);
        assertThat(rows.get(0).get(2)).isEqualTo("EXPENSE");
    }

    /** "Nessa categoria" e a que o passo anterior acabou de conferir como criada. */
    @E("^uma despesa de R\\$ ([\\d.,]+) é registrada nessa categoria$")
    public void expenseRegisteredInThatCategory(String amount) {
        expenseRegistered(amount, world.lastCategoryChecked);
    }

    @Entao("^exatamente uma despesa de R\\$ ([\\d.,]+) é registrada$")
    public void exactlyOneExpenseRegistered(String amount) {
        List<List<Object>> rows = fixtures.query("SELECT amount_cents FROM transaction");
        assertThat(rows).hasSize(1);
        assertThat(((Number) rows.get(0).get(0)).longValue()).isEqualTo(cents(amount));
    }

    @Entao("^nenhuma despesa é registrada ainda$")
    public void noExpenseYet() {
        assertThat(fixtures.count("SELECT count(*) FROM transaction")).isZero();
    }

    @Entao("^nenhuma despesa é registrada$")
    public void noExpenseAtAll() {
        assertThat(fixtures.count("SELECT count(*) FROM transaction")).isZero();
    }

    @E("^o sistema não inventa um valor$")
    public void noAmountInvented() {
        assertThat(fixtures.count("SELECT count(*) FROM transaction")).isZero();
    }

    @E("^o lançamento fica atribuído a \"([^\"]*)\"$")
    public void expenseAttributedTo(String memberName) {
        List<List<Object>> rows = fixtures.query("SELECT created_by_member_id, source FROM transaction");
        assertThat(rows.get(0).get(0)).isEqualTo(world.members.get(memberName));
        assertThat(rows.get(0).get(1)).isEqualTo("CHAT");
    }

    // ------------------------------------------------------------------
    // Categoria criada por chat (ADR-0024, ADR-0026)
    // ------------------------------------------------------------------

    @Entao("^a categoria de despesa \"([^\"]*)\" é criada no household \"([^\"]*)\"$")
    public void expenseCategoryCreated(String categoryName, String householdName) {
        assertThat(categoryIdIn(householdName, categoryName)).isNotNull();
        world.lastCategoryChecked = categoryName;
    }

    @Entao("^a categoria de despesa \"([^\"]*)\" é criada no household \"([^\"]*)\" como categoria raiz$")
    public void rootExpenseCategoryCreated(String categoryName, String householdName) {
        List<List<Object>> rows = fixtures.query("""
                SELECT c.parent_category_id FROM category c JOIN household h ON h.id = c.household_id
                WHERE h.name = ? AND c.name = ?""", householdName, categoryName);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get(0)).isNull();
        world.lastCategoryChecked = categoryName;
    }

    @Entao("^a categoria de despesa \"([^\"]*)\" é criada no household \"([^\"]*)\" como subcategoria de \"([^\"]*)\"$")
    public void subcategoryCreatedIn(String categoryName, String householdName, String parentName) {
        assertSubcategoryOf(householdName, categoryName, parentName);
        world.lastCategoryChecked = categoryName;
    }

    @E("^a categoria de despesa \"([^\"]*)\" é criada como subcategoria de \"([^\"]*)\"$")
    public void subcategoryCreated(String categoryName, String parentName) {
        assertSubcategoryOf(world.households.keySet().iterator().next(), categoryName, parentName);
        world.lastCategoryChecked = categoryName;
    }

    /**
     * "Nenhuma" e sobre o que o chat criou, nao sobre o banco inteiro: o cenario
     * monta "Alimentacao" e "Restaurante" por fixture antes de tentar o terceiro
     * nivel.
     */
    @Entao("^nenhuma categoria é criada$")
    public void noCategoryCreated() {
        assertThat(fixtures.count("SELECT count(*) FROM category")).isEqualTo(world.categories.size());
    }

    // ------------------------------------------------------------------
    // Descricao (ADR-0023)
    // ------------------------------------------------------------------

    @E("^o lançamento fica com uma descrição contendo \"([^\"]*)\" e \"([^\"]*)\"$")
    public void expenseHasDescriptionContaining(String first, String second) {
        // Comparacao sem acento e sem caixa: o que o cenario garante e que o
        // residuo da mensagem chegou ao campo, nao que o modelo corrigiu a
        // grafia -- a qualidade da descricao fica fora da metrica da Etapa 5 por
        // decisao da propria ADR-0023, e o stub nao e o modelo.
        String description = descriptionOfSingleExpense();
        assertThat(description).isNotNull();
        assertThat(fold(description)).contains(fold(first)).contains(fold(second));
    }

    @E("^a descrição não repete a categoria nem o valor$")
    public void descriptionRepeatsNeitherCategoryNorAmount() {
        List<List<Object>> rows = fixtures.query("""
                SELECT t.description, c.name, t.amount_cents
                FROM transaction t JOIN category c ON c.id = t.category_id""");
        String description = fold(String.valueOf(rows.get(0).get(0)));
        assertThat(description).doesNotContain(fold(String.valueOf(rows.get(0).get(1))));
        long amountCents = ((Number) rows.get(0).get(2)).longValue();
        assertThat(description).doesNotContain(String.valueOf(amountCents / 100));
    }

    @E("^o recibo de \"([^\"]*)\" mostra a descrição registrada$")
    public void receiptShowsDescription(String actor) {
        assertThat(fold(world.lastReplyTo(actor))).contains(fold(descriptionOfSingleExpense()));
    }

    @E("^o lançamento fica sem descrição$")
    public void expenseHasNoDescription() {
        assertThat(descriptionOfSingleExpense()).isNull();
    }

    @E("^o sistema não inventa uma descrição a partir da categoria$")
    public void noDescriptionInventedFromCategory() {
        assertThat(descriptionOfSingleExpense()).isNull();
    }

    @E("^\"([^\"]*)\" não é perguntada sobre descrição em nenhum momento$")
    public void neverAskedAboutDescription(String actor) {
        assertThat(world.repliesTo(actor)).allSatisfy(reply ->
                assertThat(fold(reply.text())).doesNotContain("descricao"));
        assertThat(fixtures.count("SELECT count(*) FROM pending_action")).isZero();
    }

    // ------------------------------------------------------------------
    // Estorno (ADR-0025)
    // ------------------------------------------------------------------

    @Entao("^a despesa é estornada$")
    public void expenseIsReversed() {
        List<List<Object>> rows = fixtures.query(
                "SELECT reversed_at, reversed_by_member_id FROM transaction");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get(0)).isNotNull();
        assertThat(rows.get(0).get(1)).isNotNull();
    }

    @E("^a despesa continua visível no histórico marcada como estornada$")
    public void reversedExpenseStaysVisible() {
        // Estorno em vez de delete: a linha continua la (modelo-de-dados.md).
        assertThat(fixtures.count("SELECT count(*) FROM transaction WHERE reversed_at IS NOT NULL"))
                .isEqualTo(1);
    }

    @E("^\"([^\"]*)\" recebe a confirmação do estorno$")
    public void receivesReversalConfirmation(String actor) {
        assertThat(fold(world.lastReplyTo(actor))).contains("estornei");
    }

    @Entao("^a pergunta pendente é cancelada$")
    public void pendingQuestionCancelled() {
        List<List<Object>> rows = fixtures.query("SELECT resolution, resolved_at FROM pending_action");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get(0)).isEqualTo("REJECTED");
        assertThat(rows.get(0).get(1)).isNotNull();
    }

    @E("^a despesa de R\\$ ([\\d.,]+) em \"([^\"]*)\" continua sem estorno$")
    public void expenseStillNotReversed(String amount, String categoryName) {
        assertThat(fixtures.count("""
                SELECT count(*) FROM transaction t JOIN category c ON c.id = t.category_id
                WHERE t.amount_cents = ? AND c.name = ? AND t.reversed_at IS NULL""",
                cents(amount), categoryName)).isEqualTo(1);
    }

    @E("^\"([^\"]*)\" recebe a confirmação de que a pergunta foi cancelada, não de um estorno$")
    public void receivesQuestionCancelledNotReversal(String actor) {
        String reply = fold(world.lastReplyTo(actor));
        assertThat(reply).contains("cancelei a pergunta");
        // A propria ADR-0025 registra confundir as duas coisas como o risco desta
        // decisao: o texto tem que negar o estorno, nao so omiti-lo.
        assertThat(reply).contains("nao estornei");
    }

    // ------------------------------------------------------------------
    // Perguntas (ADR-0004, ADR-0018)
    // ------------------------------------------------------------------

    @E("^\"([^\"]*)\" recebe uma única pergunta oferecendo criar a categoria \"([^\"]*)\"$")
    public void receivesSingleCategoryCreationQuestion(String actor, String categoryName) {
        assertThat(world.repliesTo(actor)).hasSize(1);
        assertThat(world.lastReplyTo(actor)).contains(categoryName);
        assertThat(fold(world.lastReplyTo(actor))).contains("crie");
    }

    @E("^\"([^\"]*)\" recebe uma pergunta com as opções numeradas \"([^\"]*)\" e \"([^\"]*)\"$")
    public void receivesNumberedOptions(String actor, String first, String second) {
        assertThat(world.lastReplyTo(actor))
                .contains("1) " + first)
                .contains("2) " + second);
    }

    @E("^\"([^\"]*)\" recebe uma pergunta curta pedindo o valor$")
    public void receivesAmountQuestion(String actor) {
        assertThat(fold(world.lastReplyTo(actor))).contains("quanto foi");
    }

    @E("^\"([^\"]*)\" recebe uma pergunta pedindo a categoria correta, não um erro genérico$")
    public void receivesCategoryQuestionNotGenericError(String actor) {
        String reply = world.lastReplyTo(actor);
        assertThat(reply).doesNotContain("Não entendi essa");
        assertThat(fold(reply))
                .contains("um nivel")
                .contains("qual categoria principal");
    }

    @E("^\"([^\"]*)\" recebe um recibo, não uma pergunta$")
    public void receivesReceiptNotQuestion(String actor) {
        assertThat(world.lastReplyTo(actor)).doesNotContain("?");
        assertThat(fixtures.count("SELECT count(*) FROM pending_action")).isZero();
    }

    // ------------------------------------------------------------------
    // Recibos
    // ------------------------------------------------------------------

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

    // ------------------------------------------------------------------

    private void assertSubcategoryOf(String householdName, String categoryName, String parentName) {
        List<List<Object>> rows = fixtures.query("""
                SELECT p.name FROM category c
                JOIN household h ON h.id = c.household_id
                JOIN category p ON p.id = c.parent_category_id
                WHERE h.name = ? AND c.name = ?""", householdName, categoryName);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get(0)).isEqualTo(parentName);
    }

    private UUID categoryIdIn(String householdName, String categoryName) {
        List<List<Object>> rows = fixtures.query("""
                SELECT c.id FROM category c JOIN household h ON h.id = c.household_id
                WHERE h.name = ? AND c.name = ? AND c.kind = 'EXPENSE'""", householdName, categoryName);
        assertThat(rows).hasSize(1);
        return (UUID) rows.get(0).get(0);
    }

    private String descriptionOfSingleExpense() {
        List<List<Object>> rows = fixtures.query("SELECT description FROM transaction");
        assertThat(rows).hasSize(1);
        return (String) rows.get(0).get(0);
    }

    /** Minuscula e sem acento, pra assercao de texto nao virar teste de grafia. */
    private String fold(String text) {
        return Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    private long cents(String amount) {
        return new BigDecimal(amount.replace(".", "").replace(',', '.')).movePointRight(2).longValueExact();
    }
}
