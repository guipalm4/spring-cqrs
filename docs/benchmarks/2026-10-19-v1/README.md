# v1 — 2026-10-19: evidência do problema (H1)

Baseline revisada (ADR-0009), dataset de referência recém-carregado (100 mil contas, 20,3 milhões de
lançamentos), Apple M1 Pro / Docker Desktop, outros containers parados. Ambiente completo em
[env.md](env.md), números agregados em [summary.md](summary.md), planos em [explain.md](explain.md).

Escrita constante de 20 comandos/s; leitura de 0 (S1), 25, 50 e 75 consultas/s (S3) e só leitura a 75/s
(S2). 3 repetições de 3 minutos, intercaladas, depois de 5 minutos de aquecimento.

## Resposta ao H1

> *Sob carga mista, leituras Q2/Q3 degradam a latência de escrita na v1.*

**Confirmada, com duas correções no enunciado:**

1. **O canal é o pool de conexões, não o Postgres.** Dentro do banco, um comando custa 3–8 ms em qualquer
   cenário; no cliente, o p95 vai de 44 ms (sem leitura) a 23–44 s (com leitura a ≥ 50/s). Mais de 99% da
   degradação é espera por uma das 10 conexões do Hikari, que as consultas limitadas por I/O seguram.
2. **A leitura que pesa é o Q4, não Q2/Q3.** O Q4 consome 58–81% do tempo de leitura no banco, o Q2
   14–30% e o Q3 3–12%.

E um terceiro achado: **a degradação não é gradual, é metaestável.** Acima de ~25 consultas/s, cada
execução cai em um de dois estados: estável ou colapso, com fila de milhares de requisições e falhas.
Com leitura a 50 ou 75/s, colapsou em 6 de 9 execuções; a 25/s, em nenhuma.

## Comandos por execução

Estado *colapso*: p95 global acima de 5 s, critério fixo para todas as fases (ADR-0009); o `summary.md` classifica
cada execução e conta os colapsos por cenário. "Espera no pool" é a média de todas as requisições (comandos e
consultas); "comando no banco" soma o tempo médio das cinco instruções de um comando no
`pg_stat_statements` (INSERT do lançamento, `SELECT … FOR UPDATE`, UPDATE do saldo, INSERT e UPDATE da
idempotência).

| Cenário | Rep. | p50 | p95 | p99 | % KO | Espera média no pool | Comando no banco | Estado |
|---|---|---:|---:|---:|---:|---:|---:|---|
| s1 | r1 | 10 | 35 | 123 | 0.0 | 0.4 ms | 3.5 ms | estável |
| s1 | r2 | 9 | 44 | 297 | 0.0 | 1.7 ms | 5.9 ms | estável |
| s1 | r3 | 11 | 53 | 288 | 0.0 | 0.6 ms | 6.7 ms | estável |
| s3-r025 | r1 | 7 | 42 | 240 | 0.0 | 1.8 ms | 2.9 ms | estável |
| s3-r025 | r2 | 8 | 483 | 1,259 | 0.0 | 51.6 ms | 4.2 ms | estável |
| s3-r025 | r3 | 7 | 66 | 700 | 0.0 | 12.4 ms | 3.4 ms | estável |
| s3-r050 | r1 | 821 | 23,476 | 30,010 | 1.2 | 6,352.3 ms | 8.1 ms | colapso |
| s3-r050 | r2 | 5,223 | 29,859 | 30,494 | 6.8 | 9,432.1 ms | 7.8 ms | colapso |
| s3-r050 | r3 | 8 | 254 | 819 | 0.0 | 33.2 ms | 4.3 ms | estável |
| s3-r075 | r1 | 935 | 44,268 | 48,547 | 20.4 | 12,126.1 ms | 7.0 ms | colapso |
| s3-r075 | r2 | 26 | 2,090 | 2,958 | 0.0 | 394.0 ms | 4.6 ms | estável |
| s3-r075 | r3 | 9 | 30,335 | 34,766 | 12.0 | 6,903.1 ms | 5.1 ms | colapso |

Latências em ms, medidas no Gatling e incluindo requisições com KO. Só leitura (S2, 75/s): colapso em r1
(p95 33 s, 23% KO) e r2 (p95 22 s), estável em r3 (p95 2,7 s).

O que a tabela mostra:

- **Abaixo da saturação (25/s), a interferência já existe, mas é moderada:** o p50 não muda, o p95 sobe
  de 35–53 ms para 42–483 ms e o p99 de 123–297 ms para 240–1.259 ms. O tempo do comando no banco não
  sobe; o que sobe é a espera no pool.
- **No colapso, comando e consulta têm o mesmo destino**, porque esperam na mesma fila: o p95 dos
  comandos fica igual ao das consultas (ver [summary.md](summary.md)). O tempo do comando no banco no
  máximo dobra (até 8 ms, por I/O e WAL disputados), irrelevante diante de segundos de fila.
