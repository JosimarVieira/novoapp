package com.novoapp.conversation;

import com.novoapp.common.message.InboundMessage;
import com.novoapp.conversation.ProcessingOutcome.Result;
import com.novoapp.finance.FinanceService;
import com.novoapp.finance.RegisteredExpense;
import com.novoapp.identity.ContextResolution.ResolvedContext;
import com.novoapp.identity.spi.OutboundMessagePort;
import com.novoapp.identity.Channel;
import com.novoapp.nlu.Intent;
import com.novoapp.nlu.NluService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Politica de confianca e formatacao de recibo (ADR-0004). Nao decide regra de
 * negocio de dominio -- so orquestra nlu -> confianca -> dominio -> recibo.
 *
 * <p>Escopo da Etapa 1: so confianca alta, decisao imediata, nenhuma pergunta de
 * volta no meio do registro. {@code PendingAction}, curto-circuito de
 * confirmacao e confianca media ficam pra Etapa 2 -- e por isso que aqui
 * qualquer coisa que nao seja confianca alta vira um recibo de erro generico,
 * nao uma pergunta especifica.
 *
 * <p>Nao e transacional de proposito: a chamada ao LLM tem cauda de latencia
 * imprevisivel (ADR-0005) e nao pode segurar conexao de banco aberta. Cada
 * passo abre a propria transacao curta.
 */
@ApplicationScoped
public class ConversationOrchestrator {

    private static final Logger LOG = Logger.getLogger(ConversationOrchestrator.class);

    @Inject
    NluService nlu;

    @Inject
    FinanceService finance;

    @Inject
    ReceiptFormatter receipts;

    @Inject
    OutboundMessagePort outbound;

    /**
     * Nome em ingles, e nao <code>processar</code> como no SDD: identificador em
     * ingles e regra sem excecao no CLAUDE.md.
     *
     * @param channel    e {@code externalId} sao so o endereco de resposta;
     *                   nenhuma decisao daqui olha pra eles (regra 5)
     */
    public ProcessingOutcome process(InboundMessage message,
                                     ResolvedContext context,
                                     Channel channel,
                                     String externalId) {
        try {
            Intent intent = nlu.interpret(context.householdId(), message.rawText());

            if (!intent.isHighConfidence() || intent.kind() != Intent.Kind.REGISTER_EXPENSE) {
                outbound.send(channel, externalId, receipts.notUnderstood());
                return new ProcessingOutcome(Result.INTERPRETED, intent.confidence(), null);
            }

            RegisteredExpense expense = finance.registerExpense(
                    context.householdId(), context.memberId(), intent.categoryId(),
                    intent.amountCents(), message.id());

            outbound.send(channel, externalId, receipts.expenseReceipt(expense, context));
            return new ProcessingOutcome(Result.EXECUTED, intent.confidence(), describe(intent));
        } catch (RuntimeException e) {
            // Erro depois do 200 do webhook: recibo de erro no chat, nunca so no
            // log (sdd-visao-geral.md).
            LOG.errorf(e, "Falha ao processar a mensagem %s", message.id());
            outbound.send(channel, externalId, receipts.failure());
            return new ProcessingOutcome(Result.FAILED, null, null);
        }
    }

    /**
     * Registro cru da intencao escolhida, pra calibrar o interpretador na
     * Etapa 5. Montado a mao porque nao ha contrato de serializacao definido
     * ainda -- e log, nao API.
     */
    private String describe(Intent intent) {
        return """
                {"tool":"registrarDespesa","categoria":"%s","valor_cents":%d}"""
                .formatted(intent.categoryName(), intent.amountCents());
    }
}
