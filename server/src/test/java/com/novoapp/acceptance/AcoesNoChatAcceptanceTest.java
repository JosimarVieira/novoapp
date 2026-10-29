package com.novoapp.acceptance;

import com.novoapp.support.PostgresTestResource;
import io.quarkiverse.cucumber.CucumberOptions;
import io.quarkiverse.cucumber.CucumberQuarkusTest;
import io.quarkus.test.common.TestResourceScope;
import io.quarkus.test.common.WithTestResource;
import org.junit.jupiter.api.Tag;

/**
 * Botoes na lista de compras: os cenarios de
 * {@code mercado-botoes-na-lista.feature}, tag {@code @acoes-no-chat}
 * (ADR-0040).
 *
 * <p>Nasceu desabilitado em 2026-10-29, no dia em que a ADR foi aceita e antes de
 * qualquer codigo -- pelo mesmo motivo que o {@link Etapa3AcceptanceTest} nasceu
 * assim: escopo que falta tem de ser visivel na propria suite, e nao so no
 * ROADMAP.
 *
 * <p>O {@code @Disabled} saiu no primeiro commit de codigo, trocado pela tag
 * {@code em-construcao}: os cenarios aparecem falhando por passo indefinido desde
 * o inicio, o placar sobe cenario a cenario, e a suite fica fora do portao do CI
 * enquanto isso, num passo informativo (`.github/workflows/ci.yml`) -- a mesma
 * disciplina da Etapa 3. A tag sai quando os quinze cenarios estiverem verdes,
 * e ai esta suite entra no portao.
 */
@Tag("em-construcao")
@CucumberOptions(
        features = "../docs/03-specs/features",
        glue = "com.novoapp.acceptance",
        tags = "@acoes-no-chat",
        plugin = "pretty")
@WithTestResource(value = PostgresTestResource.class, scope = TestResourceScope.GLOBAL)
class AcoesNoChatAcceptanceTest extends CucumberQuarkusTest {
}
