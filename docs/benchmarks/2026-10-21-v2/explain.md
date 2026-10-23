# EXPLAIN ANALYZE — v2

Gerado por `make bench` (bench/explain/v2.sql) depois das execuções. Conta empresarial `seed-b-0000001`;
pessoa física `seed-r-0050000`. `Buffers: shared hit` = página no cache; `read` = leitura fora do shared_buffers.

## Q1 — saldo (empresa)

```
Index Scan using account_pkey on account  (cost=0.42..2.64 rows=1 width=72) (actual time=0.051..0.051 rows=1.00 loops=1)
  Index Cond: (id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid)
  Index Searches: 1
  Buffers: shared hit=4
Planning:
  Buffers: shared hit=16 read=4
  I/O Timings: shared read=0.533
Planning Time: 0.608 ms
Execution Time: 0.059 ms
```

## Q2 — extrato do trimestre, primeira página (empresa)

```
Sort  (cost=38.41..38.44 rows=11 width=388) (actual time=3.304..3.306 rows=51.00 loops=1)
  Sort Key: e.ts DESC, e.id DESC
  Sort Method: quicksort  Memory: 32kB
  Buffers: shared hit=166 read=1
  I/O Timings: shared read=0.022
  ->  Nested Loop  (cost=3.30..38.22 rows=11 width=388) (actual time=3.208..3.243 rows=51.00 loops=1)
        Buffers: shared hit=160 read=1
        I/O Timings: shared read=0.022
        ->  Limit  (cost=3.16..25.29 rows=11 width=166) (actual time=3.177..3.183 rows=51.00 loops=1)
              Buffers: shared hit=58 read=1
              I/O Timings: shared read=0.022
              ->  Incremental Sort  (cost=3.16..25.29 rows=11 width=166) (actual time=3.176..3.179 rows=51.00 loops=1)
                    Sort Key: b.month DESC, e.ts DESC, e.id DESC
                    Presorted Key: b.month
                    Full-sort Groups: 1  Sort Method: quicksort  Average Memory: 39kB  Peak Memory: 39kB
                    Pre-sorted Groups: 1  Sort Method: top-N heapsort  Average Memory: 44kB  Peak Memory: 44kB
                    Buffers: shared hit=58 read=1
                    I/O Timings: shared read=0.022
                    ->  Nested Loop  (cost=0.43..24.87 rows=11 width=166) (actual time=2.003..2.676 rows=319.00 loops=1)
                          Buffers: shared hit=52 read=1
                          I/O Timings: shared read=0.022
                          ->  Index Scan Backward using statement_view_pkey on statement_view b  (cost=0.43..8.25 rows=11 width=846) (actual time=0.091..0.093 rows=3.00 loops=1)
                                Index Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (month >= '2026-01-01'::date) AND (month <= '2026-04-01'::date))
                                Index Searches: 1
                                Buffers: shared hit=4
                          ->  Function Scan on jsonb_to_recordset e  (cost=0.00..1.50 rows=1 width=162) (actual time=0.842..0.848 rows=106.33 loops=3)
                                Filter: ((ts >= '2026-01-01 00:00:00+00'::timestamp with time zone) AND (ts < '2026-04-01 00:00:00+00'::timestamp with time zone))
                                Rows Removed by Filter: 109
                                Buffers: shared hit=48 read=1
                                I/O Timings: shared read=0.022
        ->  Index Scan using category_pkey on category c  (cost=0.15..1.17 rows=1 width=230) (actual time=0.001..0.001 rows=1.00 loops=51)
              Index Cond: (id = e.cat)
              Index Searches: 51
              Buffers: shared hit=102
Planning:
  Buffers: shared hit=293 read=10
  I/O Timings: shared read=1.926
Planning Time: 2.694 ms
Execution Time: 3.412 ms
```

## Q2 — extrato do trimestre, primeira página (pessoa física)

