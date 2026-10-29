package com.novoapp.channel.outbound;

/**
 * Recusa da Telegram Bot API, com a descricao que ela devolveu.
 *
 * <p>Existe para {@link TelegramMessageSender} distinguir as recusas que nao
 * sao erro -- ver {@link #isMessageNotModified()}.
 */
public class TelegramApiException extends RuntimeException {

    private final int status;
    private final String body;

    public TelegramApiException(int status, String body) {
        super("Telegram respondeu " + status + ": " + body);
        this.status = status;
        this.body = body;
    }

    public int status() {
        return status;
    }

    /**
     * A edicao deu exatamente o mesmo texto e teclado que a mensagem ja tinha:
     * toque duplo, ou clique que nao mudou nada. Para quem tocou, e sucesso.
     */
    public boolean isMessageNotModified() {
        return status == 400 && body != null && body.contains("message is not modified");
    }
}
