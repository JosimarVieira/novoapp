package com.novoapp.acceptance;

import com.novoapp.support.PostgresTestResource;
import io.quarkiverse.cucumber.CucumberOptions;
import io.quarkiverse.cucumber.CucumberQuarkusTest;
import io.quarkus.test.common.TestResourceScope;
import io.quarkus.test.common.WithTestResource;

/**
 * O elo lista -> despesa: os nove cenarios de
 * {@code elo-fechamento-de-compra.feature}, que materializam o diferencial do
 * produto (CLAUDE.md). Cenario cortado deste arquivo tira a razao de existir do
 * produto -- esta escrito no topo do proprio {@code .feature}.
 *
 * <p>Nasceu desabilitado em 2026-09-19 e o {@code @Disabled} saiu em 2026-09-21,
 * com a {@code V7} -- no <b>primeiro</b> passo de codigo da etapa, e nao no
 * ultimo, pelo mesmo motivo que na Etapa 2a: assim os nove cenarios aparecem
 * falhando por passo indefinido desde o inicio, e o placar sobe cenario a
 * cenario.
 *
 * <p><b>A Etapa 3 fechou em 2026-09-21, e a tag {@code em-construcao} saiu
 * junto.</b> Enquanto ela existiu, esta suite ficava vermelha de proposito e
 * rodava num passo informativo do CI, fora do portao
 * (`.github/workflows/ci.yml`). Sem a tag, ela integra o portao: uma regressao
 * no elo volta a barrar merge, que e o ponto de fechar a etapa.
 *
 * <p>A tag existiu por tres dias e nasceu de um acidente: o primeiro push com a
 * suite ligada derrubou o job inteiro e levou junto o {@code docker build} --
 * que existe por causa de uma regressao que ja quebrou o deploy duas vezes. Ela
 * nao se chamava {@code etapa3} de proposito: na Etapa 2b a tag muda de classe,
 * em vez de acumular uma por etapa.
 *
 * <p>A tag sozinha nunca deu cobertura ({@code 03-specs/README.md}): ela torna
 * os cenarios selecionaveis. Esta classe e o que faltava para que "os nove
 * cenarios do elo" deixe de ser uma frase e passe a ser um numero.
 *
 * <h2>Catorze cenarios, e nao nove</h2>
 * Os nove do elo, mais dois abertos dentro da etapa pela ADR-0038
 * (<code>acucar 20</code>) e tres pela ADR-0039 (remover item) -- as decisoes
 * abertas #23 e #25, que o ROADMAP mandava resolver por dentro. Os tres de
 * remocao moram em {@code mercado-lista-de-compras.feature}, que e
 * {@code @etapa2}, e levam {@code @etapa3} no cenario: e o que os traz para ca
 * sem pintar de vermelho o portao de uma etapa fechada.
 *
 * <p>O passo {@code que o registro de despesas esta indisponivel} era o mais
 * importante dos novos, e foi o mais instrutivo: escreve-lo expos que
 * {@code ShoppingService} chamava <code>finance</code> antes de tocar em item
 * nenhum, e que o cenario de falha passaria <b>por ordenacao</b>, sem exercitar
 * a transacao uma vez sequer. Ver {@link CheckoutSteps} e
 * {@code sdd-modulo-shopping.md}.
 */
@CucumberOptions(
        features = "../docs/03-specs/features",
        glue = "com.novoapp.acceptance",
        tags = "@etapa3",
        plugin = "pretty")
@WithTestResource(value = PostgresTestResource.class, scope = TestResourceScope.GLOBAL)
class Etapa3AcceptanceTest extends CucumberQuarkusTest {
}
