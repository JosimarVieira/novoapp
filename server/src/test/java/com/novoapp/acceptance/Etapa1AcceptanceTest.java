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
 * <p>Selecao positiva por <code>@etapa1</code>, nao <code>not @etapa2</code>.
 * Com selecao negativa, todo cenario novo que nao fosse <code>@etapa2</code>
 * cairia aqui por padrao -- inclusive os <code>@etapa3</code> do elo, se algum
 * dia a lista de arquivos acima virar o diretorio inteiro. Uma etapa fechada
 * nao deve herdar escopo futuro por omissao. Ver {@link Etapa2AcceptanceTest}.
 */
@CucumberOptions(
        features = {
                "../docs/03-specs/features/financas-lancamento-por-chat.feature",
                "../docs/03-specs/features/vinculo-de-identidade.feature"
        },
        glue = "com.novoapp.acceptance",
        tags = "@etapa1",
        plugin = "pretty")
@WithTestResource(value = PostgresTestResource.class, scope = TestResourceScope.GLOBAL)
class Etapa1AcceptanceTest extends CucumberQuarkusTest {
}
