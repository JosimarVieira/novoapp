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
import com.novoapp.shopping.ItemDraft;
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
 * formatacao de recibo (ADR-0004, ADR-0018, ADR-0029). Nao decide regra de
 * negocio de dominio -- so orquestra pendencia -> nlu -> confianca -> dominio ->
 * recibo.
 *
 * <p>A ordem do metodo {@link #process} e a decisao central da Etapa 2a:
 * <b>pergunta em aberto vem antes de qualquer interpretacao</b>. E o que faz o
 * curto-circuito da regra 6 do CLAUDE.md valer -- responder "sim" nao pode gastar
 * chamada de modelo -- e o que da ao <code>desfazer</code> a precedencia
 * absoluta sobre estorno que a ADR-0025 decidiu.
 *
 * <p>Duas coisas que a ADR-0029 mudou, e que atravessam este arquivo inteiro:
 * <ol>
 *   <li><b>confianca media nunca executa</b>, para nenhuma intencao que escreva
 *       -- vira pendencia com a intencao ecoada de volta. A unica excecao
 *       declarada e a consulta de lista, que nao escreve nada;</li>
 *   <li><b>a pendencia guarda o que executar</b> ({@link PendingIntent#deferred()})
 *       em vez de o orquestrador deduzir pelo tipo da pergunta. E o que faz
 *       "quanto foi?" deixar de significar sempre "uma despesa".</li>
 * </ol>
 *
 * <p>Nao e transacional de proposito: a chamada ao LLM tem cauda de latencia
 * imprevisivel (ADR-0005) e nao pode segurar conexao de banco aberta. Cada passo
 * abre a propria transacao curta. A consequencia -- um passo pode ter commitado
 * quando o seguinte falha -- esta declarada no texto do recibo de erro, que por
 * isso nao afirma "nao gravei nada".
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
            // esquecida travaria a conversa pra sempre. E tambem o caminho de
            // quem ja teve a pendencia superada (ADR-0029), que fecha a janela
            // do atalho gravando expires_at.
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
            // Confiança media (ADR-0029) e "nao estava na lista" (ADR-0018) sao a
            // mesma mecanica: a intencao inteira estava guardada esperando o sim.
            case CONFIRM_PURCHASE, CONFIRM_INTENT -> {
                pendingActions.resolve(pending.id(), PendingResolution.CONFIRMED);
                yield executeDeferred(intent, intent.amountCents(), context, reply);
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
        if (intent.type() != PendingActionType.CHOOSE_CATEGORY
                || chosen == null || chosen < 1 || chosen > options.size()) {
            return repeatQuestion(pending, reply);
        }

        PendingIntent.Option option = options.get(chosen - 1);
        pendingActions.resolve(pending.id(), PendingResolution.CONFIRMED);
        return registerOrAskAmount(context, option, intent.amountCents(), intent.description(),
                intent.sourceMessageId(), reply);
    }

    /**
     * Resposta que nao e atalho nenhum, com pergunta em aberto (ADR-0029).
     *
     * <p>Uma chamada ao modelo, com as ferramentas do dia a dia mais a correcao
     * de categoria quando ha categoria oferecida a corrigir. Antes desta ADR so
     * a correcao era declarada, e o modelo nao tinha como dizer "isto nao
     * responde a pergunta" -- mensagem sobre outro assunto podia virar categoria
     * errada levando junto o valor guardado na pendencia.
     *
     * <p>Superar a pergunta exige <b>confianca alta</b>, e nao so "escolheu uma
     * tool": superar e destrutivo -- a pergunta sai do fio e o que ja estava
     * capturado (valor, categoria escolhida) deixa de estar ao alcance de um
     * "sim". Quem responde <code>mercado</code> em vez de <code>1</code> esta
     * respondendo, nao mudando de assunto.
     */
    private ProcessingOutcome otherAnswer(PendingActionService.Open pending,
                                          InboundMessage message,
                                          ResolvedContext context,
                                          Reply reply) {
        PendingIntent intent = pending.intent();

        // Valor solto numa pendencia que pede valor continua deterministico: nao
        // gasta modelo e nao corre o risco de ser lido como assunto novo.
        if (intent.type() == PendingActionType.ASK_AMOUNT
                && ShortCircuit.amountCents(message.rawText()) != null) {
            return amountAnswer(pending, message, context, reply);
        }

        boolean correctionOffered = intent.type() == PendingActionType.CREATE_CATEGORY;
        Intent read = nlu.interpretAnsweringPending(context.householdId(), pending.questionAsked(),
                message.rawText(), correctionOffered);

        if (read instanceof Intent.ConfirmSuggestedCategory correction) {
            // A correcao livre da ADR-0026. Confianca baixa repete a pergunta em
            // vez de criar categoria no palpite.
            if (!correctionOffered || confidence.levelOf(correction.confidence()) == ConfidencePolicy.Level.LOW) {
                return repeatQuestion(pending, reply);
            }
            CategoryCreation creation = categories.createExpenseCategory(context.householdId(),
                    context.memberId(), correction.name(), correction.parentName());
            return afterCategoryCreated(pending, creation, context, reply);
        }

        if (read instanceof Intent.Unknown
                || confidence.levelOf(read.confidence()) != ConfidencePolicy.Level.HIGH) {
            return repeatQuestion(pending, reply);
        }

        // Mudou de assunto, e esta claro que mudou: a pergunta para de
        // interceptar o fio, mas continua sem resolucao -- ninguem a respondeu, e
        // ela segue na central de pendencias (ADR-0018 + ADR-0029).
        pendingActions.closeShortcutWindow(pending.id());
        return execute(read, message, context, reply);
    }

    private ProcessingOutcome amountAnswer(PendingActionService.Open pending,
                                           InboundMessage message,
                                           ResolvedContext context,
                                           Reply reply) {
        Long amountCents = ShortCircuit.amountCents(message.rawText());
        PendingIntent intent = pending.intent();
        if (amountCents == null || amountCents <= 0) {
            return repeatQuestion(pending, reply);
        }

        pendingActions.resolve(pending.id(), PendingResolution.CONFIRMED);
        return executeDeferred(intent, amountCents, context, reply);
    }

    /**
     * Executa o que a pendencia guardou (ADR-0029).
     *
     * <p>E aqui que "a pendencia sabe o que fazer" deixa de ser so uma frase da
     * ADR: o <code>switch</code> e sobre {@link PendingIntent#deferred()}, nao
     * sobre o tipo da pergunta. Acrescentar <code>fecharCompra</code> na Etapa 3
     * e acrescentar um valor ao enum e um caso aqui -- que era exatamente o que o
     * gatilho de revisao do SDD deste modulo mandava verificar.
     */
    private ProcessingOutcome executeDeferred(PendingIntent intent, Long amountCents,
                                              ResolvedContext context, Reply reply) {
        return switch (intent.deferred()) {
            case REGISTER_EXPENSE -> {
                List<PendingIntent.Option> options = intent.optionsOrEmpty();
                if (options.isEmpty()) {
                    // Pendencia de despesa sem categoria guardada nao deveria
                    // existir; tratar como "nao entendi" e melhor que estourar em
                    // cima de um lancamento.
                    LOG.warnf("Pendencia de despesa sem categoria guardada: %s", intent);
                    yield notUnderstood(reply, 0.0d);
                }
                yield registerOrAskAmount(context, options.get(0), amountCents, intent.description(),
                        intent.sourceMessageId(), reply);
            }
            case ADD_LIST_ITEMS -> addItems(context, intent.itemsOrEmpty(), intent.sourceMessageId(), reply);
            case MARK_PURCHASED -> markPurchased(context, intent.itemName(), intent.sourceMessageId(), reply);
            case ADD_ALREADY_PURCHASED -> {
                MarkPurchasedResult.Purchased purchased = shopping.addAlreadyPurchased(context.householdId(),
                        context.memberId(), intent.itemName(), intent.sourceMessageId());
                reply.send(receipts.purchasedReceipt(reply.locale, purchased.name()));
                yield executed(json(Map.of("tool", "marcarItemComprado", "item", purchased.name())));
            }
            case INVITE_MEMBER -> issueInvite(context, intent.memberName(), intent.phoneNumber(), reply);
        };
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

        return execute(nlu.interpret(context.householdId(), message.rawText()), message, context, reply);
    }

    /**
     * O caminho de execucao de uma intencao recem-interpretada.
     *
     * <p>Compartilhado entre a mensagem nova e a mensagem que superou uma
     * pendencia (ADR-0029): depois de a pergunta antiga sair do caminho, mudar
     * de assunto tem que valer exatamente o mesmo que ter dito aquilo do nada.
     */
    private ProcessingOutcome execute(Intent intent, InboundMessage message,
                                      ResolvedContext context, Reply reply) {
        return switch (intent) {
            case Intent.RegisterExpense expense -> registerExpense(expense, message, context, reply);
            case Intent.AddListItems items -> addListItems(items, message, context, reply);
            case Intent.MarkItemPurchased purchase -> markItemPurchased(purchase, message, context, reply);
            case Intent.QueryList query -> queryList(query, context, reply);
            case Intent.InviteMember invite -> inviteMember(invite, message, context, reply);
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
        ConfidencePolicy.Level level = confidence.levelOf(expense.confidence());

        // Categoria que nao existe tem confirmacao propria, e nao a linha
        // "opcoes numeradas" da tabela da ADR-0004: aquela linha e sobre escolher
        // entre alternativas existentes, e aqui nao ha nenhuma (ADR-0024).
        //
        // Vem ANTES da faixa baixa, e nao depois (corrigido em 2026-09-17, com
        // dado de uso real): "Pet shop 80" chegava com confianca 0,3 -- o modelo
        // se declara inseguro justamente quando tem de sugerir nome novo -- e a
        // faixa baixa engolia a mensagem antes de alguem reparar que havia um
        // nome de categoria ali. O usuario recebia "nao entendi essa" com o bot
        // sabendo exatamente o que ele quis.
        //
        // Colisao entre ADRs aceitas: a ADR-0004 manda confianca baixa nao
        // adivinhar; a ADR-0024 manda categoria inexistente sempre perguntar.
        // Prevalece a ADR-0024, porque perguntar aqui nao e adivinhar -- nada e
        // criado sem o "sim", e uma pergunta com um nome concreto dentro custa
        // uma palavra para responder, enquanto "nao entendi" custa reescrever a
        // mensagem inteira e nao ensina nada.
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

        if (level == ConfidencePolicy.Level.LOW) {
            return notUnderstood(reply, expense.confidence());
        }

        PendingIntent.Option chosen = new PendingIntent.Option(expense.categoryId(),
                expense.categoryDisplayName());

        if (level == ConfidencePolicy.Level.MEDIUM && expense.alternatives().size() > 1) {
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

        // ADR-0029: confianca media com uma candidata so nao tem opcao a numerar,
        // mas continua sendo palpite -- vira sim/nao com a despesa ecoada de
        // volta, em vez de executar. Sem valor, quem pergunta e o passo do valor
        // logo abaixo: responder quanto foi ja e confirmar a categoria, e duas
        // perguntas seguidas e o que o SDD deste modulo proibe.
        if (level == ConfidencePolicy.Level.MEDIUM && expense.hasAmount()) {
            String question = receipts.confirmExpense(reply.locale, chosen.label(),
                    expense.amountCents(), expense.description());
            pendingActions.open(context,
                    PendingIntent.confirmExpense(chosen, expense.amountCents(), expense.description(),
                            message.id()),
                    question, List.of());
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
        ConfidencePolicy.Level level = confidence.levelOf(intent.confidence());
        if (level == ConfidencePolicy.Level.LOW) {
            return notUnderstood(reply, intent.confidence());
        }
        if (level == ConfidencePolicy.Level.MEDIUM) {
            String question = receipts.confirmListItems(reply.locale,
                    intent.items().stream().map(ItemDraft::name).toList());
            pendingActions.open(context, PendingIntent.confirmListItems(intent.items(), message.id()),
                    question, List.of());
            reply.send(question);
            return interpreted(intent.confidence());
        }
        return addItems(context, intent.items(), message.id(), reply);
    }

    private ProcessingOutcome addItems(ResolvedContext context, List<ItemDraft> drafts,
                                       UUID sourceMessageId, Reply reply) {
        List<AddedItem> added = shopping.addItems(context.householdId(), context.memberId(),
                drafts, sourceMessageId);

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
        ConfidencePolicy.Level level = confidence.levelOf(intent.confidence());
        if (level == ConfidencePolicy.Level.LOW) {
            return notUnderstood(reply, intent.confidence());
        }
        if (level == ConfidencePolicy.Level.MEDIUM) {
            String question = receipts.confirmMarkPurchased(reply.locale, intent.itemName());
            pendingActions.open(context,
                    PendingIntent.confirmMarkPurchased(intent.itemName(), message.id()),
                    question, List.of());
            reply.send(question);
            return interpreted(intent.confidence());
        }
        return markPurchased(context, intent.itemName(), message.id(), reply);
    }

    private ProcessingOutcome markPurchased(ResolvedContext context, String itemName,
                                            UUID sourceMessageId, Reply reply) {
        MarkPurchasedResult result = shopping.markPurchased(context.householdId(), context.memberId(),
                itemName);
        return switch (result) {
            case MarkPurchasedResult.Purchased purchased -> {
                reply.send(receipts.purchasedReceipt(reply.locale, purchased.name()));
                yield executed(json(Map.of("tool", "marcarItemComprado", "item", purchased.name())));
            }
            case MarkPurchasedResult.NotOnTheList missing -> {
                String question = receipts.offerPurchaseOfUnlistedItem(reply.locale, missing.name());
                pendingActions.open(context,
                        PendingIntent.confirmPurchase(missing.name(), sourceMessageId), question, List.of());
                reply.send(question);
                yield interpreted();
            }
        };
    }

    /**
     * A excecao declarada da ADR-0029 a faixa media: leitura executa sem
     * confirmacao. Nao ha o que desfazer numa consulta, e o custo de errar e a
     * pessoa reler uma lista -- perguntar antes de ler seria friccao sem risco do
     * outro lado.
     */
    private ProcessingOutcome queryList(Intent.QueryList intent, ResolvedContext context, Reply reply) {
        if (confidence.levelOf(intent.confidence()) == ConfidencePolicy.Level.LOW) {
            return notUnderstood(reply, intent.confidence());
        }
        List<ListItemView> pending = shopping.pendingItems(context.householdId());
        reply.send(receipts.pendingList(reply.locale, pending));
        return executed(json(Map.of("tool", "consultarLista", "itens", pending.size())));
    }

    private ProcessingOutcome inviteMember(Intent.InviteMember intent, InboundMessage message,
                                           ResolvedContext context, Reply reply) {
        ConfidencePolicy.Level level = confidence.levelOf(intent.confidence());
        if (level == ConfidencePolicy.Level.LOW) {
            return notUnderstood(reply, intent.confidence());
        }
        // E a intencao mais cara de errar da lista -- um convite emitido no
        // palpite da a um telefone errado o caminho para dentro da familia -- e
        // era a que executava direto em confianca media (ADR-0029).
        if (level == ConfidencePolicy.Level.MEDIUM) {
            String question = receipts.confirmInvite(reply.locale, intent.memberName(),
                    intent.phoneNumber());
            pendingActions.open(context,
                    PendingIntent.confirmInvite(intent.memberName(), intent.phoneNumber(), message.id()),
                    question, List.of());
            reply.send(question);
            return interpreted(intent.confidence());
        }
        return issueInvite(context, intent.memberName(), intent.phoneNumber(), reply);
    }

    private ProcessingOutcome issueInvite(ResolvedContext context, String memberName,
                                          String phoneNumber, Reply reply) {
        InviteIssuer.IssueResult result = invites.issue(context.householdId(), context.memberId(),
                reply.channel, memberName, phoneNumber);
        return switch (result) {
            case InviteIssuer.IssueResult.Issued issued -> {
                reply.send(receipts.inviteIssued(reply.locale, issued.invite()));
                yield executed(json(Map.of("tool", "convidarMembro", "telefone", phoneNumber)));
            }
            case InviteIssuer.IssueResult.NotOwner ignored -> {
                reply.send(receipts.inviteNotOwner(reply.locale));
                yield interpreted();
            }
            case InviteIssuer.IssueResult.AlreadyInvited already -> {
                reply.send(receipts.inviteAlreadyPending(reply.locale, already.phoneNumber()));
                yield interpreted();
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
