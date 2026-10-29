package com.novoapp.channel.outbound;

import com.novoapp.channel.inbound.TelegramActionCodec;
import com.novoapp.channel.inbound.TelegramClickOrigin;
import com.novoapp.common.message.ChatReply;
import com.novoapp.identity.Channel;
import com.novoapp.identity.spi.OutboundMessagePort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Optional;

/**
 * Implementacao Telegram do {@link OutboundMessagePort} (sdd-modulo-channel.md).
 *
 * <p>A interface vive em <code>identity</code> e a implementacao aqui: e o que
 * mantem <code>channel</code> como o modulo mais externo, do qual ninguem
 * depende.
 *
 * <p>Nenhum envio propaga falha. O lancamento ja foi gravado quando a resposta
 * sai, e derrubar o processamento por falha de envio nao desfaz nada -- so faria
 * o provedor reentregar e duplicar.
 */
@ApplicationScoped
public class TelegramMessageSender implements OutboundMessagePort {

    private static final Logger LOG = Logger.getLogger(TelegramMessageSender.class);

    @Inject
    @RestClient
    TelegramApi telegramApi;

    @ConfigProperty(name = "novoapp.channel.telegram.bot-token")
    String botToken;

    @Override
    public void send(Channel channel, String externalId, String text) {
        requireTelegram(channel);
        sendMessage(externalId, text, null);
    }

    /**
     * A resposta com acoes da ADR-0040, traduzida para a Bot API:
     * acoes viram teclado inline, "substituir a origem" vira
     * <code>editMessageText</code>, e o aviso curto vira o texto do
     * <code>answerCallbackQuery</code>.
     *
     * <p>A resposta ao clique sai primeiro e sempre, com ou sem aviso: e o que
     * tira o botao do estado de carregamento, e o Telegram so a aceita por
     * pouco tempo depois do toque.
     */
    @Override
    public void send(Channel channel, String externalId, ChatReply reply) {
        requireTelegram(channel);
        Optional<TelegramClickOrigin> origin = TelegramClickOrigin.decode(reply.origin());
        origin.ifPresent(click -> answer(click, reply.notice()));
        if (reply.text() == null) {
            return;
        }

        TelegramApi.InlineKeyboardMarkup keyboard = keyboardOf(reply);
        if (origin.isPresent() && reply.replaceOrigin() && edit(origin.get(), reply.text(), keyboard)) {
            return;
        }
        sendMessage(externalId, reply.text(), keyboard);
    }

    private void answer(TelegramClickOrigin click, String notice) {
        try {
            telegramApi.answerCallbackQuery(botToken,
                    new TelegramApi.AnswerCallbackQueryRequest(click.callbackQueryId(), notice));
        } catch (RuntimeException e) {
            // Clique velho demais para ser respondido, por exemplo. O botao para
            // de carregar sozinho; a edicao abaixo ainda vale.
            LOG.warnf(e, "Falha ao responder o clique %s", click.callbackQueryId());
        }
    }

    /**
     * @return falso quando a mensagem nao pode ser substituida, e a resposta
     *         precisa sair como mensagem nova -- quem tocou nunca fica sem
     *         resposta (sdd-modulo-channel.md, Erros)
     */
    private boolean edit(TelegramClickOrigin click, String text, TelegramApi.InlineKeyboardMarkup keyboard) {
        try {
            telegramApi.editMessageText(botToken, new TelegramApi.EditMessageTextRequest(
                    String.valueOf(click.chatId()), click.messageId(), text, keyboard));
            return true;
        } catch (TelegramApiException e) {
            if (e.isMessageNotModified()) {
                // O redesenho deu o mesmo texto e o mesmo teclado: toque duplo,
                // ou clique que nao mudou nada. E sucesso, nao recibo de erro --
                // e reenviar aqui empilharia uma lista identica no fio.
                return true;
            }
            LOG.warnf(e, "Mensagem %d nao pode ser editada; a resposta vai como mensagem nova", click.messageId());
            return false;
        } catch (RuntimeException e) {
            LOG.warnf(e, "Falha ao editar a mensagem %d; a resposta vai como mensagem nova", click.messageId());
            return false;
        }
    }

    private void sendMessage(String externalId, String text, TelegramApi.InlineKeyboardMarkup keyboard) {
        try {
            // Interacao e sempre 1:1 (ADR-0008), entao o chat de destino e a
            // propria pessoa: o external_id ja e o chat.
            telegramApi.sendMessage(botToken, new TelegramApi.SendMessageRequest(externalId, text, keyboard));
        } catch (RuntimeException e) {
            LOG.errorf(e, "Falha ao enviar mensagem para %s", externalId);
        }
    }

    private static TelegramApi.InlineKeyboardMarkup keyboardOf(ChatReply reply) {
        if (!reply.hasActions()) {
            return null;
        }
        List<List<TelegramApi.InlineKeyboardButton>> rows = reply.actions().stream()
                .map(row -> row.stream()
                        .map(action -> new TelegramApi.InlineKeyboardButton(action.label(),
                                TelegramActionCodec.encode(action.action())))
                        .toList())
                .toList();
        return new TelegramApi.InlineKeyboardMarkup(rows);
    }

    private static void requireTelegram(Channel channel) {
        if (channel != Channel.TELEGRAM) {
            // WhatsApp entra na Etapa 7 como outro adaptador, mesma interface.
            throw new UnsupportedOperationException("Canal ainda sem adaptador de envio: " + channel);
        }
    }
}
