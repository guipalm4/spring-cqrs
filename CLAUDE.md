# spring-cqrs

Ledger financeiro de portfólio que evolui de modelo único (v1) para CQRS lógico (v2) e físico (v3),
medindo cada fase. O valor do projeto está na comparação honesta, não em "CQRS ganhar".

## Fontes de verdade (ler antes de decidir algo)

- `docs/SCOPE.md` — fases, domínio, consultas Q1–Q4, hipóteses H1–H9, marcos M0–M7 e M4.5.
- `docs/api/openapi.json` — contrato REST congelado; `ApiContractTests` compara com `/v3/api-docs`.
  Mudança intencional: `./mvnw verify -Dcontract.update=true` e revisar o diff.
- `docs/adr/` — decisões fechadas. **Não reabrir uma decisão sem escrever um ADR novo** que a substitua.
- Issue do marco no GitHub (`gh issue view <n>`) — critérios de pronto. Marcos M0–M7 = issues #1–#8; M4.5 = #17.
- `docs/diario.md` — o caminho até as decisões (o que acreditávamos, o que os dados mostraram, o que mudou, erros
  no meio). **Ao fim de toda sessão em que uma hipótese, um plano ou a leitura de um resultado mudou, uma entrada
  datada de 5 a 10 linhas, com links para a evidência.** Não repete os ADRs.

## Restrições inegociáveis

- Java 25 e Spring Boot 4.1 (Spring Framework 7). Recursos novos do Java só onde resolvem algo (ADR-0001).
- Plano gratuito como norte: nada que exija plano pago, nem como opcional.
- Benchmark justo (ADR-0005): mesmo contrato REST em todas as fases; baseline bem indexada; nunca
  medir através do gateway do LocalStack.

## Comandos

```bash
make verify          # build + testes (Testcontainers; precisa de Docker)
make up PHASE=v1     # sobe a fase e espera ficar saudável (v1 | v1-split | v1-big | v2 | v3 | v3-s5)
make smoke           # health da aplicação em :8080 (na v3, também a query-api em :8081)
make seed SCALE=1    # APAGA e carrega o dataset (1 = referência; 0.01 para testes manuais rápidos)
                     # na v3, o projetor carrega o Mongo a partir do ledger (MongoSeed), sem o rebuild da v2
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
  O registro de eventos do Modulith também consulta o banco na partida (`DatabaseType`), sem propriedade para
  evitar: `EventRegistryDatabaseType` fixa o bean em POSTGRES.
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
- Para testar uma fase sem apagar o dataset de outra, `COMPOSE_PROJECT_NAME=spring-cqrs-<x>` dá volumes próprios
  (o `make` e o `run.sh` respeitam). Pare a outra fase antes (`make down`): as portas são as mesmas.
- `docker-credential-desktop` pode travar (prompt do Keychain); imagens públicas baixam com
  `DOCKER_CONFIG` apontando para um `config.json` vazio.
- Picos de um segundo somem na média de 5 s do `timeseries.csv`: use o `seconds.csv` (percentis por segundo do
  Gatling). Os relatórios HTML em `bench/target` não são versionados.
- O I/O do `docker stats` tem 3 algarismos significativos (10 MB de resolução na faixa de GB): serve para comparar
  ordens de grandeza e saltos, não para bytes por comando no Postgres (passos de 100 MB).
- O Grafana a 512 MB pode bater no limite e fazer GBs de I/O no disco compartilhado da VM (21/10). Confira a linha
  dele no `docker-stats.csv` antes de atribuir uma cauda a uma fase.
- O MongoDB 8 sai na partida em kernels de 6.19 a 7.0.13 (SERVER-121912). O `verify` do CI fica no `ubuntu-22.04`
  por isso; o Docker Desktop local também pode chegar nessa faixa ao atualizar.

## Marco atual: M4.5 — estudo de capacidade (issue #17)

Medir capacidade, não latência a taxa fixa ([ADR-0013](docs/adr/0013-estudo-de-capacidade.md)): cada fase sobe uma
escada de carga e o resultado é o maior degrau sustentado (critérios absolutos e regra de parada no ADR, fixos). C1 =
pico de leitura (escrita a 20/s), C2 = escrita intensa com busca (leitura a 75/s), em dias diferentes. Fases: v1, v1-big,
v1-replica (nova: réplica streaming do Postgres, consultas na réplica), v2, v3; v3-s5 só no C1. As previsões P1–P7 já
estão escritas no ADR: conferir contra elas, sem ajustar a régua depois. Antes do primeiro dia: confirmar a causa da
cauda da v3 (S1 com o projetor parado, `pg_stat_wal`), Grafana desligado durante a medição, carga do host no `env.md`.
Sem ajuste do WiredTiger para suavizar rajadas (vantagem de configuração).
Lições do M4: longe da saturação a v3 só mostra custo fixo (3× memória, 2–5× CPU, Kafka > Postgres da v1) e uma cauda
nos comandos (p99 ~110 ms) correlacionada com rajadas de escrita do Mongo no disco compartilhado; a v3 lê mais caro
que a v2 em Q2/Q3 com o mesmo documento.
