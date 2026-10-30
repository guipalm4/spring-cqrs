# spring-cqrs

Ledger financeiro que começa com um modelo único (leitura e escrita no mesmo Postgres) e evolui para
CQRS, medindo a cada fase o que melhora e o que custa.

> **Status:** M4.5 — levadas até a saturação, as fases se separam pelo cenário. No pico de leitura (C1), a v3 (CQRS
> físico) é, das fases medidas a 600 consultas/s, a única que mantém os comandos de pé; na escrita intensa (C2), ela
> sustenta um quarto da v1, com os dois stores no mesmo disco. A v2 (CQRS lógico) empata no C1 e lidera o C2; a réplica
> de leitura foi a pior fase nos dois ([capítulo 5](#5-em-que-regime-o-cqrs-se-paga-capacidade-m45-17)). Próximo:
> OpenSearch para o Q4 (M5). Escopo, hipóteses e marcos em [docs/SCOPE.md](docs/SCOPE.md); contrato e dataset no
> [ADR-0008](docs/adr/0008-contrato-v1-e-dataset.md).

## A história

Esta é a história de como um problema comum de um modelo único leva ao CQRS: uma aplicação tradicional, com
a baseline bem ajustada, que falha sob carga mista, e a evolução em fases até leitura e escrita com modelos
próprios. Cada passo é justificado por medição, e a alternativa mais barata é medida antes da mais cara, para
que o ganho do CQRS seja atribuível a ele.

Cada capítulo é um marco: a pergunta, a medição que a responde, a decisão que ela motivou e o que custou.
Os números vêm de `make bench`, os gráficos são gerados pelo `bench/Report.java` a partir dos dados
versionados, e cada capítulo aponta para os dados brutos. Resultados desfavoráveis ao CQRS entram também.

### 1. O problema: a leitura derruba a escrita (v1, M2)

**Pergunta.** Num ledger com modelo único (comandos e consultas na mesma aplicação, no mesmo Postgres,
dividindo um pool de 10 conexões), a carga de leitura degrada a escrita? (H1)

**Antes de medir, uma baseline justa.** Culpar a arquitetura por um índice ruim invalidaria a comparação. A
revisão da baseline ([ADR-0009](docs/adr/0009-revisao-da-baseline-e-parametros-do-benchmark.md)) corrigiu a
estatística de `account_id`, trocou os índices de busca por índices compostos com a conta e dimensionou o
Postgres para o container: a busca textual (Q4) numa conta empresarial caiu de 3,7 s para 0,2–0,5 s a frio. O
que sobrou é do modelo: 20 milhões de lançamentos (7,2 GB), os de cada conta espalhados pela tabela, para um
cache de até 2 GB.

**Evidência.** Escrita constante a 20 comandos/s; leitura a 0 (`s1`), 25, 50 e 75 consultas/s (`s3-rNNN`);
três repetições de 3 minutos cada.

<!-- java bench/Report.java summarize docs/benchmarks/2026-10-19-v1 -->
```mermaid
xychart-beta horizontal
    title "p95 dos comandos por execução"
    x-axis ["só escrita, r1", "só escrita, r2", "só escrita, r3", "25 consultas/s, r1", "25 consultas/s, r2", "25 consultas/s, r3", "50 consultas/s, r1", "50 consultas/s, r2", "50 consultas/s, r3", "75 consultas/s, r1", "75 consultas/s, r2", "75 consultas/s, r3"]
    y-axis "segundos"
    bar [0.04, 0.04, 0.05, 0.04, 0.48, 0.07, 23.48, 29.86, 0.25, 44.27, 2.09, 30.34]
```

Sem leitura, o p95 dos comandos fica em 35–53 ms. Com 25 consultas/s, sobe até 483 ms, sem colapsar. Com 50
ou mais, o sistema colapsou em 6 de 9 execuções (contando as só de leitura): p95 de 21 a 44 s e até 23% de
requisições com falha.

**Mecanismo.** O comando não ficou mais lento dentro do banco; ele esperou na fila por uma conexão.

| Execução com 50 consultas/s (r1) | |
|---|---:|
| Comando dentro do Postgres | 8 ms |
| Espera média por uma conexão do pool | 6,4 s |
| p95 do comando no cliente | 23,5 s |

<!-- java bench/Report.java chart docs/benchmarks/2026-10-19-v1/runs/s3-r050/r1/timeseries.csv hikari_pending "..." -->
```mermaid
xychart-beta
    title "Requisições esperando conexão (50 consultas/s, r1)"
    x-axis "segundos desde o início" 0 --> 210
    line [0.00, 0.00, 102.00, 159.00, 0.00, 0.00, 14.00, 0.00, 0.00, 24.00, 78.00, 0.00, 0.00, 16.00, 79.00, 170.00, 158.00, 104.00, 19.00, 0.00, 0.00, 4.00, 0.00, 0.00, 0.00, 79.00, 219.00, 370.00, 486.00, 671.00, 784.00, 965.00, 1071.00, 1112.00, 1129.00, 1055.00, 1102.00, 1150.00, 1078.00, 833.00, 654.00, 405.00, 81.00]
```

As consultas limitadas por I/O seguram as 10 conexões. Quando uma fila se forma, as dez passam a rodar
consultas ao mesmo tempo, cada uma fica mais lenta, e a fila se realimenta até a carga acabar: no gráfico, ela
oscila e se desfaz até ~125 s; dali em diante cresce sem parar, passa de mil requisições e só esvazia quando o
teste termina. A busca textual (Q4)
responde por 58–81% do tempo de leitura no banco.

**O que mudou no entendimento.** H1 se confirma, mas não como foi escrita: o canal é o pool de conexões, não
a disputa dentro do Postgres; a leitura que pesa é o Q4, não Q2/Q3; e a degradação não é gradual, é um colapso
tudo ou nada.

**Decisão.** Não ir direto para o CQRS. Se o problema é a fila num pool compartilhado, a correção mais barata
é separar os pools, e ela precisa ser medida antes: sem isso, o ganho da v2 e da v3 não é atribuível ao CQRS
(capítulo 2).

**Limites.** Dataset sintético e carga gerada; uma máquina (macOS com Docker Desktop); três repetições só
permitem dizer que o colapso é frequente a partir de 50 consultas/s e raro a 25/s.

Dados: [análise completa](docs/benchmarks/2026-10-19-v1/README.md) · [estado por execução](docs/benchmarks/2026-10-19-v1/summary.md) ·
[planos de execução](docs/benchmarks/2026-10-19-v1/explain.md) · tag [`m2`](https://github.com/guipalm4/spring-cqrs/tree/m2)

### 2. A correção barata: separar os pools (controle, [#11](https://github.com/guipalm4/spring-cqrs/issues/11))

**Pergunta.** Um pool só para comandos, com o mesmo total de conexões, elimina a degradação da escrita sem
mudar a arquitetura? Se sim, o argumento do CQRS para a escrita (H3) enfraquece, e o que sobra para ele é a
leitura.

**Antes de medir, a regra.** A variante liga por profile, sem mudar a v1: 2 conexões para comandos e 8 para
consultas, divisão fixada a partir do uso de conexão dos comandos no M2, e o que contaria como "basta" e "não basta"
escrito antes da medição ([ADR-0010](docs/adr/0010-controle-pools-separados.md)).

**Um tropeço no caminho.** Comparada à v1 do capítulo 1, a variante parecia resolver: 2 colapsos em 9 contra 6.
Mas o S1, que só tem escrita e não depende dos pools, também ficou ~40% mais rápido, com o mesmo ambiente declarado.
A v1 foi medida de novo no mesmo dia e colapsou em 3 de 9. A diferença era do dia, não dos pools. Desde então,
fases só se comparam com a v1 medida no mesmo dia.

**Evidência.** Mesmo protocolo do capítulo 1, as duas no mesmo dia, cada uma com o dataset recarregado.

<!-- java bench/Report.java summarize docs/benchmarks/2026-10-20-v1 -->
```mermaid
xychart-beta horizontal
    title "v1: p95 dos comandos por execução (20/10)"
    x-axis ["só escrita, r1", "só escrita, r2", "só escrita, r3", "25 consultas/s, r1", "25 consultas/s, r2", "25 consultas/s, r3", "50 consultas/s, r1", "50 consultas/s, r2", "50 consultas/s, r3", "75 consultas/s, r1", "75 consultas/s, r2", "75 consultas/s, r3"]
    y-axis "segundos"
    bar [0.02, 0.02, 0.05, 0.02, 0.02, 0.05, 0.02, 0.21, 0.81, 0.04, 40.79, 18.76]
```

<!-- java bench/Report.java summarize docs/benchmarks/2026-10-20-v1-split -->
```mermaid
xychart-beta horizontal
    title "Pools separados: p95 dos comandos por execução (20/10)"
    x-axis ["só escrita, r1", "só escrita, r2", "só escrita, r3", "25 consultas/s, r1", "25 consultas/s, r2", "25 consultas/s, r3", "50 consultas/s, r1", "50 consultas/s, r2", "50 consultas/s, r3", "75 consultas/s, r1", "75 consultas/s, r2", "75 consultas/s, r3"]
    y-axis "segundos"
    bar [0.03, 0.03, 0.05, 0.07, 0.02, 0.03, 0.13, 0.18, 15.02, 0.13, 0.05, 0.07]
```

Com leitura a 50/s ou mais, a v1 colapsou em 3 de 9 execuções e a variante em 2 de 9, o que não é diferença com três
repetições. No colapso, os comandos caem junto mesmo com pool próprio: p95 de 15 s e 23% de falhas. Fora dele, as
faixas de p95 se sobrepõem.

**Mecanismo.** O pool de comandos fica livre até a JVM parar. No colapso, milhares de consultas esperando enchem o
heap, e o GC passa a pausar a aplicação inteira:

| Execução | Comando no banco | Comando segurando a conexão | JVM parada em GC (de 180 s) |
|---|---:|---:|---:|
| Pools separados, colapso (50 consultas/s, r3) | 13 ms | 110 ms | 64 s |
| Pools separados, 13 execuções estáveis | 2–5 ms | 5–16 ms | ≤ 2,4 s |

A pausa para também os comandos que já têm conexão. Com 110 ms por comando, 20 comandos/s ocupam 2,2 conexões, mais
que as 2 do pool, e o pool de comandos faz fila também. Separar as conexões não separa o heap.

**O que mudou no entendimento.** O capítulo 1 disse que o canal era o pool. É onde as requisições esperam, mas o
colapso se sustenta pela JVM compartilhada: a fila enche o heap, e o GC atrasa todo mundo.

**Decisão.** A v1 continua com o pool compartilhado (o controle não mudou o resultado), e H3 segue em aberto com um
alvo mais preciso: o que a v3 tem e esta variante não tem é um processo próprio para a escrita. Falta uma correção
barata que ataca o heap: contrapressão (recusar consultas em excesso em vez de enfileirá-las). Sem medi-la, parte do
ganho da v3 pode não ser do CQRS.

**Limites.** Três repetições só mostram diferenças grandes na frequência de colapso; a variação entre dias é do
tamanho do efeito procurado; a divisão 2 + 8 foi dimensionada para o uso normal da conexão, e com o GC o pool de
comandos saturou (uma terceira conexão não evitaria 64 s de pausa). As séries do controle foram exportadas de novo
depois do resultado para incluir o GC.

Dados: [análise completa](docs/benchmarks/2026-10-20-v1-split/README.md) ·
[estado por execução, pools separados](docs/benchmarks/2026-10-20-v1-split/summary.md) ·
[v1 no mesmo dia](docs/benchmarks/2026-10-20-v1/README.md)

### 3. Separar o modelo: CQRS lógico (v2, M3)

**Pergunta.** Projeções no mesmo Postgres, atualizadas na transação do comando, dão às consultas de uma conta a
localidade que o ledger não tem? Quanto isso custa na escrita? (H2, H4)

**Antes de medir, a forma e a regra.** Uma projeção com uma linha por lançamento teria a mesma dispersão da
`ledger_entry`. A v2 guarda um bloco por conta e mês, com os lançamentos já no formato do extrato num `jsonb`, as
palavras do bloco num `tsvector` e os totais mensais numa tabela à parte. É o documento que o MongoDB vai guardar na
v3. A `ledger_entry` perdeu os índices que só serviam à leitura, e um teste confere que a v2 responde exatamente
como o SQL da v1 sobre o mesmo ledger. A forma e o que contaria como confirmação foram escritos antes da medição
([ADR-0011](docs/adr/0011-v2-projecoes-e-escopo-congelado.md)).

**Evidência.** Mesmo protocolo dos capítulos anteriores, v1 e v2 no mesmo dia, cada uma com o dataset recarregado.

<!-- java bench/Report.java summarize docs/benchmarks/2026-10-20-v1-m3 -->
```mermaid
xychart-beta horizontal
    title "v1: p95 dos comandos por execução (20/10, tarde)"
    x-axis ["só escrita, r1", "só escrita, r2", "só escrita, r3", "25 consultas/s, r1", "25 consultas/s, r2", "25 consultas/s, r3", "50 consultas/s, r1", "50 consultas/s, r2", "50 consultas/s, r3", "75 consultas/s, r1", "75 consultas/s, r2", "75 consultas/s, r3"]
    y-axis "segundos"
    bar [0.02, 0.02, 0.02, 0.01, 0.01, 0.01, 0.01, 0.01, 0.01, 0.01, 0.02, 0.01]
```

<!-- java bench/Report.java summarize docs/benchmarks/2026-10-20-v2 -->
```mermaid
xychart-beta horizontal
    title "v2: p95 dos comandos por execução (20/10, tarde)"
    x-axis ["só escrita, r1", "só escrita, r2", "só escrita, r3", "25 consultas/s, r1", "25 consultas/s, r2", "25 consultas/s, r3", "50 consultas/s, r1", "50 consultas/s, r2", "50 consultas/s, r3", "75 consultas/s, r1", "75 consultas/s, r2", "75 consultas/s, r3"]
    y-axis "segundos"
    bar [0.03, 0.02, 0.02, 0.01, 0.02, 0.02, 0.02, 0.01, 0.02, 1.62, 0.01, 0.02]
```

Nenhuma das duas colapsou. A v1 desta tarde é a mesma que colapsou em 6 de 9 no dia 19 e em 3 de 9 hoje de manhã.
O que mudou entre as três foi o tempo para ler uma página fora do cache do Postgres:

| v1 | Colapsos com leitura ≥ 50/s | Tempo por página lida fora do cache |
|---|---:|---:|
| 19/10 | 6 de 9 | 1,8–4,1 ms |
| 20/10, manhã | 3 de 9 | 0,31–2,2 ms |
| 20/10, tarde | 0 de 9 | 0,17–0,21 ms |

Sem uma v1 que colapse para comparar, o que dá para afirmar está no custo de cada consulta:

| No banco, por chamada, 25 consultas/s | v1 | v2 |
|---|---:|---:|
| Q3: tempo / páginas tocadas | 6,0 ms / 4.558 | 1,6 ms / 199 |
| Q2 + Q4: tempo / páginas tocadas | 14,5 ms / 369 | 11,2 ms / 104 |
| Q2 + Q4: páginas lidas fora do cache | 58,7 | 16,9 |

| p95 no cliente | v1 | v2 |
|---|---:|---:|
| Q3 empresa, 25 consultas/s | 25 ms | 11 ms |
| Q2 empresa, 25 consultas/s | 25 ms | 23 ms |
| Q4 empresa, 25 consultas/s | 85 ms | 68 ms |
| Q4 empresa, 75 consultas/s | 134 ms | 60 ms |
| Q1 (as duas contas), 25 consultas/s | 6 ms | 7–8 ms |
| Comandos, só escrita | 22 ms | 23 ms |

**H2 confirmada:** o Q3 empresarial caiu mais da metade (−56%) e o Q2 bem menos (−8%). O maior ganho em tempo, porém,
ficou fora do H2, no Q4. **H4 confirmada no que afirma:** o Q1 não melhorou. Ele ficou 1–2 ms acima da faixa da v1, o
que o critério combinado não previa, com o mesmo custo no banco.

**O custo.** No benchmark, cada comando gasta ~0,7 ms a mais no Postgres (+40%): o lançamento ficou mais barato sem os
índices de leitura, mas as duas projeções somam 1,5 ms. No cliente, a diferença some no ruído. Esse número é otimista:
os comandos do benchmark caem num mês cujos blocos começam vazios. Num bloco cheio, um lançamento custa 2,9 ms e
grava 104 KB no WAL, porque o mês é reescrito inteiro. A v2 também ocupa 8,9 GB contra 7,2 GB e leva o dobro do tempo
no seed.

**O que mudou no entendimento.** O colapso do M2 é real, mas depende de leituras lentas: com o disco rápido, nem a v1
cai. A v2 ataca exatamente esse ponto, porque cada consulta precisa de 3 a 8 vezes menos páginas fora do cache. Que
isso a proteja quando o disco fica lento é inferência: hoje não houve disco lento para testar.

**Decisão.** A v2 fica como está e vira a segunda referência do M4. Não entra nenhum controle novo
(ADR-0011): a latência do disco passa a acompanhar cada comparação, ao lado da contagem de colapsos. O argumento da v3
fica mais estreito. Ela precisa mostrar o que um processo próprio para a escrita dá além de leituras mais baratas,
e a comparação com a v2 do mesmo dia é que vai dizer isso.

**Limites.** A v1 de referência não colapsou, então "separar o modelo evita o colapso" ficou sem resposta. A latência do
disco, que decide o colapso, não é controlada pelo benchmark. Os blocos vazios subestimam a escrita, e a sonda a mede à
parte. Na v2, uma execução teve 15 s de lentidão no banco, que se resolveu sozinha e cuja causa não foi identificada.
No `pg_stat_statements`, Q2 e Q4 da v2 aparecem somados. São três repetições por cenário.

Dados: [análise completa](docs/benchmarks/2026-10-20-v2/README.md) ·
[estado por execução, v2](docs/benchmarks/2026-10-20-v2/summary.md) ·
[v1 no mesmo dia](docs/benchmarks/2026-10-20-v1-m3/README.md) · [planos e sonda de escrita](docs/benchmarks/2026-10-20-v2/explain.md)

### 4. Separar o armazenamento: CQRS físico (v3, M4)

**Pergunta.** Com escrita e leitura em stores e processos separados, sincronizados por outbox e Kafka, a
interferência desaparece? Qual o preço em atraso de projeção, recursos e operação? Escalar só a leitura
funciona? (H3, H5, H7)

**Antes de medir, um controle e a regra.** A v3 tem `command-api` (Postgres), outbox do Modulith, Kafka, projetor
idempotente e `query-api` (Q1 no Postgres, Q2–Q4 no Mongo, com o mesmo documento conta/mês da v2). Ela tem também o
dobro de JVMs e de cache. Se a escrita da v3 não degradasse, a primeira objeção seria "foi o hardware". Por isso entrou
a v1-big, a v1 sem nenhuma mudança com os recursos da v3 somados, e o que contaria como resposta foi escrito antes
([ADR-0012](docs/adr/0012-v3-projecao-mongo-e-controle-de-hardware.md)). Um trace segue o comando do HTTP até as duas
escritas no Mongo ([registro](docs/trace/README.md)).

**Evidência.** As cinco fases no mesmo dia, cada uma com o dataset recarregado, nas taxas de sempre. Nenhuma
colapsou: 0 de 9 na v1, na v1-big, na v2 e na v3. A v1 usou ~5% do pool, e o Postgres dela, 36% de um núcleo de 200%.

| p95 dos comandos (ms) | Só escrita | 75 consultas/s |
|---|---:|---:|
| v1 | 15 | 9 |
| v1-big | 14 | 9 |
| v2 | 15 | 8 |
| v3 | 14 | 17 |

**H3 e H7 ficaram inconclusivas.** Sem degradação na v1, não houve interferência para a v3 eliminar, e uma `query-api`
já aguentava 75 consultas/s com 10% de CPU; com duas, o p95 das consultas foi de 26 para 22 ms.

**H5 confirmada.** O atraso da projeção ficou em p50 de ~15 ms e p99 de 110–210 ms, sem reentrega. A v3 roda 6
containers contra 2, usa 3,1 GiB de memória contra 0,9 GiB e de 2 a 5 vezes a CPU da v1; o Kafka sozinho, com 20
escritas/s, gasta mais CPU que o Postgres inteiro da v1 sem leitura. No Postgres, o comando ficou mais barato (0,6–0,8
ms contra 1,3–1,7 ms): ele grava o lançamento e o outbox, e nada mais. No cliente, porém, apareceu uma cauda:

<!-- java bench/Report.java chart docs/benchmarks/2026-10-21-v1/runs/s1/r2/seconds.csv max "..." 1 900 -->
```mermaid
xychart-beta
    title "v1: resposta máxima dos comandos a cada segundo, em ms (só escrita, r2)"
    x-axis "segundos desde o início" 0 --> 179
    y-axis 0 --> 900
    line [45.00, 45.00, 19.00, 22.00, 21.00, 29.00, 17.00, 16.00, 18.00, 16.00, 16.00, 21.00, 19.00, 15.00, 20.00, 17.00, 19.00, 20.00, 23.00, 19.00, 22.00, 20.00, 16.00, 14.00, 19.00, 14.00, 23.00, 16.00, 44.00, 15.00, 15.00, 32.00, 27.00, 13.00, 24.00, 12.00, 16.00, 17.00, 22.00, 23.00, 14.00, 14.00, 22.00, 15.00, 24.00, 17.00, 23.00, 14.00, 15.00, 12.00, 15.00, 14.00, 13.00, 18.00, 16.00, 19.00, 18.00, 17.00, 14.00, 12.00, 25.00, 20.00, 17.00, 14.00, 14.00, 16.00, 14.00, 12.00, 25.00, 13.00, 16.00, 22.00, 14.00, 15.00, 13.00, 16.00, 15.00, 14.00, 11.00, 15.00, 17.00, 16.00, 13.00, 11.00, 12.00, 15.00, 14.00, 20.00, 22.00, 16.00, 15.00, 19.00, 15.00, 13.00, 11.00, 14.00, 18.00, 12.00, 14.00, 12.00, 25.00, 18.00, 15.00, 18.00, 15.00, 16.00, 22.00, 11.00, 23.00, 18.00, 22.00, 13.00, 16.00, 19.00, 15.00, 13.00, 27.00, 14.00, 13.00, 15.00, 17.00, 15.00, 10.00, 13.00, 16.00, 15.00, 12.00, 11.00, 14.00, 11.00, 20.00, 13.00, 14.00, 12.00, 16.00, 22.00, 12.00, 23.00, 22.00, 13.00, 14.00, 22.00, 22.00, 24.00, 15.00, 18.00, 50.00, 13.00, 18.00, 12.00, 23.00, 19.00, 18.00, 31.00, 19.00, 11.00, 24.00, 13.00, 13.00, 15.00, 27.00, 15.00, 21.00, 13.00, 17.00, 18.00, 14.00, 23.00, 18.00, 19.00, 11.00, 21.00, 12.00, 24.00, 14.00, 16.00, 18.00, 13.00, 20.00, 24.00]
```

<!-- java bench/Report.java chart docs/benchmarks/2026-10-21-v3/runs/s1/r2/seconds.csv max "..." 1 900 -->
```mermaid
xychart-beta
    title "v3: resposta máxima dos comandos a cada segundo, em ms (só escrita, r2)"
    x-axis "segundos desde o início" 0 --> 179
    y-axis 0 --> 900
    line [41.00, 18.00, 13.00, 27.00, 14.00, 15.00, 18.00, 10.00, 11.00, 14.00, 10.00, 12.00, 10.00, 13.00, 14.00, 11.00, 26.00, 14.00, 11.00, 11.00, 12.00, 11.00, 8.00, 18.00, 7.00, 13.00, 18.00, 18.00, 10.00, 11.00, 10.00, 8.00, 487.00, 390.00, 18.00, 25.00, 40.00, 36.00, 6.00, 8.00, 16.00, 14.00, 21.00, 20.00, 14.00, 86.00, 66.00, 9.00, 228.00, 9.00, 21.00, 12.00, 9.00, 12.00, 7.00, 9.00, 65.00, 15.00, 18.00, 17.00, 17.00, 15.00, 18.00, 14.00, 9.00, 8.00, 32.00, 12.00, 5.00, 11.00, 20.00, 31.00, 23.00, 19.00, 10.00, 12.00, 23.00, 11.00, 10.00, 366.00, 20.00, 17.00, 23.00, 19.00, 12.00, 12.00, 36.00, 30.00, 23.00, 17.00, 17.00, 10.00, 17.00, 135.00, 8.00, 11.00, 11.00, 12.00, 8.00, 8.00, 15.00, 17.00, 10.00, 11.00, 7.00, 19.00, 32.00, 10.00, 9.00, 13.00, 263.00, 33.00, 6.00, 16.00, 13.00, 10.00, 23.00, 15.00, 8.00, 6.00, 16.00, 12.00, 9.00, 13.00, 11.00, 13.00, 53.00, 21.00, 18.00, 7.00, 12.00, 48.00, 9.00, 23.00, 7.00, 11.00, 12.00, 10.00, 9.00, 13.00, 9.00, 157.00, 9.00, 8.00, 9.00, 9.00, 17.00, 8.00, 7.00, 8.00, 16.00, 9.00, 8.00, 29.00, 470.00, 8.00, 67.00, 18.00, 12.00, 15.00, 14.00, 13.00, 22.00, 16.00, 9.00, 8.00, 27.00, 10.00, 13.00, 7.00, 5.00, 15.00, 87.00, 10.00, 17.00, 16.00, 34.00, 7.00, 12.00, 20.00]
```

O p99 dos comandos da v3 sem nenhuma leitura é de ~110 ms, contra ~22 ms nas outras fases: de 9 a 15 segundos por
execução com picos de até 822 ms. Não é GC nem uma instrução lenta. 84% dos picos caem a até 3 s de uma rajada de
escrita do Mongo (43% ao acaso), que grava ~57 KB por comando no mesmo disco virtual do Postgres. A explicação provável
é o fsync do commit esperando o disco, mas isso é correlação, não medição.

Nas leituras, a v3 fica entre a v1 e a v2: Q3 empresarial com p95 de 12 ms, contra 18 na v1 e 4 na v2, com o mesmo
documento. Q1 piorou 3–4 ms.

**O que mudou no entendimento.** A pergunta "o CQRS ganha neste ponto de carga?" não tem resposta estável: a mesma
taxa que derrubou a v1 no dia 19 usou 5% dela no dia 21. Longe da saturação, a v1 basta, a v2 deixa a leitura mais
barata a custo baixo, e a v3 mostra só os custos fixos: mais memória, mais CPU, mais peças e uma cauda na escrita que
o modelo único não tem. A separação física ainda não foi posta no regime em que deveria se pagar.

**Decisão.** Fechar o M4 com o que foi medido e não subir as taxas até a v1 cair, o que seria escolher a carga pelo
resultado. O [ADR-0013](docs/adr/0013-estudo-de-capacidade.md) muda a pergunta para "em que regime o CQRS se paga, e
contra qual alternativa": escadas de carga até a saturação, com o maior degrau sustentado como resultado; dois
cenários com mecanismo próprio (pico de leitura e escrita intensa com busca); e uma réplica de leitura da v1 como
rival, porque é o que um time tentaria antes do CQRS. As previsões estão escritas no ADR.

**Limites.** Taxas longe da saturação; gerador, fases e stores no mesmo host e no mesmo disco virtual; a soma dos
limites de CPU da v3 passa da VM; a causa da cauda é inferida; o Grafana fez GBs de I/O em duas execuções; três
repetições por cenário.

Dados: [análise completa](docs/benchmarks/2026-10-21-v3/README.md) ·
[v3](docs/benchmarks/2026-10-21-v3/summary.md) · [v1](docs/benchmarks/2026-10-21-v1/summary.md) ·
[v1-big](docs/benchmarks/2026-10-21-v1-big/summary.md) · [v2](docs/benchmarks/2026-10-21-v2/summary.md) ·
[v3 com duas `query-api`](docs/benchmarks/2026-10-21-v3-s5/summary.md) · [trace](docs/trace/README.md) ·
tag [`m4`](https://github.com/guipalm4/spring-cqrs/tree/m4)

### 5. Em que regime o CQRS se paga: capacidade (M4.5, [#17](https://github.com/guipalm4/spring-cqrs/issues/17))

**Pergunta.** Levando cada fase até a saturação, a v3 sustenta mais carga que a v1 e que uma réplica de leitura da v1,
num pico de leitura (C1) e numa escrita intensa com busca (C2)? (H8, H9, e a H3 e a H7 relidas)

**Antes de medir, a régua.** O [ADR-0013](docs/adr/0013-estudo-de-capacidade.md) trocou a taxa fixa por escadas de
carga, com o maior degrau sustentado como resultado:
- critérios absolutos: comandos com p95 ≤ 50 ms, consultas com p95 ≤ 250 ms, KO ≤ 1%, vazão ≥ 98% da oferecida e, com
  consistência eventual, atraso p99 ≤ 2 s;
- degrau sustentado em pelo menos 2 de 3 repetições de 180 s;
- a escada para no primeiro degrau que não passa.

Entrou uma fase nova, a **v1-replica**: a v1 sem mudança de código, com as consultas numa réplica *streaming* do
Postgres. É o que um time tentaria antes do CQRS. As previsões P1–P7 e o que contaria como vitória de cada lado foram
escritos antes. Antes do C2, ficou escrito também que ele seria a medição final, que o disco seria informado ao lado
do veredito sem mudá-lo e que empate é resultado.

**Evidência: C1, pico de leitura** (escrita a 20/s, leitura de 75 a 1200/s).

<!-- java bench/Report.java capacity docs/benchmarks/2026-10-27-c1-* docs/benchmarks/2026-10-28-c1-* -->
```mermaid
xychart-beta horizontal
    title "C1: maior degrau sustentado (consultas/s)"
    x-axis ["v1", "v1-big", "v1-replica", "v2", "v3 (27/10)", "v3 (28/10)"]
    y-axis "consultas/s"
    bar [300.00, 300.00, 150.00, 300.00, 300.00, 300.00]
```

- **H3 confirmada.** A 600/s, os comandos da v3 ficam com 0% de KO, e os da v1 e da v1-big com 94% e 83%. É a
  interferência que a taxa fixa do M4 não alcançou.
- **O CQRS vence a réplica no C1** (P4): 300 contra 150. A réplica perdeu também para a v1 sem réplica. Ela reaplica
  todo o WAL no mesmo disco virtual, e cada bloco que lê custa 2 a 3 vezes mais.
- **H8 inconclusiva.** O degrau em que a réplica protegeria a escrita (600) não foi medido, porque as consultas dela
  caíram antes e a regra de parada acopla os dois lados.
- **H7 sem veredito pela regra.** A v3 com duas `query-api` não sustentou 600, e o Mongo não estava no teto de CPU. Lido
  depois: o limite foi o disco.
- **v1, v1-big, v2 e v3 empatam em 300.** Com degraus que dobram, a régua não as separa.

**Evidência: C2, escrita intensa** (leitura a 75/s, escrita de 20 a 800/s). A v1-replica não sustentou nenhum degrau
e não aparece no gráfico.

<!-- java bench/Report.java capacity docs/benchmarks/2026-10-29-c2-* -->
```mermaid
xychart-beta horizontal
    title "C2: maior degrau sustentado (comandos/s)"
    x-axis ["v1", "v1-big", "v2", "v3"]
    y-axis "comandos/s"
    bar [200.00, 100.00, 400.00, 50.00]
```

- **O CQRS físico está refutado no C2.** A v3 parou dois degraus abaixo da v1, e pelos comandos (p95 de 151 a 274
  ms a 100/s), com a projeção em dia (atraso p99 de 0,36 s). A **H9** cai junto: tirar os índices de leitura do
  modelo de escrita num store separado reduziu a capacidade de escrita a um quarto.
- **A v2 sustentou o dobro da v1** (P6 refutada a favor dela). Ela também tira o `search_vector` e os GIN da tabela
  de escrita, mas no mesmo Postgres, e lê do disco de um sexto a um terço das páginas da v1.
- **A v1-replica caiu no primeiro degrau**, a mesma carga que sustentou no C1, com a Q3 na réplica 340 vezes mais
  lenta. A causa não foi achada. Pela regra, não se repete: a P5 fica refutada pela letra, com o mecanismo sem
  teste.

A mediana dos comandos da v3 a 100/s é de 3 ms. O que derruba o p95 são travadas periódicas:

<!-- java bench/Report.java chart docs/benchmarks/2026-10-29-c2-v3/runs/c2-w0100/r2/seconds.csv max "..." 1 2000 -->
```mermaid
xychart-beta
    title "v3 a 100 comandos/s: resposta máxima a cada segundo, comandos e consultas, em ms (r2)"
    x-axis "segundos desde o início" 0 --> 179
    y-axis 0 --> 2000
    line [65.00, 63.00, 60.00, 40.00, 26.00, 20.00, 41.00, 28.00, 45.00, 24.00, 43.00, 25.00, 25.00, 40.00, 34.00, 1347.00, 1044.00, 113.00, 25.00, 31.00, 17.00, 21.00, 27.00, 126.00, 42.00, 24.00, 33.00, 20.00, 24.00, 24.00, 21.00, 83.00, 20.00, 25.00, 20.00, 26.00, 42.00, 19.00, 1493.00, 1234.00, 341.00, 18.00, 26.00, 18.00, 25.00, 41.00, 29.00, 24.00, 27.00, 26.00, 31.00, 32.00, 30.00, 23.00, 11.00, 38.00, 25.00, 28.00, 17.00, 25.00, 26.00, 30.00, 21.00, 29.00, 19.00, 22.00, 34.00, 33.00, 44.00, 16.00, 1421.00, 1371.00, 396.00, 34.00, 23.00, 18.00, 40.00, 244.00, 14.00, 19.00, 17.00, 26.00, 17.00, 31.00, 19.00, 48.00, 24.00, 51.00, 26.00, 23.00, 33.00, 129.00, 71.00, 27.00, 25.00, 38.00, 41.00, 17.00, 28.00, 24.00, 22.00, 31.00, 33.00, 1594.00, 836.00, 26.00, 23.00, 29.00, 28.00, 28.00, 22.00, 39.00, 182.00, 24.00, 20.00, 36.00, 25.00, 24.00, 44.00, 25.00, 18.00, 21.00, 22.00, 30.00, 18.00, 20.00, 26.00, 42.00, 28.00, 18.00, 19.00, 29.00, 26.00, 28.00, 30.00, 1758.00, 1225.00, 225.00, 38.00, 35.00, 26.00, 25.00, 35.00, 17.00, 47.00, 30.00, 30.00, 20.00, 22.00, 22.00, 23.00, 24.00, 10.00, 28.00, 43.00, 91.00, 54.00, 37.00, 26.00, 18.00, 27.00, 24.00, 22.00, 17.00, 25.00, 25.00, 33.00, 1830.00, 1693.00, 682.00, 31.00, 39.00, 30.00, 72.00, 25.00, 19.00, 57.00, 23.00, 33.00, 24.00]
```

São travadas de 1 a 1,8 s a cada ~32 s. Cada uma vem seguida de um segundo em que o fsync do WAL leva de 2,5 a 33 ms,
contra 0,6 ms nos outros. É a cauda do capítulo 4, agora grande o bastante para passar do critério. Por comando, a v3
grava 70 KB no Mongo e 3,7 KB de WAL, no mesmo disco. A regra escrita antes (fsync médio do degrau com pelo menos o
dobro do anterior) não atribui essa parada ao disco: a média não enxerga 14 segundos em 180. A leitura acima foi feita
depois de medir.

**O que mudou no entendimento.** O CQRS físico se paga onde a leitura satura: no C1, das fases medidas a 600/s, a v3 é
a única que mantém os comandos de pé. Ele não se paga quando a escrita é a carga e os dois stores dividem um disco: a
v3 grava muito mais por comando (70 KB no Mongo, além do Kafka), e as travadas do disco derrubam os comandos (leitura
feita depois de medir). O que ganhou nos dois cenários foi o mais barato dos separadores. A v2, com as projeções no
mesmo Postgres, empata em 300 no C1 com as consultas mais rápidas e lidera o C2. A réplica, a alternativa "sem mudar
código", foi a pior fase nos dois dias neste host. O modo de falha também ficou claro: nenhuma fase degrada aos
poucos. As threads virtuais aceitam requisições até o heap acabar, e a JVM cai de uma vez um degrau depois do último
sustentado.

**Decisão.** Fechar o M4.5 com as duas noites, sem medição nova e sem mudar a régua. O que ficou sem resposta vai para
o M6 como limite do estudo:
- H8 e o mecanismo da P5;
- a queda da réplica no C2;
- o disco único do host;
- a falta de limite de concorrência na entrada.

Um semáforo ou um `max-connections` trocaria a queda por recusas, igual em todas as fases. Ele fica fora deste marco
para não mudar o código no meio do estudo.

**Custo.** Quatro noites de medição. Duas se perderam por falhas da bancada (amostrador órfão, SIGPIPE no `ls | head`)
e foram apagadas ([diário](docs/diario.md)). A bancada passou a separar o que invalida o dado (fatal) da coleta
auxiliar (aviso), e foi ensaiada inteira antes da noite final.

**Limites.**
- Um host, com fases, stores e gerador no mesmo disco virtual. Em produção, o Mongo e a réplica teriam disco próprio.
- Degraus que dobram: a capacidade de cada fase está entre o último degrau sustentado e o seguinte.
- Uma noite por cenário. O C1 e o C2 não se comparam entre si.
- A réplica mudou de comportamento entre as noites com a mesma carga, e a causa não foi isolada.

Dados: [análise do C1](docs/benchmarks/2026-10-27-c1/README.md) ·
[análise do C2](docs/benchmarks/2026-10-29-c2/README.md) ·
[diagnóstico da cauda da v3](docs/benchmarks/2026-10-27-v3-diag/README.md) ·
[ADR-0013](docs/adr/0013-estudo-de-capacidade.md) ·
tag [`m4.5`](https://github.com/guipalm4/spring-cqrs/tree/m4.5)

### 6. Um modelo de escrita, vários de leitura: OpenSearch (M5)

**Pergunta.** Uma projeção dedicada à busca supera Postgres e Mongo no Q4, a consulta que dominou o
problema da v1? (H6) *A medir.*

### 7. Balanço: quando não usar CQRS (M6)

**Pergunta.** Somando ganhos e custos medidos, em que condições cada fase compensa, e em quais a v1 com
ajustes bastava? *A escrever.*

## Fases

| Fase | O que muda | Módulo |
|---|---|---|
| v1 — Baseline | — | `app-baseline` |
| v2 — CQRS lógico | Modelos separados, mesmo Postgres | `app-cqrs` |
| v3 — CQRS físico | Stores de leitura separados, sincronização assíncrona | `app-cqrs` + projetores |

## Stack

Java 25 · Spring Boot 4.1 · PostgreSQL 18 · MongoDB · OpenSearch · Kafka · Spring Modulith ·
Testcontainers · Gatling · Prometheus · Grafana. Decisões em [docs/adr](docs/adr).

## Rodando

Pré-requisitos: JDK 25 e um runtime Docker com Compose v2.

```bash
make verify          # build + testes (sobe Postgres via Testcontainers)
make up PHASE=v1     # Postgres, app-baseline, Prometheus, Grafana
make smoke           # confere a saúde da aplicação
make seed SCALE=0.01 # APAGA e carrega um dataset (1 = referência: 100 mil contas, ~20 mi lançamentos)
make bench PHASE=v1  # benchmark da fase no ar (~1 h com SCALE=1); resultados em docs/benchmarks/
make down
```

| Serviço | Endereço |
|---|---|
| Aplicação | http://localhost:8080/actuator/health |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Contrato OpenAPI | http://localhost:8080/v3/api-docs ([versionado](docs/api/openapi.json)) |
| Collection Postman | [docs/api/ledger.postman_collection.json](docs/api/ledger.postman_collection.json) |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000/d/ledger (aplicação e Postgres) |

## Benchmark

`make bench` roda a mesma simulação Gatling contra a fase no ar: só escrita (S1), escrita constante com
leitura crescente (S3) e só leitura (S2), com aquecimento descartado e três repetições intercaladas.
Metodologia no [ADR-0005](docs/adr/0005-metodologia-de-benchmark.md), parâmetros e revisão da baseline no
[ADR-0009](docs/adr/0009-revisao-da-baseline-e-parametros-do-benchmark.md), resultados em
[docs/benchmarks](docs/benchmarks).

A partir do M4.5, `make bench CAPACITY=c1` (pico de leitura) ou `CAPACITY=c2` (escrita intensa) sobe uma escada de
carga e para no primeiro degrau que a fase não sustenta; o resultado é o maior degrau sustentado
([ADR-0013](docs/adr/0013-estudo-de-capacidade.md)). `CAPACITY=c1 bench/day.sh` mede todas as fases do cenário em
sequência, cada uma com o dataset recarregado.
