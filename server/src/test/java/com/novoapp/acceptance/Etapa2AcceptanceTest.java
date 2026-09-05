package com.novoapp.acceptance;

import com.novoapp.support.PostgresTestResource;
import io.quarkiverse.cucumber.CucumberOptions;
import io.quarkiverse.cucumber.CucumberQuarkusTest;
import io.quarkus.test.common.TestResourceScope;
import io.quarkus.test.common.WithTestResource;
import org.junit.jupiter.api.Disabled;

/**
 * Os cenarios que a Etapa 1 deliberadamente nao entrega: ambiguidade entre
 * categorias, criacao de categoria por chat, valor ausente, desfazer, e a
 * emissao de convite pelo OWNER.
 *
 * <p>Fica desabilitado, e nao apagado nem filtrado em silencio, pra que o
 * escopo que falta seja visivel no proprio suite. Habilitado hoje, ele falha
 * com passo indefinido -- e exatamente o que deve acontecer: nao ha
 * {@code PendingAction} nem politica de confianca media (ADR-0004) ainda.
 *
 * <p>Tirar o {@code @Disabled} e o primeiro passo da Etapa 2.
 */
@Disabled("Cenários @etapa2: exigem PendingAction e a política de confiança média/baixa (ADR-0004). ROADMAP, Etapa 2.")
@CucumberOptions(
        features = "../docs/03-specs/features",
        glue = "com.novoapp.acceptance",
        tags = "@etapa2",
        plugin = "pretty")
@WithTestResource(value = PostgresTestResource.class, scope = TestResourceScope.GLOBAL)
class Etapa2AcceptanceTest extends CucumberQuarkusTest {
}
