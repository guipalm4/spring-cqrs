# Diagnóstico da cauda dos comandos da v3 — 27/10

Pergunta (ADR-0013, antes do primeiro dia de capacidade): a cauda dos comandos da v3 (p99 de ~110 ms em 21/10, contra
~22 ms nas outras fases) vem das rajadas de escrita do Mongo disputando o disco da VM com o WAL do Postgres?

Método: o S1 (só escrita, 20 comandos/s, 180 s) com e sem o projetor, intercalados (`s1`, `s1-sem-projetor`, três vezes),
no seed de referência (escala 1), com o Grafana parado. Sem o projetor, os lançamentos se acumulam no Kafka e o Mongo
não grava. O resto é igual: command-api, outbox, Kafka e Postgres. Parâmetros e carga do host no [env.md](env.md).

## Resultado

| Execução | p95 | p99 | máx. | Picos > 60 ms | Mongo gravou | fsync médio do WAL |
|---|---:|---:|---:|---:|---:|---:|
| s1 r1 | 9 | 41 | 329 | 9 | 246 MB | 0,45 ms |
| s1 r2 | 13 | 143 | 449 | 10 | 251 MB | 0,49 ms |
| s1 r3 | 9 | 53 | 386 | 9 | 249 MB | 0,44 ms |
| s1-sem-projetor r1 | 11 | 14 | 67 | 1 | 12 MB | 0,28 ms |
| s1-sem-projetor r2 | 11 | 15 | 33 | 0 | 0 MB | 0,28 ms |
| s1-sem-projetor r3 | 12 | 19 | 71 | 1 | 12 MB | 0,32 ms |

Latências em ms, no cliente (`stats.csv`). "Mongo gravou" é o `block-manager.bytes written` do WiredTiger entre o
início e o fim da execução (`mongo-cache.csv`). O fsync médio vem do `pg_stat_io` (`pg-seconds.csv`): o número de
fsyncs foi o mesmo nas seis execuções (~11 800), mudou só o tempo de cada um.

**A hipótese se confirma.** Com o projetor parado, os picos caem de 28 para 2 e o p99 vai para 14–19 ms, a cauda das
outras fases. Os três pares deram o mesmo resultado, e a carga do host foi parecida nos dois cenários (load 2–7 nos
dois).

Segundo a segundo, o mecanismo é o fsync do WAL. Somando o segundo do pico e o seguinte (o `pg_stat_io` publica com
até ~1 s de atraso), o fsync levou 210–285 ms nos segundos com pico, contra 48–51 ms nos outros. Com o
`CommandTail.java`, 27 dos 28 picos ficam a até 3 s de um salto de ≥ 10 MB nas escritas do Mongo, contra 47% dos
segundos por acaso:

```bash
java bench/analysis/CommandTail.java docs/benchmarks/2026-10-27-v3-diag s1
java bench/analysis/CommandTail.java docs/benchmarks/2026-10-27-v3-diag s1-sem-projetor
```

## Limites

- Parar o projetor tira também a CPU dele e o consumo do Kafka, não só as escritas do Mongo. O que liga a cauda ao
  disco, e não à CPU, é a correlação com o tempo de fsync (o número de fsyncs não muda) e com os saltos de escrita do
  Mongo.
- A janela de dois segundos para o fsync foi escolhida depois de ver os dados, por causa do atraso de publicação do
  `pg_stat_io`. Com um segundo só, a diferença é a mesma em direção, mas mais ruidosa.
- Os outliers aparecem também dentro de instruções (um `INSERT INTO ledger_entry` de 165 ms, o `SELECT ... FOR UPDATE`
  da conta a 68 ms), não só no commit. Isso é compatível com quem espera o lock da conta ou a escrita do WAL de outra
  transação que está no fsync, mas não foi separado.
- É uma VM com um disco virtual só, no macOS. Em produção, Postgres e Mongo não dividiriam o disco. A cauda é um custo
  de colocar os dois stores no mesmo host, não do CQRS em si. No estudo de capacidade ela continua valendo, porque
  todas as fases rodam no mesmo hardware (ADR-0013, sem ajuste do WiredTiger).
