package com.novoapp.identity;

/**
 * Meio de entrada da mensagem (glossario).
 *
 * <p>Vive em <code>identity</code> porque quem guarda o par
 * <code>(channel, external_id)</code> e <code>channel_identity</code>. Isso nao
 * contradiz a regra nao negociavel 5 do CLAUDE.md: a regra proibe codigo abaixo
 * de <code>channel</code> <em>decidir</em> alguma coisa em funcao do canal, nao
 * proibe a chave de identidade registrar de onde a pessoa fala -- sem isso o
 * mesmo telefone no WhatsApp e no Telegram seria a mesma linha.
 */
public enum Channel {
    TELEGRAM,
    WHATSAPP,
    /** ADR-0021, Etapa 4. Nao passa por webhook. */
    WEB
}
