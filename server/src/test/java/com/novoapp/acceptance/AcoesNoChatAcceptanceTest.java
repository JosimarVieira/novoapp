package com.novoapp.acceptance;

import com.novoapp.support.PostgresTestResource;
import io.quarkiverse.cucumber.CucumberOptions;
import io.quarkiverse.cucumber.CucumberQuarkusTest;
import io.quarkus.test.common.TestResourceScope;
import io.quarkus.test.common.WithTestResource;

/**
 * Botoes na lista de compras: os quinze cenarios de
 * {@code mercado-botoes-na-lista.feature}, tag {@code @acoes-no-chat}
 * (ADR-0040).
 *
 * <p>Nasceu desabilitado em 2026-10-29, no dia em que a ADR foi aceita e antes de
 * qualquer codigo -- pelo mesmo motivo que o {@link Etapa3AcceptanceTest} nasceu
 * assim: escopo que falta tem de ser visivel na propria suite, e nao so no
 * ROADMAP.
 *
 * <p>O {@code @Disabled} saiu no primeiro commit de codigo, trocado pela tag
 * {@code em-construcao}: os cenarios apareceram falhando por passo indefinido, e
 * o placar subiu passo a passo da implementacao -- seis com remover, onze com o
 * carrinho, quinze com o fechamento. Enquanto a tag existiu, a suite rodou fora
 * do portao do CI, no passo informativo (`.github/workflows/ci.yml`).
 *
 * <p><b>A tag saiu em 2026-10-29, com os quinze verdes.</b> Sem ela esta suite
 * integra o portao: uma regressao nos botoes -- e, por eles, no elo -- volta a
 * barrar merge.
 */
@CucumberOptions(
        features = "../docs/03-specs/features",
        glue = "com.novoapp.acceptance",
        tags = "@acoes-no-chat",
        plugin = "pretty")
@WithTestResource(value = PostgresTestResource.class, scope = TestResourceScope.GLOBAL)
class AcoesNoChatAcceptanceTest extends CucumberQuarkusTest {
}
