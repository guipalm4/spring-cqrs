# ADR-0001 — Stack base

- **Status:** Aceito
- **Data:** 2026-10-18

## Contexto

Requisitos inegociáveis: Java 25 usando recursos novos e Spring Boot 4.1 (sobre Spring Framework 7).
Spring Boot 4.1.0 está GA e suporta Java 17–26.

Risco: "usar recursos novos" pode virar uso forçado, o que um revisor nota e penaliza. Regra adotada:
cada recurso entra onde resolve um problema do projeto; preview fica fora do caminho principal.

## Decisão

| Camada | Escolha |
|---|---|
| Linguagem | Java 25 (LTS) |
| Framework | Spring Boot 4.1 / Spring Framework 7 |
| Build | Maven multi-módulo, BOM do Boot; versões pinadas no M0 |
| Escrita | PostgreSQL + Spring Data JDBC (sem JPA: controle explícito de SQL e locks) |
| Migrações | Flyway |
| Testes | JUnit 5, Testcontainers (conexão automática do Boot), ArchUnit ou Spring Modulith verify |
| API | REST + OpenAPI; mesmo contrato em todas as fases |
| Documentação da API | springdoc-openapi 3.x (Swagger UI) — linha compatível com Boot 4.1 |

### Recursos do Java 25 e onde entram

| Recurso | Status | Uso |
|---|---|---|
| Sealed interfaces + pattern matching em `switch` | Final | Hierarquia de comandos e eventos; projetor com `switch` exaustivo |
| Records | Final | Comandos, eventos, DTOs de consulta, `Money` |
| Flexible Constructor Bodies (JEP 513) | Final | Validação de value objects antes de `this(...)` |
| Scoped Values (JEP 506) | Final | Chave de idempotência e correlation id no lugar de `ThreadLocal` |
| Virtual threads | Final | `spring.threads.virtual.enabled`; efeito no pool Hikari é medido |
| Compact Source Files (JEP 512) | Final | Scripts utilitários (ex.: seeder) |
| Compact Object Headers (JEP 519) | Produto | Flag de JVM; comparação de memória no benchmark |
| AOT cache (JEP 514/515) | Final | Startup dos projetores |
| JFR Method Timing (JEP 520) | Final | Instrumentação de métodos críticos sem código |
| Structured Concurrency (JEP 505) | **Preview** | Stretch apenas |

### Recursos do Spring Boot 4 / Framework 7 relevantes

Null-safety com JSpecify, Jackson 3, `RestClient`, `spring-boot-starter-opentelemetry`,
Testcontainers com `ConnectionDetails` automáticos (Postgres, Kafka, Mongo).

### Versões verificadas (Maven Central, 2026-10-18)

| Componente | Versão | Compatibilidade |
|---|---|---|
| Spring Boot | 4.1.1 | — |
| Spring Modulith | 2.1.1 | Compilado contra Boot 4.1.1 |
| springdoc-openapi | 3.1.1 | Linha 3.1 atualizada para Boot 4.1 |
| Spring Data OpenSearch (starter) | 3.1.4 | Compilado contra Boot 4.1.1 |
| Gatling / plugin Maven | 3.16.0 / 4.21.12 | Suporta JDK LTS 11 a 25 |
| AWS SDK for Java v2 (BOM) | 2.56.0 | Independente do Spring |
| Spring Cloud AWS | **não usado** | Linha 4.x declara suporte só a Boot 4.0.x (ver ADR-0006) |

Spring Data MongoDB, Spring Kafka, Flyway e Testcontainers vêm geridos pelo BOM do Boot.

## Alternativas consideradas

- **JPA/Hibernate na escrita:** mais familiar, mas esconde SQL e locks — justamente o que o projeto
  quer tornar visível e medir.
- **Gradle:** equivalente; Maven escolhido por ser o padrão mais comum em times de finanças.

## Consequências

- Atualizações de versão seguem o BOM do Boot; bibliotecas fora dele só sobem quando houver release
  compilada contra a mesma linha do Boot.
- Spring Data JDBC exige escrever mais SQL à mão; aceito, é parte da proposta.
