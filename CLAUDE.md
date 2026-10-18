# spring-cqrs

Ledger financeiro de portfólio que evolui de modelo único (v1) para CQRS lógico (v2) e físico (v3),
medindo cada fase. O valor do projeto está na comparação honesta, não em "CQRS ganhar".

## Fontes de verdade (ler antes de decidir algo)

- `docs/SCOPE.md` — fases, domínio, consultas Q1–Q4, hipóteses H1–H7, marcos M0–M7.
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
3. Ao terminar: `make verify` + `make up`/`smoke` locais → PR com `Closes #<issue>` → CI verde.
4. Squash merge, apagar a branch, `git tag -a mN`, push da tag, marcar os checkboxes da issue.
5. Atualizar a seção "Marco atual" abaixo para o próximo marco.

## Armadilhas já resolvidas

- Boot 4 tem starters modulares: `spring-boot-starter-webmvc`, `spring-boot-starter-flyway` etc.
- Testcontainers 2: a classe é `org.testcontainers.postgresql.PostgreSQLContainer` (sem genérico).
- O Dockerfile treina o AOT cache **sem banco** (`spring.context.exit=onRefresh`, Flyway desligado).
  Qualquer bean que conecte ao banco durante o refresh quebra o build da imagem. Ao adicionar Spring
  Data JDBC, fixar `spring.data.jdbc.dialect=postgresql` para ele não consultar o banco na partida.
- O AOT cache só vale com as mesmas flags da JVM do treino: flags vão em `JVM_OPTS` (build arg e env).
- A imagem `eclipse-temurin:25-jre` não tem curl nem wget; o healthcheck do compose usa `/dev/tcp` do bash.
- O health do Actuator só lista componentes com `management.endpoint.health.show-components: always`.

## Marco atual: M1 — v1 funcional (issue #2)

Comandos (`OpenAccount`, `Deposit`, `Withdraw`, `Transfer`) com idempotência e partida dobrada;
consultas Q1–Q4 na `app-baseline`; seeder; OpenAPI + Swagger UI (springdoc 3.1.x). Detalhes do domínio
e das consultas em `docs/SCOPE.md`. O aviso "No migrations found" do Flyway some quando o schema do M1 entrar.
