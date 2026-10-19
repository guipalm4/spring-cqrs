# ADR-0008 — Contrato REST, semântica de Q1–Q4 e dataset de referência

- **Status:** Aceito
- **Data:** 2026-10-18

## Contexto

O contrato REST e o dataset ficam congelados para v1, v2 e v3 (ADR-0005). Qualquer ambiguidade
resolvida de um jeito na v1 e de outro na v3 invalida a comparação. Três pontos não estavam decididos:

1. **Distribuição dos dados.** 20 milhões de lançamentos em 100 mil contas dão ~200 por conta. Com
   distribuição uniforme, Q2–Q4 por conta varrem ~200 linhas por índice e a v1 não tem problema a
   resolver: H1, H2 e H6 seriam refutadas por construção do dataset, não pelo mérito das arquiteturas.
2. **Escopo e semântica do Q4.** Busca global favorece o OpenSearch, mas não é um caso de uso natural
   da API de cliente. Relevância e tokenização diferem entre Postgres, Mongo e OpenSearch.
3. **Como gerar 20 milhões de linhas** de forma reproduzível e rápida.

## Decisão

### Dataset de referência

- **Dois grupos de contas:** 1% empresariais (`holder_id` `seed-b-*`, 4.350–13.050 movimentos cada,
  ~44% dos lançamentos, já que os créditos de transferência caem quase todos em contas de pessoa
  física) e 99% de pessoa física (`seed-r-*`, 17–158 movimentos). Movimentos: 30%
  depósito, 55% saque, 15% transferência. Como cada transferência gera dois lançamentos e cada conta
  recebe um saldo inicial, ~17,4 milhões de movimentos resultam nos ~20 milhões de lançamentos do
  ADR-0005. O benchmark sorteia
  contas dos dois grupos e **reporta os resultados separadamente**.
- **Janela fixa:** out/2024 a set/2026 (UTC). Não depende da data em que o seed roda.
- **Moeda única** (BRL) no seed; multimoeda segue fora de escopo.
- **Invariantes do domínio valem no seed:** saldo = soma dos lançamentos, `balance_after` acumulado
  na ordem dos ids, transferência = par OUT/IN somando zero, nenhum saldo intermediário abaixo do
  limite. Um saldo inicial por conta cobre o pior momento do histórico.
- **Seeder em SQL** (`bench/seed/seed.sql`, procedure `seed.load(scale, salt)`, `make seed`). Todo
  sorteio sai de `hashint8extended(k, stream)`: o resultado não depende de paralelismo nem da ordem de
  execução, só de `scale`, `salt` e da versão do Postgres (pinada). Isso substitui o exemplo de
  Compact Source Files citado para o seeder no ADR-0001: gerar no próprio banco evita trafegar 20
  milhões de linhas pelo JDBC.
- Os índices secundários de `ledger_entry` são removidos durante a carga e recriados a partir da
  própria definição no catálogo (`pg_get_indexdef`), sem duplicar o DDL da migração.

### Contrato REST

- **Comandos:** `POST /accounts`, `POST /accounts/{id}/deposits`, `POST /accounts/{id}/withdrawals`,
  `POST /transfers`. Valores em unidades menores (`long`) + código ISO da moeda.
- **Idempotência:** header `Idempotency-Key` obrigatório. A chave é reservada na mesma transação do
  comando (`INSERT … ON CONFLICT DO NOTHING`; a segunda transação espera a primeira terminar).
  Reenvio com o mesmo corpo devolve a resposta original com `Idempotent-Replayed: true`, **inclusive
  recusas de negócio** (ex.: `INSUFFICIENT_FUNDS` continua recusado mesmo depois de um depósito).
  Mesma chave com outro corpo: `422`. Sem expiração de chaves nesta fase.
- **Erros:** RFC 9457 (`application/problem+json`), com o código de negócio em `violation`.
- **Q1** `GET /accounts/{id}`: saldo atual.
- **Q2** `GET /accounts/{id}/statement?from&to&limit&cursor`: período `[from, to)` obrigatório, mais
  recentes primeiro, **paginação por keyset** com cursor opaco. Offset seria uma baseline fraca em
  páginas profundas e não se traduz para os outros stores.
- **Q3** `GET /accounts/{id}/monthly-summary?until=yyyy-MM`: 12 meses terminando em `until` (padrão:
  mês corrente). O benchmark sempre passa `until`, para não depender da data da execução.
- **Q4** `GET /accounts/{id}/entries/search`, **por conta**, filtros combinados com E:
  - `q`: todas as palavras presentes em descrição ou contraparte, sem stemming (`simple` no Postgres);
  - `counterparty`: trecho do nome da contraparte, sem diferenciar maiúsculas;
  - `category`, `from`, `to`, `minAmount`, `maxAmount` (valor em módulo), `limit`.
  - Ordenação por data, **não por relevância**: cada engine pontua de um jeito, e o contrato precisa
    devolver o mesmo resultado nas três fases.
- **Contraparte gravada no lançamento** (foto no momento da transação, como num extrato). Q2 faz join
  só com `category`; o custo do Q2 vem do range scan e da paginação nas contas grandes, não de joins.
- O contrato publicado em `/v3/api-docs` é versionado em `docs/api/openapi.json` e conferido por teste.
  Na v2 e na v3, o mesmo arquivo serve de referência.

## Alternativas consideradas

- **Distribuição uniforme:** mais simples, mas esvazia o experimento (ver Contexto).
- **Zipf sobre todas as contas:** mais realista na cauda, mas a conta do topo teria ~1,6 milhão de
  lançamentos e o resultado do benchmark dependeria muito de quais contas fossem sorteadas.
- **Q4 global ou nas duas formas:** mais superfície de contrato para manter idêntica, e o caso global
  favorece de antemão uma das hipóteses (H6).
- **Seeder em Java (Compact Source File + COPY):** coerente com o ADR-0001, mais lento e exige o
  driver JDBC no classpath do script.

## Consequências

- As contas empresariais são onde H1/H2/H6 podem aparecer; as de pessoa física funcionam como segundo
  grupo de controle, ao lado do Q1.
- O seed escreve direto no modelo de escrita. Na v2 e na v3 as projeções precisarão ser construídas a
  partir do ledger já carregado. Isso puxa a "reconstrução de projeção" do stretch para o caminho
  crítico do M3/M4.
- Registros de idempotência crescem sem limite; a limpeza fica para quando houver motivo medido.
