# Diário de decisões

O caminho até as decisões: o que acreditávamos, o que os dados mostraram, o que mudou e os erros no meio. As decisões
em si estão nos [ADRs](adr/); os resultados, na [história do README](../README.md#a-história). Uma entrada por sessão
em que uma hipótese, um plano ou a leitura de um resultado mudou, da mais antiga para a mais nova.

Até o M3, esse caminho está nos capítulos 1 a 3 do README ("O que mudou no entendimento") e nos ADRs 0009 a 0011.

## 2026-10-21 — O dia de medição que não estressou ninguém

*Reconstruída a partir dos commits e do ADR-0013.*

- Acreditávamos que as taxas do ADR-0009 (até 75 consultas/s) levariam a v1 ao colapso, como em 19/10, e que a v3
  mostraria que a separação física o evita (H3).
- As cinco fases rodaram em sequência com o seed recarregado ([dados](benchmarks/2026-10-21-v3/README.md)). Nenhuma
  colapsou: o Postgres da v1 ficou em 33–40% de CPU, contra 96–122% em 19/10.
- Surpresa: o p99 dos comandos da v3 ficou em ~110 ms já sem leitura, contra ~22 ms nas outras fases.
- Erro no meio: o Grafana bateu no limite de memória e fez 8 a 23 GB de I/O no disco compartilhado em duas execuções.

## 2026-10-22 — A taxa fixa mede o host, não a fase

- Pela lei de Little, a v1 usou ~5% do pool no dia 21. A mesma taxa que a derrubou em 19/10 ficou longe da capacidade:
  o ponto de carga muda de lugar com o estado do host. H3 e H7 ficaram inconclusivas por construção.
- A cauda da v3 não é GC nem instrução lenta; 84% dos picos coincidem com rajadas de escrita do Mongo
  ([CommandTail.java](../bench/analysis/CommandTail.java)). Hipótese: fsync do WAL esperando o disco. Não confirmada.
- Mudança de pergunta: de "o CQRS ganha neste ponto?" para "em que regime ele se paga, e contra qual alternativa?".
  Escadas até a saturação, dois cenários com mecanismo (C1, C2) e uma réplica de leitura como rival
  ([ADR-0013](adr/0013-estudo-de-capacidade.md)). Recusado: subir as taxas até a v1 cair (escolher a carga pelo
  resultado).

## 2026-10-23 — Fechamento do M4

- A previsão de que o p99 do atraso da projeção subiria uma ordem de grandeza com leitura foi refutada (+40%): o
  projetor não disputou o Mongo com as consultas nesta carga ([análise](benchmarks/2026-10-21-v3/README.md#h5-confirmada)).
- A v3 lê mais caro que a v2 em Q2 e Q3 com o mesmo documento (Q3: 12 contra 4 ms). Esperávamos empate, porque o
  documento é o mesmo; a causa (cache do WiredTiger, salto de rede, BSON) não foi isolada.
- O Kafka sozinho gasta mais CPU que o Postgres da v1 sem leitura. O custo fixo da v3 domina longe da saturação.
- O trace ponta a ponta só foi registrado hoje ([trace](trace/README.md)): o código existia desde 21/10, mas a
  evidência não estava em `docs/`. O primeiro comando depois da partida leva 765 ms, quase tudo no produtor do Kafka.
- Na v1, os comandos ficaram mais rápidos com leitura do que sem (p50 de 8 para 4 ms). Não investigado; fica anotado.

## 2026-10-27 — A bancada da capacidade, ensaiada antes de medir

- O ADR-0013 mandava ler o fsync do WAL no `pg_stat_wal`. No PostgreSQL 18, esse tempo foi para o `pg_stat_io`
  (`object = 'wal'`). Achado ao implementar, registrado como nota no ADR, sem mudar o critério.
- A regra "CPU das fases baixa" do limite do gerador saiu como média por degrau. O ensaio (seed 0,01, projeto à parte,
  fora do estudo) mostrou que a média engana: a 1200 consultas/s, a réplica da v1-replica saturou por 15 s, o app das
  consultas morreu por falta de heap e a média ficou em 47%. A regra passou a usar o p90 das amostras, antes de qualquer
  medição real.
- Surpresa: acima do joelho, a v1 não perde vazão aos poucos, cai de uma vez. As threads virtuais aceitam requisições
  sem limite (4945 esperando conexão) até o heap acabar. Isso não muda a capacidade medida, mas entra no capítulo.
- Erro no meio: com degraus de 30 s, o degrau de 600/s parecia limite do gerador. Era o Gatling contando a vazão em
  segundos inteiros (31 em vez de 30). O gerador sustentou 620 requisições/s em 90 s; acima disso, a fase caiu antes
  dele, então o teto do gerador continua desconhecido.
