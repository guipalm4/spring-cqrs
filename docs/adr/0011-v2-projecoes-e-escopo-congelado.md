# ADR-0011 — v2: projeções em blocos por conta e mês, e escopo congelado até o M6

- **Status:** Aceito
- **Data:** 2026-10-20
- **Exceção ao escopo congelado:** [ADR-0012](0012-v3-projecao-mongo-e-controle-de-hardware.md) (controle v1-big, uma medição)

## Contexto

A v2 separa o modelo de leitura do de escrita, no mesmo Postgres e na mesma transação (ADR-0002). O SCOPE
descreve as projeções só pelo nome: `statement_view` "desnormalizada", `monthly_summary` pré-agregada e
`tsvector` na projeção. Falta decidir a forma, e o M2 mostra que ela decide o resultado:

- O custo da leitura na v1 vem da **dispersão**: os lançamentos de uma conta ficam espalhados pela tabela de
  7,2 GB. No Q4 com termo comum, 290 lançamentos casam e o Postgres lê 270 páginas, quase uma por lançamento
  ([planos do M2](../benchmarks/2026-10-19-v1/explain.md)). O Q4 responde por 58–81% do tempo de leitura no banco.
- O Q3 já roda por *index-only scan* na v1 (22 ms numa conta empresarial).
- Uma `statement_view` com uma linha por lançamento teria a mesma dispersão da `ledger_entry`: a v2 perderia
  por construção, e a comparação não diria nada sobre separar o modelo.

Além disso, depois do M2 e da #11 cada medição abria uma pergunta nova, e o rumo do projeto ficou difícil de
acompanhar. Este ADR também fecha o escopo daqui até o M6.

## Decisão

### Modelo de leitura (`app-cqrs`, `V2__projections.sql`)

1. **`statement_view`: um bloco por conta e mês** (`account_id`, `month`), com os lançamentos já no formato do
   extrato num `jsonb`, as palavras do bloco num `tsvector` e os nomes de contraparte num texto. É a forma do
   documento conta/mês do MongoDB na v3 (ADR-0003), então **da v2 para a v3 muda só o armazenamento e a
   consistência**, não o modelo, como pede o ADR-0002.
2. **`monthly_summary`**: totais por conta, mês e categoria, atualizados por soma.
3. **Q2 e Q4** percorrem os blocos da conta do mês mais recente para o mais antigo e param quando a página
   enche. O período, as palavras e a contraparte descartam blocos inteiros; cada lançamento é conferido de
   novo com a regra da v1. Um teste compara as respostas da v2 com o SQL da v1 sobre o mesmo ledger, depois do
   rebuild e depois de novos comandos (`ProjectionEquivalenceTests`): o contrato continua idêntico (ADR-0008).
4. **Q1** continua lendo a `account`, como em todas as fases (SCOPE).

### Modelo de escrita

5. **A `ledger_entry` da v2 fica sem o que só serve à leitura**: sem a coluna `search_vector` e sem os índices de
   Q2–Q4 da v1. O custo de manter a leitura sai dos índices e vai para as projeções, e a comparação de escrita
   entre v1 e v2 fica justa.
6. **Atualização na transação do comando**, em código da aplicação (`Projections.apply`), e não por trigger:
   na v3 o mesmo passo vira o projetor que consome o Kafka. O lançamento é lido pelo id com as mesmas funções
   SQL (`statement_entry`, `ledger_month`) que o `rebuild_projections()` usa, e um teste confere que
   atualizar um a um e reconstruir do zero dão as mesmas projeções. O lock da conta, que o comando já tem,
   serializa as atualizações de um mesmo bloco.
7. **Rebuild depois do seed:** `make seed` chama `rebuild_projections()` quando o banco é da v2 (ADR-0008).
8. **Trocar de fase exige `make clean`:** v1 e v2 têm históricos de migração diferentes no mesmo banco. O
   protocolo já recarrega o seed antes de cada fase, então nada muda na medição.

