# ADR-0013 — Estudo de capacidade: o regime em que o CQRS deveria se pagar

- **Status:** Aceito
- **Data:** 2026-10-22
- **Substitui:** a regra de escopo congelado do [ADR-0011](0011-v2-projecoes-e-escopo-congelado.md)
- **Substitui em parte:** [ADR-0009](0009-revisao-da-baseline-e-parametros-do-benchmark.md) (taxas fixas como único
  ponto de comparação) e [ADR-0012](0012-v3-projecao-mongo-e-controle-de-hardware.md) (a leitura da H3 e da H7)

## Contexto

### O dia de medição de 2026-10-21 não estressou nenhuma fase

As cinco fases (v1, v1-big, v2, v3, v3-s5) rodaram com as taxas do ADR-0009 e nenhuma execução colapsou, nem a
da v1 (0 de 9 em `s3-r050` e `s3-r075`). No `s3-r075`, a carga mais alta:

| | v1 em 19/10 (colapsou) | v1 em 21/10 | v3 em 21/10 |
|---|---:|---:|---:|
| CPU do Postgres (média, % de um núcleo; teto de 200%) | 96–122% | 33–40% | 6% |
| CPU da aplicação | 21–50% | 8–9% | 9% (comandos) / 17–19% (consultas) |
| Tempo por página lida do Postgres | ~4 ms | 0,15–0,37 ms | — (0 páginas lidas) |

Pela lei de Little, a v1 do dia 21 ocupava em média ~0,55 das 10 conexões (95 req/s × 5,8 ms de uso): cerca de 5%
do pool. As taxas foram calibradas em 19/10, um dia em que, por inferência, o host estava em uso durante a medição
(o `env.md` não registrava a carga do host). A mesma taxa que derrubou a v1 em 19/10 usou ~5% dela em 21/10. **Taxa
fixa mede latência num ponto de carga, e esse ponto muda de lugar em relação à capacidade de um dia para outro.**

Com isso, pelos critérios do ADR-0012, a H3 ficou inconclusiva (não houve interferência para a v3 eliminar) e a H7
também (uma réplica da `query-api` já aguentava 75/s). A H5 foi respondida.

### A cauda dos comandos da v3

O p99 dos comandos da v3 ficou em 107–183 ms já no S1, contra ~22 ms na v1 e na v2, com o p95 na mesma faixa
(14 ms). O diagnóstico com os dados do dia 21:

- **São picos isolados, não uma cauda espalhada.** Por execução de 3 min, de 9 a 15 segundos com resposta máxima
  acima de 60 ms (até 822 ms), sem período fixo. A v1 e a v2 têm de 0 a 4.
- **Não é GC:** a fração de pausa da JVM da `command-api` é 0 nas janelas dos picos.
- **Não é uma instrução lenta:** o desvio-padrão de cada instrução no `pg_stat_statements` limita o maior valor
  isolado possível (desvio × √chamadas). Nas repetições r2 e r3 do S1, esse limite é de ~20 ms para todas as
  instruções, e os picos chegam a 822 ms. Na r1, as escritas chegam a ~220 ms, mas ela coincide com o I/O do Grafana
  (abaixo). No pico, o tempo de uso da conexão do Hikari sobe ~10 vezes (de ~1 para ~10 ms) com as instruções
  normais: o tempo está entre as instruções, possivelmente no `COMMIT` (fsync do WAL), que o export atual não mostra.
- **Coincide com rajadas de escrita do Mongo.** No S1, 84% dos picos da v3 (31 de 37) e 94% dos da v3-s5 (31 de 33)
  caem a até 3 s de um salto no contador de escrita do Mongo no `docker stats`; ao acaso, seriam 43% e 48%. Com
  saltos de 20 MB ou mais: 62% contra 25%, e 70% contra 29%. O contador tem resolução de 10 MB, então a associação
  é robusta, mas é correlação.
- **O Mongo escreve ~57 KB por comando** (210–220 MB por execução de S1), o Kafka, 1,7 KB. Não é o bloco grande do
  ADR-0011: no benchmark, os blocos do mês corrente começam vazios. Cada lançamento suja páginas em pontos aleatórios
  de duas coleções e dos seus índices (inclusive o text index, multikey), e o WiredTiger as grava em rajadas.

