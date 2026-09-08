package com.novoapp.acceptance;

import com.novoapp.support.Fixtures;
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
 * Passos de `mercado-lista-de-compras.feature` -- todos os nove cenarios sao
 * @etapa2, com a tag no nivel da Funcionalidade.
 *
 * <p>Arquivo proprio, e nao adaptacao de {@link ExpenseByChatSteps}: nenhum
 * passo de mercado existia quando aqueles foram escritos, e o pouco que os dois
 * compartilham -- montar household, mandar mensagem, contar recibos -- ja mora
 * em {@link AcceptanceWorld} e nos passos genericos de la, que este arquivo
 * reusa em vez de duplicar.
 */
public class ShoppingListSteps {

    private static final String PENDING = "PENDING";
    private static final String PURCHASED = "PURCHASED";

    @Inject
    AcceptanceWorld world;

    @Inject
    Fixtures fixtures;

    // ------------------------------------------------------------------
    // Contexto
    // ------------------------------------------------------------------

    @Dado("^que \"([^\"]*)\" e \"([^\"]*)\" são membros do household \"([^\"]*)\" com Telegram vinculado$")
    public void bothMembersLinked(String first, String second, String householdName) {
        UUID householdId = world.households.get(householdName);
        link(householdId, first, "OWNER");
        link(householdId, second, "MEMBER");
    }

    /**
     * A lista ativa vem da fixture aqui porque o cenario a declara como estado
     * inicial. Em producao ela nasce sob demanda, no primeiro item
     * (sdd-modulo-shopping.md) -- e o cenario "Adicionar item pela linguagem
     * natural" continuaria passando sem este passo.
     */
    @Dado("^que existe uma lista de compras ativa no household \"([^\"]*)\"$")
    public void activeListExists(String householdName) {
        UUID householdId = world.households.get(householdName);
        world.shoppingLists.put(householdName, fixtures.insertShoppingList(householdId));
    }

    @Dado("^que o item \"([^\"]*)\" já está pendente na lista$")
    public void itemAlreadyPending(String itemName) {
        // Pedido pela primeira pessoa vinculada: e ela que o cenario "Item ja
        // pendente na lista" espera ver citada na resposta.
        insertItem(itemName, PENDING, world.members.keySet().iterator().next());
    }

    @Dado("^que apenas o item \"([^\"]*)\" está pendente$")
    public void onlyItemPending(String itemName) {
        itemAlreadyPending(itemName);
    }

    @Dado("^que os itens \"([^\"]*)\" e \"([^\"]*)\" estão pendentes$")
    public void itemsPending(String first, String second) {
        itemAlreadyPending(first);
        itemAlreadyPending(second);
    }

    @E("^que o item \"([^\"]*)\" já foi marcado como comprado$")
    public void itemAlreadyPurchased(String itemName) {
        insertItem(itemName, PURCHASED, world.members.keySet().iterator().next());
    }

    @Dado("^que não há itens pendentes na lista$")
    public void noPendingItems() {
        assertThat(fixtures.count("SELECT count(*) FROM list_item WHERE status = 'PENDING'")).isZero();
    }

    // ------------------------------------------------------------------
    // Itens
    // ------------------------------------------------------------------

    @Entao("^o item \"([^\"]*)\" entra na lista de compras com status pendente$")
    public void itemEnteredAsPending(String itemName) {
        assertThat(statusOf(itemName)).isEqualTo(PENDING);
    }

    @Entao("^os itens \"([^\"]*)\", \"([^\"]*)\" e \"([^\"]*)\" entram na lista como pendentes$")
    public void threeItemsEnteredAsPending(String first, String second, String third) {
        assertThat(statusOf(first)).isEqualTo(PENDING);
        assertThat(statusOf(second)).isEqualTo(PENDING);
        assertThat(statusOf(third)).isEqualTo(PENDING);
    }

    @Entao("^o item \"([^\"]*)\" entra na lista com quantidade (\\d+) e unidade \"([^\"]*)\"$")
    public void itemEnteredWithQuantity(String itemName, int quantity, String unit) {
        List<List<Object>> rows = fixtures.query(
                "SELECT quantity, unit FROM list_item WHERE lower(name) = ?", lower(itemName));
        assertThat(rows).hasSize(1);
        assertThat(((BigDecimal) rows.get(0).get(0)).compareTo(BigDecimal.valueOf(quantity))).isZero();
        assertThat(rows.get(0).get(1)).isEqualTo(unit);
    }

