# C1 — pico de leitura, 27/10: capacidade por fase

Escada do [ADR-0013](../../adr/0013-estudo-de-capacidade.md):
- escrita fixa a 20 comandos/s e leitura em 75, 150, 300, 600 e 1200 consultas/s;
- 3 repetições de 180 s por degrau;
- parada no primeiro degrau não sustentado.

As fases rodaram na noite de 27 para 28/10, entre 23h57 e 05h09, nesta ordem, cada uma com os volumes apagados e o
seed de referência recarregado:
[v1](../2026-10-27-c1-v1/summary.md), [v1-big](../2026-10-27-c1-v1-big/summary.md),
[v1-replica](../2026-10-27-c1-v1-replica/summary.md), [v2](../2026-10-27-c1-v2/summary.md) e
[v3](../2026-10-27-c1-v3/summary.md). Carga do host no `env.md` de cada pasta (load de 2 a 7, Mac sem uso).

A v3-s5 desta noite foi descartada: aqueceu a 600/s, com as JVMs frias, e chegou morta ao primeiro degrau (nota de
implementação no ADR). A v3 e a v3-s5 foram refeitas juntas em 28/10, entre 05h14 e 06h41, para a H7:
[v3](../2026-10-28-c1-v3/summary.md) e [v3-s5](../2026-10-28-c1-v3-s5/summary.md).

## Resultado

| Fase | Maior degrau sustentado | Parou em | O que parou |
|---|---:|---:|---|
| v1 | 300 | 600 | app fora do ar, com a memória no limite desde o início do degrau; 94% de KO nos dois lados |
| v1-big | 300 | 600 | não caiu, mas comandos e consultas com p95 de 60 s; leitura de disco a 2,3 ms por bloco (0,24–0,27 a 300/s) |
| v1-replica | 150 | 300 | app das consultas fora do ar; a réplica lê do disco 2 a 3 vezes mais devagar que a v1 |
| v2 | 300 | 600 | Postgres no teto (200% de CPU) e app fora do ar; 47% de KO nos dois lados |
| v3 | 300 | 600 | query-api fora do ar na r2 (a r1 passou); comandos dentro do critério nas duas |
| v3, 28/10 | 300 | 600 | query-api fora do ar na r1; comandos dentro do critério (p95 de 33 ms) |
| v3-s5, 28/10 | nenhum (começa em 600) | 600 | as duas query-apis fora do ar; comandos com p95 de 7 s |

Tabela e gráfico gerados: [capacity.md](capacity.md). A v3 refeita repete o resultado de 27/10: 300, com p95 de 16 ms
nos comandos e 34 nas consultas (mediana). Na v1, a r1 do degrau de 300/s teve um colapso de cache (p95
de 10 s nos dois lados, 0,37 ms por bloco lido contra 0,15 nas outras duas), e o degrau passou por 2 de 3.

**A medida do ADR não separa quatro das cinco fases.** v1, v1-big, v2 e v3 empatam em 300, e os degraus dobram a
carga: a capacidade de cada uma está em algum ponto entre 300 e 600, e a escada não diz onde. A régua foi fixada
antes de medir e fica como está; a margem no último degrau sustentado (abaixo) é uma leitura secundária, sem critério
escrito antes.

p95 em ms, mediana das 3 repetições:

| Fase | 150/s: comandos | 150/s: consultas | 300/s: comandos | 300/s: consultas |
|---|---:|---:|---:|---:|
| v1 | 5 | 22 | 6 | 35 |
| v1-big | 8 | 27 | 21 | 59 |
| v1-replica | 10 | 54 | 20 (r1) | não sustentou |
| v2 | 6 | 18 | 6 | 19 |
| v3 | 11 | 19 | 19 | 31 |

A v2 chega a 300 com folga: as consultas mantêm o p95 de 150/s, e ela lê do disco um quarto dos blocos da v1. A v1-big
foi mais lenta que a v1 no mesmo degrau, com mais CPU, mais memória e mais cache; o tempo por bloco lido foi maior nela
(0,24–0,27 ms contra 0,15).

## Previsões

- **P1, metade confirmada e metade refutada.** A v1 e a v1-big param nos dois lados no mesmo degrau (600). Mas a
  v1-big não sustenta um degrau a mais: com o dobro do hardware, empata em 300.
- **P2, confirmada nos degraus alcançados.** A 300/s, os comandos da v1-replica ficam em p95 de 20 ms e 0% de KO
  enquanto as consultas dão 81% de KO. As consultas param um degrau abaixo da v1-big, o limite que a previsão admitia.
- **P3, refutada na metade das consultas e confirmada na dos comandos.** A v2 não sustenta um degrau acima da v1 (as
  duas param em 600). Os comandos param junto, com o Postgres no teto.
- **P4, confirmada.** A v3 sustenta um degrau de consultas acima da v1-replica (300 contra 150). A 600/s, os comandos
  ficam em p95 de 20 e 36 ms, sem KO, enquanto as consultas caem.

