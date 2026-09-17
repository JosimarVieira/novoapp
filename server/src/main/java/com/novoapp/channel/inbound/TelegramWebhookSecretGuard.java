package com.novoapp.channel.inbound;

import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;

/**
 * Recusa subir em producao sem o segredo do webhook.
 *
 * <p>Sem esta guarda, esquecer <code>TELEGRAM_WEBHOOK_SECRET</code> nao falha,
 * nao loga e nao avisa: {@link TelegramWebhookResource} simplesmente pula a
 * verificacao quando o segredo esta vazio, e quem descobrir a URL passa a
 * conseguir injetar mensagem com o identificador externo de qualquer pessoa --
 * que e a identidade inteira do sistema (despesa, convite, estorno).
 *
 * <p>Falha fechada e no boot, e nao na primeira requisicao, pelo mesmo motivo
 * que o papel de banco de login e NOINHERIT (ADR-0003): configuracao de
 * isolamento esquecida tem que quebrar na hora, nunca vazar em silencio.
 *
 * <p>So vale em producao. Desligar a verificacao em desenvolvimento local
 * continua legitimo -- nao ha segredo combinado com o Telegram quando nao ha
 * webhook registrado -- e em teste o canal inteiro e stubbado.
 */
@ApplicationScoped
public class TelegramWebhookSecretGuard {

    static final String MISSING_SECRET = """
            novoapp.channel.telegram.webhook-secret esta vazio em producao. \
            Sem ele o webhook aceita entrega de qualquer origem, em nome de qualquer pessoa. \
            Defina TELEGRAM_WEBHOOK_SECRET com o mesmo valor passado ao setWebhook do Telegram.""";

    @ConfigProperty(name = "novoapp.channel.telegram.webhook-secret")
    Optional<String> webhookSecret;

    void onStart(@Observes StartupEvent event) {
        verify(LaunchMode.current(), webhookSecret);
    }

    /**
     * Separado do observador para ser exercitavel sem subir a aplicacao em modo
     * de producao -- o que um teste nao consegue fazer de dentro do mesmo build.
     */
    static void verify(LaunchMode mode, Optional<String> secret) {
        if (mode != LaunchMode.NORMAL) {
            return;
        }
        if (secret.filter(value -> !value.isBlank()).isPresent()) {
            return;
        }
        throw new IllegalStateException(MISSING_SECRET);
    }
}