- **As falhas são da fila:** na maioria, HTTP 500 pelo timeout de 30 s do Hikari para obter conexão; em
  quatro execuções, também conexões fechadas com a requisição pendente (`Premature close`, 21–49% das
  falhas, ver limitações); e poucos timeouts de 60 s do Gatling.

## Mecanismo

1. Os lançamentos de uma conta estão espalhados pela tabela de 7,2 GB: cada página tem ~15 lançamentos
   de contas empresariais (ADR-0009). Ler uma conta empresarial toca páginas por todo o heap, e o cache
   (≤ 2 GB) não comporta a tabela. Hit ratio de 95–97% no `shared_buffers` ainda deixa 150–590 mil
   leituras de bloco por execução com leitura, quase todas aleatórias.
2. As consultas ficam limitadas por I/O: Q4 com média de 83–600 ms no banco, Q2 de 37–117 ms
   ([explain.md](explain.md) mostra o plano com `read=` em todas as consultas de conta empresarial).
3. Com 10 conexões e tempo médio de uso *u*, o pool atende no máximo 10/*u* requisições/s. Nas execuções
   estáveis com leitura, *u* fica em 0,02–0,06 s (capacidade de 170–500/s). Quando uma fila se forma, as 10
   conexões passam a rodar consultas ao mesmo tempo, o I/O concorrente encarece cada uma, e *u* sobe para
   0,08–0,16 s na média da execução (capacidade de 60–120/s), perto ou abaixo da taxa de chegada
   (70–95/s). A fila se realimenta e não se desfaz
   enquanto a carga dura.
4. Os comandos quase não disputam o banco; disputam a conexão. Sem lugar na fila exclusivo para eles, herdam
   toda a espera das leituras.

Por isso o resultado depende do estado do cache no início de cada execução: um surto de leituras frias
dispara a fila, e o modelo aberto, como num serviço real, não reduz a chegada para ajudar.

## O que isso pede das próximas fases

- **v2 (H2):** se as projeções derem localidade aos dados de uma conta, *u* cai e o pool volta a ter
  folga. O efeito sobre a escrita seria indireto, pela fila, e é o que o S3 da v2 deve medir.
- **Controle — pools separados ([#11](https://github.com/guipalm4/spring-cqrs/issues/11)):** se um pool
  só de comandos eliminar a degradação da escrita na v1, o ganho de escrita da v3 (H3) vem do
  isolamento de conexões, não da separação de armazenamento. Precisa ser medido antes de atribuir o
  ganho ao CQRS.
- **Q4 é o candidato natural para projeção dedicada** desde a v2, e não só no M5 (OpenSearch).

## Limitações e ameaças à validade

- **Bimodalidade com 3 repetições:** a 50 e 75/s, a mediana mistura estados diferentes e não descreve
  nenhum. Leia "colapsou em k de 3", não a mediana. Mais repetições estreitariam a probabilidade de
  colapso por taxa; com 3, só se afirma que ela é alta (≥ 50/s) e baixa (25/s).
- **A calibração superestimou a capacidade:** feita com o cache aquecido por execuções anteriores,
  indicou saturação entre 50 e 100/s; com o dataset recém-carregado e repetições intercaladas, o
  joelho ficou entre 25 e 50/s. As taxas não foram reajustadas depois da execução, para não escolhê-las
  pelo resultado; v2 e v3 usam as mesmas.
- **A mistura de leitura inclui Q1 e Q4,** além de Q2/Q3 do enunciado do H1. Sem o Q4, a carga de leitura
  seria bem menor no mesmo número de requisições.
- **Ambiente compartilhado:** o Gatling roda no host e disputa CPU com a VM do Docker (ADR-0007); latências
  incluem a rede da VM. Comparações valem entre fases nesta máquina.
- **`Premature close`:** parte das falhas no colapso são conexões HTTP fechadas por volta de 15 s de espera,
  sem causa identificada (Tomcat ou o port-forward do Docker Desktop). Só ocorre no colapso.
- **Memória da aplicação:** com heap em 75% do container, o pico no colapso foi de 1.000 MiB para um limite
  de 1.024 MiB. Não houve OOM nem reinício, mas a margem é pequena.
- **Coleta do Prometheus falhou no colapso:** em 10 instantes de 5 s, todos em execuções com colapso, a
  aplicação não respondeu ao `/actuator/prometheus` e a linha do `timeseries.csv` ficou vazia. Causa não
  investigada.
- **`timeseries.csv` exportado depois:** as séries desta execução foram extraídas do Prometheus em 2026-10-20,
  com a janela de cada execução tirada do `docker-stats.csv`. As fases seguintes as exportam durante o `run.sh`.
- **`hikari_acquire_max_s` em `prometheus.csv` não é confiável:** o máximo do Micrometer decai em janelas e
  carrega valores da execução anterior. Use a espera média.
- **Execução com alterações locais** (`env.md`): sobre o commit `3d32327`, só o passo de `EXPLAIN` no fim do
  `run.sh` e documentação; nada que afete a carga ou a aplicação.
