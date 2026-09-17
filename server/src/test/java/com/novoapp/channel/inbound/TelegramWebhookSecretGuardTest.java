package com.novoapp.channel.inbound;

import io.quarkus.runtime.LaunchMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A guarda que impede o webhook de subir aberto em producao.
 *
 * <p>Sem Quarkus e sem banco: o que se verifica e a regra, e ela e funcao pura
 * de (modo de execucao, segredo configurado). Subir a aplicacao de verdade em
 * modo de producao de dentro do build nao e possivel, e e por isso que a regra
 * mora num metodo separado do observador de <code>StartupEvent</code>.
 *
 * <p>Mora no mesmo pacote da classe testada, e nao em <code>channel</code> com
 * os demais testes de canal: o metodo e package-private de proposito -- nao e
 * API de ninguem -- e alcancar por reflexao so pra manter a convencao de pacote
 * seria esconder acoplamento atras de um truque.
 */
class TelegramWebhookSecretGuardTest {

    @Test
    @DisplayName("producao sem segredo nao sobe")
    void productionWithoutSecretFails() {
        assertThatThrownBy(() -> TelegramWebhookSecretGuard.verify(LaunchMode.NORMAL, Optional.empty()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TELEGRAM_WEBHOOK_SECRET");
    }

    /**
     * O SmallRye ja entrega <code>Optional.empty()</code> para valor vazio, mas
     * um segredo so de espaco viria como presente -- e seria tao aberto quanto
     * nenhum.
     */
    @Test
    @DisplayName("producao com segredo em branco nao sobe")
    void productionWithBlankSecretFails() {
        assertThatThrownBy(() -> TelegramWebhookSecretGuard.verify(LaunchMode.NORMAL, Optional.of("   ")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("producao com segredo sobe")
    void productionWithSecretStarts() {
        assertThatCode(() -> TelegramWebhookSecretGuard.verify(LaunchMode.NORMAL, Optional.of("s3cr3t")))
                .doesNotThrowAnyException();
    }

    /**
     * Desenvolvimento local nao tem webhook registrado no Telegram, entao nao ha
     * segredo combinado; e teste stubba o canal inteiro. Exigir o segredo nos
     * dois casos so ensinaria a contornar a guarda.
     */
    @Test
    @DisplayName("fora de producao o segredo continua opcional")
    void nonProductionModesStayOptional() {
        assertThatCode(() -> TelegramWebhookSecretGuard.verify(LaunchMode.DEVELOPMENT, Optional.empty()))
                .doesNotThrowAnyException();
        assertThatCode(() -> TelegramWebhookSecretGuard.verify(LaunchMode.TEST, Optional.empty()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a mensagem diz qual variavel definir e por que")
    void messageIsActionable() {
        assertThat(TelegramWebhookSecretGuard.MISSING_SECRET)
                .contains("TELEGRAM_WEBHOOK_SECRET")
                .contains("setWebhook");
    }
}
