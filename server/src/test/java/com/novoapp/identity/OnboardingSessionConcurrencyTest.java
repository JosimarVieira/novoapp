package com.novoapp.identity;

import com.novoapp.common.tenancy.IdentityScoped;
import com.novoapp.identity.entity.OnboardingState;
import com.novoapp.identity.repository.OnboardingSessionRepository;
import com.novoapp.support.Fixtures;
import com.novoapp.support.PostgresTestResource;
import io.quarkus.test.common.TestResourceScope;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O Telegram entrega ate 40 mensagens em paralelo. Duas mensagens da mesma
 * pessoa chegando juntas viam ambas "nao existe sessao de onboarding" e
 * tentavam inserir; uma estourava violacao de unicidade e o processamento
 * morria. Aconteceu em producao no primeiro uso real, com mensagens que ficaram
 * na fila do Telegram enquanto a aplicacao estava fora.
 */
@QuarkusTest
@WithTestResource(value = PostgresTestResource.class, scope = TestResourceScope.GLOBAL)
class OnboardingSessionConcurrencyTest {

    /** Precisa de bean proprio: o escopo de tenancy vem de interceptor CDI. */
    @ApplicationScoped
    public static class SessionWriter {

        @Inject
        OnboardingSessionRepository sessions;

        @Transactional
        @IdentityScoped
        public void write(String externalId, OnboardingState state, String token) {
            sessions.save(Channel.TELEGRAM, externalId, state, token);
        }
    }

    @Inject
    Fixtures fixtures;

    @Inject
    SessionWriter writer;

    @BeforeEach
    void setUp() {
        fixtures.truncateAll();
    }

    @Test
    @DisplayName("gravar o mesmo passo duas vezes nao viola unicidade: atualiza a linha")
    void savingTwiceUpsertsInsteadOfFailing() {
        writer.write("700123", OnboardingState.AWAITING_CREATE_CONFIRMATION, null);
        writer.write("700123", OnboardingState.AWAITING_HOUSEHOLD_NAME, null);

        assertThat(fixtures.count("SELECT count(*) FROM onboarding_session")).isEqualTo(1);
        assertThat(fixtures.query("SELECT state FROM onboarding_session").get(0).get(0))
                .isEqualTo("AWAITING_HOUSEHOLD_NAME");
    }

    @Test
    @DisplayName("o token do convite tambem e atualizado no upsert")
    void keepsTheInviteTokenUpToDate() {
        writer.write("700124", OnboardingState.AWAITING_CREATE_CONFIRMATION, null);
        writer.write("700124", OnboardingState.AWAITING_SHARED_CONTACT, "convite-abc");

        assertThat(fixtures.query("SELECT invite_token FROM onboarding_session").get(0).get(0))
                .isEqualTo("convite-abc");
    }
}