Hipótese de trabalho: **as rajadas do Mongo saturam o disco virtual que o Postgres compartilha na VM do Docker
Desktop, e o fsync do WAL no commit dos comandos espera.** É inferência; não está medido.

Os números acima saem de `java bench/analysis/CommandTail.java docs/benchmarks/2026-10-21-v3` (e `-v3-s5`), sobre o
`seconds.csv` de cada execução: os percentis por segundo do relatório do Gatling, que agora o `run.sh` grava ao lado
do `stats.csv` e que foram extraídos para todas as execuções já versionadas.

### Ruído encontrado no mesmo dia

O Grafana atingiu o limite de 512 MB e passou a fazer I/O descontrolado no disco compartilhado: 8 GB na última
repetição da v1 e 23 GB na primeira da v3. Depois disso, deixou de aparecer no `docker stats`. A pior execução de
comandos da v1 (`s3-r075` r3: p95 18 ms, p99 81 ms) é a que coincide com ele. Não explica a cauda da v3, que
continua nas repetições sem o Grafana.

### O objetivo muda

Até aqui, a pergunta era "o CQRS ganha no mesmo ponto de carga?". A resposta medida é que, longe da saturação, a v1
basta, a v2 deixa a leitura 2 a 3 vezes mais barata na cauda (Q3 e Q4) a custo baixo, e a v3 só mostra os custos
fixos. Isso fica na história como está.

A pergunta agora é **em que regime o CQRS se paga**, e contra qual alternativa. Para não virar uma busca pela
configuração em que a v3 vence: o cenário sai do mecanismo, as previsões ficam escritas aqui antes de medir, e o
CQRS enfrenta a alternativa mais barata que um time tentaria antes dele.

## Decisão

### 1. Medir capacidade, não latência a uma taxa fixa

Cada fase sobe uma escada de carga, e o resultado é **o maior degrau sustentado**. Um degrau é sustentado numa
repetição se, ao mesmo tempo:

- p95 dos comandos ≤ **50 ms** (cerca de 3 vezes o S1 de todas as fases em 21/10);
- p95 das consultas ≤ **250 ms** (a Q4 empresarial da v1 fica em ~100 ms sem carga);
- KO ≤ **1%**;
- vazão atingida ≥ **98%** da oferecida (modelo aberto, ADR-0009);
- nas fases com consistência eventual (v3 e v1-replica): atraso p99 ≤ **2 s**, e a fila (outbox mais lag do consumer
  group, ou `replay_lag` da réplica) no fim do degrau ≤ 1 s de escrita.

Os limites são absolutos, os mesmos para todas as fases, e ficam fixos a partir deste ADR. O degrau é sustentado
na fase se for sustentado em pelo menos 2 de 3 repetições. O "colapsou em k de n" do ADR-0009 continua sendo
reportado ao lado.

**Ordem e parada:** os degraus sobem em ordem, com as 3 repetições de um degrau em seguida, e a escada para quando
um degrau não é sustentado. Isso abandona a intercalação do ADR-0009 dentro da escada (o cache esquenta ao longo
dela, a favor dos degraus altos), e fica declarado.

### 2. Dois cenários, cada um com um mecanismo

| Cenário | Problema real | Carga | Por que o CQRS deveria vencer | Rival a vencer |
|---|---|---|---|---|
| **C1 — pico de leitura** | Dia de pagamento, fechamento do mês | Escrita fixa a 20/s; leitura em 75, 150, 300, 600, 1200/s | A leitura satura o próprio store e a própria JVM; os comandos ficam isolados; o modelo de leitura é mais barato | Réplica de leitura |
| **C2 — escrita intensa com busca** | Ledger de alto volume com extrato pesquisável | Leitura fixa a 75/s; escrita em 20, 50, 100, 200, 400, 800/s | O modelo de escrita da v3 não mantém os dois GIN compostos (~1 GB) da v1; a v3 grava 0,6 ms por comando no Postgres, contra 1,3–1,7 ms | Réplica de leitura, que **não pode** tirar índices do primário |

