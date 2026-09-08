package com.novoapp.common.i18n;

/**
 * A chave de cada texto que o usuario le (ADR-0015).
 *
 * <p>Enum, e nao string solta na chamada, por um motivo so: chave que nao existe
 * no arquivo de mensagens vira falha de teste em vez de
 * {@code MissingResourceException} na cara de quem mandou a mensagem.
 * {@code MessageBundleTest} percorre estes valores e cobra o arquivo -- e cobra
 * o contrario tambem, chave no arquivo que ninguem usa.
 *
 * <p>Acrescentar mensagem e: constante aqui, linha no
 * {@code messages_pt_BR.properties}. Esquecer um dos dois quebra o build.
 */
public enum MessageKey {

    // ---------------------------------------------------------------- recibo
    EXPENSE_RECEIPT("expense.receipt"),
    EXPENSE_RECEIPT_DESCRIPTION("expense.receipt.description"),
    RECEIPT_HOUSEHOLD("receipt.household"),
    RECEIPT_UNDO_HINT("receipt.undo.hint"),

    // ------------------------------------------------------------- categoria
    CATEGORY_CREATE_OFFER("category.create.offer"),
    CATEGORY_CREATE_OFFER_WITHOUT_AMOUNT("category.create.offer.without.amount"),
    CATEGORY_CHOOSE("category.choose"),
    CATEGORY_CHOOSE_WITHOUT_AMOUNT("category.choose.without.amount"),
    CATEGORY_CHOOSE_FOOTER("category.choose.footer"),
    CATEGORY_PARENT_IS_SUBCATEGORY("category.parent.is.subcategory"),
    CATEGORY_PARENT_IS_SUBCATEGORY_WHERE("category.parent.is.subcategory.where"),
    CATEGORY_PARENT_IS_SUBCATEGORY_WHERE_OF("category.parent.is.subcategory.where.of"),

    // ----------------------------------------------------------------- valor
    AMOUNT_ASK("amount.ask"),
    AMOUNT_ASK_WITHOUT_CATEGORY("amount.ask.without.category"),

    // --------------------------------------------------------------- estorno
    REVERSAL_RECEIPT("reversal.receipt"),
    REVERSAL_RECEIPT_BY("reversal.receipt.by"),
    REVERSAL_RECEIPT_FOOTER("reversal.receipt.footer"),
    REVERSAL_NOTHING("reversal.nothing"),

    // -------------------------------------------------------------- pendencia
    PENDING_CANCELLED("pending.cancelled"),
    PENDING_REJECTED("pending.rejected"),
    PENDING_EXPIRED("pending.expired"),
    PENDING_NOT_UNDERSTOOD("pending.not.understood"),

    // --------------------------------------------------------------- mercado
    LIST_ADDED_ONE("list.added.one"),
    LIST_ADDED_MANY("list.added.many"),
    LIST_ALREADY_PENDING("list.already.pending"),
    LIST_ALREADY_PENDING_BY("list.already.pending.by"),
    LIST_PURCHASED("list.purchased"),
    LIST_PURCHASE_OFFER("list.purchase.offer"),
    LIST_EMPTY("list.empty"),
    LIST_PENDING("list.pending"),
    LIST_ITEM_QUANTITY("list.item.quantity"),
    LIST_ITEM_QUANTITY_UNIT("list.item.quantity.unit"),

    // --------------------------------------------------------------- convite
    INVITE_ISSUED("invite.issued"),
    INVITE_NOT_OWNER("invite.not.owner"),
    INVITE_ALREADY_PENDING("invite.already.pending"),
    INVITE_ASK_CONTACT("invite.ask.contact"),
    INVITE_ACCEPTED("invite.accepted"),
    INVITE_ACCEPTED_OTHER_HOUSEHOLDS("invite.accepted.other.households"),
    INVITE_PHONE_MISMATCH("invite.phone.mismatch"),
    INVITE_EXPIRED("invite.expired"),
    INVITE_ALREADY_USED("invite.already.used"),
    INVITE_NOT_FOUND("invite.not.found"),

    // ------------------------------------------------------------ onboarding
    ONBOARDING_WELCOME("onboarding.welcome"),
    ONBOARDING_DECLINED("onboarding.declined"),
    ONBOARDING_ASK_HOUSEHOLD_NAME("onboarding.ask.household.name"),
    ONBOARDING_HOUSEHOLD_CREATED("onboarding.household.created"),
    ONBOARDING_APP_NOT_AVAILABLE("onboarding.app.not.available"),
    ONBOARDING_CONTINUING_BY_CHAT("onboarding.continuing.by.chat"),
    ONBOARDING_CHOOSE_HOUSEHOLD("onboarding.choose.household"),

    // -------------------------------------------------------------- genericos
    OPTION_LINE("option.line"),
    BULLET_LINE("bullet.line"),
    NOT_UNDERSTOOD("not.understood"),
    FAILURE("failure");

    private final String key;

    MessageKey(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
