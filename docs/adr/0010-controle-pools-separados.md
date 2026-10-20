# ADR-0010 — Controle: v1 com pools separados para comandos e consultas

- **Status:** Aceito
- **Data:** 2026-10-20
- **Complementa:** [ADR-0009](0009-revisao-da-baseline-e-parametros-do-benchmark.md) (não muda a baseline)

## Contexto

No M2, a interferência da leitura na escrita (H1) veio quase toda da fila no pool compartilhado do Hikari: um
comando custa 3–8 ms dentro do Postgres, mas espera segundos por uma das 10 conexões que as consultas limitadas
por I/O seguram ([resultados](../benchmarks/2026-10-19-v1/README.md)). Separar os pools mitigaria isso sem CQRS,
e é o contra-argumento mais forte ao H3. O ADR-0009 deixou a v1 com o pool compartilhado (como um time a
implantaria por padrão) e previu medir a variante como controle antes do M3 (issue #11).

## Decisão

### Variante

- **Profile `split-pools` da `app-baseline`**, desligado por padrão. Sem ele, a v1 é exatamente a do ADR-0009.
- O DataSource autoconfigurado vira o pool **`commands`**: transações, repositórios do Spring Data JDBC e Flyway
  ficam nele, sem mudança de código. As consultas Q1–Q4 usam um segundo DataSource, o pool **`queries`**, via
  `JdbcClient` (`SplitPoolsConfiguration`).
- **Mesmo total de conexões: 2 para comandos e 8 para consultas.** Um teste falha se a soma deixar de ser 10.
- No compose, o serviço `app-baseline-split` (mesma imagem) no profile `v1-split`: `make up PHASE=v1-split` e
  `make bench PHASE=v1-split`. Mesmo Postgres, mesmos limites de container, mesma JVM.

### Por que 2 + 8, e não outra divisão

Fixada antes de medir, como as taxas do ADR-0009, para não escolher a divisão pelo resultado.

No S1 do M2, cada comando segurou a conexão por 10–15 ms em média (`hikari_usage_avg_s`). A 20 comandos/s,
são 0,3 conexões ocupadas em média; com o tempo no banco dobrado sob leitura pesada (o pior visto no M2), 0,6.
Num modelo M/M/c, com 2 conexões a chance de um comando esperar fica em 4–14% e a espera média em 0,3–3 ms;
com 1 conexão, 30–60% e até 45 ms. A 3ª conexão reduziria a espera a ~0,3 ms no pior caso, mas tiraria 30% da
capacidade de leitura em vez de 20%. Com 2, o pool de comandos não é o gargalo, e a leitura perde o mínimo.

### Medição

Mesmo protocolo e mesmas taxas do ADR-0009 (escrita a 20/s; leitura a 25, 50 e 75/s; 3 repetições
intercaladas), sobre o dataset recém-carregado (`make seed SCALE=1`). Saída em
`docs/benchmarks/<data>-v1-split/`, com espera, uso e fila por pool em cada execução. O `run.sh` passa a somar
fila e conexões ativas dos pools (com um pool, o valor é o mesmo de antes).

### Leitura combinada antes de medir

- **Separar os pools basta para a escrita** se, nas execuções em que as consultas colapsam, o p95 dos comandos
  continuar na ordem do S1 (dezenas de ms) e a espera no pool `commands` ficar em milissegundos. Nesse caso, o
  ganho de escrita da v3 (H3) não é atribuível à separação de armazenamento, e o argumento do CQRS fica na leitura.
- **Não basta** se os comandos ainda degradarem com o pool próprio livre: o canal que sobra é o compartilhado
  fora do pool (I/O, WAL e CPU do Postgres; heap, CPU e threads da JVM). Isso é o que a v3 ainda pode resolver.
- **A leitura pode piorar:** com 8 conexões, o colapso das consultas pode ser mais frequente que na v1. É custo da
  variante e entra no resultado.

## Alternativas consideradas

- **Limitar consultas simultâneas num pool único** (semáforo de 8 sobre o pool de 10): efeito parecido, mas é um
  bulkhead na aplicação, não o que um time faria com configuração. Pools separados são a forma usual.
- **Roteamento por transação somente leitura** (`AbstractRoutingDataSource`): exigiria marcar as consultas como
  `@Transactional(readOnly = true)`, mudando o código da baseline congelada.
- **Pools de 10 + 10:** mais conexões que a v1; o ganho viria de capacidade, não de isolamento. Na v3, cada
  serviço tem o seu pool de 10 (ADR-0009), e essa diferença de recursos é declarada na comparação.

## Consequências

- A comparação das próximas fases tem três referências de escrita: v1, v1 com pools separados e a fase medida.
- O isolamento é só de conexões. Comandos e consultas continuam dividindo o Postgres e a JVM: no colapso do M2, o
  heap chegou a 1.000 MiB de 1.024 MiB com milhares de requisições esperando, e isso não muda com dois pools.
- O controle roda a mesma imagem da v1; o profile do Spring fica registrado no `env.md` de cada execução.
