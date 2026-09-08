package com.novoapp.conversation;

import com.novoapp.common.i18n.MessageKey;
import com.novoapp.common.i18n.Messages;
import com.novoapp.finance.RegisteredExpense;
import com.novoapp.finance.ReversedExpense;
import com.novoapp.identity.ContextResolution.ResolvedContext;
import com.novoapp.identity.onboarding.InviteIssuer;
import com.novoapp.shopping.AddedItem;
import com.novoapp.shopping.ListItemView;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Todo texto que <code>conversation</code> manda pro chat, num lugar so: recibo
 * do que foi gravado e pergunta do que ficou pendente.
 *
 * <p>Recibo e "resposta curta confirmando o que foi gravado, sempre com a saida
 * de <code>desfazer</code>" (glossario). Pergunta e sempre <b>uma so</b>, com
 * opcoes numeradas quando ha opcoes (ADR-0004) -- nunca duas perguntas seguidas,
 * nunca uma pergunta aberta quando da pra oferecer alternativa.
 *
 * <p><b>Nenhum texto e literal aqui</b> (ADR-0015): esta classe monta a
 * estrutura da resposta e o conteudo vem de {@link Messages}, pelo idioma do
 * membro. Um teste trava isso -- literal longo neste arquivo quebra o build.
 */
@ApplicationScoped
public class ReceiptFormatter {

    /**
     * Formatacao de dinheiro e de data fica em pt-BR mesmo quando o texto sair em
     * outro idioma: a ADR-0015 poe moeda explicitamente fora de escopo, e o
     * household e brasileiro enquanto a validacao for na familia do fundador.
     */
    private static final Locale BRAZIL = Locale.of("pt", "BR");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy", BRAZIL);

    // ------------------------------------------------------------------
    // Financas
    // ------------------------------------------------------------------

    /**
     * Cenario "Despesa com categoria reconhecida": valor, categoria e como
     * desfazer.
     *
     * <p>Ecoa a descricao quando ela existe (ADR-0023) e a hierarquia quando a
     * categoria tem pai (ADR-0026) -- tornar a extracao visivel no instante em
     * que acontece e o que sustenta ser agressivo na execucao automatica.
     *
     * <p>Nao nomeia a conta usada. A ADR-0019 afirma de passagem que "recibo
     * sempre nomear a conta usada" seria "regra existente", mas essa regra nao
     * aparece no glossario, no .feature nem no SDD deste modulo -- e nesta etapa
     * so existe uma conta por household, entao nomear so acrescentaria ruido.
     *
     * <p>Nomeia o household so quando a pessoa tem mais de um vinculo: e o que a
     * ADR-0007 exige pra que erro de contexto fique visivel, e o que ela proibe
     * de aparecer pra quem tem uma familia so.
     */
    public String expenseReceipt(RegisteredExpense expense, ResolvedContext context) {
        Locale locale = context.locale();
        StringBuilder receipt = new StringBuilder(Messages.get(locale, MessageKey.EXPENSE_RECEIPT,
                expense.categoryDisplayName(), formatAmount(expense.amountCents())));
        if (expense.description() != null) {
            receipt.append(Messages.get(locale, MessageKey.EXPENSE_RECEIPT_DESCRIPTION, expense.description()));
        }
        if (context.multipleHouseholds()) {
            receipt.append(Messages.get(locale, MessageKey.RECEIPT_HOUSEHOLD, context.householdName()));
        }
        return receipt.append("\n")
                .append(Messages.get(locale, MessageKey.RECEIPT_UNDO_HINT))
                .toString();
    }

    /** ADR-0024: uma pergunta de sim/nao oferecendo criar a categoria que nao existe. */
    public String offerCategoryCreation(Locale locale, String suggestedCategory, Long amountCents) {
        String example = suggestedCategory.toLowerCase(locale);
        return amountCents == null
                ? Messages.get(locale, MessageKey.CATEGORY_CREATE_OFFER_WITHOUT_AMOUNT,
                        suggestedCategory, example)
                : Messages.get(locale, MessageKey.CATEGORY_CREATE_OFFER,
                        suggestedCategory, formatAmount(amountCents), example);
    }

    /**
     * ADR-0004, confianca media: UMA pergunta com opcoes numeradas. As opcoes
     * chegam ja ordenadas, a escolhida pelo modelo primeiro.
     */
    public String askWhichCategory(Locale locale, List<PendingIntent.Option> options, Long amountCents) {
        String header = amountCents == null
                ? Messages.get(locale, MessageKey.CATEGORY_CHOOSE_WITHOUT_AMOUNT)
                : Messages.get(locale, MessageKey.CATEGORY_CHOOSE, formatAmount(amountCents));
        return header
                + "\n" + numbered(locale, options.stream().map(PendingIntent.Option::label).toList())
                + "\n" + Messages.get(locale, MessageKey.CATEGORY_CHOOSE_FOOTER);
    }

