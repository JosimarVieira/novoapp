package com.novoapp.nlu;

import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O retry de pipeline do adaptador.
 *
 * <p>Nao da pra cobrir por cenario de aceitacao: o stub de LLM substitui
 * justamente a classe onde esta politica mora, entao um cenario exercitaria o
 * stub. Aqui se testa a regra pura, com as excecoes tipadas reais do
 * LangChain4j 1.19.
 *
 * <p>O que se prova aqui e a decisao, e nao a rede: quantas vezes se tenta, e
 * quando nao se tenta de novo. O caso que mais importa e o 429 -- a biblioteca o
 * classifica como retriavel e nos nao, e sem teste essa divergencia deliberada
 * viraria um <code>instanceof</code> que alguem "conserta" seis meses depois.
 */
class MistralRetryPolicyTest {

    private static final Duration NO_WAIT = Duration.ZERO;

    @Test
    @DisplayName("falha transitoria e tentada de novo, dentro do limite")
    void retriesTransientFailure() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> MistralMessageInterpreter.callWithRetry(() -> {
            calls.incrementAndGet();
            throw new TimeoutException("provedor demorou");
        }, 2, NO_WAIT)).isInstanceOf(TimeoutException.class);

        assertThat(calls).hasValue(2);
    }

    @Test
    @DisplayName("sucesso na segunda tentativa nao propaga a falha da primeira")
    void succeedsOnSecondAttempt() {
        AtomicInteger calls = new AtomicInteger();

        ChatResponse response = MistralMessageInterpreter.callWithRetry(() -> {
            if (calls.incrementAndGet() == 1) {
                throw new InternalServerException("502");
            }
            return null;
        }, 2, NO_WAIT);

        assertThat(calls).hasValue(2);
        assertThat(response).isNull();
    }

    @Test
    @DisplayName("429 nao e repetido, apesar de a biblioteca classifica-lo como retriavel")
    void doesNotRetryRateLimit() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> MistralMessageInterpreter.callWithRetry(() -> {
            calls.incrementAndGet();
            throw new RateLimitException("429");
        }, 2, NO_WAIT)).isInstanceOf(RateLimitException.class);

        assertThat(calls)
                .as("insistir num 429 gasta cota do tier gratuito para tomar outro 429")
                .hasValue(1);
    }

    @Test
    @DisplayName("defeito nosso nao e repetido")
    void doesNotRetryOurOwnFault() {
        assertThat(MistralMessageInterpreter.worthRetrying(new InvalidRequestException("schema"))).isFalse();
        assertThat(MistralMessageInterpreter.worthRetrying(new AuthenticationException("chave"))).isFalse();
        assertThat(MistralMessageInterpreter.worthRetrying(new RateLimitException("429"))).isFalse();
    }

    @Test
    @DisplayName("excecao de fora da hierarquia do provedor nao e repetida")
    void doesNotRetryUnknownFailure() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> MistralMessageInterpreter.callWithRetry(() -> {
            calls.incrementAndGet();
            throw new IllegalStateException("nao se sabe o que e isto");
        }, 2, NO_WAIT)).isInstanceOf(IllegalStateException.class);

        assertThat(calls).hasValue(1);
    }

    @Test
    @DisplayName("uma tentativa configurada desliga o retry sem caso especial")
    void singleAttemptDisablesRetry() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> MistralMessageInterpreter.callWithRetry(() -> {
            calls.incrementAndGet();
            throw new TimeoutException("provedor demorou");
        }, 1, NO_WAIT)).isInstanceOf(TimeoutException.class);

        assertThat(calls).hasValue(1);
    }
}
