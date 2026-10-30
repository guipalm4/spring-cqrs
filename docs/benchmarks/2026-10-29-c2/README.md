# C2 — escrita intensa com busca, 29/10: capacidade por fase

Escada do [ADR-0013](../../adr/0013-estudo-de-capacidade.md):
- leitura fixa a 75 consultas/s e escrita em 20, 50, 100, 200, 400 e 800 comandos/s;
- 3 repetições de 180 s por degrau;
- parada no primeiro degrau não sustentado.

As fases rodaram na noite de 29 para 30/10, entre 21h34 e 02h17, nesta ordem, cada uma com os volumes apagados e o
seed de referência recarregado:
[v1](../2026-10-29-c2-v1/summary.md), [v1-big](../2026-10-29-c2-v1-big/summary.md),
[v1-replica](../2026-10-29-c2-v1-replica/summary.md), [v2](../2026-10-29-c2-v2/summary.md) e
[v3](../2026-10-29-c2-v3/summary.md). Bancada no commit 92c9e4a, a mesma do ensaio completo daquela noite. O dia correu
sem falha da bancada: nenhuma fase interrompida, nenhum dado faltando, nenhum `warnings.txt`. Pela nota antes do C2
no ADR, esta é a medição final do cenário. Carga do host no `env.md` de cada pasta: load de 1 a 7, com um pico de 14
no fim da r2 da v2 a 100/s, num degrau que ela sustentou.

As duas tentativas anteriores (28/10 e 29/10, às 18h) caíram por falhas da bancada e foram apagadas
([diário](../../diario.md)).

## Resultado

| Fase | Maior degrau sustentado | Parou em | O que parou |
|---|---:|---:|---|
| v1 | 200 | 400 | app fora do ar na r1 (`OutOfMemoryError` no log); comandos e consultas com p95 de 60 s, 39% de KO |
| v1-big | 100 | 200 | não caiu, mas 2 das 3 repetições deram p95 de 13 a 30 s nos dois lados |
| v1-replica | nenhum (começa em 20) | 20 | consultas com p95 de 31 s e 48% de KO nas 3 repetições; comandos com p95 de 13 ms |
| v2 | 400 | 800 | r1 com p95 de 710 ms nos dois lados; r2 com app fora do ar (`OutOfMemoryError`) e 97% de KO |
| v3 | 50 | 100 | comandos com p95 de 151 a 274 ms nas 3 repetições; consultas com p95 de 21 a 23 ms, atraso p99 de 0,36 s e fila zerada |

Tabela e gráfico gerados: [capacity.md](capacity.md). Os logs das JVMs que caíram estão nas pastas das repetições
(`logs-app-*.txt`). O modo de falha é o mesmo do C1: as threads virtuais enfileiram até o heap acabar.

p95 em ms, mediana das 3 repetições:

| Fase | 50/s: comandos | 50/s: consultas | 100/s: comandos | 100/s: consultas | 200/s: comandos | 200/s: consultas | 400/s: comandos | 400/s: consultas |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| v1 | 5 | 27 | 4 | 29 | 7 | 51 | não sustentou | |
| v1-big | 5 | 26 | 6 | 30 | não sustentou | | | |
| v2 | 5 | 20 | 5 | 20 | 4 | 20 | 8 | 26 |
| v3 | 14 | 19 | não sustentou (243) | 22 | | | | |

## Previsões

- **P5, refutada pela letra, com o mecanismo sem teste.** A v1-replica não sustentou nem o primeiro degrau, enquanto
  a v1 sustentou 200: as duas não param no mesmo degrau. O que parou a réplica foram as consultas, com os comandos
  em p95 de 13 ms. A pergunta da P5, se a manutenção dos índices no primário segura a escrita da réplica junto com a
  da v1, não chegou a ser feita (ver "A v1-replica", abaixo).
- **P6, refutada, a favor da v2.** A v2 sustenta 400, um degrau acima da v1. Reescrever a projeção na transação do
  comando não custou o degrau. A v2 grava um pouco mais de WAL por comando (12 KB contra 10 a 200/s), mas lê do disco
  de um sexto a um terço das páginas da v1 (120 a 290 mil por repetição, contra 450 mil a 1 milhão).
- **P7, refutada nas duas metades.** A v3 sustenta 50, dois degraus abaixo da v1. Ela parou pelos comandos, com a
  projeção em dia: atraso p99 de 0,32 a 0,36 s e fila zerada no fim das três repetições.

## Leitura pelas regras do ADR

- **O CQRS físico não vence no C2. Está refutado,** e com folga: a regra refutava se a v3 parasse pelos comandos no
  mesmo degrau da v1, e ela parou dois degraus abaixo. A conclusão não depende da réplica, porque "vence" exigia a
  P7, e a P7 caiu.
- **H9 refutada nesta escala e neste host.** A métrica é o maior degrau de escrita sustentado, v1 e v1-replica
  contra v3: 200 e nenhum contra 50. Tirar os índices de leitura do modelo de escrita **num store separado** não
  aumentou a capacidade de escrita; ela caiu a um quarto.
