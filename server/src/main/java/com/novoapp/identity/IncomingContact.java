package com.novoapp.identity;

/**
 * Tudo que <code>channel</code> sabe sobre quem acabou de falar, antes de
 * qualquer resolucao.
 *
 * <p>Desvio consciente do <code>sdd-modulo-channel.md</code>, que desenhou
 * <code>resolveContext(channel, externalId)</code>: o token de convite chega
 * dentro do texto (<code>/start &lt;token&gt;</code>, ADR-0020) e o telefone
 * chega no contato compartilhado. Com so o par
 * <code>(canal, external_id)</code>, <code>identity</code> nao teria como saber
 * que a mensagem e um aceite de convite, e a decisao vazaria pra
 * <code>channel</code> -- que, por regra, nao decide nada.
 *
 * @param senderName        nome informado pelo proprio canal, usado ao criar o member
 * @param sharedPhoneNumber E.164 do contato compartilhado, quando houve um
 */
public record IncomingContact(Channel channel,
                              String externalId,
                              String senderName,
                              String text,
                              String sharedPhoneNumber) {
}
