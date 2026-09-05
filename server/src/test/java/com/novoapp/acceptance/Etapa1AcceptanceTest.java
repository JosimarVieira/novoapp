package com.novoapp.acceptance;

import com.novoapp.support.PostgresTestResource;
import io.quarkiverse.cucumber.CucumberOptions;
import io.quarkiverse.cucumber.CucumberQuarkusTest;
import io.quarkus.test.common.TestResourceScope;
import io.quarkus.test.common.WithTestResource;

/**
 * Aceitacao: o Gherkin de `docs/03-specs/features` rodando contra a aplicacao
 * inteira, do webhook ao Postgres.
 *
 * <p>Os arquivos ficam em <code>docs/</code>, nao copiados pra dentro do modulo:
 * "o Gherkin e a fonte de verdade; o teste de aceitacao implementa o Gherkin,
 * nao o contrario" (CLAUDE.md). Copia viraria duas verdades.
 *
 * <p>O filtro exclui <code>@etapa2</code> -- ver {@link Etapa2AcceptanceTest}.
 */
@CucumberOptions(
        features = {
                "../docs/03-specs/features/financas-lancamento-por-chat.feature",
                "../docs/03-specs/features/vinculo-de-identidade.feature"
        },
        glue = "com.novoapp.acceptance",
        tags = "not @etapa2",
        plugin = "pretty")
@WithTestResource(value = PostgresTestResource.class, scope = TestResourceScope.GLOBAL)
class Etapa1AcceptanceTest extends CucumberQuarkusTest {
}