    @E("^o item fica registrado como solicitado por \"([^\"]*)\"$")
    public void itemRequestedBy(String memberName) {
        List<List<Object>> rows = fixtures.query("SELECT requested_by_member_id FROM list_item");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get(0)).isEqualTo(world.members.get(memberName));
    }

    @Entao("^a lista continua com um único item \"([^\"]*)\" pendente$")
    public void singlePendingItemRemains(String itemName) {
        assertThat(fixtures.count("SELECT count(*) FROM list_item WHERE status = 'PENDING' AND lower(name) = ?",
                lower(itemName))).isEqualTo(1);
    }

    @Entao("^o item \"([^\"]*)\" entra na lista de compras uma única vez$")
    public void itemEnteredOnce(String itemName) {
        assertThat(fixtures.count("SELECT count(*) FROM list_item WHERE lower(name) = ?", lower(itemName)))
                .isEqualTo(1);
    }

    @Entao("^o item \"([^\"]*)\" fica com status comprado, registrado por \"([^\"]*)\"$")
    public void itemPurchasedBy(String itemName, String memberName) {
        List<List<Object>> rows = fixtures.query(
                "SELECT status, purchased_by_member_id, purchased_at FROM list_item WHERE lower(name) = ?",
                lower(itemName));
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get(0)).isEqualTo(PURCHASED);
        assertThat(rows.get(0).get(1)).isEqualTo(world.members.get(memberName));
        assertThat(rows.get(0).get(2)).isNotNull();
    }

    @E("^o item \"([^\"]*)\" continua pendente$")
    public void itemStillPending(String itemName) {
        assertThat(statusOf(itemName)).isEqualTo(PENDING);
    }

    @Entao("^nenhum item é marcado como comprado$")
    public void noItemPurchased() {
        assertThat(fixtures.count("SELECT count(*) FROM list_item WHERE status = 'PURCHASED'")).isZero();
    }

    /**
     * O cenario e explicito: marcar item comprado nao mexe em dinheiro. O elo
     * lista -> despesa e <code>fecharCompra</code>, Etapa 3.
     */
    @E("^nenhum lançamento financeiro é criado$")
    public void noExpenseCreated() {
        assertThat(fixtures.count("SELECT count(*) FROM transaction")).isZero();
    }

    // ------------------------------------------------------------------
    // Respostas
    // ------------------------------------------------------------------

    @E("^\"([^\"]*)\" recebe um recibo confirmando o item$")
    public void receivesItemReceipt(String actor) {
        List<List<Object>> rows = fixtures.query("SELECT name FROM list_item");
        assertThat(world.lastReplyTo(actor)).contains(String.valueOf(rows.get(0).get(0)));
    }

    @E("^\"([^\"]*)\" recebe um único recibo listando os três itens$")
    public void receivesSingleReceiptWithThreeItems(String actor) {
        assertThat(world.repliesTo(actor)).hasSize(1);
        String receipt = world.lastReplyTo(actor);
        for (List<Object> row : fixtures.query("SELECT name FROM list_item")) {
            assertThat(receipt).contains(String.valueOf(row.get(0)));
        }
    }

    @E("^\"([^\"]*)\" é informado de que o item já estava na lista, pedido por \"([^\"]*)\"$")
    public void informedItemWasAlreadyThere(String actor, String requesterName) {
        assertThat(fold(world.lastReplyTo(actor)))
                .contains("ja estava na lista")
                .contains(fold(requesterName));
    }

    @Entao("^\"([^\"]*)\" recebe uma lista contendo \"([^\"]*)\" e \"([^\"]*)\"$")
    public void receivesListContaining(String actor, String first, String second) {
        assertThat(world.lastReplyTo(actor)).contains(first).contains(second);
    }

    @E("^a lista não contém \"([^\"]*)\"$")
    public void listDoesNotContain(String itemName) {
        assertThat(world.lastReplyToCurrentActor()).doesNotContain(itemName);
    }

    @Entao("^\"([^\"]*)\" recebe uma resposta informando que não falta nada$")
    public void receivesNothingMissing(String actor) {
        assertThat(fold(world.lastReplyTo(actor))).contains("nao esta faltando nada");
    }

    @E("^\"([^\"]*)\" recebe uma pergunta oferecendo registrar \"([^\"]*)\" como comprado$")
    public void receivesOfferToRegisterPurchase(String actor, String itemName) {
        assertThat(world.lastReplyTo(actor)).contains(itemName);
        assertThat(fold(world.lastReplyTo(actor))).contains("registre como comprado");
        // A oferta e uma PendingAction de verdade, e nao so um texto: e o que
        // permite responder "sim" depois (ADR-0018).
        assertThat(fixtures.count("SELECT count(*) FROM pending_action WHERE resolved_at IS NULL"))
                .isEqualTo(1);
    }

    // ------------------------------------------------------------------

    private void link(UUID householdId, String memberName, String role) {
        UUID memberId = fixtures.insertMember(memberName, null);
        fixtures.insertMembership(householdId, memberId, role);
        fixtures.insertChannelIdentity(memberId, "TELEGRAM", world.externalIdFor(memberName), householdId);
        world.members.put(memberName, memberId);
        world.nameOf(memberName, memberName);
    }

    private void insertItem(String itemName, String status, String requesterName) {
        String householdName = world.households.keySet().iterator().next();
        fixtures.insertListItem(world.households.get(householdName),
                world.shoppingLists.get(householdName), itemName, status,
                world.members.get(requesterName));
    }

    private String statusOf(String itemName) {
        List<List<Object>> rows = fixtures.query(
                "SELECT status FROM list_item WHERE lower(name) = ?", lower(itemName));
        assertThat(rows).hasSize(1);
        return (String) rows.get(0).get(0);
    }

    /**
     * So minuscula, sem tirar acento: e o par exato do <code>lower(name)</code>
     * do SQL. Dobrar o acento aqui faria "Cafe" procurar por um item que o banco
     * guardou como "Cafe" com acento e nunca achar.
     */
    private String lower(String text) {
        return text.toLowerCase(Locale.ROOT);
    }

    /** Minuscula e sem acento, pra assercao de texto nao virar teste de grafia. */
    private String fold(String text) {
        return Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }
}
