package com.novoapp.acceptance;

import com.novoapp.support.PostgresTestResource;
import io.quarkiverse.cucumber.CucumberOptions;
import io.quarkiverse.cucumber.CucumberQuarkusTest;
import io.quarkus.test.common.TestResourceScope;
import io.quarkus.test.common.WithTestResource;

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
 * o inicio, e o placar subiu passo a passo -- sete com o desfazer, dezoito com a
 * troca de categoria. Enquanto a tag existiu, a suite rodou fora do portao do
 * CI, no passo informativo (`.github/workflows/ci.yml`).
 *
 * <p><b>A tag saiu em 2026-11-03, com os dezoito verdes.</b> Sem ela esta suite
 * integra o portao: uma regressao nos botoes do recibo -- o desfazer que volta a
 * estornar o mais recente, a troca que deixa de ir para o historico -- volta a
 * barrar merge.
 */
@CucumberOptions(
        features = "../docs/03-specs/features",
        glue = "com.novoapp.acceptance",
        tags = "@acoes-no-recibo",
        plugin = "pretty")
@WithTestResource(value = PostgresTestResource.class, scope = TestResourceScope.GLOBAL)
class AcoesNoReciboAcceptanceTest extends CucumberQuarkusTest {
}