    /** Cenario "Valor ausente": pergunta curta, e nunca um valor inventado. */
    public String askAmount(Locale locale, String categoryLabel) {
        return categoryLabel == null
                ? Messages.get(locale, MessageKey.AMOUNT_ASK_WITHOUT_CATEGORY)
                : Messages.get(locale, MessageKey.AMOUNT_ASK, categoryLabel);
    }

    /**
     * ADR-0026: a correcao pediria dois niveis de hierarquia, o que a ADR-0016
     * proibe. Volta a perguntar, com o motivo -- nao e erro generico, e uma
     * pergunta que da pra responder.
     */
    public String parentIsSubcategory(Locale locale, String suggestedCategory,
                                      String parentName, String grandparentName) {
        String where = grandparentName == null
                ? Messages.get(locale, MessageKey.CATEGORY_PARENT_IS_SUBCATEGORY_WHERE, parentName)
                : Messages.get(locale, MessageKey.CATEGORY_PARENT_IS_SUBCATEGORY_WHERE_OF,
                        parentName, grandparentName);
        return Messages.get(locale, MessageKey.CATEGORY_PARENT_IS_SUBCATEGORY, where, suggestedCategory);
    }

    /** ADR-0025: estorno do lancamento mais recente do household, de qualquer membro. */
    public String reversalReceipt(ReversedExpense reversed, String createdByName, ResolvedContext context) {
        Locale locale = context.locale();
        StringBuilder receipt = new StringBuilder(Messages.get(locale, MessageKey.REVERSAL_RECEIPT,
                reversed.categoryDisplayName(), formatAmount(reversed.amountCents())));
        if (createdByName != null && !createdByName.equals(context.memberName())) {
            // Estorno cross-member e permitido (ADR-0012), mas nao pode ser
            // silencioso: quem desfez precisa ver que o lancamento era de outra
            // pessoa.
            receipt.append(Messages.get(locale, MessageKey.REVERSAL_RECEIPT_BY, createdByName));
        }
        return receipt.append(Messages.get(locale, MessageKey.REVERSAL_RECEIPT_FOOTER)).toString();
    }

    public String nothingToReverse(Locale locale) {
        return Messages.get(locale, MessageKey.REVERSAL_NOTHING);
    }

    // ------------------------------------------------------------------
    // Pendencias
    // ------------------------------------------------------------------

    /**
     * ADR-0025: pendencia aberta vence o estorno. O texto precisa deixar
     * explicito <b>o que</b> foi desfeito -- a propria ADR registra que confundir
     * "cancelei uma pergunta" com "estornei um lancamento" e o risco desta
     * decisao.
     */
    public String pendingCancelled(Locale locale) {
        return Messages.get(locale, MessageKey.PENDING_CANCELLED);
    }

    public String pendingRejected(Locale locale) {
        return Messages.get(locale, MessageKey.PENDING_REJECTED);
    }

    /**
     * ADR-0018, fora do TTL: o atalho de resposta curta nao vale mais, o bot
     * avisa, aponta pro aplicativo e repete a pergunta ali mesmo. A pendencia
     * continua em aberto -- nada se perde por falta de resposta a tempo.
     */
    public String pendingExpired(Locale locale, String questionAsked) {
        return Messages.get(locale, MessageKey.PENDING_EXPIRED, questionAsked);
    }

    /** Resposta que nao e atalho nem correcao: repete a pergunta em vez de perde-la. */
    public String didNotUnderstandPending(Locale locale, String questionAsked) {
        return Messages.get(locale, MessageKey.PENDING_NOT_UNDERSTOOD, questionAsked);
    }

    // ------------------------------------------------------------------
    // Mercado
    // ------------------------------------------------------------------

