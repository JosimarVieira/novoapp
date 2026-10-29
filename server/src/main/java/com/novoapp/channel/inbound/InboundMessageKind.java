package com.novoapp.channel.inbound;

/**
 * Se a linha de <code>inbound_message</code> e texto ou Acao de botao
 * (ADR-0040).
 *
 * <p>Existe para a calibracao da ADR-0035 nao ter de adivinhar: deduzir clique
 * pelo formato de <code>raw_text</code> ou pelo prefixo de
 * <code>provider_message_id</code> seria depender de duas escolhas deste
 * adaptador, que podem mudar sem aviso.
 */
public enum InboundMessageKind {
    TEXT,
    ACTION
}
