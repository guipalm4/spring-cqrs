# ADR-0012 — v3: projeção no MongoDB por eventos, topologia e controle de hardware

- **Status:** Aceito
- **Data:** 2026-10-21
- **Substitui em parte:** [ADR-0004](0004-sincronizacao-outbox-kafka.md) (a idempotência por sequência)
- **Exceção ao escopo congelado do:** [ADR-0011](0011-v2-projecoes-e-escopo-congelado.md) (um controle, e só um)

## Contexto

A v3 muda o armazenamento e a consistência: o modelo de leitura da v2 (ADR-0011) sai do Postgres para o MongoDB
e passa a ser atualizado de forma assíncrona, por outbox → Kafka → projetor (ADR-0004). Até o M3, o canal da
interferência no colapso foi a JVM compartilhada (heap e GC), não só o pool (ADR-0010). A v3 precisa mostrar se
tirar as consultas do processo dos comandos resolve isso (H3), quanto custa (H5) e se a leitura escala sozinha (H7).

Antes de implementar, apareceram dois problemas:

1. **A idempotência do ADR-0004 depende de uma ordem que o transporte não garante.** O projetor descartaria todo
   evento com `accountSequence ≤ última aplicada`. Mas o Modulith externaliza os eventos num listener assíncrono
   depois do commit, e a documentação do 2.1 diz que "a externalização de eventos posteriores pode ultrapassar a
   de eventos anteriores", principalmente no reenvio. Se o evento 7 de uma conta chegar depois do 8, a regra
   descarta o 7, e a projeção perde um lançamento sem nenhum erro visível. Com
   `serialize-externalization=true`, a ordem se mantém no caminho feliz, mas um evento reenviado depois de uma falha
   chega fora de ordem de qualquer jeito, e enviar um por vez limita a vazão da externalização.
2. **A v3 usa mais hardware que a v1.** Com cada peça do tamanho da v1, a aplicação passa de 2 CPU/1 GB para
   2 × (2 CPU/1 GB), e o cache das leituras passa de 512 MB (`shared_buffers`) para 512 MB do Postgres + 512 MB do
   WiredTiger. Se a escrita da v3 não degradar, a primeira objeção é "foi o hardware, não a separação". Declarar
   isso como limite deixaria a H3 ambígua por construção.

## Decisão

### Topologia (profile `v3` do compose)

Um artefato (`app-cqrs`), três papéis por profile do Spring, cada um com a sua metade do contrato (ADR-0002):

| Container | Papel | CPU / memória | Observação |
|---|---|---|---|
| `postgres` | escrita | 2 / 2 GB | Mesma configuração de todas as fases (ADR-0009) |
| `command-api` | comandos | 2 / 1 GB | Igual ao app da v1: mesma JVM, pool de 10. Porta 8080 |
| `query-api` | Q1–Q4 | 2 / 1 GB | Pool de 10 no Postgres (Q1) e pool de 10 no Mongo (`maxPoolSize`). Porta 8081 |
| `projector` | Kafka → Mongo | 1 / 512 MB | Separado da `query-api` (ADR-0007): escalar a leitura não escala o projetor |
| `mongo` | leitura | 2 / 2 GB | Cache do WiredTiger fixado em 0,5 GB, como o `shared_buffers` |
| `kafka` | transporte | 1 / 1 GB | KRaft, broker único (ADR-0004) |

- **Cada peça tem o tamanho da peça equivalente da v1.** O `command-api` roda nas mesmas condições do app da v1;
  o que muda é que as consultas saíram dele. O pool do Mongo fica em 10, como o do Hikari: o driver usa 100 por
  padrão, e a v3 ganharia concorrência no store por configuração, não por arquitetura.
- **O Gatling chama cada serviço direto** (comandos em `:8080`, consultas em `:8081`), sem proxy na frente
  (ADR-0005). Na v1 e na v2, as duas URLs apontam para o mesmo app.
- **O Q1 continua lendo a `account` no Postgres** (SCOPE), servido pela `query-api`. É o grupo de controle (H4):
  não muda de store. O catálogo de categorias (13 linhas fixas) também vem do Postgres e fica em memória.

### Evento e outbox

