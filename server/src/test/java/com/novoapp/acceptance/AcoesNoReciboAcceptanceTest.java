package com.novoapp.acceptance;

import com.novoapp.support.PostgresTestResource;
import io.quarkiverse.cucumber.CucumberOptions;
import io.quarkiverse.cucumber.CucumberQuarkusTest;
import io.quarkus.test.common.TestResourceScope;
import io.quarkus.test.common.WithTestResource;
import org.junit.jupiter.api.Tag;

/**
 * Desfazer e trocar categoria pelo recibo: os cenarios de
 * {@code financas-acoes-no-recibo.feature}, tag {@code @acoes-no-recibo}
 * (ADR-0041).
 *
 * <p>Nasceu desabilitado em 2026-10-31, no dia em que a ADR foi aceita e antes de
 * qualquer codigo -- pelo mesmo motivo que o {@link AcoesNoChatAcceptanceTest}
 * nasceu assim: escopo que falta tem de ser visivel na propria suite, e nao so
 * no ROADMAP.
 *
 * <p>O {@code @Disabled} saiu no primeiro commit de codigo, trocado pela tag
 * {@code em-construcao}: os cenarios aparecem falhando por passo indefinido desde
 * o inicio, o placar sobe passo a passo -- primeiro o desfazer, depois a troca de
 * categoria --, e a suite fica fora do portao do CI enquanto isso, num passo
 * informativo (`.github/workflows/ci.yml`). A tag sai quando os dezoito cenarios
 * estiverem verdes, e ai esta suite entra no portao.
 */
@Tag("em-construcao")
@CucumberOptions(
        features = "../docs/03-specs/features",
        glue = "com.novoapp.acceptance",
        tags = "@acoes-no-recibo",
        plugin = "pretty")
@WithTestResource(value = PostgresTestResource.class, scope = TestResourceScope.GLOBAL)
class AcoesNoReciboAcceptanceTest extends CucumberQuarkusTest {
}
