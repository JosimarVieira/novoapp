package com.novoapp.conversation.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novoapp.common.message.ChatReply;
import com.novoapp.conversation.ProcessingOutcome;
import com.novoapp.conversation.ProcessingOutcome.Result;
import com.novoapp.conversation.ReceiptFormatter;
import com.novoapp.conversation.Reply;
import com.novoapp.finance.ExpenseView;
import com.novoapp.finance.FinanceService;
import com.novoapp.finance.ReversedExpense;
import com.novoapp.identity.ContextResolution.ResolvedContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Os botoes do recibo de lancamento (ADR-0041), resolvidos sem modelo (regra 6
 * do CLAUDE.md) e antes da busca por pendencia, como toda Acao de botao
 * (ADR-0040). {@link ChatActionHandler} entrega aqui os tipos do recibo.
 *
 * <p><b>Nunca chama o estorno do mais recente.</b> O alvo e o lancamento que o
 * botao carrega, e e isso que torna o toque duplo inofensivo: o segundo toque
 * encontra o mesmo lancamento ja estornado e redesenha, em vez de estornar o
 * proximo da fila (ADR-0041, item 7). O cenario do toque duplo existe para pegar
 * exatamente essa regressao.
 *
 * <p><b>Nao toca a pendencia aberta</b> (ADR-0041, item 8). Diferente do
 * fechamento pelo carrinho, que supera a pergunta, nenhum botao do recibo fala
 * com {@code PendingActionService}: a pergunta continua esperando a resposta
 * depois do toque.
 *
 * <p>Todo toque rele o lancamento. Estornado por qualquer caminho -- inclusive o
 * <code>desfazer</code> digitado -- vira aviso curto e o recibo redesenhado como
 * estornado.
 *
 * <p>Nenhum desfecho daqui carrega proveniencia: clique nao gasta modelo
 * (ADR-0036).
 */
@ApplicationScoped
public class ReceiptActionHandler {

    private static final Logger LOG = Logger.getLogger(ReceiptActionHandler.class);

    @Inject
    FinanceService finance;

    @Inject
    ReceiptView view;

    @Inject
    ReceiptFormatter receipts;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    Clock clock;

    /**
     * Quanto tempo depois de gravado um lancamento se desfaz com um toque. Config
     * global, provisoria e explicita, nunca constante no codigo: 24 horas e numero
     * sem base, a calibrar com uso real (decisao aberta #30). Lida a cada toque,
     * contra quando o lancamento foi gravado -- o botao nao carrega prazo nenhum.
     */
    @ConfigProperty(name = "novoapp.conversation.receipt.undo-window")
    Duration undoWindow;

    /**
     * <code>desfazer</code>: dentro da janela, um toque estorna o lancamento do
     * recibo; fora dela, a mensagem vira a confirmacao e nada e estornado
     * (ADR-0041, item 3).
     */
    public ProcessingOutcome undo(UUID transactionId, ResolvedContext context, Reply reply) {
        Optional<ExpenseView> expense = finance.expense(transactionId);
        if (expense.isEmpty() || expense.get().reversed()) {
            return cannotAct(expense, context, reply);
        }
        if (outsideWindow(expense.get())) {
            redraw(reply, view.confirmation(expense.get(), reply.locale()), null);
            return interpreted();
        }
        return reverseAndRedraw("desfazer", transactionId, context, reply);
    }

    /**
     * <code>estornar</code>: o segundo toque da confirmacao. Nao olha a janela --
     * e deliberado, vendo o que vai sumir. O risco de uma confirmacao esquecida
     * aberta no chat esta declarado na ADR-0041.
     */
    public ProcessingOutcome reverse(UUID transactionId, ResolvedContext context, Reply reply) {
        return reverseAndRedraw("estornar", transactionId, context, reply);
    }

    /** <code>voltar</code>: o recibo como o lancamento esta agora. Nao escreve nada. */
    public ProcessingOutcome back(UUID transactionId, ResolvedContext context, Reply reply) {
        Optional<ExpenseView> expense = finance.expense(transactionId);
        if (expense.isEmpty() || expense.get().reversed()) {
            return cannotAct(expense, context, reply);
        }
        redraw(reply, view.receipt(expense.get(), context), null);
        return interpreted();
    }

    /**
     * O estorno determinado e o redesenho como recibo de estorno, sem botoes
     * (ADR-0041, item 2). Os itens de um fechamento voltam para a lista pelo
     * mesmo evento do <code>desfazer</code> digitado (ADR-0032), dentro de
     * <code>finance</code>.
     */
    private ProcessingOutcome reverseAndRedraw(String action, UUID transactionId,
                                               ResolvedContext context, Reply reply) {
        Optional<ReversedExpense> reversed = finance.reverse(context.householdId(), context.memberId(),
                transactionId);
        if (reversed.isEmpty()) {
            // Estornado, ou sumido, entre a leitura e a escrita: outro toque, o
            // desfazer digitado, o outro telefone.
            return cannotAct(finance.expense(transactionId), context, reply);
        }
        redraw(reply, view.reversed(reversed.get(), context), null);
        return executed(action, transactionId);
    }

    /**
     * O banco disse que o botao ja nao vale. Lancamento estornado: aviso curto e o
     * recibo redesenhado como estornado. Lancamento que nao existe neste
     * household: so o aviso, e a mensagem fica como esta -- nao ha o que
     * desenhar no lugar dela.
     */
    private ProcessingOutcome cannotAct(Optional<ExpenseView> expense, ResolvedContext context, Reply reply) {
        if (expense.isEmpty()) {
            reply.send(ChatReply.text(null).withNotice(receipts.entryNotFound(reply.locale())));
        } else {
            redraw(reply, view.reversed(expense.get(), context), receipts.alreadyReversed(reply.locale()));
        }
        return interpreted();
    }

    /** Todo toque termina substituindo a mensagem onde o botao estava. */
    private void redraw(Reply reply, ChatReply state, String notice) {
        reply.send(state.replacingOrigin().withNotice(notice));
    }

    private boolean outsideWindow(ExpenseView expense) {
        return Instant.now(clock).isAfter(expense.createdAt().plus(undoWindow));
    }

    private ProcessingOutcome interpreted() {
        return new ProcessingOutcome(Result.INTERPRETED, null, null);
    }

    /**
     * O registro do clique para a Etapa 5, no vocabulario do glossario. Mesma
     * chave do <code>desfazer</code> digitado para o lancamento; a coluna
     * <code>kind</code> ja separa clique de texto.
     */
    private ProcessingOutcome executed(String action, UUID transactionId) {
        Map<String, Object> intent = new LinkedHashMap<>();
        intent.put("acao", action);
        intent.put("transaction_id", transactionId.toString());
        return new ProcessingOutcome(Result.EXECUTED, null, json(intent));
    }

    private String json(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            LOG.warnf(e, "Nao foi possivel serializar o intent_json de %s", value);
            return null;
        }
    }
}