```
Sort  (cost=38.41..38.44 rows=11 width=388) (actual time=0.723..0.725 rows=21.00 loops=1)
  Sort Key: e.ts DESC, e.id DESC
  Sort Method: quicksort  Memory: 28kB
  Buffers: shared hit=48 read=2
  I/O Timings: shared read=0.528
  ->  Nested Loop  (cost=3.30..38.22 rows=11 width=388) (actual time=0.705..0.719 rows=21.00 loops=1)
        Buffers: shared hit=48 read=2
        I/O Timings: shared read=0.528
        ->  Limit  (cost=3.16..25.29 rows=11 width=166) (actual time=0.701..0.703 rows=21.00 loops=1)
              Buffers: shared hit=6 read=2
              I/O Timings: shared read=0.528
              ->  Incremental Sort  (cost=3.16..25.29 rows=11 width=166) (actual time=0.701..0.702 rows=21.00 loops=1)
                    Sort Key: b.month DESC, e.ts DESC, e.id DESC
                    Presorted Key: b.month
                    Full-sort Groups: 1  Sort Method: quicksort  Average Memory: 30kB  Peak Memory: 30kB
                    Buffers: shared hit=6 read=2
                    I/O Timings: shared read=0.528
                    ->  Nested Loop  (cost=0.43..24.87 rows=11 width=166) (actual time=0.378..0.694 rows=21.00 loops=1)
                          Buffers: shared hit=6 read=2
                          I/O Timings: shared read=0.528
                          ->  Index Scan Backward using statement_view_pkey on statement_view b  (cost=0.43..8.25 rows=11 width=846) (actual time=0.347..0.348 rows=4.00 loops=1)
                                Index Cond: ((account_id = '22a1fabb-71ca-ee7a-5ff4-6cf7b6dba0a7'::uuid) AND (month >= '2026-01-01'::date) AND (month <= '2026-04-01'::date))
                                Index Searches: 1
                                Buffers: shared hit=3 read=1
                                I/O Timings: shared read=0.304
                          ->  Function Scan on jsonb_to_recordset e  (cost=0.00..1.50 rows=1 width=162) (actual time=0.085..0.085 rows=5.25 loops=4)
                                Filter: ((ts >= '2026-01-01 00:00:00+00'::timestamp with time zone) AND (ts < '2026-04-01 00:00:00+00'::timestamp with time zone))
                                Rows Removed by Filter: 1
                                Buffers: shared hit=3 read=1
                                I/O Timings: shared read=0.224
        ->  Index Scan using category_pkey on category c  (cost=0.15..1.17 rows=1 width=230) (actual time=0.000..0.000 rows=1.00 loops=21)
              Index Cond: (id = e.cat)
              Index Searches: 21
              Buffers: shared hit=42
Planning Time: 0.079 ms
Execution Time: 0.739 ms
```

## Q3 — resumo mensal de 12 meses (empresa)

```
Incremental Sort  (cost=2.97..56.97 rows=90 width=288) (actual time=0.964..1.153 rows=156.00 loops=1)
  Sort Key: s.month DESC, c.code
  Presorted Key: s.month
  Full-sort Groups: 4  Sort Method: quicksort  Average Memory: 28kB  Peak Memory: 28kB
  Buffers: shared hit=31 read=5
  I/O Timings: shared read=0.739
  ->  Nested Loop  (cost=0.59..54.50 rows=90 width=288) (actual time=0.868..1.061 rows=156.00 loops=1)
        Buffers: shared hit=28 read=5
        I/O Timings: shared read=0.739
        ->  Index Scan Backward using monthly_summary_pkey on monthly_summary s  (cost=0.43..47.49 rows=90 width=30) (actual time=0.712..0.819 rows=156.00 loops=1)
              Index Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (month >= '2025-10-01'::date) AND (month <= '2026-09-01'::date))
              Index Searches: 1
              Buffers: shared hit=2 read=5
              I/O Timings: shared read=0.739
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
  I/O Timings: shared read=0.021
Planning Time: 0.183 ms
Execution Time: 1.182 ms
```

