# EXPLAIN ANALYZE — v2

Gerado por `make bench` (bench/explain/v2.sql) depois das execuções. Conta empresarial `seed-b-0000001`;
pessoa física `seed-r-0050000`. `Buffers: shared hit` = página no cache; `read` = leitura fora do shared_buffers.

## Q1 — saldo (empresa)

```
Index Scan using account_pkey on account  (cost=0.42..2.64 rows=1 width=73) (actual time=0.101..0.102 rows=1.00 loops=1)
  Index Cond: (id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid)
  Index Searches: 1
  Buffers: shared hit=4
Planning:
  Buffers: shared hit=20
Planning Time: 0.158 ms
Execution Time: 0.112 ms
```

## Q2 — extrato do trimestre, primeira página (empresa)

```
Sort  (cost=39.19..39.22 rows=11 width=388) (actual time=4.403..4.406 rows=51.00 loops=1)
  Sort Key: e.ts DESC, e.id DESC
  Sort Method: quicksort  Memory: 32kB
  Buffers: shared hit=148 read=19
  I/O Timings: shared read=1.630
  ->  Nested Loop  (cost=3.39..39.00 rows=11 width=388) (actual time=4.329..4.363 rows=51.00 loops=1)
        Buffers: shared hit=142 read=19
        I/O Timings: shared read=1.630
        ->  Limit  (cost=3.24..26.07 rows=11 width=166) (actual time=4.281..4.286 rows=51.00 loops=1)
              Buffers: shared hit=40 read=19
              I/O Timings: shared read=1.630
              ->  Incremental Sort  (cost=3.24..26.07 rows=11 width=166) (actual time=4.280..4.282 rows=51.00 loops=1)
                    Sort Key: b.month DESC, e.ts DESC, e.id DESC
                    Presorted Key: b.month
                    Full-sort Groups: 1  Sort Method: quicksort  Average Memory: 39kB  Peak Memory: 39kB
                    Pre-sorted Groups: 1  Sort Method: top-N heapsort  Average Memory: 44kB  Peak Memory: 44kB
                    Buffers: shared hit=40 read=19
                    I/O Timings: shared read=1.630
                    ->  Nested Loop  (cost=0.43..25.64 rows=11 width=166) (actual time=3.316..4.175 rows=319.00 loops=1)
                          Buffers: shared hit=34 read=19
                          I/O Timings: shared read=1.630
                          ->  Index Scan Backward using statement_view_pkey on statement_view b  (cost=0.43..9.03 rows=11 width=842) (actual time=0.504..0.505 rows=3.00 loops=1)
                                Index Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (month >= '2026-01-01'::date) AND (month <= '2026-04-01'::date))
                                Index Searches: 1
                                Buffers: shared hit=3 read=1
                                I/O Timings: shared read=0.386
                          ->  Function Scan on jsonb_to_recordset e  (cost=0.00..1.50 rows=1 width=162) (actual time=1.204..1.210 rows=106.33 loops=3)
                                Filter: ((ts >= '2026-01-01 00:00:00+00'::timestamp with time zone) AND (ts < '2026-04-01 00:00:00+00'::timestamp with time zone))
                                Rows Removed by Filter: 109
                                Buffers: shared hit=31 read=18
                                I/O Timings: shared read=1.244
        ->  Index Scan using category_pkey on category c  (cost=0.15..1.17 rows=1 width=230) (actual time=0.001..0.001 rows=1.00 loops=51)
              Index Cond: (id = e.cat)
              Index Searches: 51
              Buffers: shared hit=102
Planning:
  Buffers: shared hit=295 read=8
  I/O Timings: shared read=3.244
Planning Time: 4.163 ms
Execution Time: 4.472 ms
```

## Q2 — extrato do trimestre, primeira página (pessoa física)

