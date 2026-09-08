package com.novoapp.identity.spi;

import com.novoapp.identity.Channel;

/**
 * Transforma o token de um convite no link que a pessoa convidada abre.
 *
 * <p>Existe pelo mesmo motivo que {@link OutboundMessagePort}: o formato do link
 * e especifico do provedor -- <code>t.me/&lt;bot&gt;?start=&lt;token&gt;</code>
 * no Telegram, outra coisa no WhatsApp --, e nenhum codigo abaixo de
 * <code>channel</code> pode saber de qual canal a mensagem veio (regra 5 do
 * CLAUDE.md). Montar essa URL dentro de <code>identity</code> quebraria a regra
 * de um jeito que o ArchUnit nem pegaria, porque seria uma string e nao um tipo.
 *
 * <p>A interface e de <code>identity</code>, a implementacao e de
 * <code>channel</code> -- assim a direcao da dependencia continua sendo
 * <code>channel -&gt; identity</code>, nunca o contrario.
 */
public interface InviteLinkPort {

    String linkFor(Channel channel, String token);
}