- **`EntryPosted`, um por lançamento**, publicado com o `ApplicationEventPublisher` na transação do comando, no
  lugar de `Projections.apply` (ADR-0011, item 6). O evento carrega tudo o que a projeção precisa: o projetor não
  lê o Postgres.
- Outbox do Modulith com registro JDBC, **completion mode `DELETE`** (ADR-0004), modo de externalização padrão
  (`module-listener`). Tópico `ledger.entry-posted`, **chave `accountId`**, 6 partições.
- **`serialize-externalization` fica desligado**: a idempotência abaixo não depende da ordem. Publicações
  incompletas são reenviadas na partida (`republish-outstanding-events-on-restart`), o que é seguro com uma única
  instância de `command-api`.

### Idempotência que não depende da ordem (substitui a regra por sequência do ADR-0004)

O documento do Mongo guarda os ids dos lançamentos que já recebeu, e a atualização só acontece se o id ainda não
estiver lá. A verificação e a escrita são uma única operação atômica no documento.

- **`statement_view`**: um documento por conta e mês (`_id` = conta + mês), com os lançamentos no formato do
  extrato, como o bloco da v2. A atualização é um `updateOne` com filtro `{_id, "entries.id": {$ne: id}}`, `$push`
  do lançamento e `upsert`. Se o lançamento já estiver no bloco, o filtro não casa e o upsert colide na chave
  (`E11000`). Como o `E11000` também aparece se outra escrita criar o documento entre o filtro e a inserção, o
  projetor tenta de novo uma vez: agora o documento existe, e o filtro só deixa de casar se o lançamento já estiver
  nele. Só então é "já aplicado".
- **`monthly_summary`**: um documento por conta, mês e categoria, com os totais e os ids já somados, na mesma
  regra (`$inc` só se o id não estiver em `applied`).
- **Duas coleções, como as duas tabelas da v2**, e não os totais dentro do bloco: com os totais no bloco, o Q3
  leria documentos de ~100 KB por mês de conta empresarial, e da v2 para a v3 mudaria o modelo, não só o
  armazenamento (ADR-0011).
- O projetor confirma o offset depois das duas escritas. Se cair entre elas, o Kafka reentrega, e as duas são
  idempotentes. Não é preciso transação no Mongo (nem replica set).
- A ordem dos lançamentos dentro do bloco deixa de ser a ordem de chegada. As consultas já ordenam por
  `(occurred_at, id)`, como na v2.

### Consultas na v3

- **Q2 e Q4** percorrem os blocos da conta do mês mais recente para o mais antigo e param quando a página enche,
  como na v2. Cada lançamento é conferido de novo com a regra da v1.
- **Q4 usa um text index do Mongo** (`account` como prefixo, `entries.dsc` e `entries.cp`, `default_language:
  none`) só para descartar blocos (ADR-0003). O `$text` do Mongo é OU entre termos (com ou sem aspas) e ignora
  acentos e maiúsculas; a v1 usa `plainto_tsquery('simple')`, que é E entre termos. O índice devolve um superconjunto
  dos blocos, e a conferência por lançamento aplica o E da v1.
- **A conferência do texto roda em Java**, e não no Postgres como na v2: palavras são sequências de letras e dígitos
  em minúsculas, com acento. É o que o parser `simple` faz com o vocabulário do ledger, mas não com termos de
  pontuação interna (hífen, ponto, e-mail, URL), que o Postgres também guarda inteiros. O dataset e os termos do
  benchmark não têm nenhum, e o teste de equivalência confere a bateria de buscas contra a v1. Fica como limite.
- **O filtro por trecho de contraparte** descarta blocos por `cps`, os nomes do bloco em minúsculas (o papel da coluna
  `counterparties` da v2), em vez de `$regex` com `i`, cuja comparação de letras acentuadas não é garantida.
- **Q3** lê os documentos de `monthly_summary` dos 12 meses.
- Um teste compara as respostas da v3 com as da v2 sobre o mesmo ledger, depois do seed e depois de novos comandos,
  como o `ProjectionEquivalenceTests`: o contrato continua idêntico (ADR-0008).

### Atraso da projeção (H5)

- **Timer `ledger.projection.lag`** no projetor: instante em que as duas escritas terminaram menos o `occurred_at`
  do lançamento. Como o `occurred_at` é o `now()` do Postgres, isto é, o início da transação, o valor superestima o
  atraso em até a duração do comando. O p95 dos comandos é reportado ao lado.
