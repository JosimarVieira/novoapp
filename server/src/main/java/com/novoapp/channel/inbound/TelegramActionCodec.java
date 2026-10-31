package com.novoapp.channel.inbound;

import com.novoapp.common.message.ChatAction;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * <code>callback_data</code> &lt;-&gt; {@link ChatAction} (ADR-0040, ADR-0041,
 * sdd-modulo-channel.md).
 *
 * <p><b>O unico lugar que conhece os codigos curtos.</b> {@code c+}, {@code c-},
 * {@code rm}, {@code rt}, {@code fx} na lista e {@code dz}, {@code es},
 * {@code vt} no recibo nao existem fora daqui -- se <code>conversation</code>
 * precisasse ler um deles, a regra 5 do CLAUDE.md estaria furada pelo lado de
 * dentro, e o ArchUnit nao pegaria: e string, nao import.
 *
 * <p>O Telegram limita o <code>callback_data</code> a 64 bytes. A referencia de
 * item continua em forma textual -- codigo, dois-pontos e UUID de 36 caracteres,
 * 39 no maior caso --, porque ha listas com botoes no Telegram da familia e botao
 * ja enviado tem de continuar valendo. A de lancamento vai em base64url, 22
 * caracteres (ADR-0041, item 9): e o que deixa espaco para o botao que leva duas
 * referencias. O decodificador distingue as duas formas pelo codigo.
 */
public final class TelegramActionCodec {

    /** Limite da Bot API para <code>callback_data</code>, em bytes. */
    public static final int MAX_BYTES = 64;

    private static final String SEPARATOR = ":";

    /** Um UUID tem 16 bytes; em base64url sem preenchimento, 22 caracteres. */
    private static final int COMPACT_LENGTH = 22;

    private static final Map<ChatAction.Type, String> CODES = Map.of(
            ChatAction.Type.ADD_TO_CART, "c+",
            ChatAction.Type.REMOVE_FROM_CART, "c-",
            ChatAction.Type.REMOVE_ITEM, "rm",
            ChatAction.Type.RESTORE_ITEM, "rt",
            ChatAction.Type.CLOSE_CART, "fx",
            ChatAction.Type.UNDO, "dz",
            ChatAction.Type.REVERSE, "es",
            ChatAction.Type.BACK, "vt");

    private TelegramActionCodec() {
    }

    public static String encode(ChatAction action) {
        String code = CODES.get(action.type());
        String data = action.reference() == null ? code : code + SEPARATOR + reference(action.reference());
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
        String[] parts = data.split(SEPARATOR, -1);
        for (Map.Entry<ChatAction.Type, String> entry : CODES.entrySet()) {
            if (!entry.getValue().equals(parts[0])) {
                continue;
            }
            ChatAction.Type type = entry.getKey();
            if (type.reference() == null) {
                return parts.length == 1 ? Optional.of(ChatAction.closeCart()) : Optional.empty();
            }
            if (parts.length != 2) {
                return Optional.empty();
            }
            return switch (type.reference()) {
                case LIST_ITEM -> textual(parts[1]).map(itemId -> ChatAction.onItem(type, itemId));
                case TRANSACTION -> compact(parts[1]).map(transactionId -> ChatAction.onTransaction(type, transactionId));
            };
        }
        return Optional.empty();
    }

    private static String reference(ChatAction.Reference reference) {
        return switch (reference.kind()) {
            case LIST_ITEM -> reference.id().toString();
            case TRANSACTION -> compact(reference.id());
        };
    }

    private static Optional<UUID> textual(String reference) {
        try {
            return Optional.of(UUID.fromString(reference));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    private static String compact(UUID id) {
        ByteBuffer bytes = ByteBuffer.allocate(16);
        bytes.putLong(id.getMostSignificantBits());
        bytes.putLong(id.getLeastSignificantBits());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.array());
    }

    /**
     * So a forma que {@link #compact(UUID)} produz: 22 caracteres que voltam a
     * ser exatamente eles. Base64 admite mais de uma escrita para os mesmos
     * bytes; aceitar so a canonica e o que mantem "nao casa, descarta" valendo
     * para dado forjado.
     */
    private static Optional<UUID> compact(String reference) {
        if (reference.length() != COMPACT_LENGTH) {
            return Optional.empty();
        }
        try {
            ByteBuffer bytes = ByteBuffer.wrap(Base64.getUrlDecoder().decode(reference));
            if (bytes.remaining() != 16) {
                return Optional.empty();
            }
            UUID id = new UUID(bytes.getLong(), bytes.getLong());
            return compact(id).equals(reference) ? Optional.of(id) : Optional.empty();
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }
}
