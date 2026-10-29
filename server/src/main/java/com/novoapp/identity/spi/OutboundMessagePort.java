package com.novoapp.identity.spi;

import com.novoapp.common.message.ChatReply;
import com.novoapp.identity.Channel;

/**
 * Como o sistema fala de volta com uma pessoa.
 *
 * <p>A interface vive em <code>identity</code> e e implementada por
 * <code>channel</code> -- nunca o contrario, pra nao inverter a direcao de
 * dependencia do <code>sdd-visao-geral.md</code>.
 *
 * <p>Desvio consciente do que o <code>sdd-modulo-identity.md</code> desenhou
 * (<code>send(ChannelIdentity destino, String texto)</code>): durante o
 * onboarding ainda nao existe <code>ChannelIdentity</code> -- criar uma e o
 * desfecho do fluxo, nao o comeco dele. Endereca-se por
 * <code>(canal, external_id)</code>, que e o que se tem em maos desde a
 * primeira mensagem.
 */
public interface OutboundMessagePort {

    /** Texto puro. Onboarding e recibos continuam aqui, sem saber que botao existe. */
    void send(Channel channel, String externalId, String text);

    /**
     * Resposta com acoes, e a resposta a um clique (ADR-0040). Quem decide como
     * isso vira teclado, edicao de mensagem e resposta ao clique e o adaptador;
     * aqui so ha {@link ChatReply}, sem traco de canal (regra 5).
     *
     * <p>Toda resposta que carrega {@link ChatReply#origin()} responde ao clique
     * no canal, com ou sem aviso: sem isso o cliente do Telegram deixa o botao
     * carregando.
     */
    void send(Channel channel, String externalId, ChatReply reply);
}
