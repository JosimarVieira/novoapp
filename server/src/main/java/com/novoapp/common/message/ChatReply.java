package com.novoapp.common.message;

import java.util.List;

/**
 * Resposta com acoes (ADR-0040): texto, botoes neutros em linhas e, quando a
 * resposta atende a um clique, o que fazer com a mensagem de onde ele veio.
 *
 * <p>Nao sabe de teclado inline, de <code>editMessageText</code> nem de
 * <code>answerCallbackQuery</code>: quem traduz isto para o canal e o adaptador
 * (regra 5 do CLAUDE.md). No WhatsApp a mesma resposta vira lista interativa e
 * mensagem nova, porque la nao ha edicao.
 *
 * @param text nulo so numa resposta a clique que nao tem o que dizer: o clique e
 *        respondido e nenhuma mensagem sai
 * @param actions linhas de botoes; vazio numa resposta que e so texto
 * @param origin identificador opaco do clique que esta resposta atende. Nulo
 *        quando a resposta e a uma mensagem de texto. Ninguem fora de
 *        <code>channel</code> o interpreta -- so o devolve aqui
 * @param replaceOrigin substituir a mensagem onde o botao estava, em vez de
 *        mandar uma nova. E pedido, nao ordem: o adaptador manda mensagem nova
 *        quando o canal nao edita, ou quando a mensagem ja nao pode ser editada
 * @param notice aviso curto ao clique ("Cafe ja saiu da lista"). Nulo quando
 *        nao ha o que avisar -- o clique e respondido do mesmo jeito
 */
public record ChatReply(String text,
                        List<List<ReplyAction>> actions,
                        String origin,
                        boolean replaceOrigin,
                        String notice) {

    /** Um botao: o rotulo que a pessoa le e a acao que o toque devolve. */
    public record ReplyAction(String label, ChatAction action) {
    }

    public ChatReply {
        actions = actions == null ? List.of() : actions.stream().map(List::copyOf).toList();
    }

    public static ChatReply of(String text, List<List<ReplyAction>> actions) {
        return new ChatReply(text, actions, null, false, null);
    }

    public static ChatReply text(String text) {
        return of(text, List.of());
    }

    public ChatReply inReplyTo(String clickOrigin) {
        return new ChatReply(text, actions, clickOrigin, replaceOrigin, notice);
    }

    public ChatReply replacingOrigin() {
        return new ChatReply(text, actions, origin, true, notice);
    }

    public ChatReply withNotice(String shortNotice) {
        return new ChatReply(text, actions, origin, replaceOrigin, shortNotice);
    }

    public boolean hasActions() {
        return !actions.isEmpty();
    }
}