## Q4 — termo comum (empresa)

```
Sort  (cost=321.66..321.69 rows=10 width=388) (actual time=8.119..8.122 rows=50.00 loops=1)
  Sort Key: e.ts DESC, e.id DESC
  Sort Method: quicksort  Memory: 32kB
  Buffers: shared hit=181
  ->  Nested Loop  (cost=39.22..321.50 rows=10 width=388) (actual time=5.275..8.108 rows=50.00 loops=1)
        Buffers: shared hit=181
        ->  Limit  (cost=39.07..309.84 rows=10 width=166) (actual time=5.251..8.057 rows=50.00 loops=1)
              Buffers: shared hit=81
              ->  Incremental Sort  (cost=39.07..309.84 rows=10 width=166) (actual time=5.249..8.053 rows=50.00 loops=1)
                    Sort Key: b.month DESC, e.ts DESC, e.id DESC
                    Presorted Key: b.month
                    Full-sort Groups: 2  Sort Method: quicksort  Average Memory: 34kB  Peak Memory: 34kB
                    Buffers: shared hit=81
                    ->  Nested Loop  (cost=0.43..309.46 rows=10 width=166) (actual time=0.785..8.014 rows=54.00 loops=1)
                          Buffers: shared hit=81
                          ->  Index Scan Backward using statement_view_pkey on statement_view b  (cost=0.43..41.86 rows=10 width=846) (actual time=0.080..0.145 rows=6.00 loops=1)
                                Index Cond: (account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid)
                                Filter: (search_vector @@ '''supermercado'''::tsquery)
                                Rows Removed by Filter: 1
                                Index Searches: 1
                                Buffers: shared hit=30
                          ->  Function Scan on jsonb_to_recordset e  (cost=0.00..26.75 rows=1 width=162) (actual time=0.691..1.306 rows=9.00 loops=6)
                                Filter: (to_tsvector('simple'::regconfig, ((dsc || ' '::text) || cp)) @@ '''supermercado'''::tsquery)
                                Rows Removed by Filter: 252
                                Buffers: shared hit=51
        ->  Index Scan using category_pkey on category c  (cost=0.15..1.16 rows=1 width=230) (actual time=0.001..0.001 rows=1.00 loops=50)
              Index Cond: (id = e.cat)
              Index Searches: 50
              Buffers: shared hit=100
Planning:
  Buffers: shared hit=8 read=16
  I/O Timings: shared read=1.383
Planning Time: 1.769 ms
Execution Time: 8.145 ms
```

## Q4 — termo raro (empresa)

```
Sort  (cost=73.45..73.46 rows=2 width=388) (actual time=20.997..21.000 rows=50.00 loops=1)
  Sort Key: e.ts DESC, e.id DESC
  Sort Method: quicksort  Memory: 31kB
  Buffers: shared hit=284 read=10
  I/O Timings: shared read=0.610
  ->  Nested Loop  (cost=68.78..73.44 rows=2 width=388) (actual time=13.192..20.985 rows=50.00 loops=1)
        Buffers: shared hit=284 read=10
        I/O Timings: shared read=0.610
        ->  Limit  (cost=68.63..68.68 rows=2 width=166) (actual time=13.185..20.950 rows=50.00 loops=1)
              Buffers: shared hit=184 read=10
              I/O Timings: shared read=0.610
              ->  Incremental Sort  (cost=68.63..68.68 rows=2 width=166) (actual time=13.184..20.946 rows=50.00 loops=1)
                    Sort Key: b.month DESC, e.ts DESC, e.id DESC
                    Presorted Key: b.month
                    Full-sort Groups: 2  Sort Method: quicksort  Average Memory: 30kB  Peak Memory: 30kB
                    Buffers: shared hit=184 read=10
                    I/O Timings: shared read=0.610
                    ->  Nested Loop  (cost=0.43..68.62 rows=1 width=166) (actual time=0.608..20.921 rows=52.00 loops=1)
                          Buffers: shared hit=184 read=10
                          I/O Timings: shared read=0.610
                          ->  Index Scan Backward using statement_view_pkey on statement_view b  (cost=0.43..41.86 rows=1 width=846) (actual time=0.019..0.159 rows=15.00 loops=1)
                                Index Cond: (account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid)
                                Filter: (search_vector @@ '''sushi'' & ''kaze'''::tsquery)
                                Rows Removed by Filter: 1
                                Index Searches: 1
                                Buffers: shared hit=67
                          ->  Function Scan on jsonb_to_recordset e  (cost=0.00..26.75 rows=1 width=162) (actual time=0.823..1.381 rows=3.47 loops=15)
                                Filter: (to_tsvector('simple'::regconfig, ((dsc || ' '::text) || cp)) @@ '''sushi'' & ''kaze'''::tsquery)
                                Rows Removed by Filter: 302
                                Buffers: shared hit=117 read=10
                                I/O Timings: shared read=0.610
        ->  Index Scan using category_pkey on category c  (cost=0.15..2.37 rows=1 width=230) (actual time=0.000..0.000 rows=1.00 loops=50)
              Index Cond: (id = e.cat)
              Index Searches: 50
              Buffers: shared hit=100
Planning Time: 0.139 ms
Execution Time: 21.023 ms
```

