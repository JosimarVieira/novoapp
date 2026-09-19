package com.novoapp.channel.inbound;

import com.novoapp.common.i18n.MessageKey;
import com.novoapp.common.i18n.Messages;
import com.novoapp.identity.Channel;
import com.novoapp.support.Fixtures;
import com.novoapp.support.PostgresTestResource;
import com.novoapp.support.StubOutboundMessagePort;
import io.quarkus.test.common.TestResourceScope;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Falha nascida <b>antes</b> do orquestrador ainda responde no chat
 * (sdd-modulo-channel.md, secao "Erros").
 *
 * <p>Achado em 2026-09-18 <b>lendo o codigo</b>, investigando uma mensagem que
 * parecia ter ficado sem resposta em producao. O log de ingestao mostrou depois
 * que aquela mensagem estava <code>EXECUTED</code> -- o silencio era do recorte
 * do transcript, e o caso que motivou a investigacao era falso. O furo nao: o
 * <code>ConversationOrchestrator</code> captura a falha dele proprio e responde
 * recibo de erro, e o <code>InboundDispatcher</code> confiava nisso, mas
 * ninguem respondia pela falha de <code>resolveContext</code>, do onboarding ou
 * do proprio log de ingestao. Silencio e o desfecho que o
 * <code>sdd-visao-geral.md</code> proibe em uma linha: quem nao recebe nada
 * reenvia, e pode duplicar o que ja gravou.
 *
 * <p>Sem caso de producao, este teste e a unica coisa que sustenta a correcao.
 * Ele foi visto vermelho antes de verde.
 *
 * <p>A falha e forcada pelo caminho mais proximo do real que nao precisa de
 * mock: um <code>messageId</code> que nao existe em
 * <code>inbound_message</code>. E exatamente o que acontece quando
 * <code>attachHousehold</code> nao encontra a linha -- o primeiro passo dentro
 * de <code>execute</code>, antes de qualquer interpretacao.
 */
@QuarkusTest
@WithTestResource(value = PostgresTestResource.class, scope = TestResourceScope.GLOBAL)
class InboundPipelineFailureTest {

    private static final String EXTERNAL_ID = "700000555";

    @Inject
    InboundPipeline pipeline;

    @Inject
    Fixtures fixtures;

    @Inject
    StubOutboundMessagePort outbound;

    @BeforeEach
    void setUp() {
        fixtures.truncateAll();
        outbound.clear();

        UUID householdId = fixtures.insertHousehold("Silva");
        fixtures.insertWallet(householdId);
        UUID memberId = fixtures.insertMember("Ana", null);
        fixtures.insertMembership(householdId, memberId, "OWNER");
        fixtures.insertChannelIdentity(memberId, "TELEGRAM", EXTERNAL_ID, householdId);
    }

    @Test
    @DisplayName("falha antes do orquestrador responde recibo de erro, e nao silencio")
    void failureBeforeOrchestratorStillAnswers() {
        NormalizedInbound inbound = new NormalizedInbound(Channel.TELEGRAM, EXTERNAL_ID, "Ana",
                "1:1", "comprei arroz", null);

        pipeline.process(UUID.randomUUID(), inbound);

        assertThat(outbound.lastTextTo(EXTERNAL_ID))
                .isEqualTo(Messages.get(Messages.DEFAULT, MessageKey.FAILURE));
    }
}
