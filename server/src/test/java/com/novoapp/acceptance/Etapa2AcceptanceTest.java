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
/*
 * A exclusao de @saneamento nao contradiz a selecao positiva defendida em
 * Etapa1AcceptanceTest: a base continua sendo uma tag positiva, com uma unica
 * excecao escrita. Ela existe porque a tag de `mercado-lista-de-compras.feature`
 * esta no nivel da Funcionalidade e e herdada por todo cenario novo do arquivo
 * -- e o cenario de acentuacao e saneamento posterior, nao escopo da Etapa 2a.
 * Sem isto, o portao de uma etapa fechada ficaria vermelho por trabalho que ela
 * nao prometeu.
 */
@CucumberOptions(
        features = "../docs/03-specs/features",
        glue = "com.novoapp.acceptance",
        tags = "@etapa2 and not @saneamento",
        plugin = "pretty")
@WithTestResource(value = PostgresTestResource.class, scope = TestResourceScope.GLOBAL)
class Etapa2AcceptanceTest extends CucumberQuarkusTest {
}
