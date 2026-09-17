package com.novoapp.acceptance;

import com.novoapp.support.PostgresTestResource;
import io.quarkiverse.cucumber.CucumberOptions;
import io.quarkiverse.cucumber.CucumberQuarkusTest;
import io.quarkus.test.common.TestResourceScope;
import io.quarkus.test.common.WithTestResource;

/**
 * Os cenarios do saneamento anterior a Etapa 3
 * (`PLANO-SANEAMENTO-PRE-ETAPA-3.md`): furos encontrados na auditoria de
 * 2026-09-14, cada um provado por um cenario que falha antes da correcao.
 *
 * <p>Tag propria, e nao <code>@etapa2</code>: aquela suite e o portao de uma
 * etapa fechada, e deixa-la vermelha por escopo novo apagaria a unica coisa que
 * ela sinaliza. Pelo mesmo motivo os cenarios daqui nao entram em
 * {@link Etapa1AcceptanceTest}, que seleciona por tag positiva justamente para
 * nao herdar escopo futuro por omissao.
 *
 * <p>Sem <code>@Disabled</code>: diferente do {@link Etapa2AcceptanceTest}, que
 * nasceu desabilitado porque anunciava uma etapa inteira por fazer, estes seis
 * cenarios nasceram junto com a correcao que eles cobrem -- nao houve intervalo
 * em que desabilitar significasse alguma coisa.
 *
 * <p>O custo dessa simultaneidade esta declarado: **nao foi observado o vermelho
 * antes do verde**. O Docker estava indisponivel na maquina onde o bloco foi
 * escrito, entao estes cenarios afirmam a regressao em vez de te-la demonstrado.
 * Quem quiser a prova reverte a correcao correspondente e roda so esta suite.
 */
@CucumberOptions(
        features = "../docs/03-specs/features",
        glue = "com.novoapp.acceptance",
        tags = "@saneamento",
        plugin = "pretty")
@WithTestResource(value = PostgresTestResource.class, scope = TestResourceScope.GLOBAL)
class SaneamentoAcceptanceTest extends CucumberQuarkusTest {
}
