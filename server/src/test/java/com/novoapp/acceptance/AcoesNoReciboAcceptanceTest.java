package com.novoapp.acceptance;

import com.novoapp.support.PostgresTestResource;
import io.quarkiverse.cucumber.CucumberOptions;
import io.quarkiverse.cucumber.CucumberQuarkusTest;
import io.quarkus.test.common.TestResourceScope;
import io.quarkus.test.common.WithTestResource;
import org.junit.jupiter.api.Disabled;

/**
 * Desfazer e trocar categoria pelo recibo: os cenarios de
 * {@code financas-acoes-no-recibo.feature}, tag {@code @acoes-no-recibo}
 * (ADR-0041).
 *
 * <p>Nasce desabilitado em 2026-10-31, no dia em que a ADR foi aceita e antes de
 * qualquer codigo -- pelo mesmo motivo que o {@link AcoesNoChatAcceptanceTest}
 * nasceu assim: escopo que falta tem de ser visivel na propria suite, e nao so
 * no ROADMAP. O {@code @Disabled} sai no primeiro passo de codigo, trocado pela
 * tag {@code em-construcao}, para que os cenarios aparecam falhando por passo
 * indefinido desde o inicio e o placar suba passo a passo: primeiro o desfazer,
 * depois a troca de categoria.
 */
@Disabled("ADR-0041 aceita, sem codigo ainda -- ver ROADMAP, Desfazer e trocar categoria pelo recibo")
@CucumberOptions(
        features = "../docs/03-specs/features",
        glue = "com.novoapp.acceptance",
        tags = "@acoes-no-recibo",
        plugin = "pretty")
@WithTestResource(value = PostgresTestResource.class, scope = TestResourceScope.GLOBAL)
class AcoesNoReciboAcceptanceTest extends CucumberQuarkusTest {
}
