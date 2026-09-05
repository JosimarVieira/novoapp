package com.novoapp.support;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

/**
 * Postgres de verdade, nunca H2 (estrategia-de-testes.md): RLS e a coisa mais
 * importante a testar aqui e simplesmente nao existe em banco em memoria.
 *
 * <p>O container sobe com um superusuario, usado so pelo Flyway e pelas fixtures
 * -- e de proposito que ele ignora RLS: e assim que o teste de vazamento
 * consegue montar dois households e depois conferir o que cada papel enxerga. A
 * aplicacao mesma conecta como <code>novoapp_runtime</code>, que e papel comum
 * e NOINHERIT, exatamente como em producao.
 */
public class PostgresTestResource implements QuarkusTestResourceLifecycleManager {

    /**
     * NULLS NOT DISTINCT no indice unico de category exige Postgres 15+; 16 e a
     * versao que a aplicacao declara em <code>quarkus.datasource.db-version</code>.
     */
    private static final DockerImageName IMAGE = DockerImageName.parse("postgres:16-alpine");

    private static final String ADMIN_USER = "novoapp_admin";
    private static final String ADMIN_PASSWORD = "admin";
    private static final String RUNTIME_PASSWORD = "runtime";

    private PostgreSQLContainer<?> container;

    @Override
    public Map<String, String> start() {
        container = new PostgreSQLContainer<>(IMAGE)
                .withDatabaseName("novoapp")
                .withUsername(ADMIN_USER)
                .withPassword(ADMIN_PASSWORD);
        container.start();

        String url = container.getJdbcUrl();
        return Map.of(
                "quarkus.datasource.admin.jdbc.url", url,
                "quarkus.datasource.admin.username", ADMIN_USER,
                "quarkus.datasource.admin.password", ADMIN_PASSWORD,
                "quarkus.flyway.admin.placeholders.runtimepwd", RUNTIME_PASSWORD,
                "quarkus.datasource.jdbc.url", url,
                "quarkus.datasource.username", "novoapp_runtime",
                "quarkus.datasource.password", RUNTIME_PASSWORD);
    }

    @Override
    public void stop() {
        if (container != null) {
            container.stop();
        }
    }
}
