# syntax=docker/dockerfile:1
# Imagem de qualquer aplicação do reactor: docker build --build-arg MODULE=app-baseline .
# Decisões em docs/adr/0007-ambiente-de-execucao.md.

FROM maven:3.9-eclipse-temurin-25 AS build
ARG MODULE
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -q -pl ${MODULE} -am package -DskipTests \
 && cp ${MODULE}/target/${MODULE}-*.jar /workspace/application.jar

FROM eclipse-temurin:25-jre AS extract
WORKDIR /builder
COPY --from=build /workspace/application.jar application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

FROM eclipse-temurin:25-jre
# Flags da JVM entram no treino do AOT cache e na execução: o cache só vale com as mesmas flags.
ARG JVM_OPTS=""
ENV JVM_OPTS=${JVM_OPTS}
RUN useradd --system --uid 10001 app
WORKDIR /application
COPY --from=extract /builder/extracted/dependencies/ ./
COPY --from=extract /builder/extracted/spring-boot-loader/ ./
COPY --from=extract /builder/extracted/snapshot-dependencies/ ./
COPY --from=extract /builder/extracted/application/ ./
# Treino sem banco: Flyway desligado e o pool do Hikari só conecta no primeiro uso.
RUN java ${JVM_OPTS} -XX:AOTCacheOutput=app.aot \
        -Dspring.context.exit=onRefresh -Dspring.flyway.enabled=false \
        -jar application.jar \
 && chown -R app /application
USER app
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JVM_OPTS -XX:AOTCache=app.aot -jar application.jar"]