```
Sort  (cost=39.19..39.22 rows=11 width=388) (actual time=1.626..1.627 rows=21.00 loops=1)
  Sort Key: e.ts DESC, e.id DESC
  Sort Method: quicksort  Memory: 28kB
  Buffers: shared hit=46 read=4
  I/O Timings: shared read=1.319
  ->  Nested Loop  (cost=3.39..39.00 rows=11 width=388) (actual time=1.607..1.621 rows=21.00 loops=1)
        Buffers: shared hit=46 read=4
        I/O Timings: shared read=1.319
        ->  Limit  (cost=3.24..26.07 rows=11 width=166) (actual time=1.596..1.598 rows=21.00 loops=1)
              Buffers: shared hit=4 read=4
              I/O Timings: shared read=1.319
              ->  Incremental Sort  (cost=3.24..26.07 rows=11 width=166) (actual time=1.595..1.596 rows=21.00 loops=1)
                    Sort Key: b.month DESC, e.ts DESC, e.id DESC
                    Presorted Key: b.month
                    Full-sort Groups: 1  Sort Method: quicksort  Average Memory: 30kB  Peak Memory: 30kB
                    Buffers: shared hit=4 read=4
                    I/O Timings: shared read=1.319
                    ->  Nested Loop  (cost=0.43..25.64 rows=11 width=166) (actual time=0.931..1.585 rows=21.00 loops=1)
                          Buffers: shared hit=4 read=4
                          I/O Timings: shared read=1.319
                          ->  Index Scan Backward using statement_view_pkey on statement_view b  (cost=0.43..9.03 rows=11 width=842) (actual time=0.892..0.893 rows=4.00 loops=1)
                                Index Cond: ((account_id = '22a1fabb-71ca-ee7a-5ff4-6cf7b6dba0a7'::uuid) AND (month >= '2026-01-01'::date) AND (month <= '2026-04-01'::date))
                                Index Searches: 1
                                Buffers: shared hit=2 read=2
                                I/O Timings: shared read=0.824
                          ->  Function Scan on jsonb_to_recordset e  (cost=0.00..1.50 rows=1 width=162) (actual time=0.171..0.171 rows=5.25 loops=4)
                                Filter: ((ts >= '2026-01-01 00:00:00+00'::timestamp with time zone) AND (ts < '2026-04-01 00:00:00+00'::timestamp with time zone))
                                Rows Removed by Filter: 1
                                Buffers: shared hit=2 read=2
                                I/O Timings: shared read=0.495
        ->  Index Scan using category_pkey on category c  (cost=0.15..1.17 rows=1 width=230) (actual time=0.001..0.001 rows=1.00 loops=21)
              Index Cond: (id = e.cat)
              Index Searches: 21
              Buffers: shared hit=42
Planning Time: 0.114 ms
Execution Time: 1.646 ms
```

## Q3 — resumo mensal de 12 meses (empresa)

```
Incremental Sort  (cost=2.94..58.59 rows=93 width=288) (actual time=0.783..1.039 rows=156.00 loops=1)
  Sort Key: s.month DESC, c.code
  Presorted Key: s.month
  Full-sort Groups: 4  Sort Method: quicksort  Average Memory: 28kB  Peak Memory: 28kB
  Buffers: shared hit=32 read=4
  I/O Timings: shared read=0.509
  ->  Nested Loop  (cost=0.59..56.04 rows=93 width=288) (actual time=0.674..0.947 rows=156.00 loops=1)
        Buffers: shared hit=29 read=4
        I/O Timings: shared read=0.509
        ->  Index Scan Backward using monthly_summary_pkey on monthly_summary s  (cost=0.43..49.00 rows=93 width=30) (actual time=0.549..0.724 rows=156.00 loops=1)
              Index Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (month >= '2025-10-01'::date) AND (month <= '2026-09-01'::date))
              Index Searches: 1
              Buffers: shared hit=3 read=4
              I/O Timings: shared read=0.509
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
  I/O Timings: shared read=0.231
Planning Time: 0.449 ms
Execution Time: 1.069 ms
```

## Q4 — termo comum (empresa)

