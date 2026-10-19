# ADR-0009 — Revisão da baseline v1 e parâmetros do benchmark

- **Status:** Aceito
- **Data:** 2026-10-19

## Contexto

O ADR-0005 exige uma baseline com índices revisados e `EXPLAIN ANALYZE` publicado, e a `app-baseline`
congela no fim do M2. A investigação sobre o dataset de referência (100 mil contas, ~20 milhões de
lançamentos, tabela de 7,2 GB) achou quatro fraquezas que não são do modelo único, e sim de configuração:

| Problema | Evidência | Efeito |
|---|---|---|
| Estatística de `account_id` com o alvo padrão (100) | O planner estimava 638 linhas numa conta empresarial com 7.759 | Q4 por texto escolhia bitmap da conta inteira + filtro: **3,7 s a frio** |
| GIN do Q4 só sobre o texto | Termo raro cruzava o bitmap da conta com 245 mil linhas do termo; bitmap *lossy* com `work_mem` de 4 MB | 783 ms a frio, 43 mil linhas rechecadas |
| Postgres com a configuração padrão | `shared_buffers` de 128 MB num container de 2 GB; `random_page_cost` de disco giratório | Cache pequeno e custos irreais para SSD |
| JVM sem `MaxRAMPercentage` | Heap de 256 MB num container de 1 GB | Sob sobrecarga, `OutOfMemoryError` e a aplicação não se recupera |

Os lançamentos de uma conta ficam espalhados pela tabela: os ids crescem com o tempo, e cada página de
8 KB tem ~15 lançamentos de contas empresariais. Ler uma conta empresarial toca páginas por todo o heap,
e o cache disponível (≤ 2 GB, o limite do container conta também o page cache) não comporta a tabela.
**Isso é o modelo único, e fica como está:** é o problema que a v2 deve atacar.

## Decisão

### Baseline (migração `V2__baseline_review.sql` e `infra/compose.yaml`)

1. `ALTER TABLE ledger_entry ALTER COLUMN account_id SET STATISTICS 2000`: a lista de valores mais comuns
   passa a incluir as contas empresariais (1.470 valores; `n_distinct` estimado em 95 mil, contra 100 mil
   reais).
2. **GIN compostos com `btree_gin`:** `(account_id, search_vector)` e `(account_id, counterparty_name
   gin_trgm_ops)` substituem os GIN só de texto. O Q4 é sempre por conta (ADR-0008), então os globais
   ficam redundantes. O índice devolve só as linhas da conta que casam.
3. **Postgres dimensionado para o container** (2 CPUs, 2 GB), igual em todas as fases: `shared_buffers`
   512 MB, `effective_cache_size` 1,5 GB, `work_mem` 16 MB, `maintenance_work_mem` 256 MB,
   `random_page_cost` 1.1, `track_io_timing` ligado.
4. **JVM com `-XX:MaxRAMPercentage=75`** nas aplicações, no treino do AOT cache e na execução (ADR-0007).
5. **Pool do Hikari explícito em 10 conexões por instância**, em todas as fases. Na v1 e na v2, leitura e
   escrita dividem o pool; na v3, `command-api` e `query-api` têm um pool cada (mais recursos, contados
   no custo).

Efeito medido a frio, em contas empresariais ainda não lidas:

| Q4 | Antes | Só estatística | Estatística + GIN composto |
|---|---:|---:|---:|
| Termo comum (`supermercado`) | 3.727 ms | 418 ms | 498 ms |
| Termo raro (`sushi kaze`) | — | 783 ms | 218 ms |
| Contraparte (`%nonna%`) | — | 475 ms | 194 ms |

### Parâmetros do benchmark (`bench/`, `make bench`)

- **Modelo aberto** (`constantUsersPerSec`): a taxa de chegada não depende da latência, e a fila aparece
  na medição (sem *coordinated omission*). Um pool de conexões HTTP compartilhado no Gatling.
- **Uma simulação, três cenários:** S1 só escrita, S2 só leitura e S3 escrita constante com leitura
  variando entre execuções.
- **Taxas fixas para todas as fases:** escrita a 20 comandos/s; leitura a 25, 50 e 75 consultas/s, para
  a comparação ser no mesmo ponto de carga. A calibração, com cache aquecido por execuções anteriores,
  indicava saturação de leitura entre 50 e 100/s. Na execução oficial, com o dataset recém-carregado, o
  joelho ficou entre 25 e 50/s ([resultados](../benchmarks/2026-10-19-v1/README.md)). As taxas não foram
  reajustadas depois, para não escolhê-las pelo resultado.
- **Mistura:** comandos com 30% depósito, 55% saque e 15% transferência (a do seed); consultas Q1–Q4 com o
  mesmo peso; metade das requisições em contas empresariais e metade em pessoa física, com o segmento no
  nome da requisição (ADR-0008).
- **Sorteio com semente fixa por repetição:** cada repetição lê outras contas, e a mesma repetição lê as
  mesmas contas em todas as fases.
- **Aquecimento** de 5 minutos, descartado. **3 repetições de 3 minutos**, intercaladas entre os
  cenários, para a deriva do cache e do autovacuum se espalhar. Reporta-se a mediana e a faixa.
- **Saída versionada:** tabelas extraídas do relatório do Gatling, `pg_stat_statements`, `docker stats` e
  métricas do Prometheus por execução, mais `env.md` e `summary.md`. Os relatórios HTML completos (~1 MB
  cada) ficam em `bench/target/`, fora do Git.

## Alternativas consideradas

- **`CLUSTER` da tabela por conta:** resolveria a localidade no dataset carregado, mas exige lock
  exclusivo, se desfaz com as escritas e não é prática sustentável num ledger em produção. Daria à v1 uma
  vantagem que ela não teria fora do benchmark.
- **Particionar `ledger_entry` por hash de conta:** reduz o tamanho de cada índice, mas não muda a
  dispersão dos lançamentos de uma conta dentro da partição.
- **Índice de cobertura para o Q2** (todas as colunas em `INCLUDE`): duplicaria a tabela no índice.
- **Só aumentar `work_mem`:** resolve o bitmap *lossy*, mas não a estimativa errada nem a leitura da
  conta inteira.
- **Pools separados para leitura e escrita na v1:** mitigaria a fila no pool, que é o principal canal de
  interferência observado ([resultados do M2](../benchmarks/2026-10-19-v1/README.md)). **Não entra na v1**, que é medida como um time a
  implantaria por padrão. Por ser o contra-argumento mais forte ao H3, a variante será medida como
  controle antes do M3, sem alterar o padrão da baseline.

## Consequências

- Escrita mais cara: dois GIN compostos (278 MB + 737 MB) mantidos a cada lançamento; o custo aparece no S1.
- A `app-baseline` congela com estas otimizações. A v2 e a v3 herdam o mesmo Postgres e o mesmo pool por
  instância.
- Sem contrapressão (virtual threads aceitam requisições sem limite e todas esperam o pool), a v1 colapsa
  acima da saturação em vez de recusar carga. Fica registrado como comportamento, não corrigido.
- Com o working set maior que o cache, os resultados dependem do estado do cache: aquecimento longo,
  repetições intercaladas e dispersão publicada são obrigatórios.
