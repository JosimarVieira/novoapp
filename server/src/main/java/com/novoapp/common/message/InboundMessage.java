package com.novoapp.common.message;

import java.time.Instant;
import java.util.UUID;

/**
 * Mensagem recebida, ja normalizada e sem traco do canal de origem (glossario).
 *
 * <p>E o que <code>channel</code> repassa pra baixo. Nao carrega qual canal
 * entregou a mensagem de proposito: e a regra nao negociavel 5 do CLAUDE.md --
 * nada abaixo de <code>channel</code> sabe se veio de Telegram ou WhatsApp.
 *
 * <p>Nao confundir com a entidade <code>InboundMessageEntity</code>, que e o log
 * de ingestao dentro de <code>channel</code>, com canal, id do provedor e
 * status. Este record e so o que o resto do sistema tem direito de ver.
 *
 * <p>Desde a ADR-0040 pode ser texto ou Acao de botao. A primeira mudanca neste
 * contrato desde a Etapa 1, e por isso o construtor de tres argumentos continua:
 * mensagem de texto e o caso comum, e nao precisa saber que botao existe.
 *
 * @param action a Acao de botao, quando a mensagem e um clique; nula em texto
 * @param origin identificador opaco do clique. So <code>channel</code> sabe o que
 *        ha dentro; <code>conversation</code> so o devolve na resposta
 *        ({@link ChatReply#origin()}), nunca o interpreta (regra 5)
 */
public record InboundMessage(UUID id, String rawText, Instant receivedAt, ChatAction action, String origin) {

    public InboundMessage(UUID id, String rawText, Instant receivedAt) {
        this(id, rawText, receivedAt, null, null);
    }

    public boolean isAction() {
        return action != null;
    }
}