```
Sort  (cost=355.31..355.34 rows=11 width=388) (actual time=10.306..10.308 rows=50.00 loops=1)
  Sort Key: e.ts DESC, e.id DESC
  Sort Method: quicksort  Memory: 32kB
  Buffers: shared hit=153 read=29 dirtied=2
  I/O Timings: shared read=1.934
  ->  Nested Loop  (cost=38.52..355.12 rows=11 width=388) (actual time=7.090..10.291 rows=50.00 loops=1)
        Buffers: shared hit=153 read=29 dirtied=2
        I/O Timings: shared read=1.934
        ->  Limit  (cost=38.37..342.19 rows=11 width=166) (actual time=7.083..10.253 rows=50.00 loops=1)
              Buffers: shared hit=53 read=29 dirtied=2
              I/O Timings: shared read=1.934
              ->  Incremental Sort  (cost=38.37..342.19 rows=11 width=166) (actual time=7.082..10.248 rows=50.00 loops=1)
                    Sort Key: b.month DESC, e.ts DESC, e.id DESC
                    Presorted Key: b.month
                    Full-sort Groups: 2  Sort Method: quicksort  Average Memory: 34kB  Peak Memory: 34kB
                    Buffers: shared hit=53 read=29 dirtied=2
                    I/O Timings: shared read=1.934
                    ->  Nested Loop  (cost=0.43..341.77 rows=11 width=166) (actual time=1.611..10.210 rows=54.00 loops=1)
                          Buffers: shared hit=53 read=29 dirtied=2
                          I/O Timings: shared read=1.934
                          ->  Index Scan Backward using statement_view_pkey on statement_view b  (cost=0.43..47.40 rows=11 width=842) (actual time=0.491..0.785 rows=6.00 loops=1)
                                Index Cond: (account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid)
                                Filter: (search_vector @@ '''supermercado'''::tsquery)
                                Rows Removed by Filter: 1
                                Index Searches: 1
                                Buffers: shared hit=28 read=3 dirtied=2
                                I/O Timings: shared read=0.498
                          ->  Function Scan on jsonb_to_recordset e  (cost=0.00..26.75 rows=1 width=162) (actual time=0.943..1.558 rows=9.00 loops=6)
                                Filter: (to_tsvector('simple'::regconfig, ((dsc || ' '::text) || cp)) @@ '''supermercado'''::tsquery)
                                Rows Removed by Filter: 252
                                Buffers: shared hit=25 read=26
                                I/O Timings: shared read=1.435
        ->  Index Scan using category_pkey on category c  (cost=0.15..1.17 rows=1 width=230) (actual time=0.000..0.000 rows=1.00 loops=50)
              Index Cond: (id = e.cat)
              Index Searches: 50
              Buffers: shared hit=100
Planning:
  Buffers: shared hit=8 read=15 dirtied=1
  I/O Timings: shared read=1.206
Planning Time: 1.914 ms
Execution Time: 10.364 ms
```

## Q4 — termo raro (empresa)

