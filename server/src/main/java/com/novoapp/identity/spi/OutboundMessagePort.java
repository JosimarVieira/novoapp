package com.novoapp.identity.spi;

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

    void send(Channel channel, String externalId, String text);
}
