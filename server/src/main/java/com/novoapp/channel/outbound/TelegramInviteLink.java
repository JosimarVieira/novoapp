package com.novoapp.channel.outbound;

import com.novoapp.identity.Channel;
import com.novoapp.identity.spi.InviteLinkPort;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;

/**
 * O link do convite no formato do Telegram: <code>t.me/&lt;bot&gt;?start=&lt;token&gt;</code>
 * (ADR-0020).
 *
 * <p>Mora em <code>channel</code>, e nao em <code>identity</code>, porque o
 * formato e do provedor: nenhum codigo abaixo de <code>channel</code> sabe de
 * qual canal a mensagem veio (regra 5). Quando o WhatsApp entrar (Etapa 7),
 * entra outra implementacao deste mesmo porto e nada em
 * <code>identity</code> muda.
 */
@ApplicationScoped
public class TelegramInviteLink implements InviteLinkPort {

    /**
     * Username do bot, sem o arroba. Nao da pra derivar do token da Bot API sem
     * uma chamada de rede, e o link e montado no meio de uma resposta de chat --
     * config e mais barato e mais previsivel que um getMe a cada convite.
     *
     * <p>{@code Optional} e nao {@code String}: o SmallRye converte string vazia
     * em ausencia, e vazio e o valor normal em desenvolvimento e em teste, onde
     * nao ha bot publicado.
     */
    @ConfigProperty(name = "novoapp.channel.telegram.bot-username")
    Optional<String> botUsername;

    @Override
    public String linkFor(Channel channel, String token) {
        if (channel != Channel.TELEGRAM) {
            throw new IllegalArgumentException("Canal sem adaptador de link de convite: " + channel);
        }
        // Sem username configurado, devolve o token cru em vez de um link
        // quebrado: em desenvolvimento local nao ha bot publicado, e o cenario de
        // aceite so precisa do token.
        return botUsername.filter(username -> !username.isBlank())
                .map(username -> "https://t.me/%s?start=%s".formatted(username, token))
                .orElse(token);
    }
}
