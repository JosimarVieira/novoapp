package com.novoapp.acceptance;

import com.novoapp.support.PostgresTestResource;
import io.quarkiverse.cucumber.CucumberOptions;
import io.quarkiverse.cucumber.CucumberQuarkusTest;
import io.quarkus.test.common.TestResourceScope;
import io.quarkus.test.common.WithTestResource;
import org.junit.jupiter.api.Disabled;

/**
 * Botoes na lista de compras: os cenarios de
 * {@code mercado-botoes-na-lista.feature}, tag {@code @acoes-no-chat}
 * (ADR-0040).
 *
 * <p>Nasce desabilitado em 2026-10-29, no dia em que a ADR foi aceita e antes de
 * qualquer codigo -- pelo mesmo motivo que o {@link Etapa3AcceptanceTest} nasceu
 * assim: escopo que falta tem de ser visivel na propria suite, e nao so no
 * ROADMAP. O {@code @Disabled} sai no primeiro passo de codigo, para que os
 * cenarios aparecam falhando por passo indefinido desde o inicio e o placar suba
 * cenario a cenario.
 *
 * <p>Quando sair, esta suite ficara vermelha por algum tempo. Antes de liga-la,
 * decidir se ela entra no portao do CI ou num passo informativo, como a Etapa 3
 * fez com a tag {@code em-construcao}.
 */
@Disabled("ADR-0040 aceita, sem codigo ainda -- ver ROADMAP, Botoes na lista de compras")
@CucumberOptions(
        features = "../docs/03-specs/features",
        glue = "com.novoapp.acceptance",
        tags = "@acoes-no-chat",
        plugin = "pretty")
@WithTestResource(value = PostgresTestResource.class, scope = TestResourceScope.GLOBAL)
class AcoesNoChatAcceptanceTest extends CucumberQuarkusTest {
}
