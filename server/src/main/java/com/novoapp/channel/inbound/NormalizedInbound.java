package com.novoapp.channel.inbound;

import com.novoapp.common.message.ChatAction;
import com.novoapp.identity.Channel;

/**
 * Update do provedor ja traduzido pros campos que o resto de <code>channel</code>
 * usa. Ainda dentro de <code>channel</code>: nada disso atravessa a fronteira do
 * modulo.
 *
 * @param providerMessageId chave de idempotencia junto com o canal (ADR-0005)
 * @param rawText o texto da mensagem, ou o <code>callback_data</code> cru de um
 *        clique -- o registro forense do que o provedor entregou
 * @param sharedPhoneNumber E.164 quando a pessoa compartilhou o contato (ADR-0020)
 * @param action a Acao de botao, quando o update e um clique (ADR-0040)
 * @param origin o identificador opaco do clique, que volta na resposta
 */
public record NormalizedInbound(Channel channel,
                                String externalId,
                                String senderName,
                                String providerMessageId,
                                String rawText,
                                String sharedPhoneNumber,
                                ChatAction action,
                                String origin) {

    public NormalizedInbound(Channel channel, String externalId, String senderName,
                             String providerMessageId, String rawText, String sharedPhoneNumber) {
        this(channel, externalId, senderName, providerMessageId, rawText, sharedPhoneNumber, null, null);
    }

    public boolean isAction() {
        return action != null;
    }

    /** O tipo da linha em <code>inbound_message.kind</code> (ADR-0040). */
    public InboundMessageKind kind() {
        return isAction() ? InboundMessageKind.ACTION : InboundMessageKind.TEXT;
    }
}
