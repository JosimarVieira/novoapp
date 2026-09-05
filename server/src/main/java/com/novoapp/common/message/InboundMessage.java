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
 */
public record InboundMessage(UUID id, String rawText, Instant receivedAt) {
}
