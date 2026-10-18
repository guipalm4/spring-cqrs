# ADR-0003 — Stores de leitura: MongoDB e OpenSearch

- **Status:** Aceito
- **Data:** 2026-10-18

## Contexto

O lado de escrita permanece no PostgreSQL (ACID, constraints, locks — padrão de mercado para ledger).
O lado de leitura tem duas naturezas de consulta:

- **Formato fixo** (Q2 extrato, Q3 resumo mensal): a tela é conhecida; o ganho vem de um documento já
  no formato da resposta.
- **Busca livre** (Q4): texto em descrição/contraparte com filtros combinados; o ganho vem de índice
  invertido e relevância.

## Decisão

- **MongoDB** para Q2 e Q3 (M4). Documento por conta/mês com lançamentos embutidos e totais por
  categoria pré-calculados.
- **OpenSearch** para Q4 (M5), como segunda projeção independente, alimentada pelo mesmo fluxo de
  eventos com consumer group próprio.
- Até o M5, Q4 na v3 é servida por text index do MongoDB — de propósito, para medir store genérico ×
  store especializado.

## Alternativas consideradas

| Opção | Por que não (como principal) |
|---|---|
| Elasticsearch | Equivalente técnico ao OpenSearch; OpenSearch evita discussão de licença e é o oferecido como serviço gerenciado na AWS |
| Cassandra/ScyllaDB | Comum para extrato em altíssimo volume, mas o ganho só aparece em escala distribuída, inviável de demonstrar em notebook |
| ClickHouse | OLAP para BI/analytics, não read model de tela de cliente |
| Redis | Cache, não read model principal |
| Réplica de leitura Postgres | Não muda o modelo — serve como alternativa a discutir no ADR "quando NÃO usar" |

## Consequências

- Demonstra o ponto mais forte do CQRS: **um modelo de escrita, N modelos de leitura**, cada um no store
  adequado à consulta.
- Duas projeções com **atrasos independentes**: um lançamento pode aparecer na busca antes do extrato.
  Documentar e medir; não esconder.
- OpenSearch é pesado (JVM, ~1–2 GB de heap). Fica em profile próprio do compose e com limite de
  memória declarado no benchmark.
- Contraponto aceito: para este volume, Postgres bem indexado pode empatar em Q4. Se acontecer,
  publica-se.
