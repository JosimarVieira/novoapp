package com.novoapp.acceptance;

import com.novoapp.support.PostgresTestResource;
import io.quarkiverse.cucumber.CucumberOptions;
import io.quarkiverse.cucumber.CucumberQuarkusTest;
import io.quarkus.test.common.TestResourceScope;
import io.quarkus.test.common.WithTestResource;

/**
 * Os cenarios que a Etapa 1 deliberadamente nao entrega: ambiguidade entre
 * categorias, criacao de categoria por chat, valor ausente, desfazer, e a
 * emissao de convite pelo OWNER.
 *
 * <p>Nasceu desabilitado, pra que o escopo que faltava ficasse visivel no
 * proprio suite. O {@code @Disabled} saiu no primeiro passo de codigo da Etapa
 * 2a, e nao no ultimo: assim os 22 cenarios aparecem falhando por passo
 * indefinido desde o inicio, e o placar sobe cenario a cenario.
 */
@CucumberOptions(
        features = "../docs/03-specs/features",
        glue = "com.novoapp.acceptance",
        tags = "@etapa2",
        plugin = "pretty")
@WithTestResource(value = PostgresTestResource.class, scope = TestResourceScope.GLOBAL)
class Etapa2AcceptanceTest extends CucumberQuarkusTest {
}