- **O que o C2 mostra a favor da separação vem da v2, não da v3.** A v2 também tira os índices de leitura da
  `ledger_entry`: o modelo de escrita dela não tem o `search_vector` nem os dois GIN compostos da v1, e as projeções ficam em
  tabelas por conta e mês, no mesmo Postgres. Ela sustenta o dobro da v1. Isso não é veredito de hipótese (nenhuma
  previsão escrita dizia isso), mas é o dado mais claro da noite: separar o modelo no mesmo store pagou, separar o
  store custou.

## O disco ao lado do veredito

Pela nota antes do C2, o fsync médio do WAL no degrau em que a fase para, contra o do degrau anterior (mediana das
repetições, a convenção dos resumos). Pelo menos o dobro atribui a parada ao disco do host, sem mudar o veredito.

| Fase | Último sustentado: fsync (ms) | Primeiro não sustentado: fsync (ms) | Razão | Atribuída ao disco |
|---|---:|---:|---:|---|
| v1 | 0,56 (200/s) | 1,16 (400/s) | 2,1× | sim, no limiar (1,9× pela média ponderada das repetições) |
| v1-big | 0,44 (100/s) | 1,38 (200/s) | 3,1× | sim |
| v1-replica | — | 2,57 (20/s) | — | sem degrau anterior; no C1, a mesma fase no mesmo degrau ficou em 0,48–0,59 |
| v2 | 0,29 (400/s) | 1,24 (800/s) | 4,3× | sim |
| v3 | 0,44 (50/s) | 0,42 (100/s) | 1,0× | não |

O custo por página lida do Mongo na v3 subiu pouco, de 0,42 ms a 50/s para 0,51 ms a 100/s. O atraso de replicação da
v1-replica ficou em 0,01 s (p99): a réplica acompanhou o primário.

**Pela regra, a parada da v3 não é do disco.** O que os dados mostram, lido depois de medir e sem critério escrito
antes, é outra coisa. A mediana dos comandos da v3 a 100/s é de 3 ms. A cauda vem de travadas de 1 a 1,8 s, a cada
~32 s (na r2, nos segundos 15, 38, 70, 103, 135 e 167), e cada uma vem seguida, no `pg-seconds.csv`, de um segundo
com o fsync médio do WAL de 2,5 a 33 ms. Nos outros segundos, ele fica em 0,6 ms. A 50/s as travadas já existem
(até 1,2 s), mas são poucas para o p95. A média do degrau não pega isso, porque são 14 segundos em 180. Por
comando, a v3 grava 70 KB no Mongo e 3,7 KB de WAL no Postgres, no mesmo disco virtual. É o mecanismo da cauda já
visto no [diagnóstico](../2026-10-27-v3-diag/README.md), agora com escrita suficiente para passar do critério. O ADR
declarou a contenção de disco como limite das fases com dois stores no mesmo disco. Com discos separados, como em
produção, o resultado pode ser outro, e este estudo não responde isso.

## A v1-replica

O primeiro degrau do C2 (escrita a 20/s, leitura a 75/s) é a mesma carga do primeiro degrau do C1, que a v1-replica
sustentou em 3 de 3 em 27/10. Nesta noite, a mesma carga deu 31 s de p95 nas consultas desde o início da r1. Na
noite do C1, a Q3 na réplica levava em média 4,6 ms e lia do disco 17 páginas por chamada. Nesta, levou 1,6 s e leu
1900 páginas, a 0,8 ms cada. A JVM das consultas já chegou à r1 com a memória no limite (1 GB), cheia desde o
aquecimento.

As duas noites usaram o mesmo seed, o mesmo `VACUUM (ANALYZE)`, o mesmo aquecimento e o mesmo código. A causa não
foi identificada: não há `EXPLAIN` da réplica, e a bancada só guarda o plano da v1 e da v2. Não é falha da bancada
pela definição do ADR, já que o dia correu inteiro e nenhum dado falta. Por isso o resultado fica como está, sem
repetir. O que ele mostra é uma fase que, com a mesma carga e no mesmo host, passou numa noite e caiu na outra.
Isso também é um resultado sobre a réplica neste host: ela depende do cache, e o cache dela não é controlado. O
veredito da P5 vale com essa ressalva, e o mecanismo da P5 vai para o M6 como limite do estudo.

## Limites

- Uma noite e um host. As fases só se comparam com as do mesmo dia (ADR-0010). Nenhum resultado do C2 se compara com
  o C1.
- Os degraus dobram: a capacidade de cada fase está entre o último degrau sustentado e o seguinte.
- A v1 parou a 400 numa única repetição, porque a fase caiu e a escada parou ali. Nas tentativas descartadas de 28/10
  e 29/10, que caíram por falha da bancada, a v1 também sustentou 200 em uma e parou em 200 na outra. A capacidade
  dela está no joelho de 200.
- A v1-big sustentou menos que a v1, como no C1 (mais lenta no mesmo degrau, apesar do dobro de recursos). O C2 não
  explica isso.