```
Sort  (cost=78.99..79.00 rows=2 width=388) (actual time=24.143..24.146 rows=50.00 loops=1)
  Sort Key: e.ts DESC, e.id DESC
  Sort Method: quicksort  Memory: 31kB
  Buffers: shared hit=256 read=38
  I/O Timings: shared read=2.963
  ->  Nested Loop  (cost=74.32..78.98 rows=2 width=388) (actual time=13.813..24.131 rows=50.00 loops=1)
        Buffers: shared hit=256 read=38
        I/O Timings: shared read=2.963
        ->  Limit  (cost=74.18..74.22 rows=2 width=166) (actual time=13.805..24.089 rows=50.00 loops=1)
              Buffers: shared hit=156 read=38
              I/O Timings: shared read=2.963
              ->  Incremental Sort  (cost=74.18..74.22 rows=2 width=166) (actual time=13.804..24.084 rows=50.00 loops=1)
                    Sort Key: b.month DESC, e.ts DESC, e.id DESC
                    Presorted Key: b.month
                    Full-sort Groups: 2  Sort Method: quicksort  Average Memory: 30kB  Peak Memory: 30kB
                    Buffers: shared hit=156 read=38
                    I/O Timings: shared read=2.963
                    ->  Nested Loop  (cost=0.43..74.17 rows=1 width=166) (actual time=0.612..24.053 rows=52.00 loops=1)
                          Buffers: shared hit=156 read=38
                          I/O Timings: shared read=2.963
                          ->  Index Scan Backward using statement_view_pkey on statement_view b  (cost=0.43..47.40 rows=1 width=842) (actual time=0.019..0.569 rows=15.00 loops=1)
                                Index Cond: (account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid)
                                Filter: (search_vector @@ '''sushi'' & ''kaze'''::tsquery)
                                Rows Removed by Filter: 1
                                Index Searches: 1
                                Buffers: shared hit=64 read=3
                                I/O Timings: shared read=0.328
                          ->  Function Scan on jsonb_to_recordset e  (cost=0.00..26.75 rows=1 width=162) (actual time=1.003..1.554 rows=3.47 loops=15)
                                Filter: (to_tsvector('simple'::regconfig, ((dsc || ' '::text) || cp)) @@ '''sushi'' & ''kaze'''::tsquery)
                                Rows Removed by Filter: 302
                                Buffers: shared hit=92 read=35
                                I/O Timings: shared read=2.635
        ->  Index Scan using category_pkey on category c  (cost=0.15..2.37 rows=1 width=230) (actual time=0.001..0.001 rows=1.00 loops=50)
              Index Cond: (id = e.cat)
              Index Searches: 50
              Buffers: shared hit=100
Planning Time: 0.137 ms
Execution Time: 24.170 ms
```

## Q4 — contraparte (empresa)

```
Sort  (cost=59.98..59.99 rows=4 width=388) (actual time=9.095..9.098 rows=50.00 loops=1)
  Sort Key: e.ts DESC, e.id DESC
  Sort Method: quicksort  Memory: 32kB
  Buffers: shared hit=255
  ->  Nested Loop  (cost=13.59..59.94 rows=4 width=388) (actual time=6.761..9.084 rows=50.00 loops=1)
        Buffers: shared hit=255
        ->  Limit  (cost=13.45..52.63 rows=4 width=166) (actual time=6.752..9.048 rows=50.00 loops=1)
              Buffers: shared hit=155
              ->  Incremental Sort  (cost=13.45..52.63 rows=4 width=166) (actual time=6.751..9.044 rows=50.00 loops=1)
                    Sort Key: b.month DESC, e.ts DESC, e.id DESC
                    Presorted Key: b.month
                    Full-sort Groups: 2  Sort Method: quicksort  Average Memory: 30kB  Peak Memory: 30kB
                    Buffers: shared hit=155
                    ->  Nested Loop  (cost=0.43..52.45 rows=4 width=166) (actual time=0.667..9.022 rows=56.00 loops=1)
                          Buffers: shared hit=155
                          ->  Index Scan Backward using statement_view_pkey on statement_view b  (cost=0.43..47.40 rows=4 width=842) (actual time=0.065..0.515 rows=12.00 loops=1)
                                Index Cond: (account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid)
                                Filter: (counterparties ~~* '%nonna%'::text)
                                Rows Removed by Filter: 1
                                Index Searches: 1
                                Buffers: shared hit=53
                          ->  Function Scan on jsonb_to_recordset e  (cost=0.00..1.25 rows=1 width=162) (actual time=0.599..0.704 rows=4.67 loops=12)
                                Filter: (cp ~~* '%nonna%'::text)
                                Rows Removed by Filter: 292
                                Buffers: shared hit=102
        ->  Index Scan using category_pkey on category c  (cost=0.15..1.82 rows=1 width=230) (actual time=0.000..0.000 rows=1.00 loops=50)
              Index Cond: (id = e.cat)
              Index Searches: 50
              Buffers: shared hit=100
Planning:
  Buffers: shared hit=5
Planning Time: 0.307 ms
Execution Time: 9.154 ms
```

## Q4 — texto + categoria + período (empresa)

