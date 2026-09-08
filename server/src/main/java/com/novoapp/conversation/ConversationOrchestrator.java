package com.novoapp.conversation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novoapp.common.message.InboundMessage;
import com.novoapp.conversation.ProcessingOutcome.Result;
import com.novoapp.conversation.entity.PendingResolution;
import com.novoapp.finance.CategoryCreation;
import com.novoapp.finance.CategoryService;
import com.novoapp.finance.FinanceService;
import com.novoapp.finance.RegisteredExpense;
import com.novoapp.finance.ReversedExpense;
import com.novoapp.identity.Channel;
import com.novoapp.identity.ContextResolution.ResolvedContext;
import com.novoapp.identity.MemberDirectory;
import com.novoapp.identity.onboarding.InviteIssuer;
import com.novoapp.identity.spi.OutboundMessagePort;
import com.novoapp.nlu.Intent;
import com.novoapp.nlu.NluService;
import com.novoapp.shopping.AddedItem;
import com.novoapp.shopping.ListItemView;
import com.novoapp.shopping.MarkPurchasedResult;
import com.novoapp.shopping.ShoppingService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Politica de confianca, {@code PendingAction}, curto-circuito de confirmacao e
 * formatacao de recibo (ADR-0004, ADR-0018). Nao decide regra de negocio de
 * dominio -- so orquestra pendencia -> nlu -> confianca -> dominio -> recibo.
 *
 * <p>A ordem do metodo {@link #process} e a decisao central desta etapa:
 * <b>pergunta em aberto vem antes de qualquer interpretacao</b>. E o que faz o
 * curto-circuito da regra 6 do CLAUDE.md valer -- responder "sim" nao pode gastar
 * chamada de modelo -- e o que da ao <code>desfazer</code> a precedencia
 * absoluta sobre estorno que a ADR-0025 decidiu.
 *
 * <p>Nao e transacional de proposito: a chamada ao LLM tem cauda de latencia
 * imprevisivel (ADR-0005) e nao pode segurar conexao de banco aberta. Cada passo
 * abre a propria transacao curta.
 */
@ApplicationScoped
public class ConversationOrchestrator {

    private static final Logger LOG = Logger.getLogger(ConversationOrchestrator.class);

    @Inject
    NluService nlu;

    @Inject
    FinanceService finance;

    @Inject
    CategoryService categories;

    @Inject
    ShoppingService shopping;

    @Inject
    InviteIssuer invites;

    @Inject
    MemberDirectory members;

    @Inject
    PendingActionService pendingActions;

    @Inject
    ConfidencePolicy confidence;

    @Inject
    ReceiptFormatter receipts;

    @Inject
    OutboundMessagePort outbound;

    @Inject
    ObjectMapper objectMapper;

    /**
     * Nome em ingles, e nao <code>processar</code> como no SDD: identificador em
     * ingles e regra sem excecao no CLAUDE.md.
     *
     * @param channel e {@code externalId} sao so o endereco de resposta; nenhuma
     *        decisao daqui olha pra eles (regra 5). A unica excecao e o convite,
     *        que precisa do canal pra montar o link -- e quem monta e
     *        <code>channel</code>, atraves de um porto (ADR-0020)
     */
    public ProcessingOutcome process(InboundMessage message,
                                     ResolvedContext context,
                                     Channel channel,
                                     String externalId) {
        Reply reply = new Reply(channel, externalId, context.locale());
        try {
            Optional<ProcessingOutcome> answered = pendingActions.findOpen(context)
                    .flatMap(pending -> answerPending(pending, message, context, reply));
            return answered.orElseGet(() -> interpretFresh(message, context, reply));
        } catch (RuntimeException e) {
            // Erro depois do 200 do webhook: recibo de erro no chat, nunca so no
            // log (sdd-visao-geral.md).
            LOG.errorf(e, "Falha ao processar a mensagem %s", message.id());
            reply.send(receipts.failure(reply.locale));
            return new ProcessingOutcome(Result.FAILED, null, null);
        }
    }

    // ------------------------------------------------------------------
    // Resposta a uma pergunta em aberto
    // ------------------------------------------------------------------

    /**
     * @return vazio quando a mensagem <b>nao</b> era resposta a esta pendencia e
     *         deve seguir como mensagem nova
     */
    private Optional<ProcessingOutcome> answerPending(PendingActionService.Open pending,
                                                      InboundMessage message,
                                                      ResolvedContext context,
                                                      Reply reply) {
        ShortCircuit.Answer answer = ShortCircuit.classify(message.rawText());

        if (pending.expired()) {
            // ADR-0018: fora do TTL o atalho nao existe mais. O bot avisa e
            // repete a pergunta, mas nao a resolve nem a descarta -- ela continua
            // esperando na central de pendencias do app (Etapa 4).
            //
            // Excecao: desfazer. A ADR-0025 da precedencia so a pendencia "nao
            // expirada", entao aqui ele volta a significar estorno. Mensagem que
            // nao e atalho nenhum tambem segue em frente: senao uma pendencia
            // esquecida travaria a conversa pra sempre.
            if (answer == ShortCircuit.Answer.UNDO || answer == ShortCircuit.Answer.OTHER) {
                return Optional.empty();
            }
            reply.send(receipts.pendingExpired(reply.locale, pending.questionAsked()));
            return Optional.of(interpreted());
        }

        return Optional.of(switch (answer) {
            case UNDO -> {
                pendingActions.resolve(pending.id(), PendingResolution.REJECTED);
                reply.send(receipts.pendingCancelled(reply.locale));
                yield interpreted();
            }
            case NO -> {
                pendingActions.resolve(pending.id(), PendingResolution.REJECTED);
                reply.send(receipts.pendingRejected(reply.locale));
                yield interpreted();
            }
            case YES -> confirmPending(pending, context, reply);
            case NUMBER -> numberAnswer(pending, message, context, reply);
            case OTHER -> otherAnswer(pending, message, context, reply);
        });
    }

    private ProcessingOutcome confirmPending(PendingActionService.Open pending,
                                             ResolvedContext context,
                                             Reply reply) {
        PendingIntent intent = pending.intent();
        return switch (intent.type()) {
            case CREATE_CATEGORY -> {
                // "sim" simples: categoria raiz, sem pai (ADR-0024). Hierarquia so
                // vem por correcao livre (ADR-0026).
                CategoryCreation creation = categories.createExpenseCategory(context.householdId(),
                        context.memberId(), intent.suggestedCategory(), null);
                yield afterCategoryCreated(pending, creation, context, reply);
            }
            case CONFIRM_PURCHASE -> {
                pendingActions.resolve(pending.id(), PendingResolution.CONFIRMED);
                MarkPurchasedResult.Purchased purchased = shopping.addAlreadyPurchased(context.householdId(),
                        context.memberId(), intent.itemName(), intent.sourceMessageId());
                reply.send(receipts.purchasedReceipt(reply.locale, purchased.name()));
                yield executed(null);
            }
            // "sim" nao responde "qual das duas?" nem "quanto foi?".
            case CHOOSE_CATEGORY, ASK_AMOUNT -> repeatQuestion(pending, reply);
        };
    }

    private ProcessingOutcome numberAnswer(PendingActionService.Open pending,
                                           InboundMessage message,
                                           ResolvedContext context,
                                           Reply reply) {
        PendingIntent intent = pending.intent();

        // Numa pendencia que pede o valor, "50" e cinquenta reais -- nunca "a
        // opcao numero 50". E por isso que numero so vira escolha quando a
        // pendencia de fato tem opcoes.
        if (intent.type() == PendingActionType.ASK_AMOUNT) {
            return amountAnswer(pending, message, context, reply);
        }

        List<PendingIntent.Option> options = intent.optionsOrEmpty();
        Integer chosen = ShortCircuit.optionNumber(message.rawText());
        if (chosen == null || chosen < 1 || chosen > options.size()) {
            return repeatQuestion(pending, reply);
        }

        PendingIntent.Option option = options.get(chosen - 1);
        pendingActions.resolve(pending.id(), PendingResolution.CONFIRMED);
        return registerOrAskAmount(context, option, intent.amountCents(), intent.description(),
                intent.sourceMessageId(), reply);
    }

    private ProcessingOutcome otherAnswer(PendingActionService.Open pending,
                                          InboundMessage message,
                                          ResolvedContext context,
                                          Reply reply) {
        PendingIntent intent = pending.intent();

        if (intent.type() == PendingActionType.ASK_AMOUNT
                && ShortCircuit.amountCents(message.rawText()) != null) {
            return amountAnswer(pending, message, context, reply);
        }

        // A excecao da ADR-0026 a regra 6, e a unica: so a pendencia de criacao de
        // categoria trata resposta livre como correcao, e so ela gasta uma segunda
        // chamada ao modelo. Qualquer outro tipo repete a pergunta.
        if (intent.type() != PendingActionType.CREATE_CATEGORY) {
            return repeatQuestion(pending, reply);
        }

        Intent corrected = nlu.interpretCategoryCorrection(context.householdId(),
                pending.questionAsked(), message.rawText());
        if (!(corrected instanceof Intent.ConfirmSuggestedCategory correction)
                || confidence.levelOf(correction.confidence()) == ConfidencePolicy.Level.LOW) {
            return repeatQuestion(pending, reply);
        }

        CategoryCreation creation = categories.createExpenseCategory(context.householdId(),
                context.memberId(), correction.name(), correction.parentName());
        return afterCategoryCreated(pending, creation, context, reply);
    }

    private ProcessingOutcome amountAnswer(PendingActionService.Open pending,
                                           InboundMessage message,
                                           ResolvedContext context,
                                           Reply reply) {
        Long amountCents = ShortCircuit.amountCents(message.rawText());
        PendingIntent intent = pending.intent();
        List<PendingIntent.Option> options = intent.optionsOrEmpty();
        if (amountCents == null || amountCents <= 0 || options.isEmpty()) {
            return repeatQuestion(pending, reply);
        }

        pendingActions.resolve(pending.id(), PendingResolution.CONFIRMED);
        RegisteredExpense expense = finance.registerExpense(context.householdId(), context.memberId(),
                options.get(0).id(), amountCents, intent.description(), intent.sourceMessageId());
        reply.send(receipts.expenseReceipt(expense, context));
        return executed(expenseIntentJson(expense));
    }

    /**
     * O desfecho comum das duas vias de confirmacao de categoria: o "sim" simples
     * (ADR-0024) e a correcao livre (ADR-0026).
     */
    private ProcessingOutcome afterCategoryCreated(PendingActionService.Open pending,
                                                   CategoryCreation creation,
                                                   ResolvedContext context,
                                                   Reply reply) {
        PendingIntent intent = pending.intent();
        return switch (creation) {
            case CategoryCreation.ParentIsSubcategory refused -> {
                // Nada criado: dois niveis violaria a ADR-0016. A pendencia
                // continua aberta e a pergunta muda -- e pergunta, nao erro
                // generico, que e o que o cenario exige.
                String question = receipts.parentIsSubcategory(reply.locale, intent.suggestedCategory(),
                        refused.parentName(), refused.grandparentName());
                pendingActions.updateQuestion(pending.id(), question);
                reply.send(question);
                yield interpreted();
            }
            case CategoryCreation.Created created -> {
                pendingActions.resolve(pending.id(), PendingResolution.CONFIRMED);
                yield registerOrAskAmount(context,
                        new PendingIntent.Option(created.categoryId(), created.name()),
                        intent.amountCents(), intent.description(), intent.sourceMessageId(), reply);
            }
        };
    }

    private ProcessingOutcome repeatQuestion(PendingActionService.Open pending, Reply reply) {
        reply.send(receipts.didNotUnderstandPending(reply.locale, pending.questionAsked()));
        return interpreted();
    }

    // ------------------------------------------------------------------
    // Mensagem nova
    // ------------------------------------------------------------------

    private ProcessingOutcome interpretFresh(InboundMessage message, ResolvedContext context, Reply reply) {
        // Sem pendencia aberta, desfazer volta a ser estorno (ADR-0025) -- e
        // continua sem gastar chamada de modelo (regra 6).
        if (ShortCircuit.classify(message.rawText()) == ShortCircuit.Answer.UNDO) {
            return reverseLatest(context, reply);
        }

        Intent intent = nlu.interpret(context.householdId(), message.rawText());
        return switch (intent) {
            case Intent.RegisterExpense expense -> registerExpense(expense, message, context, reply);
            case Intent.AddListItems items -> addListItems(items, message, context, reply);
            case Intent.MarkItemPurchased purchase -> markItemPurchased(purchase, message, context, reply);
            case Intent.QueryList query -> queryList(query, context, reply);
            case Intent.InviteMember invite -> inviteMember(invite, context, reply);
            // Correcao de categoria so existe respondendo a uma pendencia; fora
            // dela nao ha o que corrigir.
            case Intent.ConfirmSuggestedCategory ignored -> notUnderstood(reply, intent.confidence());
            case Intent.Unknown unknown -> notUnderstood(reply, unknown.confidence());
        };
    }

    private ProcessingOutcome registerExpense(Intent.RegisterExpense expense,
                                              InboundMessage message,
                                              ResolvedContext context,
                                              Reply reply) {
        if (confidence.levelOf(expense.confidence()) == ConfidencePolicy.Level.LOW) {
            return notUnderstood(reply, expense.confidence());
        }

        // Categoria que nao existe tem confirmacao propria, e nao a linha
        // "opcoes numeradas" da tabela da ADR-0004: aquela linha e sobre escolher
        // entre alternativas existentes, e aqui nao ha nenhuma (ADR-0024).
        if (expense.needsCategoryCreation()) {
            String question = receipts.offerCategoryCreation(reply.locale, expense.suggestedCategory(),
                    expense.amountCents());
            pendingActions.open(context,
                    PendingIntent.createCategory(expense.suggestedCategory(), expense.amountCents(),
                            expense.description(), message.id()),
                    question, List.of());
            reply.send(question);
            return interpreted(expense.confidence());
        }

        PendingIntent.Option chosen = new PendingIntent.Option(expense.categoryId(),
                expense.categoryDisplayName());

        if (confidence.levelOf(expense.confidence()) == ConfidencePolicy.Level.MEDIUM
                && expense.alternatives().size() > 1) {
            List<PendingIntent.Option> options = expense.alternatives().stream()
                    .map(choice -> new PendingIntent.Option(choice.id(), choice.label()))
                    .toList();
            String question = receipts.askWhichCategory(reply.locale, options, expense.amountCents());
            pendingActions.open(context,
                    PendingIntent.chooseCategory(options, expense.amountCents(), expense.description(),
                            message.id()),
                    question, options.stream().map(PendingIntent.Option::label).toList());
            reply.send(question);
            return interpreted(expense.confidence());
        }

        return registerOrAskAmount(context, chosen, expense.amountCents(), expense.description(),
                message.id(), reply);
    }

    /**
     * Grava, ou pergunta o valor quando ele falta. Os dois caminhos convergem
     * aqui: mensagem direta, escolha numerada e criacao de categoria terminam
     * todos em "tenho categoria; tenho valor?".
     */
    private ProcessingOutcome registerOrAskAmount(ResolvedContext context,
                                                  PendingIntent.Option category,
                                                  Long amountCents,
                                                  String description,
                                                  UUID sourceMessageId,
                                                  Reply reply) {
        if (amountCents == null || amountCents <= 0) {
            String question = receipts.askAmount(reply.locale, category.label());
            pendingActions.open(context,
                    PendingIntent.askAmount(List.of(category), description, sourceMessageId),
                    question, List.of());
            reply.send(question);
            return interpreted();
        }

        RegisteredExpense expense = finance.registerExpense(context.householdId(), context.memberId(),
                category.id(), amountCents, description, sourceMessageId);
        reply.send(receipts.expenseReceipt(expense, context));
        return executed(expenseIntentJson(expense));
    }

    private ProcessingOutcome reverseLatest(ResolvedContext context, Reply reply) {
        Optional<ReversedExpense> reversed = finance.reverseLatest(context.householdId(), context.memberId());
        if (reversed.isEmpty()) {
            reply.send(receipts.nothingToReverse(reply.locale));
            return interpreted();
        }
        ReversedExpense expense = reversed.get();
        String createdBy = members.nameOf(expense.createdByMemberId()).orElse(null);
        reply.send(receipts.reversalReceipt(expense, createdBy, context));
        return executed(json(Map.of("tool", "desfazer", "transaction_id", expense.transactionId().toString())));
    }

    private ProcessingOutcome addListItems(Intent.AddListItems intent,
                                           InboundMessage message,
                                           ResolvedContext context,
                                           Reply reply) {
        if (confidence.levelOf(intent.confidence()) == ConfidencePolicy.Level.LOW) {
            return notUnderstood(reply, intent.confidence());
        }
        List<AddedItem> added = shopping.addItems(context.householdId(), context.memberId(),
                intent.items(), message.id());

        // O nome de quem pediu antes vem de identity: member nao tem
        // household_id, entao o papel de dominio nao enxerga a tabela (ADR-0022).
        Map<UUID, String> requesters = new HashMap<>();
        added.stream().filter(AddedItem::alreadyPending).forEach(item ->
                requesters.computeIfAbsent(item.requestedByMemberId(),
                        memberId -> members.nameOf(memberId).orElse(null)));

        reply.send(receipts.listItemsReceipt(reply.locale, added, requesters));
        return executed(json(Map.of("tool", "adicionarItemLista",
                "itens", added.stream().map(AddedItem::name).toList())));
    }

    private ProcessingOutcome markItemPurchased(Intent.MarkItemPurchased intent,
                                                InboundMessage message,
                                                ResolvedContext context,
                                                Reply reply) {
        if (confidence.levelOf(intent.confidence()) == ConfidencePolicy.Level.LOW) {
            return notUnderstood(reply, intent.confidence());
        }

        MarkPurchasedResult result = shopping.markPurchased(context.householdId(), context.memberId(),
                intent.itemName());
        return switch (result) {
            case MarkPurchasedResult.Purchased purchased -> {
                reply.send(receipts.purchasedReceipt(reply.locale, purchased.name()));
                yield executed(json(Map.of("tool", "marcarItemComprado", "item", purchased.name())));
            }
            case MarkPurchasedResult.NotOnTheList missing -> {
                String question = receipts.offerPurchaseOfUnlistedItem(reply.locale, missing.name());
                pendingActions.open(context,
                        PendingIntent.confirmPurchase(missing.name(), message.id()), question, List.of());
                reply.send(question);
                yield interpreted(intent.confidence());
            }
        };
    }

    private ProcessingOutcome queryList(Intent.QueryList intent, ResolvedContext context, Reply reply) {
        if (confidence.levelOf(intent.confidence()) == ConfidencePolicy.Level.LOW) {
            return notUnderstood(reply, intent.confidence());
        }
        List<ListItemView> pending = shopping.pendingItems(context.householdId());
        reply.send(receipts.pendingList(reply.locale, pending));
        return executed(json(Map.of("tool", "consultarLista", "itens", pending.size())));
    }

    private ProcessingOutcome inviteMember(Intent.InviteMember intent, ResolvedContext context, Reply reply) {
        if (confidence.levelOf(intent.confidence()) == ConfidencePolicy.Level.LOW) {
            return notUnderstood(reply, intent.confidence());
        }

        InviteIssuer.IssueResult result = invites.issue(context.householdId(), context.memberId(),
                reply.channel, intent.memberName(), intent.phoneNumber());
        return switch (result) {
            case InviteIssuer.IssueResult.Issued issued -> {
                reply.send(receipts.inviteIssued(reply.locale, issued.invite()));
                yield executed(json(Map.of("tool", "convidarMembro",
                        "telefone", intent.phoneNumber())));
            }
            case InviteIssuer.IssueResult.NotOwner ignored -> {
                reply.send(receipts.inviteNotOwner(reply.locale));
                yield interpreted(intent.confidence());
            }
            case InviteIssuer.IssueResult.AlreadyInvited already -> {
                reply.send(receipts.inviteAlreadyPending(reply.locale, already.phoneNumber()));
                yield interpreted(intent.confidence());
            }
        };
    }

    // ------------------------------------------------------------------

    private ProcessingOutcome notUnderstood(Reply reply, double confidenceValue) {
        reply.send(receipts.notUnderstood(reply.locale));
        return new ProcessingOutcome(Result.INTERPRETED, confidenceValue, null);
    }

    private ProcessingOutcome interpreted() {
        return new ProcessingOutcome(Result.INTERPRETED, null, null);
    }

    private ProcessingOutcome interpreted(double confidenceValue) {
        return new ProcessingOutcome(Result.INTERPRETED, confidenceValue, null);
    }

    private ProcessingOutcome executed(String intentJson) {
        return new ProcessingOutcome(Result.EXECUTED, null, intentJson);
    }

    /**
     * Registro cru do que foi executado, pra calibrar o interpretador na Etapa 5.
     * Espelha em portugues o vocabulario da tool, porque e disso que ele e
     * registro -- diferente do <code>intent_json</code> de
     * <code>pending_action</code>, que e estado interno.
     */
    private String expenseIntentJson(RegisteredExpense expense) {
        Map<String, Object> intent = new LinkedHashMap<>();
        intent.put("tool", "registrarDespesa");
        intent.put("categoria", expense.categoryDisplayName());
        intent.put("valor_cents", expense.amountCents());
        if (expense.description() != null) {
            intent.put("descricao", expense.description());
        }
        return json(intent);
    }

    private String json(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            // Log de calibracao nao pode derrubar um lancamento ja gravado.
            LOG.warnf(e, "Nao foi possivel serializar o intent_json de %s", value);
            return null;
        }
    }

    /**
     * Pra onde responder, e em que idioma. Nenhuma decisao de dominio olha pro
     * canal (regra 5); o idioma anda junto porque toda resposta precisa dele
     * (ADR-0015) e carrega-lo em parametro separado em cada metodo seria mais um
     * lugar onde esquecer de propagar responde no idioma errado em silencio.
     */
    private final class Reply {

        private final Channel channel;
        private final String externalId;
        private final Locale locale;

        private Reply(Channel channel, String externalId, Locale locale) {
            this.channel = channel;
            this.externalId = externalId;
            this.locale = locale;
        }

        private void send(String text) {
            outbound.send(channel, externalId, text);
        }
    }
}