- **Fila do outbox** (publicações incompletas, que no modo `DELETE` são o backlog) e **lag do consumer group** no
  Prometheus.
- O cenário S4 do ADR-0005 (escrever e consultar logo em seguida) não é implementado: o timer mede o mesmo
  intervalo sem depender de polling no cliente. Fica como limite declarado.

### Tracing

`spring-boot-starter-opentelemetry` com o Grafana Tempo: HTTP → transação → externalização → Kafka → projetor →
Mongo. **No `make bench`, a amostragem é 0**, porque a v1 e a v2 não pagaram esse custo. O trace ponta a ponta é
registrado numa execução à parte, com amostragem 1. Se o listener assíncrono do Modulith perder o contexto, o
`traceparent` vai nos headers da mensagem (`EventExternalizationConfiguration.headers`).

### Seed

`make seed` na v3 carrega o mesmo dataset no Postgres, roda o `rebuild_projections()` da v2 (as tabelas e funções
do `V2__projections.sql` existem no schema da v3, vazias em execução), exporta as projeções para o Mongo (`COPY`
→ `mongoimport`, com os ids aplicados de cada documento) e esvazia as tabelas de projeção no Postgres. O Mongo parte
exatamente da projeção da v2. Tópico e outbox começam vazios.

### Controle: v1 com o hardware da v3 (exceção ao ADR-0011)

- **Profile `v1-big`**: a imagem da v1 sem nenhuma mudança, com o **app em 4 CPU/2 GB** (as duas JVMs da v3
  somadas; pool de 10, mesmas flags) e o **Postgres em 4 CPU/4 GB com `shared_buffers=1GB`** (os dois caches de
  leitura da v3 somados, com `effective_cache_size` proporcional). Sem código novo: só limites e parâmetros no compose.
- O Kafka e o projetor ficam de fora: não têm equivalente na v1 e entram como custo da v3 (H5).
- **Por que o Postgres também cresce:** se só o app crescesse, ficaria aberta a objeção "a v3 tem o dobro de cache
  para as leituras". O M2 mostrou que o colapso depende de leituras fora do cache.
- **Por que é exceção, e não um precedente:** sem esse controle, nenhum resultado da H3 se distingue de "mais
  hardware". Ele vale só para essa pergunta e é medido uma vez, com as demais fases do dia. **Nenhum resultado dele
  abre outro controle ou variante antes do M6.** Um efeito inesperado vira limite declarado no capítulo.

### Medição e leitura combinada antes de medir

Mesmo protocolo e mesmas taxas do ADR-0009 (escrita a 20/s; leitura a 25, 50 e 75/s; 3 repetições intercaladas;
"colapsou em k de n"). **Ordem no mesmo dia, com `make clean` + `make up` + `make seed SCALE=1` antes de cada uma:
v1, v1-big, v2, v3 e v3 com duas `query-api` (S5).** Saída em `docs/benchmarks/<data>-<fase>/`. Ao lado de toda
comparação: o tempo por página lida do Postgres (`read_ms / shared_blks_read`) e, na v3, as páginas lidas pelo
WiredTiger para o cache (`serverStatus`).

- **H3 confirmada, e atribuível à separação**, se nas execuções de `s3-r050` e `s3-r075` o p95 dos comandos da v3
  ficar na ordem do S1 da v3 (dezenas de ms), enquanto a v1 do dia colapsa, **e a v1-big também degradar**.
- **H3 confirmada, mas o hardware basta**, se a v1-big também mantiver os comandos na ordem do S1. Nesse caso, para
  este volume, a v3 resolve a interferência com o mesmo hardware com que a v1 a resolveria. Isso vai para o ADR
  "quando não usar CQRS" (M6), desfavorável ao CQRS como for.
- **H3 refutada** se os comandos da v3 degradarem com a `query-api` em colapso. O canal que sobra é o compartilhado
  fora das JVMs: o Postgres (o Q1 continua nele) e a CPU da VM (abaixo).
- **H5:** atraso p50/p99 no S1 e no S3, número de containers e CPU/RAM somados de `docker stats`. Previsão: p50 de
  dezenas de ms, e p99 no `s3-r075` uma ordem de grandeza acima do S1, porque o projetor divide o Mongo com as
  consultas. Custo de escrita: p95 dos comandos no S1 da v3 contra o da v1 e o da v2. Previsão: abaixo da v2 (o
  comando grava uma linha no outbox e a apaga, em vez de reescrever o bloco `jsonb`).