A mistura de comandos e consultas é a do ADR-0009. Um degrau do C1 a 1200/s pode esbarrar no gerador (Gatling no
mesmo host) ou no port-forward do Docker Desktop antes da fase: um degrau em que a vazão atingida fica abaixo da
oferecida com a CPU das fases baixa é marcado como **limite do gerador**, não da fase.

### 3. Fases

v1, v1-big (controle de hardware, ADR-0012), **v1-replica (nova)**, v2 e v3. A v3-s5 roda só no C1 (H7), a partir do
primeiro degrau que a v3 não sustentar.

**v1-replica** é a alternativa barata: a v1 sem nenhuma mudança de código, com o armazenamento da leitura separado
por replicação física.

| Container | Papel | CPU / memória |
|---|---|---|
| `postgres` | primário | 2 / 2 GB, `shared_buffers=512MB` |
| `postgres-replica` | réplica *streaming*, `hot_standby_feedback=on` | 2 / 2 GB, `shared_buffers=512MB` |
| `app-baseline` | comandos, apontando para o primário (porta 8080) | 2 / 1 GB |
| `app-baseline-query` | consultas, apontando para a réplica (porta 8081), Flyway desligado | 2 / 1 GB |

- Mesmo tamanho de peça da v3 (dois stores de 2 GB com 512 MB de cache, duas JVMs de 1 GB), sem Kafka nem projetor.
  O Gatling já chama comandos e consultas em URLs separadas (ADR-0012).
- A Q1 também passa a ler da réplica, com consistência eventual como as projeções da v3. O atraso de replicação
  (`replay_lag` do `pg_stat_replication`) é o equivalente ao atraso da projeção e entra no critério do item 1.
- `hot_standby_feedback` evita que consultas longas na réplica sejam canceladas por conflito com a replicação
  (seriam KO que a v1 não tem). O custo é segurar o vacuum no primário, que aparece na medição.

### 4. Previsões, escritas antes de medir

**C1 — pico de leitura**

- **P1:** a v1 e a v1-big deixam de sustentar por comandos e consultas no mesmo degrau, porque dividem o pool, a
  JVM e o Postgres. A v1-big sustenta um degrau a mais que a v1.
- **P2:** a v1-replica sustenta os comandos em todos os degraus que o gerador alcançar. As consultas param de
  sustentar no mesmo degrau da v1-big, ou um abaixo (mesmas consultas, num Postgres de 2 CPUs só para leitura).
- **P3:** a v2 sustenta as consultas pelo menos um degrau acima da v1, mas os comandos param junto com elas, porque
  dividem o Postgres.
- **P4:** a v3 sustenta as consultas pelo menos um degrau acima da v1-replica (modelo de leitura mais barato) e os
  comandos até a CPU da VM saturar.
- **Leitura:**
  - **H3 confirmada** se a v3 sustentar os comandos num degrau em que a v1 e a v1-big não sustentam.
  - **O CQRS vence a réplica no C1** se P4 se confirmar; **a réplica basta no C1** se a v1-replica sustentar tantos
    degraus de leitura quanto a v3.
  - **H7:** confirmada se a v3-s5 sustentar um degrau que a v3 não sustenta, com os comandos dentro do critério.
    Refutada se, no primeiro degrau que a v3 não sustenta, o Mongo já estiver no teto de CPU (escalar a aplicação não
    escala o store).

**C2 — escrita intensa com busca**

- **P5:** a v1 e a v1-replica param no mesmo degrau de escrita. A réplica não tira a manutenção dos índices do
  primário.
- **P6:** a v2 para no mesmo degrau da v1 ou antes, porque reescreve a projeção na transação do comando.
- **P7:** a v3 sustenta os comandos pelo menos um degrau acima da v1. Quando parar, para pelo atraso da projeção
  (projetor ou Mongo), não pelos comandos.
- **Leitura:**
  - **O CQRS vence no C2** se P5 e P7 se confirmarem.
  - **Refutado** se a v3 parar pelos comandos no mesmo degrau da v1. Nesse caso, tirar os índices de leitura do
    modelo de escrita não se paga nesta escala.

Resultado contrário a qualquer previsão entra no capítulo igual.

### 5. Antes do dia de medição

