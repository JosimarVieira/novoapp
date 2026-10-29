package com.novoapp.channel.inbound;

import com.novoapp.common.message.ChatAction;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * <code>callback_data</code> &lt;-&gt; {@link ChatAction} (ADR-0040,
 * sdd-modulo-channel.md).
 *
 * <p><b>O unico lugar que conhece os codigos curtos.</b> {@code c+}, {@code c-},
 * {@code rm}, {@code rt} e {@code fx} nao existem fora daqui -- se
 * <code>conversation</code> precisasse ler um deles, a regra 5 do CLAUDE.md
 * estaria furada pelo lado de dentro, e o ArchUnit nao pegaria: e string, nao
 * import.
 *
 * <p>O Telegram limita o <code>callback_data</code> a 64 bytes. Codigo de duas
 * letras, dois-pontos e um UUID de 36 caracteres: 39 no maior caso.
 */
public final class TelegramActionCodec {

    /** Limite da Bot API para <code>callback_data</code>, em bytes. */
    public static final int MAX_BYTES = 64;

    private static final Map<ChatAction.Type, String> CODES = Map.of(
            ChatAction.Type.ADD_TO_CART, "c+",
            ChatAction.Type.REMOVE_FROM_CART, "c-",
            ChatAction.Type.REMOVE_ITEM, "rm",
            ChatAction.Type.RESTORE_ITEM, "rt",
            ChatAction.Type.CLOSE_CART, "fx");

    private TelegramActionCodec() {
    }

    public static String encode(ChatAction action) {
        String code = CODES.get(action.type());
        String data = action.itemId() == null ? code : code + ":" + action.itemId();
        if (data.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            // Nao acontece com os tipos de hoje; acontecer seria tipo novo com
            // referencia longa, e o Telegram recusaria a mensagem inteira.
            throw new IllegalStateException("callback_data passa de " + MAX_BYTES + " bytes: " + data);
        }
        return data;
    }

    /**
     * Vazio para o que nao casa com codigo nenhum -- botao de uma versao antiga
     * do bot, ou dado forjado. Quem chama trata como trata {@code Update}
     * desconhecido: descarta.
     */
    public static Optional<ChatAction> decode(String data) {
        if (data == null || data.isBlank()) {
            return Optional.empty();
        }
        int separator = data.indexOf(':');
        String code = separator < 0 ? data : data.substring(0, separator);
        String reference = separator < 0 ? null : data.substring(separator + 1);

        for (Map.Entry<ChatAction.Type, String> entry : CODES.entrySet()) {
            if (!entry.getValue().equals(code)) {
                continue;
            }
            ChatAction.Type type = entry.getKey();
            if (!type.needsItem()) {
                return reference == null ? Optional.of(ChatAction.closeCart()) : Optional.empty();
            }
            return itemId(reference).map(itemId -> new ChatAction(type, itemId));
        }
        return Optional.empty();
    }

    private static Optional<UUID> itemId(String reference) {
        if (reference == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(reference));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }
}