- **H7 (S5):** `s3-r075` com duas `query-api` (round-robin no Gatling, `baseUrls`) contra o mesmo cenário com uma.
  Confirmada se o p95 das consultas ou o "k de n" melhorar e o p95 dos comandos ficar dentro da faixa da v3 com uma
  réplica. Como o modelo é aberto e as taxas são fixas, a vazão só muda se uma réplica não aguentar 75/s. **Se a v3
  com uma réplica já for estável a 75/s, a H7 fica inconclusiva nestas taxas**, e as taxas não sobem depois (ADR-0009).
  Se o Mongo saturar antes da `query-api`, escalar a aplicação não escala o store, e isso também é resultado.
- **Q4 no Mongo** é reportado contra a v1 e a v2, sem hipótese: é o ponto de partida do M5 (H6).

## Alternativas consideradas

- **Regra por sequência com `serialize-externalization=true`** (ADR-0004 como está): ainda perde lançamentos no
  reenvio, e enviar um evento por vez coloca um teto na vazão da externalização, que apareceria como atraso.
- **Sequência por conta sem lacunas, com o projetor esperando a que falta:** detecta a lacuna, mas exige coluna
  nova no ledger e espera explícita no projetor. O filtro por id resolve o mesmo problema numa operação.
- **Modo `outbox` da externalização do Modulith (2.1):** é mais novo que o ADR-0004 e não foi avaliado. O modo
  padrão é o documentado e basta com a idempotência acima.
- **Transação no Mongo para as duas coleções:** exige replica set e custa mais por evento, sem necessidade com as
  duas escritas idempotentes.
- **Projetor dentro da `query-api`:** um container a menos, mas na H7 escalaria o projetor junto, e no colapso da
  leitura o atraso cresceria por disputa de JVM, misturando duas causas.
- **Gateway ou proxy na frente dos serviços:** um salto e um container a mais em toda medição (ADR-0005).
- **Controle só com o app maior (4 CPU/2 GB):** deixaria a v3 com o dobro de cache de leitura sem contrapartida.
- **Dividir o hardware da v1 entre as duas JVMs (1 CPU/512 MB cada):** mesmo total, mas cada JVM com metade do heap,
  que é justamente o canal do colapso. A v3 perderia por falta de memória, e o resultado não diria nada sobre CQRS.
- **Declarar o hardware a mais como limite, sem controle:** mais barato, mas deixa a conclusão principal da H3 sem
  como se distinguir de "mais hardware".

## Consequências

- **A v3 tem seis containers de aplicação e dados contra dois da v1**, ~8,6 GB de limite de memória contra ~4,1 GB
  (com observabilidade), e ~9,6 GB com duas `query-api`. Cabe na VM de 11,7 GB com a folga do ADR-0007.
- **A soma dos limites de CPU passa das 8 CPUs da VM** (11,25 na v3, 13,25 com duas `query-api`; 9,25 na v1-big).
  Os limites são tetos, não reservas: no colapso, Postgres, Mongo e as JVMs disputam a CPU da mesma máquina. A
  separação física da v3 não separa a CPU, e esse canal fica declarado no capítulo.
- **A `query-api` ainda toca o Postgres** (Q1 e categorias). A carga de leitura no store de escrita não vai a zero;
  o `pg_stat_statements` da v3 mostra quanto sobra.
- **Consistência eventual visível no contrato:** uma consulta logo depois de um comando pode não trazer o
  lançamento. O contrato REST não muda, e os testes de integração da v3 esperam a projeção em vez de supor leitura
  imediata.
- **O seed da v3 é o mais lento:** rebuild da v2 mais a exportação para o Mongo.
- **O dia de medição fica em ~8 h** (cinco execuções com seed recarregado). Se não couber num dia, a v1 é repetida
  no dia seguinte, e cada fase é comparada só com a v1 do mesmo dia (ADR-0010).
- O ADR-0004 continua valendo para o resto (outbox Modulith, Kafka KRaft, chave por conta, consumer group por
  projeção, modo `DELETE`); só a regra de idempotência é substituída.