1. **Confirmar a causa da cauda da v3** com uma execução de diagnóstico, fora da comparação: S1 da v3 com o
   projetor parado. Se os picos somem, a contenção de I/O com o Mongo está confirmada. A medição passa a exportar
   `pg_stat_wal` com `track_wal_io_timing=on` (tempo de fsync do WAL) e os bytes gravados pelo WiredTiger
   (`serverStatus`) por execução.
   - **Sem ajuste do WiredTiger para suavizar as rajadas:** seria vantagem de configuração. A contenção de disco é
     declarada como limite das fases com dois stores no mesmo disco (v3 e v1-replica). Em produção, seriam discos
     separados.
2. **O Grafana não fica no ar durante a medição.** O `run.sh` o para antes do primeiro degrau e o religa no fim. O
   Prometheus continua, e os dados vêm dele pela API.
3. **Host parado:** a medição roda sem uso do Mac (de madrugada), e o `env.md` registra a carga do host no início e
   no fim de cada degrau (`sysctl vm.loadavg`, `memory_pressure`).
4. **Bancada:**
   - `WRITE_RATES` como lista;
   - degraus em sequência com a regra de parada;
   - `bench/Report.java` com a tabela e o gráfico de capacidade por fase;
   - export do `replay_lag` na v1-replica.
5. **Profile `v1-replica`** no compose e no Makefile.

### 6. Dias de medição e marcos

- O C1 e o C2 rodam em dias diferentes, cada um com todas as fases e o seed recarregado antes de cada fase. Cada
  fase só é comparada com as fases do mesmo dia (ADR-0010). Pela regra de parada, cada dia deve levar de 6 a 9 h.
- **O M4 fecha com o que foi medido em 21/10:** H5 respondida, H3 e H7 inconclusivas a taxa fixa (com os números
  deste ADR), cauda da v3 declarada e trace ponta a ponta.
- **O estudo de capacidade vira um marco próprio antes do M5**, com issue e capítulo no README. No SCOPE, entram
  duas hipóteses:
  - **H8:** uma réplica de leitura basta para proteger a escrita de picos de leitura (C1);
  - **H9:** com escrita intensa, tirar os índices de leitura do modelo de escrita aumenta a capacidade de escrita, o
    que uma réplica não consegue (C2).

## Alternativas consideradas

- **Subir as taxas fixas até a v1 colapsar:** escolher o ponto de carga pelo resultado, o que o ADR-0009 recusou.
  Além disso, o ponto muda de lugar com o estado do host.
- **Repetir o dia de 21/10 até a v1 colapsar:** sem regra de parada, é escolher o dia que dá o resultado esperado.
- **Forçar leitura lenta (limitar o I/O do Postgres):** mede o colapso, mas num ambiente fabricado para ele. O teste
  de capacidade chega à saturação pela carga, que é o que acontece em produção.
- **SLO relativo ao S1 de cada fase:** pune a fase com a melhor linha de base. Um limite absoluto é a régua que um
  time usaria.
- **Comparar a v3 só com a v1:** deixaria aberta a pergunta "e uma réplica não resolveria?", que é a primeira
  objeção de qualquer revisor.
- **Ajustar o WiredTiger para suavizar as rajadas:** discutido no item 5; vantagem de configuração.
- **Limitar o tamanho do bloco (N lançamentos):** não resolve a cauda medida, porque no benchmark os blocos começam
  vazios.

## Consequências

- **O escopo deixa de ser congelado.** Uma fase nova (v1-replica), dois cenários e duas hipóteses. Qualquer variante
  além delas continua exigindo um ADR.
- **Mais dois dias de medição**, mais o trabalho na bancada antes deles.
- **A história ganha um capítulo** entre o M4 e o M5, e o "quando não usar CQRS" (M6) passa a ter as duas pontas:
  onde ele não se paga (M2–M4) e onde se paga, se o estudo mostrar.
- **O teto do estudo é o hardware local:** containers de 2 CPUs numa VM de 8, com gerador e sistema no mesmo host e
  um disco virtual para todos os stores. Os degraus medem a saturação destas peças, o análogo em escala reduzida de
  um sistema maior, e não milhares de req/s de um banco de verdade.
- **A soma dos limites de CPU passa da VM** em várias fases (ADR-0012). No C1, isso pode decidir o degrau da v3
  antes do Mongo ou da `query-api`; o `docker stats` por degrau mostra qual peça saturou.