## Q4 — contraparte (empresa)

```
Sort  (cost=51.84..51.84 rows=3 width=388) (actual time=9.097..9.099 rows=50.00 loops=1)
  Sort Key: e.ts DESC, e.id DESC
  Sort Method: quicksort  Memory: 32kB
  Buffers: shared hit=255
  ->  Nested Loop  (cost=15.66..51.81 rows=3 width=388) (actual time=6.727..9.086 rows=50.00 loops=1)
        Buffers: shared hit=255
        ->  Limit  (cost=15.51..45.78 rows=3 width=166) (actual time=6.722..9.054 rows=50.00 loops=1)
              Buffers: shared hit=155
              ->  Incremental Sort  (cost=15.51..45.78 rows=3 width=166) (actual time=6.721..9.050 rows=50.00 loops=1)
                    Sort Key: b.month DESC, e.ts DESC, e.id DESC
                    Presorted Key: b.month
                    Full-sort Groups: 2  Sort Method: quicksort  Average Memory: 30kB  Peak Memory: 30kB
                    Buffers: shared hit=155
                    ->  Nested Loop  (cost=0.43..45.64 rows=3 width=166) (actual time=0.684..9.030 rows=56.00 loops=1)
                          Buffers: shared hit=155
                          ->  Index Scan Backward using statement_view_pkey on statement_view b  (cost=0.43..41.86 rows=3 width=846) (actual time=0.082..0.530 rows=12.00 loops=1)
                                Index Cond: (account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid)
                                Filter: (counterparties ~~* '%nonna%'::text)
                                Rows Removed by Filter: 1
                                Index Searches: 1
                                Buffers: shared hit=53
                          ->  Function Scan on jsonb_to_recordset e  (cost=0.00..1.25 rows=1 width=162) (actual time=0.601..0.706 rows=4.67 loops=12)
                                Filter: (cp ~~* '%nonna%'::text)
                                Rows Removed by Filter: 292
                                Buffers: shared hit=102
        ->  Index Scan using category_pkey on category c  (cost=0.15..2.00 rows=1 width=230) (actual time=0.000..0.000 rows=1.00 loops=50)
              Index Cond: (id = e.cat)
              Index Searches: 50
              Buffers: shared hit=100
Planning:
  Buffers: shared hit=5
Planning Time: 0.340 ms
Execution Time: 9.156 ms
```

## Q4 — texto + categoria + período (empresa)