## Leitura pelas regras do ADR

- **H3 confirmada.** A 600/s, a v3 sustenta os comandos e a v1 e a v1-big não: 94% e 83% de KO nos comandos delas,
  contra 0% na v3. É a pergunta que a taxa fixa de 21/10 não alcançou.
- **"O CQRS vence a réplica no C1"**, pela regra escrita antes (P4 confirmada). A ressalva pesa tanto quanto o
  veredito: a réplica perdeu também para a v1 sem réplica. Ela lê o mesmo número de blocos do disco que a v1 (~900 mil
  por execução a 150/s), mas cada bloco custa 0,23–0,31 ms, contra 0,10 na v1. A réplica não traz disco próprio: ela
  reaplica todo o WAL no mesmo disco virtual da VM e disputa I/O com o primário. Os comandos dela também ficam mais
  lentos (p95 de 10 ms contra 5 na v1, a 150/s), o mesmo mecanismo da cauda da v3
  ([diagnóstico](../2026-10-27-v3-diag/README.md)). O ADR declarou antes a contenção de disco como limite das fases
  com dois stores. O que o C1 mostra é que, **num host só, a réplica piora a leitura**. Se uma réplica com disco
  próprio alcançaria a v3, ele não responde.
- **H8 inconclusiva no degrau que decide.** Os comandos da v1-replica ficaram protegidos a 300/s, mas ali a v1 também
  os sustentava. O degrau em que a v1 perde os comandos (600) não foi medido na v1-replica, porque a escada para no
  primeiro degrau em que a fase falha, e as consultas dela falharam antes. A regra de parada acopla comandos e
  consultas, e isso só aparece agora. Fica como limite, sem mudar a régua.
- **H7 não confirmada, e a refutação escrita também não aconteceu.** A v3-s5 não sustenta 600, o degrau em que a v3
  para, e caiu mais que ela: as duas query-apis fora do ar e os comandos com p95 de 7 s, contra 33 ms na v3. A regra
  refutava a H7 se o Mongo estivesse no teto de CPU a 600/s na v3. Não estava: p90 de 127% num limite de 200%, média
  de 90% no primeiro minuto. Nenhum container da v3 chegou ao teto (a query-api ficou em 129% de 200% no primeiro
  minuto, e o host em 294% de 800%). Pela letra da regra, a H7 fica sem veredito.

  O que os dados mostram, lido depois de medir e sem critério escrito antes, é o disco. A 300/s, o Mongo lê do disco
  ~1150 páginas/s, a 0,30–0,37 ms cada (o working set não cabe no cache dele). A r1 de 600/s que passou em 27/10 leu
  2300 páginas/s a 0,33 ms. As que caíram leram cada página mais caro: 0,45 e 0,49 ms na v3, 1,90 ms na v3-s5. Na
  v3-s5, o fsync médio do WAL foi a 3,5 ms (0,97 na v3 a 600/s, 0,65 a 300/s), e é isso que derruba os comandos dela: o
  mesmo mecanismo do [diagnóstico](../2026-10-27-v3-diag/README.md), agora com o disco saturado. O host chegou perto
  do teto na v3-s5 (p90 de 611% de 800%), o que também pesa.

  A frase da regra, "escalar a aplicação não escala o store", vale para estes dados. O mecanismo dela é que não é a
  CPU: o Mongo para no disco que divide com o Postgres. Os limites da comparação: uma repetição em cada fase; a v3-s5
  começou em 600 depois de 300 s de aquecimento a 75/s, enquanto a v3 chegou a 600 depois de 32 min de degraus (de 75 a 300/s), com o
  cache do Mongo mais quente (ele tinha lido 1,4 milhão de páginas antes do degrau, contra 270 mil na v3-s5). O
  desenho do ADR (a v3-s5 começa onde a v3 parou) traz essa diferença, e ela pesa contra a v3-s5.

## O modo de falha

Em quatro das cinco fases, a JVM que atende as consultas saiu do ar no degrau em que a fase parou. Ela ficou de pé,
mas sem responder ao health, com a memória do container no limite (1 GB). A v1-big, com 2 GB, não caiu, mas respondeu
em 60 s.

O mecanismo provável é o heap. Com threads virtuais, cada requisição que chega vira uma thread esperando conexão
(4945 no ensaio). Quando o banco não acompanha, a fila cresce até o heap acabar. O `OutOfMemoryError` aparece no log
do ensaio e da primeira tentativa da v1 nesta noite. Das outras execuções não há log: o `day.sh` apaga os containers
entre as fases.

Nenhuma fase perde vazão aos poucos: todas caem de uma vez, um degrau depois do último sustentado. Um limite de
concorrência na entrada (um semáforo, ou o `max-connections` do Tomcat) trocaria a queda por recusas com 503. Seria
uma mudança de código igual em todas as fases, e fica fora deste marco.
