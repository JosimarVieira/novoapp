package com.novoapp.support;

import com.novoapp.channel.inbound.TelegramClickOrigin;
import com.novoapp.common.message.ChatReply;
import com.novoapp.common.message.ChatReply.ReplyAction;
import com.novoapp.identity.Channel;
import com.novoapp.identity.spi.OutboundMessagePort;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Canal stubbado: nenhum teste envia mensagem de verdade
 * (estrategia-de-testes.md).
 *
 * <p>Desde a ADR-0040 ele faz o papel do adaptador inteiro, e nao so do envio de
 * texto: da id a cada mensagem enviada, como o Telegram da, guarda os botoes
 * dela, aplica a substituicao sobre a mensagem de origem e registra a resposta a
 * cada clique. E por ele que passos como "a mesma mensagem de Bruno e
 * atualizada" assertam -- e por isso ele le o identificador de origem, que fora
 * de <code>channel</code> nenhum codigo de producao le.
 */
@Mock
@ApplicationScoped
public class StubOutboundMessagePort implements OutboundMessagePort {

    /**
     * Uma mensagem que a pessoa viu aparecer no fio: nova, ou a mesma mensagem
     * substituida.
     *
     * @param messageId o id que o canal deu a mensagem; numa substituicao, o da
     *        mensagem de origem
     * @param replaced a mensagem de origem foi substituida, e nao enviada de novo
     * @param notice o aviso curto da resposta ao clique, quando houve
     */
    public record Sent(Channel channel, String externalId, String text, long messageId,
                       List<List<ReplyAction>> actions, boolean replaced, String notice) {

        public boolean hasActions() {
            return !actions.isEmpty();
        }
    }

    /** A resposta a um clique, com ou sem aviso. Todo clique tem de ter uma (ADR-0040). */
    public record ClickAnswer(String externalId, String clickId, String notice) {
    }

    private static final AtomicLong MESSAGE_IDS = new AtomicLong(10_000L);

    // Concorrente porque o pipeline pode rodar em thread propria (ADR-0005).
    private final List<Sent> sent = new CopyOnWriteArrayList<>();
    private final List<ClickAnswer> answers = new CopyOnWriteArrayList<>();
    /** O estado atual de cada mensagem, depois das substituicoes. */
    private final Map<Long, Sent> current = new ConcurrentHashMap<>();

    @Override
    public void send(Channel channel, String externalId, String text) {
        record(new Sent(channel, externalId, text, MESSAGE_IDS.incrementAndGet(), List.of(), false, null));
    }

    @Override
    public void send(Channel channel, String externalId, ChatReply reply) {
        Optional<TelegramClickOrigin> origin = TelegramClickOrigin.decode(reply.origin());
        origin.ifPresent(click -> answers.add(new ClickAnswer(externalId, click.callbackQueryId(), reply.notice())));
        if (reply.text() == null) {
            return;
        }
        boolean replacing = origin.isPresent() && reply.replaceOrigin();
        long messageId = replacing ? origin.get().messageId() : MESSAGE_IDS.incrementAndGet();
        record(new Sent(channel, externalId, reply.text(), messageId, reply.actions(), replacing, reply.notice()));
    }

    private void record(Sent message) {
        sent.add(message);
        current.put(message.messageId(), message);
    }

    public List<Sent> to(String externalId) {
        return sent.stream().filter(message -> message.externalId().equals(externalId)).toList();
    }

    public List<Sent> all() {
        return List.copyOf(sent);
    }

    public String lastTextTo(String externalId) {
        List<Sent> messages = to(externalId);
        return messages.isEmpty() ? null : messages.get(messages.size() - 1).text();
    }

    /** A ultima coisa que apareceu no fio desta pessoa, nova ou substituida. */
    public Optional<Sent> lastTo(String externalId) {
        List<Sent> messages = to(externalId);
        return messages.isEmpty() ? Optional.empty() : Optional.of(messages.get(messages.size() - 1));
    }

    /**
     * As mensagens desta pessoa como estao agora, da mais recente para a mais
     * antiga -- o que ela teria na tela para tocar.
     */
    public List<Sent> currentMessagesTo(String externalId) {
        return current.values().stream()
                .filter(message -> message.externalId().equals(externalId))
                .sorted((first, second) -> Long.compare(second.messageId(), first.messageId()))
                .toList();
    }

    /**
     * Uma mensagem como esta agora, depois das substituicoes -- o recibo onde a
     * pessoa vai tocar, que pode ja nao ser a mensagem mais recente do fio
     * (ADR-0041).
     */
    public Optional<Sent> current(long messageId) {
        return Optional.ofNullable(current.get(messageId));
    }

    public List<ClickAnswer> clickAnswersTo(String externalId) {
        return answers.stream().filter(answer -> answer.externalId().equals(externalId)).toList();
    }

    public void clear() {
        sent.clear();
        answers.clear();
        current.clear();
    }
}
