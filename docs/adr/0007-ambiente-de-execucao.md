# ADR-0007 — Ambiente de execução e containers

- **Status:** Aceito
- **Data:** 2026-10-18

## Contexto

Todo o projeto roda em containers: infraestrutura via docker compose, testes via Testcontainers,
camada AWS via LocalStack. Isso estava implícito; aqui fica explícito, junto com os limites de
recursos que afetam o benchmark.

Máquina de referência: Apple M1 Pro, 8 núcleos, 16 GB de RAM; Docker Desktop com 8 CPUs e ~11,7 GB
para a VM. A v3 completa consome ~9–11 GB.

## Decisão

1. **Runtime:** qualquer runtime compatível com Docker Engine + Compose v2 (Docker Desktop, OrbStack,
   Colima). Todos os citados são gratuitos para uso pessoal. Os resultados publicados usam
   **Docker Desktop**, e o runtime faz parte do registro de hardware de cada execução (ADR-0005).
2. **Imagens multi-arquitetura:** só imagens com variante `arm64` e `amd64`, para rodar em Apple
   Silicon e no CI (GitHub Actions, `amd64`).
3. **Imagens das aplicações:** Dockerfile multi-stage próprio sobre `eclipse-temurin:25-jre`, com jar
   em camadas e etapa de treino do AOT cache (JEP 514/515). Buildpacks descartados: escondem as flags
   da JVM, e o benchmark compara justamente flags (Compact Object Headers, AOT cache).
4. **Profiles do compose:**

   | Profile | Sobe | Uso |
   |---|---|---|
   | `v1` | Postgres, `app-baseline`, observabilidade | Fase 1 |
   | `v2` | Postgres, `app-cqrs`, observabilidade | Fase 2 |
   | `v3` | Postgres, Kafka, Mongo, `command-api`, `query-api`, projetor, observabilidade | Fase 3 |
   | `search` | OpenSearch + projetor de busca | Somado ao `v3` a partir do M5 |
   | `aws` | LocalStack | M7, nunca durante benchmark |

   Separar o OpenSearch (`search`) mantém os cenários S1–S5 da v3 dentro da memória disponível; ele
   só entra nas execuções que respondem H6.
5. **Gatling roda no host, fora da VM do Docker.** Assim não disputa a memória da VM. Disputa CPU
   com ela, o que fica registrado como limitação do ambiente.
6. **Limites declarados:** todo container tem `cpus` e `mem_limit` no compose; a soma por profile
   fica abaixo da memória da VM com folga de ~1 GB.

## Alternativas consideradas

- **Buildpacks (`spring-boot:build-image`):** menos arquivo para manter, mas tira o controle das flags
  da JVM que o benchmark mede.
- **Kubernetes local (kind, k3d):** escala de réplicas mais realista, mas fora de escopo e com custo de
  memória que não cabe na máquina de referência.
- **Rodar tudo sem containers:** inviável de reproduzir por terceiros.

## Consequências

- Latências no macOS incluem o overhead da VM do Docker; comparações valem **entre fases na mesma
  máquina**, não como números absolutos.
- Quem tiver menos de 16 GB de RAM roda v1–v3 sem o profile `search`, ou com o fator de escala do
  seeder reduzido (resultado rotulado, conforme ADR-0005).
