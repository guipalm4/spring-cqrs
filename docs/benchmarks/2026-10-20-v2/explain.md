# EXPLAIN ANALYZE — v2

Gerado por `make bench` (bench/explain/v2.sql) depois das execuções. Conta empresarial `seed-b-0000001`;
pessoa física `seed-r-0050000`. `Buffers: shared hit` = página no cache; `read` = leitura fora do shared_buffers.

## Q1 — saldo (empresa)

```
Index Scan using account_pkey on account  (cost=0.42..2.64 rows=1 width=73) (actual time=0.091..0.091 rows=1.00 loops=1)
  Index Cond: (id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid)
  Index Searches: 1
  Buffers: shared hit=4
Planning:
  Buffers: shared hit=16 read=4
  I/O Timings: shared read=0.691
Planning Time: 0.881 ms
Execution Time: 0.098 ms
```

## Q2 — extrato do trimestre, primeira página (empresa)

```
Sort  (cost=40.63..40.66 rows=12 width=388) (actual time=3.693..3.696 rows=51.00 loops=1)
  Sort Key: e.ts DESC, e.id DESC
  Sort Method: quicksort  Memory: 32kB
  Buffers: shared hit=166 read=1
  I/O Timings: shared read=0.354
  ->  Nested Loop  (cost=3.53..40.42 rows=12 width=388) (actual time=3.632..3.665 rows=51.00 loops=1)
        Buffers: shared hit=160 read=1
        I/O Timings: shared read=0.354
        ->  Limit  (cost=3.38..27.31 rows=12 width=166) (actual time=3.592..3.597 rows=51.00 loops=1)
              Buffers: shared hit=58 read=1
              I/O Timings: shared read=0.354
              ->  Incremental Sort  (cost=3.38..27.31 rows=12 width=166) (actual time=3.591..3.594 rows=51.00 loops=1)
                    Sort Key: b.month DESC, e.ts DESC, e.id DESC
                    Presorted Key: b.month
                    Full-sort Groups: 1  Sort Method: quicksort  Average Memory: 39kB  Peak Memory: 39kB
                    Pre-sorted Groups: 1  Sort Method: top-N heapsort  Average Memory: 44kB  Peak Memory: 44kB
                    Buffers: shared hit=58 read=1
                    I/O Timings: shared read=0.354
                    ->  Nested Loop  (cost=0.43..26.88 rows=12 width=166) (actual time=2.471..3.040 rows=319.00 loops=1)
                          Buffers: shared hit=52 read=1
                          I/O Timings: shared read=0.354
                          ->  Index Scan Backward using statement_view_pkey on statement_view b  (cost=0.43..8.76 rows=12 width=846) (actual time=0.111..0.112 rows=3.00 loops=1)
                                Index Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (month >= '2026-01-01'::date) AND (month <= '2026-04-01'::date))
                                Index Searches: 1
                                Buffers: shared hit=4
                          ->  Function Scan on jsonb_to_recordset e  (cost=0.00..1.50 rows=1 width=162) (actual time=0.953..0.959 rows=106.33 loops=3)
                                Filter: ((ts >= '2026-01-01 00:00:00+00'::timestamp with time zone) AND (ts < '2026-04-01 00:00:00+00'::timestamp with time zone))
                                Rows Removed by Filter: 109
                                Buffers: shared hit=48 read=1
                                I/O Timings: shared read=0.354
        ->  Index Scan using category_pkey on category c  (cost=0.15..1.08 rows=1 width=230) (actual time=0.001..0.001 rows=1.00 loops=51)
              Index Cond: (id = e.cat)
              Index Searches: 51
              Buffers: shared hit=102
Planning:
  Buffers: shared hit=294 read=10
  I/O Timings: shared read=1.978
Planning Time: 3.040 ms
Execution Time: 3.790 ms
```

## Q2 — extrato do trimestre, primeira página (pessoa física)

