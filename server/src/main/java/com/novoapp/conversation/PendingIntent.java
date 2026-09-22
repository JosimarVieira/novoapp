package com.novoapp.conversation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.novoapp.shopping.ItemDraft;

import java.util.List;
import java.util.UUID;

/**
 * O conteudo de <code>pending_action.intent_json</code>: o que gerou a pergunta,
 * com tudo que a execucao vai precisar quando a resposta chegar.
 *
 * <p>O campo {@code type} e o discriminador -- ver
 * {@link PendingActionType} para o porque de ele viver aqui dentro e nao em
 * coluna propria. Desde a ADR-0029, um segundo discriminador convive com ele:
 * {@link #deferred}, que diz qual acao roda no <code>sim</code>. Os dois nao sao
 * redundantes -- {@code type} descreve <b>a pergunta</b> (o que falta saber) e
 * {@code deferred} descreve <b>a acao</b> (o que fazer quando souber). Antes da
 * ADR-0029 o orquestrador deduzia a acao a partir da pergunta, e por isso
 * "quanto foi?" so sabia terminar em despesa.
 *
 * <p>Os nomes dos campos sao ingles, diferente do <code>intent_json</code> de
 * <code>inbound_message</code>, que espelha em portugues o vocabulario da tool
 * enviada ao modelo. Nao e inconsistencia: aquele e registro do que foi dito ao
 * LLM, este e estado interno de <code>conversation</code> e nunca sai daqui.
 *
 * @param sourceMessageId a mensagem <b>original</b>, a que gerou a pergunta --
 *        nao a que a respondeu. E o que mantem a rastreabilidade da Etapa 5
 *        apontando pro texto que de fato descreve o lancamento
 * @param options ids e rotulos das alternativas numeradas.
 *        <code>options_json</code> guarda so os rotulos, que e o que a tela da
 *        Etapa 4 mostra; os ids ficam aqui, que e onde a execucao os procura
 * @param items so em {@link DeferredAction#ADD_LIST_ITEMS}
 * @param itemNames so em {@link DeferredAction#CLOSE_PURCHASE}: os itens que a
 *        pessoa disse ter comprado. Vazio significa "comprei tudo", e e o mesmo
 *        contrato de {@code ShoppingService.checkout} -- por isso os nomes crus,
 *        e nao {@link ItemDraft}: fechar compra nao carrega quantidade nem
 *        unidade
 * @param memberName e {@code phoneNumber} so em
 *        {@link DeferredAction#INVITE_MEMBER}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record PendingIntent(PendingActionType type,
                            DeferredAction deferred,
                            String suggestedCategory,
                            Long amountCents,
                            String description,
                            String itemName,
                            List<ItemDraft> items,
                            List<String> itemNames,
                            String memberName,
                            String phoneNumber,
                            UUID sourceMessageId,
                            List<Option> options) {

    /**
     * Pendencia gravada antes da ADR-0029 nao tem {@code deferred} no JSON, e
     * desserializa com ele nulo. Uma linha dessas pode estar em aberto no fio de
     * alguem no instante do deploy -- e um <code>switch</code> sobre enum nulo
     * estouraria em cima de uma resposta legitima.
     *
     * <p>O padrao nao e chute: antes desta ADR so existiam quatro tipos, e todos
     * terminavam em despesa, menos a pergunta de item fora da lista.
     */
    public PendingIntent {
        if (deferred == null) {
            deferred = type == PendingActionType.CONFIRM_PURCHASE
                    ? DeferredAction.ADD_ALREADY_PURCHASED
                    : DeferredAction.REGISTER_EXPENSE;
        }
    }

    public record Option(UUID id, String label) {
    }

    /**
     * Qual acao roda quando a confirmacao chegar (ADR-0029).
     *
     * <p>Consulta de lista nao aparece aqui: leitura nao espera confirmacao --
     * nao ha o que desfazer, e perguntar antes de ler e friccao sem risco do
     * outro lado. E a unica excecao a faixa media, e esta declarada na ADR-0029.
     */
    public enum DeferredAction {
        REGISTER_EXPENSE,
        ADD_LIST_ITEMS,
        /** Baixar um item que <b>esta</b> na lista. */
        MARK_PURCHASED,
        /** Registrar como comprado algo que nunca esteve na lista -- nao e o mesmo que {@link #MARK_PURCHASED}. */
        ADD_ALREADY_PURCHASED,
        /**
         * <b>O elo</b>: fechar a lista gerando o lancamento (ADR-0031). A
         * pendencia que termina aqui e "comprei tudo" sem valor -- o fechamento
         * inteiro fica guardado esperando a resposta de "quanto foi?".
         *
         * <p>E o valor no enum que o gatilho de revisao do
         * <code>sdd-modulo-conversation.md</code> previu: acrescentar
         * <code>fecharCompra</code> seria "um valor no enum e um caso ali", sem
         * tipo de pendencia especial. Foi.
         */
        CLOSE_PURCHASE,
        /** Tirar da lista o que a familia desistiu de comprar (ADR-0039). */
        REMOVE_LIST_ITEM,
        INVITE_MEMBER
    }

    /** "Criar a categoria 'Pet shop'?" (ADR-0024). */
    public static PendingIntent createCategory(String suggestedCategory, Long amountCents,
                                               String description, UUID sourceMessageId) {
        return new PendingIntent(PendingActionType.CREATE_CATEGORY, DeferredAction.REGISTER_EXPENSE,
                suggestedCategory, amountCents, description, null, null, null, null, null,
                sourceMessageId, null);
    }

    /** "Foi em qual?" -- confianca media entre categorias parecidas (ADR-0004). */
    public static PendingIntent chooseCategory(List<Option> options, Long amountCents,
                                               String description, UUID sourceMessageId) {
        return new PendingIntent(PendingActionType.CHOOSE_CATEGORY, DeferredAction.REGISTER_EXPENSE,
                null, amountCents, description, null, null, null, null, null, sourceMessageId, options);
    }

    /** "Quanto foi?" -- categoria reconhecida, valor ausente. */
    public static PendingIntent askAmount(List<Option> categoryOption, String description,
                                          UUID sourceMessageId) {
        return new PendingIntent(PendingActionType.ASK_AMOUNT, DeferredAction.REGISTER_EXPENSE,
                null, null, description, null, null, null, null, null, sourceMessageId, categoryOption);
    }

    /** "Nao achei 'Feijao' na lista. Registro como comprado?" */
    public static PendingIntent confirmPurchase(String itemName, UUID sourceMessageId) {
        return new PendingIntent(PendingActionType.CONFIRM_PURCHASE, DeferredAction.ADD_ALREADY_PURCHASED,
                null, null, null, itemName, null, null, null, null, sourceMessageId, null);
    }

    // ------------------------------------------------------------------
    // Confianca media: a intencao inteira espera um "sim" (ADR-0029)
    // ------------------------------------------------------------------

    /** "Anoto Mercado, R$ 50,00?" -- categoria ja resolvida, confianca media. */
    public static PendingIntent confirmExpense(Option category, Long amountCents,
                                               String description, UUID sourceMessageId) {
        return new PendingIntent(PendingActionType.CONFIRM_INTENT, DeferredAction.REGISTER_EXPENSE,
                null, amountCents, description, null, null, null, null, null,
                sourceMessageId, List.of(category));
    }

    /** "Anoto arroz e leite na lista?" */
    public static PendingIntent confirmListItems(List<ItemDraft> items, UUID sourceMessageId) {
        return new PendingIntent(PendingActionType.CONFIRM_INTENT, DeferredAction.ADD_LIST_ITEMS,
                null, null, null, null, items, null, null, null, sourceMessageId, null);
    }

    /** "Marco 'Arroz' como comprado?" -- diferente de {@link #confirmPurchase}, que so existe fora da lista. */
    public static PendingIntent confirmMarkPurchased(String itemName, UUID sourceMessageId) {
        return new PendingIntent(PendingActionType.CONFIRM_INTENT, DeferredAction.MARK_PURCHASED,
                null, null, null, itemName, null, null, null, null, sourceMessageId, null);
    }

    /**
     * "Quanto foi a compra?" -- <b>o elo esperando o valor</b> (ADR-0031,
     * ADR-0033).
     *
     * <p>A pergunta e {@code ASK_AMOUNT}, a mesma de uma despesa sem valor, e a
     * acao e {@code CLOSE_PURCHASE}. E exatamente a separacao que a ADR-0029
     * fez: antes dela, "quanto foi?" so sabia terminar em despesa, e este
     * cenario nao caberia sem um tipo de pendencia novo.
     *
     * @param itemNames vazio significa "comprei tudo"
     */
    public static PendingIntent askCheckoutAmount(Option category, List<String> itemNames,
                                                  UUID sourceMessageId) {
        return new PendingIntent(PendingActionType.ASK_AMOUNT, DeferredAction.CLOSE_PURCHASE,
                null, null, null, null, null, itemNames, null, null,
                sourceMessageId, List.of(category));
    }

    /** "Fecho a compra de R$ 180,00 em Mercado?" -- confianca media no elo. */
    public static PendingIntent confirmCheckout(Option category, Long amountCents,
                                                List<String> itemNames, UUID sourceMessageId) {
        return new PendingIntent(PendingActionType.CONFIRM_INTENT, DeferredAction.CLOSE_PURCHASE,
                null, amountCents, null, null, null, itemNames, null, null,
                sourceMessageId, List.of(category));
    }

    /** "Removo 'Chocolate' da lista?" -- confianca media (ADR-0039). */
    public static PendingIntent confirmRemoveListItem(String itemName, UUID sourceMessageId) {
        return new PendingIntent(PendingActionType.CONFIRM_INTENT, DeferredAction.REMOVE_LIST_ITEM,
                null, null, null, itemName, null, null, null, null, sourceMessageId, null);
    }

    /** "Crio o convite para Bruno (+55...)?" -- o mais caro de errar, e o que menos podia executar no palpite. */
    public static PendingIntent confirmInvite(String memberName, String phoneNumber, UUID sourceMessageId) {
        return new PendingIntent(PendingActionType.CONFIRM_INTENT, DeferredAction.INVITE_MEMBER,
                null, null, null, null, null, null, memberName, phoneNumber, sourceMessageId, null);
    }

    public List<Option> optionsOrEmpty() {
        return options == null ? List.of() : options;
    }

    public List<ItemDraft> itemsOrEmpty() {
        return items == null ? List.of() : items;
    }

    /** Vazio e ausente sao a mesma coisa: os dois significam "comprei tudo". */
    public List<String> itemNamesOrEmpty() {
        return itemNames == null ? List.of() : itemNames;
    }
}
