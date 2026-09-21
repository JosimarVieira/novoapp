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
 * <p><b>Esta suite fica vermelha ate a etapa fechar, e isso e o sinal, nao um
 * defeito.</b> Quem rodar {@code mvn test} durante a Etapa 3 ve o que falta.
 *
 * <p>A tag sozinha nunca deu cobertura ({@code 03-specs/README.md}): ela torna
 * os cenarios selecionaveis. Esta classe e o que faltava para que "os nove
 * cenarios do elo" deixe de ser uma frase e passe a ser um numero.
 *
 * <h2>O que ja existe de glue, e o que falta</h2>
 * Levantado em 2026-09-19, antes de escrever qualquer codigo da etapa: dos 42
 * passos distintos do arquivo, <b>18 ja sao atendidos</b> por
 * {@link ExpenseByChatSteps} e {@link ShoppingListSteps} -- enviar mensagem,
 * assertar despesa registrada, consultar a lista, desfazer, reentrega. Os 24
 * restantes sao novos, e quase todos sao sobre o que so passa a existir agora:
 * item mudando de status em lote, o {@code list_checkout} ligando os dois lados,
 * fechamento parcial, e a falha atomica.
 *
 * <p>O passo {@code que o registro de despesas esta indisponivel} e o mais
 * importante dos 24: sem ele o cenario de falha nao prova a atomicidade que a
 * ADR-0031 decidiu, que e o terceiro dos quatro testes obrigatorios da
 * estrategia-de-testes.md -- e o unico que nunca existiu.
 */
@CucumberOptions(
        features = "../docs/03-specs/features",
        glue = "com.novoapp.acceptance",
        tags = "@etapa3",
        plugin = "pretty")
@WithTestResource(value = PostgresTestResource.class, scope = TestResourceScope.GLOBAL)
class Etapa3AcceptanceTest extends CucumberQuarkusTest {
}