```
Sort  (cost=40.63..40.66 rows=12 width=388) (actual time=0.701..0.703 rows=21.00 loops=1)
  Sort Key: e.ts DESC, e.id DESC
  Sort Method: quicksort  Memory: 28kB
  Buffers: shared hit=48 read=2
  I/O Timings: shared read=0.455
  ->  Nested Loop  (cost=3.53..40.42 rows=12 width=388) (actual time=0.683..0.697 rows=21.00 loops=1)
        Buffers: shared hit=48 read=2
        I/O Timings: shared read=0.455
        ->  Limit  (cost=3.38..27.31 rows=12 width=166) (actual time=0.679..0.681 rows=21.00 loops=1)
              Buffers: shared hit=6 read=2
              I/O Timings: shared read=0.455
              ->  Incremental Sort  (cost=3.38..27.31 rows=12 width=166) (actual time=0.678..0.679 rows=21.00 loops=1)
                    Sort Key: b.month DESC, e.ts DESC, e.id DESC
                    Presorted Key: b.month
                    Full-sort Groups: 1  Sort Method: quicksort  Average Memory: 30kB  Peak Memory: 30kB
                    Buffers: shared hit=6 read=2
                    I/O Timings: shared read=0.455
                    ->  Nested Loop  (cost=0.43..26.88 rows=12 width=166) (actual time=0.345..0.672 rows=21.00 loops=1)
                          Buffers: shared hit=6 read=2
                          I/O Timings: shared read=0.455
                          ->  Index Scan Backward using statement_view_pkey on statement_view b  (cost=0.43..8.76 rows=12 width=846) (actual time=0.313..0.313 rows=4.00 loops=1)
                                Index Cond: ((account_id = '22a1fabb-71ca-ee7a-5ff4-6cf7b6dba0a7'::uuid) AND (month >= '2026-01-01'::date) AND (month <= '2026-04-01'::date))
                                Index Searches: 1
                                Buffers: shared hit=3 read=1
                                I/O Timings: shared read=0.227
                          ->  Function Scan on jsonb_to_recordset e  (cost=0.00..1.50 rows=1 width=162) (actual time=0.087..0.088 rows=5.25 loops=4)
                                Filter: ((ts >= '2026-01-01 00:00:00+00'::timestamp with time zone) AND (ts < '2026-04-01 00:00:00+00'::timestamp with time zone))
                                Rows Removed by Filter: 1
                                Buffers: shared hit=3 read=1
                                I/O Timings: shared read=0.228
        ->  Index Scan using category_pkey on category c  (cost=0.15..1.08 rows=1 width=230) (actual time=0.000..0.000 rows=1.00 loops=21)
              Index Cond: (id = e.cat)
              Index Searches: 21
              Buffers: shared hit=42
Planning Time: 0.091 ms
Execution Time: 0.717 ms
```

## Q3 — resumo mensal de 12 meses (empresa)

```
Incremental Sort  (cost=2.97..56.97 rows=90 width=288) (actual time=0.885..1.149 rows=156.00 loops=1)
  Sort Key: s.month DESC, c.code
  Presorted Key: s.month
  Full-sort Groups: 4  Sort Method: quicksort  Average Memory: 28kB  Peak Memory: 28kB
  Buffers: shared hit=31 read=5
  I/O Timings: shared read=0.714
  ->  Nested Loop  (cost=0.59..54.50 rows=90 width=288) (actual time=0.610..1.059 rows=156.00 loops=1)
        Buffers: shared hit=28 read=5
        I/O Timings: shared read=0.714
        ->  Index Scan Backward using monthly_summary_pkey on monthly_summary s  (cost=0.43..47.49 rows=90 width=30) (actual time=0.471..0.820 rows=156.00 loops=1)
              Index Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (month >= '2025-10-01'::date) AND (month <= '2026-09-01'::date))
              Index Searches: 1
              Buffers: shared hit=2 read=5
              I/O Timings: shared read=0.714
        ->  Memoize  (cost=0.16..0.32 rows=1 width=230) (actual time=0.000..0.000 rows=1.00 loops=156)
              Cache Key: s.category_id
              Cache Mode: logical
              Hits: 143  Misses: 13  Evictions: 0  Overflows: 0  Memory Usage: 2kB
              Buffers: shared hit=26
              ->  Index Scan using category_pkey on category c  (cost=0.15..0.31 rows=1 width=230) (actual time=0.001..0.001 rows=1.00 loops=13)
                    Index Cond: (id = s.category_id)
                    Index Searches: 13
                    Buffers: shared hit=26
Planning:
  Buffers: shared hit=50 read=1
  I/O Timings: shared read=0.240
Planning Time: 0.716 ms
Execution Time: 1.170 ms
```

