package com.novoapp.shopping;

import com.novoapp.common.tenancy.HouseholdScoped;
import com.novoapp.common.text.Normalization;
import com.novoapp.finance.FinanceService;
import com.novoapp.finance.RegisteredExpense;
import com.novoapp.shopping.entity.ListCheckout;
import com.novoapp.shopping.entity.ListItem;
import com.novoapp.shopping.entity.ListItemStatus;
import com.novoapp.shopping.entity.ShoppingList;
import com.novoapp.shopping.entity.ShoppingListStatus;
import com.novoapp.shopping.repository.ListCheckoutRepository;
import com.novoapp.shopping.repository.ListItemRepository;
import com.novoapp.shopping.repository.ShoppingListRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lista de compras do household (sdd-modulo-shopping.md).
 *
 * <p><code>marcarItemComprado</code> nao mexe em dinheiro: marca o item e nada
 * mais. Quem junta os dois dominios e {@link #checkout} -- o elo, e o
 * diferencial do produto (CLAUDE.md).
 *
 * <p>Nao pergunta nada. Quando o item nao esta na lista, devolve
 * {@link MarkPurchasedResult.NotOnTheList} e quem transforma isso em pergunta e
 * <code>conversation</code> (ADR-0018). O mesmo vale para
 * {@link CheckoutResult.NoActiveList}.
 */
@ApplicationScoped
public class ShoppingService {

    @Inject
    ShoppingListRepository lists;

    @Inject
    ListItemRepository items;

    @Inject
    ListCheckoutRepository checkouts;

    /**
     * A aresta que a regra de dependencia sempre permitiu e que so agora existe
     * em tempo de execucao: <code>shopping</code> pode depender de
     * <code>finance</code>, nunca o contrario (sdd-visao-geral.md, ArchUnit).
     */
    @Inject
    FinanceService finance;

    @Inject
    Clock clock;

    /**
     * Adiciona um ou varios itens numa chamada -- "acabou arroz, leite e cafe" e
     * uma mensagem so, e o recibo tambem e um so.
     *
     * <p>Item que ja esta faltando nao duplica: volta marcado como
     * {@code alreadyPending}, com quem tinha pedido antes.
     */
    @Transactional
    @HouseholdScoped
    public List<AddedItem> addItems(UUID householdId, UUID memberId,
                                    List<ItemDraft> drafts, UUID sourceMessageId) {
        ShoppingList list = activeListOrCreate(householdId);
        List<AddedItem> result = new ArrayList<>();

        for (ItemDraft draft : drafts) {
            Optional<ListItem> pending = items.findPendingByName(list.id, draft.name());
            if (pending.isPresent()) {
                ListItem existing = pending.get();
                result.add(new AddedItem(existing.id, existing.name, true, existing.requestedByMemberId));
                continue;
            }

            ListItem item = new ListItem();
            item.householdId = householdId;
            item.shoppingListId = list.id;
            item.name = draft.name().trim();
            // A forma que o indice unico parcial compara (ADR-0030).
            item.nameNormalized = Normalization.of(draft.name());
            item.quantity = draft.quantity();
            item.unit = draft.unit();
            item.status = ListItemStatus.PENDING;
            item.requestedByMemberId = memberId;
            item.sourceMessageId = sourceMessageId;
            items.persist(item);
            result.add(new AddedItem(item.id, item.name, false, memberId));
        }

        // Flush dentro do escopo: no commit o SET LOCAL ROLE ja teria voltado pro
        // papel de fora, que nao tem permissao nestas tabelas.
        items.flush();
        return result;
    }

    @Transactional
    @HouseholdScoped
    public MarkPurchasedResult markPurchased(UUID householdId, UUID memberId, String itemName) {
        Optional<ShoppingList> list = lists.findActive();
        if (list.isEmpty()) {
            return new MarkPurchasedResult.NotOnTheList(itemName);
        }

        Optional<ListItem> found = items.findPendingByName(list.get().id, itemName);
        if (found.isEmpty()) {
            return new MarkPurchasedResult.NotOnTheList(itemName);
        }

        ListItem item = found.get();
        item.status = ListItemStatus.PURCHASED;
        item.purchasedByMemberId = memberId;
        item.purchasedAt = Instant.now(clock);
        items.flush();
        return new MarkPurchasedResult.Purchased(item.name);
    }

    /**
     * Registra como ja comprado um item que nunca esteve na lista -- o "sim" da
     * pergunta do cenario "Item mencionado nao existe na lista". Entra direto
     * como {@code PURCHASED}: nunca passou por {@code PENDING} porque ninguem
     * chegou a pedir.
     */
    @Transactional
    @HouseholdScoped
    public MarkPurchasedResult.Purchased addAlreadyPurchased(UUID householdId, UUID memberId,
                                                             String itemName, UUID sourceMessageId) {
        ShoppingList list = activeListOrCreate(householdId);

        ListItem item = new ListItem();
        item.householdId = householdId;
        item.shoppingListId = list.id;
        item.name = itemName.trim();
        item.nameNormalized = Normalization.of(itemName);
        item.status = ListItemStatus.PURCHASED;
        item.requestedByMemberId = memberId;
        item.purchasedByMemberId = memberId;
        item.purchasedAt = Instant.now(clock);
        item.sourceMessageId = sourceMessageId;
        items.persist(item);
        items.flush();
        return new MarkPurchasedResult.Purchased(item.name);
    }

    /**
     * <b>O elo.</b> Fecha os itens, grava o {@link ListCheckout} e registra a
     * despesa -- tudo dentro de uma transacao so (ADR-0031).
     *
     * <p>Mora aqui, e nao em <code>conversation</code>, porque atomicidade e
     * regra de dominio: o REST da Etapa 4 chama este mesmo metodo e herda a
     * garantia, que e a regra 4 do CLAUDE.md sem esforco extra. O orquestrador
     * continua sem transacao propria -- segurar conexao de banco durante a
     * chamada ao LLM e o que a ADR-0005 evita.
     *
     * <p><b>A ordem das escritas nao e arbitraria, e mudou em 2026-09-21 ao
     * escrever o teste da falha.</b> Os itens sao marcados e descarregados no
     * banco <b>antes</b> de <code>finance</code> ser chamado. Parece o contrario
     * do intuitivo -- e era: a primeira versao chamava <code>finance</code>
     * primeiro, "porque se ele falhar nada mais chegou a ser escrito".
     *
     * <p>Isso fazia o cenario "Falha ao registrar a despesa nao deixa a lista
     * fechada" passar <b>por ordenacao</b>, e nao por transacao. Com nada escrito
     * antes do passo que falha, "nenhum item muda de status" e verdade
     * trivialmente, e o teste que a estrategia-de-testes.md chama de terceiro
     * obrigatorio -- o que cobre o diferencial do produto -- nao teria exercitado
     * a fronteira transacional da ADR-0031 uma vez sequer. Teste verde que nao
     * poderia ficar vermelho nao e garantia, e afirmacao.
     *
     * <p>Com esta ordem, a falha de <code>finance</code> acontece com os
     * <code>UPDATE</code> dos itens ja enviados ao banco, e so o
     * <code>rollback</code> os desfaz. O cenario passa a poder falhar -- e e por
     * isso que ele passar significa alguma coisa.
     *
     * <p>O {@link ListCheckout} continua depois de <code>finance</code>, e nao
     * por escolha: {@code transaction_id} e {@code NOT NULL}, entao a linha nao
     * existe antes de o lancamento existir.
     *
     * @param itemNames vazio significa "comprei tudo": fecha todos os pendentes.
     *        Com nomes, fecha so os que casarem pela forma normalizada
     *        (ADR-0030) -- e o fechamento parcial, que tem cenario proprio
     */
    @Transactional
    @HouseholdScoped
    public CheckoutResult checkout(UUID householdId,
                                   UUID memberId,
                                   List<String> itemNames,
                                   UUID categoryId,
                                   long amountCents,
                                   UUID sourceMessageId) {
        Optional<ShoppingList> active = lists.findActive();
        if (active.isEmpty()) {
            return new CheckoutResult.NoActiveList();
        }

        ShoppingList list = active.get();
        List<ListItem> closing = itemNames == null || itemNames.isEmpty()
                ? items.listPending(list.id)
                : items.listPendingNamed(list.id, itemNames);
        if (closing.isEmpty()) {
            return new CheckoutResult.NothingToClose();
        }

        // Primeiro a lista, e descarregada no banco agora. Ver o javadoc: e o
        // que faz a falha de finance ter algo a desfazer, e portanto o que faz o
        // cenario da atomicidade poder ficar vermelho.
        Instant purchasedAt = Instant.now(clock);
        for (ListItem item : closing) {
            item.status = ListItemStatus.PURCHASED;
            item.purchasedByMemberId = memberId;
            item.purchasedAt = purchasedAt;
        }
        items.flush();

        RegisteredExpense expense = finance.registerExpense(householdId, memberId, categoryId,
                amountCents, null, sourceMessageId);

        ListCheckout checkout = new ListCheckout();
        checkout.householdId = householdId;
        checkout.shoppingListId = list.id;
        checkout.transactionId = expense.transactionId();
        checkout.itemsPurchasedCount = closing.size();
        checkout.performedByMemberId = memberId;
        checkout.performedAt = purchasedAt;
        checkouts.persist(checkout);
        checkouts.flush();

        // O vinculo so pode ser escrito depois de o fechamento ter id, e vai por
        // UPDATE em massa: ver o porque em ListItemRepository.linkToCheckout --
        // a segunda escrita pelo estado das entidades desfazia a primeira.
        items.linkToCheckout(closing.stream().map(item -> item.id).toList(), checkout.id);
        return new CheckoutResult.Closed(closing.size(),
                closing.stream().map(item -> item.name).toList(),
                expense.amountCents(),
                expense.categoryDisplayName());
    }

    /**
     * Tira da lista o item que a familia desistiu de comprar (ADR-0039).
     *
     * <p>Marca <code>REMOVED</code> em vez de apagar a linha, pelo mesmo motivo
     * que o estorno nao apaga o lancamento: em lista compartilhada, "sumiu" e
     * pior que "foi removido" (modelo-de-dados.md). O status existia desde a
     * Etapa 2a sem uso -- e este o uso.
     *
     * <p>So alcanca item <code>PENDING</code>. Remover algo ja comprado seria
     * desfazer uma compra, e isso e {@code desfazer} do fechamento (ADR-0032),
     * nao remocao.
     */
    @Transactional
    @HouseholdScoped
    public RemoveItemResult removeItem(UUID householdId, UUID memberId, String itemName) {
        Optional<ShoppingList> list = lists.findActive();
        if (list.isEmpty()) {
            return new RemoveItemResult.NotOnTheList(itemName);
        }

        Optional<ListItem> found = items.findPendingByName(list.get().id, itemName);
        if (found.isEmpty()) {
            return new RemoveItemResult.NotOnTheList(itemName);
        }

        ListItem item = found.get();
        item.status = ListItemStatus.REMOVED;
        item.removedByMemberId = memberId;
        item.removedAt = Instant.now(clock);
        items.flush();
        return new RemoveItemResult.Removed(item.name);
    }

    /**
     * O outro lado do <code>desfazer</code> (ADR-0032): os itens daquele
     * fechamento voltam a <code>PENDING</code>.
     *
     * <p>Chamado pelo observador de {@code ExpenseReversed}, dentro da transacao
     * de <code>finance</code> -- e por isso nao tem {@code @Transactional}
     * proprio nem {@code @HouseholdScoped}: abrir escopo aqui esconderia que a
     * atomicidade e a mesma da ADR-0031, no sentido inverso.
     *
     * <p><b>O fechamento e a unidade</b>: volta o que aquele fechamento fechou,
     * e nao a lista inteira -- fechamento parcial tem cenario proprio.
     *
     * <p>Item que colidiria com um pendente de mesmo nome normalizado permanece
     * <code>PURCHASED</code> (ADR-0030, ADR-0032). Nao e perda: quem avisou de
     * novo ja alcancou o efeito que o desfazer queria.
     *
     * @return os nomes que de fato voltaram a lista, na ordem em que foram
     *         pedidos. O observador do evento descarta isto -- quem monta o
     *         recibo le depois, por {@link #reopenedItemsOf}, ja fora da
     *         transacao. O retorno fica porque o REST da Etapa 4 chama este
     *         metodo direto, sem evento no meio
     */
    public List<String> reopenCheckout(UUID transactionId) {
        Optional<ListCheckout> checkout = checkouts.findByTransaction(transactionId);
        if (checkout.isEmpty()) {
            // Lancamento avulso: o caso comum. Nao veio de fechamento nenhum, e
            // o estorno dele nao mexe em lista nenhuma.
            return List.of();
        }

        List<String> reopened = new ArrayList<>();
        for (ListItem item : items.listPurchasedByCheckout(checkout.get().id)) {
            if (items.hasOtherPendingWithSameName(item.shoppingListId, item.nameNormalized, item.id)) {
                continue;
            }
            item.status = ListItemStatus.PENDING;
            // O item voltou a ser algo que falta. Quem o tinha comprado esta no
            // historico do lancamento estornado (ADR-0032).
            item.purchasedByMemberId = null;
            item.purchasedAt = null;
            reopened.add(item.name);
        }
        items.flush();
        return List.copyOf(reopened);
    }

    /**
     * O que voltou para a lista no estorno daquele lancamento -- leitura, para o
     * recibo.
     *
     * <p>Existe porque o caminho da escrita e um evento CDI e o retorno dele se
     * perde no observador. Ler depois e barato e nao reintroduz segunda
     * transacao de <b>escrita</b>, que e o que a ADR-0031 evita: o efeito ja
     * commitou inteiro quando esta consulta roda.
     *
     * <p>Sem isto o recibo diria so "estornei" enquanto tres itens voltaram para
     * a lista em silencio -- e a ADR-0032 pede "um desfazer, um recibo, os dois
     * lados".
     */
    @Transactional
    @HouseholdScoped
    public List<String> reopenedItemsOf(UUID householdId, UUID transactionId) {
        return checkouts.findByTransaction(transactionId)
                .map(checkout -> items.listPendingByCheckout(checkout.id).stream()
                        .map(item -> item.name)
                        .toList())
                .orElseGet(List::of);
    }

    /**
     * O que esta faltando. Household sem lista nenhuma devolve vazio, e nao erro
     * -- a diferenca entre "sem lista" e "lista sem item" nao e observavel pelo
     * usuario, e nao deve ser (sdd-modulo-shopping.md).
     */
    @Transactional
    @HouseholdScoped
    public List<ListItemView> pendingItems(UUID householdId) {
        return lists.findActive()
                .map(list -> items.listPending(list.id).stream()
                        .map(item -> new ListItemView(item.name, item.quantity, item.unit))
                        .toList())
                .orElseGet(List::of);
    }

    /** Cria a lista ativa no primeiro item, em vez de no onboarding (ADR-0011 vale so pra conta). */
    private ShoppingList activeListOrCreate(UUID householdId) {
        return lists.findActive().orElseGet(() -> {
            ShoppingList created = new ShoppingList();
            created.householdId = householdId;
            created.name = ShoppingList.DEFAULT_NAME;
            created.status = ShoppingListStatus.ACTIVE;
            lists.persist(created);
            lists.flush();
            return created;
        });
    }
}
