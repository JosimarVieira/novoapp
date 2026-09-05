package com.novoapp.support;

import com.novoapp.identity.Channel;
import com.novoapp.identity.spi.OutboundMessagePort;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Canal stubbado: nenhum teste envia mensagem de verdade
 * (estrategia-de-testes.md).
 */
@Mock
@ApplicationScoped
public class StubOutboundMessagePort implements OutboundMessagePort {

    public record Sent(Channel channel, String externalId, String text) {
    }

    // Concorrente porque o pipeline pode rodar em thread propria (ADR-0005).
    private final List<Sent> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(Channel channel, String externalId, String text) {
        sent.add(new Sent(channel, externalId, text));
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

    public void clear() {
        sent.clear();
    }
}