## Q4 — termo comum (empresa)

```
Sort  (cost=321.66..321.69 rows=10 width=388) (actual time=8.463..8.466 rows=50.00 loops=1)
  Sort Key: e.ts DESC, e.id DESC
  Sort Method: quicksort  Memory: 32kB
  Buffers: shared hit=181
  ->  Nested Loop  (cost=39.22..321.50 rows=10 width=388) (actual time=5.020..8.440 rows=50.00 loops=1)
        Buffers: shared hit=181
        ->  Limit  (cost=39.07..309.84 rows=10 width=166) (actual time=5.014..8.406 rows=50.00 loops=1)
              Buffers: shared hit=81
              ->  Incremental Sort  (cost=39.07..309.84 rows=10 width=166) (actual time=5.013..8.402 rows=50.00 loops=1)
                    Sort Key: b.month DESC, e.ts DESC, e.id DESC
                    Presorted Key: b.month
                    Full-sort Groups: 2  Sort Method: quicksort  Average Memory: 34kB  Peak Memory: 34kB
                    Buffers: shared hit=81
                    ->  Nested Loop  (cost=0.43..309.46 rows=10 width=166) (actual time=0.697..8.351 rows=54.00 loops=1)
                          Buffers: shared hit=81
                          ->  Index Scan Backward using statement_view_pkey on statement_view b  (cost=0.43..41.86 rows=10 width=846) (actual time=0.064..0.159 rows=6.00 loops=1)
                                Index Cond: (account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid)
                                Filter: (search_vector @@ '''supermercado'''::tsquery)
                                Rows Removed by Filter: 1
                                Index Searches: 1
                                Buffers: shared hit=30
                          ->  Function Scan on jsonb_to_recordset e  (cost=0.00..26.75 rows=1 width=162) (actual time=0.755..1.358 rows=9.00 loops=6)
                                Filter: (to_tsvector('simple'::regconfig, ((dsc || ' '::text) || cp)) @@ '''supermercado'''::tsquery)
                                Rows Removed by Filter: 252
                                Buffers: shared hit=51
        ->  Index Scan using category_pkey on category c  (cost=0.15..1.16 rows=1 width=230) (actual time=0.000..0.000 rows=1.00 loops=50)
              Index Cond: (id = e.cat)
              Index Searches: 50
              Buffers: shared hit=100
Planning:
  Buffers: shared hit=6 read=15
  I/O Timings: shared read=3.555
Planning Time: 3.964 ms
Execution Time: 8.482 ms
```

## Q4 — termo raro (empresa)