```
Sort  (cost=82.26..82.26 rows=2 width=388) (actual time=5.453..5.456 rows=48.00 loops=1)
  Sort Key: e.ts DESC, e.id DESC
  Sort Method: quicksort  Memory: 31kB
  Buffers: shared hit=217
  ->  Nested Loop  (cost=40.25..82.25 rows=2 width=388) (actual time=4.161..5.443 rows=48.00 loops=1)
        Buffers: shared hit=217
        ->  Limit  (cost=40.10..77.48 rows=2 width=166) (actual time=4.155..5.406 rows=48.00 loops=1)
              Buffers: shared hit=121
              InitPlan 1
                ->  Index Scan using category_code_key on category  (cost=0.15..2.37 rows=1 width=2) (actual time=0.033..0.033 rows=1.00 loops=1)
                      Index Cond: ((code)::text = 'HEALTH'::text)
                      Index Searches: 1
                      Buffers: shared hit=2
              ->  Incremental Sort  (cost=37.74..75.12 rows=2 width=166) (actual time=4.154..5.402 rows=48.00 loops=1)
                    Sort Key: b.month DESC, e.ts DESC, e.id DESC
                    Presorted Key: b.month
                    Full-sort Groups: 2  Sort Method: quicksort  Average Memory: 33kB  Peak Memory: 33kB
                    Buffers: shared hit=121
                    ->  Nested Loop  (cost=0.43..75.03 rows=2 width=166) (actual time=0.593..5.381 rows=48.00 loops=1)
                          Buffers: shared hit=121
                          ->  Index Scan Backward using statement_view_pkey on statement_view b  (cost=0.43..20.50 rows=2 width=842) (actual time=0.024..0.066 rows=9.00 loops=1)
                                Index Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (month >= '2026-01-01'::date))
                                Filter: (search_vector @@ '''farmácia'''::tsquery)
                                Rows Removed by Filter: 1
                                Index Searches: 1
                                Buffers: shared hit=42
                          ->  Function Scan on jsonb_to_recordset e  (cost=0.00..27.25 rows=1 width=162) (actual time=0.550..0.589 rows=5.33 loops=9)
                                Filter: ((ts >= '2026-01-01 00:00:00+00'::timestamp with time zone) AND (cat = (InitPlan 1).col1) AND (to_tsvector('simple'::regconfig, ((dsc || ' '::text) || cp)) @@ '''farmácia'''::tsquery))
                                Rows Removed by Filter: 308
                                Buffers: shared hit=79
        ->  Index Scan using category_pkey on category c  (cost=0.15..2.37 rows=1 width=230) (actual time=0.000..0.000 rows=1.00 loops=48)
              Index Cond: (id = e.cat)
              Index Searches: 48
              Buffers: shared hit=96
Planning Time: 0.176 ms
Execution Time: 5.525 ms
```

## Custo de escrita: um lançamento num bloco cheio (empresa, set/2026)

No benchmark, os comandos caem no mês corrente, fora da janela do seed: o bloco começa vazio. Num mês já
cheio, cada comando reescreve o bloco inteiro. Medido aqui numa transação desfeita no fim (ADR-0011).

- Bloco: 293 lançamentos, 33 kB comprimidos

```
Insert on statement_view v  (cost=0.44..2.93 rows=0 width=0) (actual time=2.205..2.205 rows=0.00 loops=1)
  Conflict Resolution: UPDATE
  Conflict Arbiter Indexes: statement_view_pkey
  Tuples Inserted: 0
  Conflicting Tuples: 1
  Buffers: shared hit=150 dirtied=17
  WAL: records=67 fpi=8 bytes=108105
  ->  Index Scan using ledger_entry_pkey on ledger_entry e  (cost=0.44..2.93 rows=1 width=116) (actual time=0.060..0.060 rows=1.00 loops=1)
        Index Cond: (id = 20921477)
        Index Searches: 1
        Buffers: shared hit=7
Planning:
  Buffers: shared hit=92 read=5 dirtied=2
  I/O Timings: shared read=1.072
Planning Time: 1.370 ms
Execution Time: 2.616 ms
```
