package com.novoapp.channel.inbound;

import com.novoapp.identity.Channel;

/**
 * Update do provedor ja traduzido pros campos que o resto de <code>channel</code>
 * usa. Ainda dentro de <code>channel</code>: nada disso atravessa a fronteira do
 * modulo.
 *
 * @param providerMessageId chave de idempotencia junto com o canal (ADR-0005)
 * @param sharedPhoneNumber E.164 quando a pessoa compartilhou o contato (ADR-0020)
 */
public record NormalizedInbound(Channel channel,
                                String externalId,
                                String senderName,
                                String providerMessageId,
                                String rawText,
                                String sharedPhoneNumber) {
}