```
Sort  (cost=73.45..73.46 rows=2 width=388) (actual time=21.911..21.913 rows=50.00 loops=1)
  Sort Key: e.ts DESC, e.id DESC
  Sort Method: quicksort  Memory: 31kB
  Buffers: shared hit=284 read=10
  I/O Timings: shared read=1.075
  ->  Nested Loop  (cost=68.78..73.44 rows=2 width=388) (actual time=13.240..21.901 rows=50.00 loops=1)
        Buffers: shared hit=284 read=10
        I/O Timings: shared read=1.075
        ->  Limit  (cost=68.63..68.68 rows=2 width=166) (actual time=13.232..21.862 rows=50.00 loops=1)
              Buffers: shared hit=184 read=10
              I/O Timings: shared read=1.075
              ->  Incremental Sort  (cost=68.63..68.68 rows=2 width=166) (actual time=13.231..21.858 rows=50.00 loops=1)
                    Sort Key: b.month DESC, e.ts DESC, e.id DESC
                    Presorted Key: b.month
                    Full-sort Groups: 2  Sort Method: quicksort  Average Memory: 30kB  Peak Memory: 30kB
                    Buffers: shared hit=184 read=10
                    I/O Timings: shared read=1.075
                    ->  Nested Loop  (cost=0.43..68.62 rows=1 width=166) (actual time=0.606..21.828 rows=52.00 loops=1)
                          Buffers: shared hit=184 read=10
                          I/O Timings: shared read=1.075
                          ->  Index Scan Backward using statement_view_pkey on statement_view b  (cost=0.43..41.86 rows=1 width=846) (actual time=0.010..0.172 rows=15.00 loops=1)
                                Index Cond: (account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid)
                                Filter: (search_vector @@ '''sushi'' & ''kaze'''::tsquery)
                                Rows Removed by Filter: 1
                                Index Searches: 1
                                Buffers: shared hit=67
                          ->  Function Scan on jsonb_to_recordset e  (cost=0.00..26.75 rows=1 width=162) (actual time=0.872..1.439 rows=3.47 loops=15)
                                Filter: (to_tsvector('simple'::regconfig, ((dsc || ' '::text) || cp)) @@ '''sushi'' & ''kaze'''::tsquery)
                                Rows Removed by Filter: 302
                                Buffers: shared hit=117 read=10
                                I/O Timings: shared read=1.075
        ->  Index Scan using category_pkey on category c  (cost=0.15..2.37 rows=1 width=230) (actual time=0.000..0.000 rows=1.00 loops=50)
              Index Cond: (id = e.cat)
              Index Searches: 50
              Buffers: shared hit=100
Planning Time: 0.100 ms
Execution Time: 21.936 ms
```

## Q4 — contraparte (empresa)

```
Sort  (cost=54.43..54.44 rows=4 width=388) (actual time=8.698..8.700 rows=50.00 loops=1)
  Sort Key: e.ts DESC, e.id DESC
  Sort Method: quicksort  Memory: 32kB
  Buffers: shared hit=255
  ->  Nested Loop  (cost=12.21..54.39 rows=4 width=388) (actual time=6.465..8.688 rows=50.00 loops=1)
        Buffers: shared hit=255
        ->  Limit  (cost=12.06..47.08 rows=4 width=166) (actual time=6.462..8.658 rows=50.00 loops=1)
              Buffers: shared hit=155
              ->  Incremental Sort  (cost=12.06..47.08 rows=4 width=166) (actual time=6.462..8.655 rows=50.00 loops=1)
                    Sort Key: b.month DESC, e.ts DESC, e.id DESC
                    Presorted Key: b.month
                    Full-sort Groups: 2  Sort Method: quicksort  Average Memory: 30kB  Peak Memory: 30kB
                    Buffers: shared hit=155
                    ->  Nested Loop  (cost=0.43..46.90 rows=4 width=166) (actual time=0.661..8.634 rows=56.00 loops=1)
                          Buffers: shared hit=155
                          ->  Index Scan Backward using statement_view_pkey on statement_view b  (cost=0.43..41.86 rows=4 width=846) (actual time=0.054..0.510 rows=12.00 loops=1)
                                Index Cond: (account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid)
                                Filter: (counterparties ~~* '%nonna%'::text)
                                Rows Removed by Filter: 1
                                Index Searches: 1
                                Buffers: shared hit=53
                          ->  Function Scan on jsonb_to_recordset e  (cost=0.00..1.25 rows=1 width=162) (actual time=0.569..0.675 rows=4.67 loops=12)
                                Filter: (cp ~~* '%nonna%'::text)
                                Rows Removed by Filter: 292
                                Buffers: shared hit=102
        ->  Index Scan using category_pkey on category c  (cost=0.15..1.82 rows=1 width=230) (actual time=0.000..0.000 rows=1.00 loops=50)
              Index Cond: (id = e.cat)
              Index Searches: 50
              Buffers: shared hit=100
Planning:
  Buffers: shared hit=5
Planning Time: 0.316 ms
Execution Time: 8.725 ms
```

## Q4 — texto + categoria + período (empresa)