```
Sort  (cost=78.18..78.19 rows=2 width=388) (actual time=5.269..5.271 rows=48.00 loops=1)
  Sort Key: e.ts DESC, e.id DESC
  Sort Method: quicksort  Memory: 31kB
  Buffers: shared hit=217
  ->  Nested Loop  (cost=38.22..78.17 rows=2 width=388) (actual time=4.065..5.260 rows=48.00 loops=1)
        Buffers: shared hit=217
        ->  Limit  (cost=38.07..73.41 rows=2 width=166) (actual time=4.062..5.232 rows=48.00 loops=1)
              Buffers: shared hit=121
              InitPlan 1
                ->  Index Scan using category_code_key on category  (cost=0.15..2.37 rows=1 width=2) (actual time=0.020..0.020 rows=1.00 loops=1)
                      Index Cond: ((code)::text = 'HEALTH'::text)
                      Index Searches: 1
                      Buffers: shared hit=2
              ->  Incremental Sort  (cost=35.70..71.04 rows=2 width=166) (actual time=4.061..5.228 rows=48.00 loops=1)
                    Sort Key: b.month DESC, e.ts DESC, e.id DESC
                    Presorted Key: b.month
                    Full-sort Groups: 2  Sort Method: quicksort  Average Memory: 33kB  Peak Memory: 33kB
                    Buffers: shared hit=121
                    ->  Nested Loop  (cost=0.43..70.95 rows=2 width=166) (actual time=0.613..5.211 rows=48.00 loops=1)
                          Buffers: shared hit=121
                          ->  Index Scan Backward using statement_view_pkey on statement_view b  (cost=0.43..16.43 rows=2 width=846) (actual time=0.028..0.055 rows=9.00 loops=1)
                                Index Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (month >= '2026-01-01'::date))
                                Filter: (search_vector @@ '''farmácia'''::tsquery)
                                Rows Removed by Filter: 1
                                Index Searches: 1
                                Buffers: shared hit=42
                          ->  Function Scan on jsonb_to_recordset e  (cost=0.00..27.25 rows=1 width=162) (actual time=0.533..0.571 rows=5.33 loops=9)
                                Filter: ((ts >= '2026-01-01 00:00:00+00'::timestamp with time zone) AND (cat = (InitPlan 1).col1) AND (to_tsvector('simple'::regconfig, ((dsc || ' '::text) || cp)) @@ '''farmácia'''::tsquery))
                                Rows Removed by Filter: 308
                                Buffers: shared hit=79
        ->  Index Scan using category_pkey on category c  (cost=0.15..2.37 rows=1 width=230) (actual time=0.000..0.000 rows=1.00 loops=48)
              Index Cond: (id = e.cat)
              Index Searches: 48
              Buffers: shared hit=96
Planning Time: 0.170 ms
Execution Time: 5.309 ms
```

## Custo de escrita: um lançamento num bloco cheio (empresa, set/2026)

No benchmark, os comandos caem no mês corrente, fora da janela do seed: o bloco começa vazio. Num mês já
cheio, cada comando reescreve o bloco inteiro. Medido aqui numa transação desfeita no fim (ADR-0011).

- Bloco: 293 lançamentos, 33 kB comprimidos

```
Insert on statement_view v  (cost=0.44..2.93 rows=0 width=0) (actual time=2.522..2.522 rows=0.00 loops=1)
  Conflict Resolution: UPDATE
  Conflict Arbiter Indexes: statement_view_pkey
  Tuples Inserted: 0
  Conflicting Tuples: 1
  Buffers: shared hit=148 read=2 dirtied=15 written=5
  I/O Timings: shared read=0.058 write=0.073
  WAL: records=66 fpi=8 bytes=102408
  ->  Index Scan using ledger_entry_pkey on ledger_entry e  (cost=0.44..2.93 rows=1 width=116) (actual time=0.064..0.064 rows=1.00 loops=1)
        Index Cond: (id = 20322238)
        Index Searches: 1
        Buffers: shared hit=7
Planning:
  Buffers: shared hit=93 read=4
  I/O Timings: shared read=0.468
Planning Time: 0.691 ms
Execution Time: 3.026 ms
```