### Medição e leitura combinada antes de medir

Mesmo protocolo e mesmas taxas do ADR-0009, com a v1 medida no mesmo dia e o seed recarregado antes de cada
fase (ADR-0010). Em `docs/benchmarks/<data>-v2/`, mais o `explain.md` da v2.

- **H2 confirmada** se, no cenário estável comum às duas fases (`s3-r025`), o p95 do Q3 empresarial cair pelo
  menos à metade e o ganho relativo do Q2 for menor que o do Q3. Como o Q3 já é barato na v1, a previsão pode
  falhar, e isso vai para o capítulo. O Q4, fora do H2, é reportado do mesmo jeito.
- **H4 confirmada** se o p95 do Q1 na v2 ficar dentro da faixa da v1 do mesmo dia.
- **Colapso:** compara-se "colapsou em k de 9" (`s3-r050`, `s3-r075` e `s2`) entre v1 e v2. Se a v2 não
  colapsar, separar o modelo bastou para este volume, o que o ADR-0002 já previa como resultado possível. Se
  colapsar, a leitura melhorou, mas o processo compartilhado continua sendo o canal (#11), e esse é o argumento da v3.
- **Custo de escrita:** p95 dos comandos no `s1` (só escrita) da v2 contra o da v1.

### Escopo congelado

A rota até o fim da tese é fixa: **M3 (v2) → M4 (v3) → M5 (OpenSearch) → M6 (relato)**; o M7 continua opcional.
Não entram novos controles nem novas variantes antes do M6. Alternativas mais baratas que apareçam, como a
contrapressão sugerida no capítulo 2 do README, entram no ADR "quando não usar CQRS" do M6 como discussão, sem
medição. Problemas de medição viram limite declarado no capítulo, não marco novo.

## Alternativas consideradas

- **Uma linha por lançamento, desnormalizada:** a leitura literal do SCOPE. Tem a mesma dispersão da v1, e o
  único ganho seria o join com `category` (13 linhas).
- **Uma linha por lançamento com índice de cobertura** na ordem da conta: daria localidade pelo índice, mas é o
  índice que o ADR-0009 recusou para a v1 por custo. O ganho poderia ser atribuído a ele, e não ao modelo, e da
  v2 para a v3 o modelo também mudaria (linha → documento).
- **Blocos de tamanho fixo** (N lançamentos) em vez de mês: limitam o custo de reescrita, mas afastam a v2 do
  documento conta/mês do ADR-0003.
- **Trigger na `ledger_entry`:** mesma transação com menos código, mas esconde a projeção do código da aplicação,
  e na v3 ela precisa ser um componente explícito.
- **`MATERIALIZED VIEW` com `REFRESH`:** não atualiza na transação do comando, e refazer 20 milhões de
  lançamentos não é viável por comando.

## Consequências

- **Escrita mais cara por lançamento:** cada comando reescreve o bloco do mês, e o `jsonb` comprimido vai inteiro
  para o WAL. Por outro lado, a `ledger_entry` deixa de manter dois GIN compostos e um índice de cobertura.
- **O benchmark subestima o custo de escrita:** os comandos caem no mês corrente, fora da janela do seed, então os
  blocos começam vazios. Numa conta empresarial, um mês tem ~540 lançamentos. O `explain.md` da v2 mede um
  lançamento num bloco cheio (transação desfeita), e o capítulo declara a diferença. A mesma subestimação vale para
  o MongoDB na v3, então a comparação v2 × v3 não é afetada.
- **Reescrita gera versões mortas** (MVCC e TOAST) que o autovacuum precisa limpar. Isso entra no custo medido.
- **O rebuild faz parte do seed:** o `make seed` da v2 demora mais que o da v1.
- **A `app-cqrs` duplica o lado de comando da `app-baseline`**, que está congelada (ADR-0002). As diferenças são a
  linha que atualiza as projeções e o schema de escrita sem estruturas de leitura.