```
Sort  (cost=78.69..78.69 rows=2 width=388) (actual time=5.089..5.091 rows=48.00 loops=1)
  Sort Key: e.ts DESC, e.id DESC
  Sort Method: quicksort  Memory: 31kB
  Buffers: shared hit=217
  ->  Nested Loop  (cost=38.47..78.68 rows=2 width=388) (actual time=3.890..5.081 rows=48.00 loops=1)
        Buffers: shared hit=217
        ->  Limit  (cost=38.32..73.91 rows=2 width=166) (actual time=3.887..5.052 rows=48.00 loops=1)
              Buffers: shared hit=121
              InitPlan 1
                ->  Index Scan using category_code_key on category  (cost=0.15..2.37 rows=1 width=2) (actual time=0.027..0.028 rows=1.00 loops=1)
                      Index Cond: ((code)::text = 'HEALTH'::text)
                      Index Searches: 1
                      Buffers: shared hit=2
              ->  Incremental Sort  (cost=35.95..71.55 rows=2 width=166) (actual time=3.887..5.049 rows=48.00 loops=1)
                    Sort Key: b.month DESC, e.ts DESC, e.id DESC
                    Presorted Key: b.month
                    Full-sort Groups: 2  Sort Method: quicksort  Average Memory: 33kB  Peak Memory: 33kB
                    Buffers: shared hit=121
                    ->  Nested Loop  (cost=0.43..71.46 rows=2 width=166) (actual time=0.541..5.034 rows=48.00 loops=1)
                          Buffers: shared hit=121
                          ->  Index Scan Backward using statement_view_pkey on statement_view b  (cost=0.43..16.93 rows=2 width=846) (actual time=0.010..0.038 rows=9.00 loops=1)
                                Index Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (month >= '2026-01-01'::date))
                                Filter: (search_vector @@ '''farmácia'''::tsquery)
                                Rows Removed by Filter: 1
                                Index Searches: 1
                                Buffers: shared hit=42
                          ->  Function Scan on jsonb_to_recordset e  (cost=0.00..27.25 rows=1 width=162) (actual time=0.515..0.554 rows=5.33 loops=9)
                                Filter: ((ts >= '2026-01-01 00:00:00+00'::timestamp with time zone) AND (cat = (InitPlan 1).col1) AND (to_tsvector('simple'::regconfig, ((dsc || ' '::text) || cp)) @@ '''farmácia'''::tsquery))
                                Rows Removed by Filter: 308
                                Buffers: shared hit=79
        ->  Index Scan using category_pkey on category c  (cost=0.15..2.37 rows=1 width=230) (actual time=0.000..0.000 rows=1.00 loops=48)
              Index Cond: (id = e.cat)
              Index Searches: 48
              Buffers: shared hit=96
Planning Time: 0.115 ms
Execution Time: 5.123 ms
```

## Custo de escrita: um lançamento num bloco cheio (empresa, set/2026)

No benchmark, os comandos caem no mês corrente, fora da janela do seed: o bloco começa vazio. Num mês já
cheio, cada comando reescreve o bloco inteiro. Medido aqui numa transação desfeita no fim (ADR-0011).

- Bloco: 293 lançamentos, 33 kB comprimidos

```
Insert on statement_view v  (cost=0.44..2.93 rows=0 width=0) (actual time=2.371..2.371 rows=0.00 loops=1)
  Conflict Resolution: UPDATE
  Conflict Arbiter Indexes: statement_view_pkey
  Tuples Inserted: 0
  Conflicting Tuples: 1
  Buffers: shared hit=150 read=5 dirtied=17 written=3
  I/O Timings: shared read=0.902 write=0.078
  WAL: records=66 fpi=8 bytes=103773
  ->  Index Scan using ledger_entry_pkey on ledger_entry e  (cost=0.44..2.93 rows=1 width=116) (actual time=0.092..0.093 rows=1.00 loops=1)
        Index Cond: (id = 20322384)
        Index Searches: 1
        Buffers: shared hit=7
Planning:
  Buffers: shared hit=93 read=4
  I/O Timings: shared read=1.273
Planning Time: 1.520 ms
Execution Time: 2.911 ms
```
