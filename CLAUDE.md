# spring-cqrs

Ledger financeiro de portfólio que evolui de modelo único (v1) para CQRS lógico (v2) e físico (v3),
medindo cada fase. O valor do projeto está na comparação honesta, não em "CQRS ganhar".

## Fontes de verdade (ler antes de decidir algo)

- `docs/SCOPE.md` — fases, domínio, consultas Q1–Q4, hipóteses H1–H7, marcos M0–M7.
- `docs/api/openapi.json` — contrato REST congelado; `ApiContractTests` compara com `/v3/api-docs`.
  Mudança intencional: `./mvnw verify -Dcontract.update=true` e revisar o diff.
- `docs/adr/` — decisões fechadas. **Não reabrir uma decisão sem escrever um ADR novo** que a substitua.
- Issue do marco no GitHub (`gh issue view <n>`) — critérios de pronto. Marcos M0–M7 = issues #1–#8.

## Restrições inegociáveis

- Java 25 e Spring Boot 4.1 (Spring Framework 7). Recursos novos do Java só onde resolvem algo (ADR-0001).
- Plano gratuito como norte: nada que exija plano pago, nem como opcional.
- Benchmark justo (ADR-0005): mesmo contrato REST em todas as fases; baseline bem indexada; nunca
  medir através do gateway do LocalStack.

## Comandos

```bash
make verify          # build + testes (Testcontainers; precisa de Docker)
make up PHASE=v1     # sobe a fase e espera ficar saudável (v1 | v2)
make smoke           # health da aplicação em :8080
make seed SCALE=1    # APAGA e carrega o dataset (1 = referência; 0.01 para testes manuais rápidos)
make bench PHASE=v1  # benchmark da fase no ar (~1h20 com SCALE=1) -> docs/benchmarks/<data>-<fase>/
make down            # make clean também apaga o volume do Postgres
```

O JDK 25 vem do `.tool-versions` (asdf). Não setar `JAVA_HOME` para outro JDK.

## Convenções de código

- Pacotes `io.github.guipalm4.ledger.<módulo>`; todo pacote tem `package-info.java` com `@NullMarked`.
- `ledger-domain` não depende de Spring nem de persistência.
- Persistência de escrita com Spring Data JDBC e SQL explícito — sem JPA (ADR-0001).
- Migrações Flyway por módulo de aplicação (`src/main/resources/db/migration`).
- Testes de integração com Testcontainers via `PostgresTestcontainer` (`@ServiceConnection`).
- Identificadores em inglês; documentação, comentários e mensagens de commit em português.
- Antes de adicionar uma dependência fora do BOM do Boot, confirmar no Maven Central que a versão foi
  compilada contra Boot 4.1 e registrar no ADR-0001.

## Fluxo de marco

1. Mover o card do marco para *In Progress* no board (GitHub Project #5 de `guipalm4`).
2. Branch `mN-<nome>` a partir da `main`.
3. Escrever o capítulo do marco em "A história" no README: pergunta, evidência (gráfico gerado pelo
   `bench/Report.java`, não desenhado à mão), decisão, custo e limites, com links para os dados. O marco não
   fecha sem ele; resultado desfavorável ao CQRS entra igual.
4. Ao terminar: `make verify` + `make up`/`smoke` locais → PR com `Closes #<issue>` → CI verde.
5. Squash merge, apagar a branch, `git tag -a mN`, push da tag, marcar os checkboxes da issue.
6. Atualizar a seção "Marco atual" abaixo para o próximo marco.

## Armadilhas já resolvidas

- Boot 4 tem starters modulares: `spring-boot-starter-webmvc`, `spring-boot-starter-flyway` etc.
- Testcontainers 2: a classe é `org.testcontainers.postgresql.PostgreSQLContainer` (sem genérico).
- O Dockerfile treina o AOT cache **sem banco** (`spring.context.exit=onRefresh`, Flyway desligado).
  Qualquer bean que conecte ao banco durante o refresh quebra o build da imagem. Ao adicionar Spring
  Data JDBC, fixar `spring.data.jdbc.dialect=postgresql` para ele não consultar o banco na partida.
- O AOT cache só vale com as mesmas flags da JVM do treino: flags vão em `JVM_OPTS` (build arg e env).
- A imagem `eclipse-temurin:25-jre` não tem curl nem wget; o healthcheck do compose usa `/dev/tcp` do bash.
- O health do Actuator só lista componentes com `management.endpoint.health.show-components: always`.
- Heap da JVM: sem `-XX:MaxRAMPercentage` a JVM usa 25% do container. A flag vai em `x-jvm-opts` do
  compose (build arg e env), senão o AOT cache não vale.
- `make bench` exige o dataset de referência e leva ~1h20; feche outros containers da VM antes. O working
  set é maior que o cache: o estado do cache muda o resultado, então nunca calibrar e medir na mesma sessão
  sem recarregar o seed.
- Métricas `*_max` do Micrometer (`hikaricp_connections_acquire_seconds_max`, `jvm_gc_pause_seconds_max`) decaem em
  janelas e herdam o valor da execução anterior. Usar totais (`increase` de `_sum`), como o `gc_pause_total_s`.
- Gatling 3.16 não gera `stats.json`; `bench/Report.java` extrai a tabela do `index.html`.
- O Prometheus não tem volume: séries somem com o container. O `run.sh` exporta `timeseries.csv` na hora.
- v1 e v2 têm históricos do Flyway diferentes no mesmo volume: trocar de fase exige `make clean` + `make up` +
  `make seed`. Na v2, o `make seed` também reconstrói as projeções (`bench/seed/rebuild.sql`).
- A latência de ler uma página fora do `shared_buffers` (`read_ms / shared_blks_read` no `pg_stat_statements`) variou de
  4 ms a 0,2 ms entre execuções da mesma v1 e decide se ela colapsa (6, 3 e 0 em 9). Informar junto de toda comparação.
- O `pg_stat_statements.csv` guarda 400 caracteres por instrução: na v2, Q2 e Q4 têm o mesmo começo e aparecem somados.
- `docker-credential-desktop` pode travar (prompt do Keychain); imagens públicas baixam com
  `DOCKER_CONFIG` apontando para um `config.json` vazio.

## Marco atual: M4 — v3, CQRS físico (issue #5)

`command-api` (Postgres) e `query-api` (MongoDB) em containers separados, sincronizados por outbox (Spring Modulith)
→ Kafka → projetor. O documento do Mongo é o bloco conta/mês da v2 (ADR-0011), então da v2 para a v3 mudam só o
armazenamento e a consistência. Pronto quando os resultados v1×v2×v3 estão em `docs/benchmarks/`, H3, H5 e H7 respondidas
e há trace ponta a ponta no Grafana. **Escopo congelado até o M6 (ADR-0011).**
Lições até o M3: o colapso da v1 depende de leituras lentas (com o disco rápido, nem a v1 colapsa); a v2 lê 3–8 vezes
menos páginas fora do cache e custa +40% por comando no banco (2,9 ms e 104 KB de WAL num bloco cheio). No colapso, o
canal é a JVM compartilhada (heap e GC), não só o pool (ADR-0010): é isso que a v3 precisa mostrar que resolve. Medir
v1, v2 e v3 no mesmo dia, cada uma com o seed recarregado, e ler "colapsou em k de 9" ao lado do tempo por página lida.
