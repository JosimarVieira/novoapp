# JDK e Maven fixados na imagem, e nao escolhidos pelo builder da plataforma.
#
# O Nixpacks instalava jdk21 no ambiente, mas o pacote maven do nixpkgs traz o
# proprio JDK (19) e compila com ele -- o javac que rodava era o 19, e o 19 nao
# conhece "release 21". Aqui nao ha o que adivinhar: a imagem ja e a versao
# certa, e o build e o mesmo na maquina de quem programa e no deploy.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# O pom sozinho primeiro: a camada de dependencias so e refeita quando ele muda.
COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline

COPY src ./src
# Os testes nao rodam aqui: sobem um Postgres via Testcontainers e nao ha Docker
# dentro do build. Quem barra codigo quebrado e o CI (estrategia-de-testes.md).
RUN mvn -B -DskipTests package


FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Nada aqui precisa de root.
RUN groupadd --system novoapp && useradd --system --gid novoapp novoapp

# Layout do quarkus-app: lib/ primeiro, porque muda menos que app/.
COPY --from=build --chown=novoapp:novoapp /build/target/quarkus-app/lib/ ./lib/
COPY --from=build --chown=novoapp:novoapp /build/target/quarkus-app/*.jar ./
COPY --from=build --chown=novoapp:novoapp /build/target/quarkus-app/app/ ./app/
COPY --from=build --chown=novoapp:novoapp /build/target/quarkus-app/quarkus/ ./quarkus/

USER novoapp

ENV QUARKUS_PROFILE=prod
# A porta vem de PORT, injetada pela plataforma (%prod.quarkus.http.port).
EXPOSE 8080

# A rede privada da Railway responde em IPv6; sem isto a resolucao do host do
# Postgres falha de um jeito dificil de diagnosticar.
ENTRYPOINT ["java", "-Djava.net.preferIPv6Addresses=true", "-jar", "quarkus-run.jar"]
