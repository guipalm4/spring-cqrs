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
- Diagnóstico da cauda da v3 com o seed de referência ([dados](benchmarks/2026-10-27-v3-diag/README.md)): com o
  projetor parado, os picos acima de 60 ms caem de 28 para 2 em três pares intercalados, e o p99 vai de 41–143 para
  14–19 ms. Nos segundos com pico, o fsync do WAL leva 4 a 5 vezes mais. A hipótese de 22/10 passa de "não confirmada"
  para confirmada. Ainda não separado: se o comando espera o próprio fsync ou o lock da conta de quem está no fsync.
- O primeiro dia C1 (23h) abortou na v1, no degrau de 600/s. Até ali, 75, 150 e 300/s foram sustentados nas três
  repetições. Na r1 do degrau de 600/s, a JVM ficou sem heap e não voltou; a r2 deu 100% de KO e a bancada quebrou ao
  extrair uma série vazia. Eu tinha escrito, horas antes, que a queda "não muda a capacidade medida", sem tratar uma
  fase que não volta. Correção: uma repetição com a fase fora do ar reprova o degrau e encerra a escada (nota no
  ADR-0013). O dia foi refeito do zero; o parcial ficou em `bench/target`, fora do Git.

## 2026-10-28 — C1: a réplica piora a leitura num host só, e a escada não separa as outras

- Resultado do C1 ([análise](benchmarks/2026-10-27-c1/README.md)): v1, v1-big, v2 e v3 sustentam 300 consultas/s; a
  v1-replica, 150. Esperávamos a v1-replica perto da v1-big (P2 admitia um degrau abaixo, e foi o que deu). Não
  esperávamos que ela perdesse também para a v1: no mesmo disco virtual, cada bloco lido custa 2 a 3 vezes mais.
- H3 confirmada pela regra do ADR: a 600/s, os comandos da v3 ficam sem erro, e os da v1 e da v1-big têm 94% e 83% de
  KO. P1 (a v1-big sustenta um degrau a mais) e P3 (a v2 sustenta um degrau acima da v1) refutadas.
- O que a régua não mostra: com degraus que dobram, quatro fases empatam em 300. A margem a 300/s separa (a v2 com p95
  de 19 ms nas consultas; a v1-big com 59 ms, pior que a v1 com o dobro do hardware), mas não tem critério escrito
  antes. A H8 ficou inconclusiva no degrau que decide: a regra de parada acopla comandos e consultas.
- Erros no meio: a v3-s5 aqueceu a 600/s com as JVMs frias e chegou morta ao primeiro degrau. O resultado foi
  descartado, e a v3 e a v3-s5 foram refeitas juntas. Nenhuma fase degrada aos poucos: a JVM das consultas sai do ar
  com a memória no limite. Os logs se perderam, porque o `day.sh` apaga os containers.
- H7 (v3 e v3-s5 refeitas em 28/10): a v3 repete 300, e a v3-s5 não sustenta 600, com os comandos caindo junto (p95 de
  7 s). Esperávamos uma de duas: a segunda query-api sustentar o degrau, ou o Mongo no teto de CPU (a refutação
  escrita). Não deu nenhuma das duas: o Mongo ficou em 127% de 200% (p90). O que os dados mostram, lido depois, é o
  disco: cada página que o Mongo lê custa de 0,33 a 1,90 ms, e o fsync do WAL vai a 3,5 ms na v3-s5. A H7 fica sem
  veredito pela letra da regra. A v3-s5 ainda começou com o cache do Mongo mais frio que a v3.
- Antes do C2: o C1 parou no disco, e o C2 é escrita. Para não escolher a leitura depois de ver os dados, ficou
  escrito no [ADR-0013](adr/0013-estudo-de-capacidade.md) (nota antes do C2) que os vereditos saem só pela regra e que
  o fsync do WAL e o custo por página do Mongo entram ao lado, com um limiar fixo (o dobro do degrau anterior) para
  atribuir a parada ao disco.

## 2026-10-29 — C2: duas noites perdidas para a bancada, e um ponto final

- 28/10: o C2 parou na v1-big, a 400/s. Uma amostra perdida do `docker stats` matou o laço de coleta, o `run.sh` saiu
  com o amostrador do Postgres vivo, e o `day.sh` ficou 10 h esperando. 29/10, 18h: o `ls | head` que achava o
  relatório do Gatling tomou SIGPIPE (335 relatórios) e parou a v1-big de novo. Os dados das duas noites foram
  apagados: falha da bancada não vira resultado.
- O que mudou na bancada: só o que invalida o dado é fatal (Gatling, relatório, veredito); a coleta auxiliar vira aviso
  em `warnings.txt`; uma fase com falha não derruba as seguintes; o amostrador para em qualquer saída. Antes da noite,
  um ensaio do dia inteiro (as cinco fases, seed 0,01, um degrau que derruba cada uma).
- Decidido antes de medir ([ADR-0013](adr/0013-estudo-de-capacidade.md), nota antes do C2): esta é a medição final
  do C2, e o M4.5 fecha com ela. Empate é resultado; hipótese sem veredito vai para o M6 como limite, sem medição nova.

## 2026-10-30 — C2: o CQRS físico refutado, e a v2 em primeiro

- A noite correu inteira, sem falha da bancada: cinco fases, nenhum aviso. Maior degrau de escrita sustentado: v2
  400, v1 200, v1-big 100, v3 50, v1-replica nenhum ([análise](benchmarks/2026-10-29-c2/README.md)).
- Esperávamos que a v3 sustentasse a escrita acima da v1 (P7) e que a v2 parasse junto ou antes dela (P6). Deu o
  contrário nas duas. A v3 parou dois degraus abaixo da v1, pelos comandos, com a projeção em dia: o CQRS físico fica
  refutado no C2, e a H9 também. A v2, que tira os índices de leitura do modelo de escrita mas no mesmo Postgres,
  sustentou o dobro da v1.
- Pela regra do fsync médio, a parada da v3 não é do disco. Lido depois: a mediana dos comandos fica em 3 ms, e a
  cauda vem de travadas de 1 a 1,8 s a cada ~32 s, seguidas de fsync do WAL de até 33 ms. É a cauda do diagnóstico,
  agora grande o bastante para passar do critério. A média do degrau não a enxerga.
- Surpresa sem causa achada: a v1-replica caiu no primeiro degrau, a mesma carga que ela sustentou no C1. A Q3 na
  réplica foi de 4,6 ms para 1,6 s, lendo 100 vezes mais páginas do disco. Pela regra, não se repete: fica como
  resultado, com a ressalva, e o mecanismo da P5 vai para o M6.