    /**
     * Um recibo so, mesmo com varios itens -- "acabou arroz, leite e cafe" e uma
     * mensagem so.
     *
     * @param requesterNames nome de quem tinha pedido antes, por member_id. So
     *        usado nos itens que ja estavam na lista
     */
    public String listItemsReceipt(Locale locale, List<AddedItem> items, Map<UUID, String> requesterNames) {
        List<AddedItem> added = items.stream().filter(item -> !item.alreadyPending()).toList();
        List<AddedItem> already = items.stream().filter(AddedItem::alreadyPending).toList();

        StringBuilder receipt = new StringBuilder();
        if (added.size() == 1) {
            receipt.append(Messages.get(locale, MessageKey.LIST_ADDED_ONE, added.get(0).name()));
        } else if (added.size() > 1) {
            receipt.append(Messages.get(locale, MessageKey.LIST_ADDED_MANY,
                    bulleted(locale, added.stream().map(AddedItem::name).toList())));
        }
        for (AddedItem item : already) {
            if (!receipt.isEmpty()) {
                receipt.append("\n");
            }
            String requester = requesterNames.get(item.requestedByMemberId());
            receipt.append(requester == null
                    ? Messages.get(locale, MessageKey.LIST_ALREADY_PENDING, item.name())
                    : Messages.get(locale, MessageKey.LIST_ALREADY_PENDING_BY, item.name(), requester));
        }
        return receipt.toString();
    }

    public String purchasedReceipt(Locale locale, String itemName) {
        return Messages.get(locale, MessageKey.LIST_PURCHASED, itemName);
    }

    /** Cenario "Item mencionado nao existe na lista": pergunta, nunca erro. */
    public String offerPurchaseOfUnlistedItem(Locale locale, String itemName) {
        return Messages.get(locale, MessageKey.LIST_PURCHASE_OFFER, itemName);
    }

    public String pendingList(Locale locale, List<ListItemView> items) {
        if (items.isEmpty()) {
            return Messages.get(locale, MessageKey.LIST_EMPTY);
        }
        return Messages.get(locale, MessageKey.LIST_PENDING,
                bulleted(locale, items.stream().map(item -> describeItem(locale, item)).toList()));
    }

    // ------------------------------------------------------------------
    // Convite (ADR-0020)
    // ------------------------------------------------------------------

    /**
     * O sistema devolve o link pra quem convidou; nao entrega pra pessoa
     * convidada. Nao e escolha de produto -- a Bot API do Telegram nao deixa um
     * bot iniciar conversa com quem nunca falou com ele, e o recibo diz isso pra
     * que o OWNER saiba que a proxima acao e dele.
     */
    public String inviteIssued(Locale locale, InviteIssuer.IssuedInvite invite) {
        return Messages.get(locale, MessageKey.INVITE_ISSUED,
                invite.invitedName(), invite.phoneNumber(),
                DAY.format(invite.expiresAt().atZone(ZoneId.systemDefault())), invite.link());
    }

    public String inviteNotOwner(Locale locale) {
        return Messages.get(locale, MessageKey.INVITE_NOT_OWNER);
    }

    public String inviteAlreadyPending(Locale locale, String phoneNumber) {
        return Messages.get(locale, MessageKey.INVITE_ALREADY_PENDING, phoneNumber);
    }

    // ------------------------------------------------------------------
    // Genericos
    // ------------------------------------------------------------------

    /** ADR-0004, confianca baixa: pergunta aberta curta, sem adivinhar. */
    public String notUnderstood(Locale locale) {
        return Messages.get(locale, MessageKey.NOT_UNDERSTOOD);
    }

    public String failure(Locale locale) {
        return Messages.get(locale, MessageKey.FAILURE);
    }

    // ------------------------------------------------------------------

    private String describeItem(Locale locale, ListItemView item) {
        if (item.quantity() == null) {
            return item.name();
        }
        String quantity = item.quantity().stripTrailingZeros().toPlainString();
        return item.unit() == null
                ? Messages.get(locale, MessageKey.LIST_ITEM_QUANTITY, item.name(), quantity)
                : Messages.get(locale, MessageKey.LIST_ITEM_QUANTITY_UNIT, item.name(), quantity, item.unit());
    }

    private String numbered(Locale locale, List<String> options) {
        List<String> lines = new ArrayList<>();
        for (int index = 0; index < options.size(); index++) {
            lines.add(Messages.get(locale, MessageKey.OPTION_LINE, index + 1, options.get(index)));
        }
        return String.join("\n", lines);
    }

    private String bulleted(Locale locale, List<String> items) {
        return String.join("\n", items.stream()
                .map(item -> Messages.get(locale, MessageKey.BULLET_LINE, item))
                .toList());
    }

    /**
     * Formatado a mao em vez de {@code NumberFormat.getCurrencyInstance}: a
     * biblioteca usa espaco nao separavel entre o simbolo e o numero, e o
     * Gherkin fala em "R$ 50,00" com espaco comum.
     */
    private String formatAmount(long amountCents) {
        BigDecimal amount = BigDecimal.valueOf(amountCents).divide(BigDecimal.valueOf(100), 2, RoundingMode.UNNECESSARY);
        return "R$ " + String.format(BRAZIL, "%,.2f", amount);
    }
}
