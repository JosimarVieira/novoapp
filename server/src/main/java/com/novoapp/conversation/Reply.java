package com.novoapp.conversation;

import com.novoapp.common.message.ChatReply;
import com.novoapp.identity.Channel;
import com.novoapp.identity.spi.OutboundMessagePort;

import java.util.Locale;

/**
 * Pra onde responder, em que idioma, e a qual clique. Nenhuma decisao de dominio
 * olha pro canal (regra 5); o idioma anda junto porque toda resposta precisa
 * dele (ADR-0015) e carrega-lo em parametro separado em cada metodo seria mais
 * um lugar onde esquecer de propagar responde no idioma errado em silencio.
 *
 * <p>Era classe interna de {@link ConversationOrchestrator} ate a ADR-0040, e
 * saiu de la pelo clique: a origem dele anda junto pelo mesmo motivo do idioma.
 * Toda resposta a um clique tem de responder a ele -- inclusive o recibo de
 * erro --, e isso so e garantido se ninguem precisar lembrar de passar a origem.
 */
public final class Reply {

    private final OutboundMessagePort outbound;
    private final Channel channel;
    private final String externalId;
    private final Locale locale;
    private final String origin;

    /**
     * @param origin o identificador opaco do clique sendo atendido, nulo quando
     *        a mensagem e texto. Ninguem aqui o interpreta (regra 5)
     */
    public Reply(OutboundMessagePort outbound, Channel channel, String externalId, Locale locale, String origin) {
        this.outbound = outbound;
        this.channel = channel;
        this.externalId = externalId;
        this.locale = locale;
        this.origin = origin;
    }

    public Locale locale() {
        return locale;
    }

    public Channel channel() {
        return channel;
    }

    public void send(String text) {
        if (origin == null) {
            outbound.send(channel, externalId, text);
        } else {
            send(ChatReply.text(text));
        }
    }

    public void send(ChatReply reply) {
        outbound.send(channel, externalId, origin == null ? reply : reply.inReplyTo(origin));
    }
}
