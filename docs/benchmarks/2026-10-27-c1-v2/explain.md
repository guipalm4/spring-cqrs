# EXPLAIN ANALYZE — v2

Gerado por `make bench` (bench/explain/v2.sql) depois das execuções. Conta empresarial `seed-b-0000001`;
pessoa física `seed-r-0050000`. `Buffers: shared hit` = página no cache; `read` = leitura fora do shared_buffers.

## Q1 — saldo (empresa)

```
Index Scan using account_pkey on account  (cost=0.42..2.64 rows=1 width=72) (actual time=0.108..0.109 rows=1.00 loops=1)
  Index Cond: (id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid)
  Index Searches: 1
  Buffers: shared hit=4
Planning:
  Buffers: shared hit=20
Planning Time: 0.161 ms
Execution Time: 0.130 ms
```

## Q2 — extrato do trimestre, primeira página (empresa)

```
Sort  (cost=38.41..38.44 rows=11 width=388) (actual time=3.544..3.547 rows=51.00 loops=1)
  Sort Key: e.ts DESC, e.id DESC
  Sort Method: quicksort  Memory: 32kB
  Buffers: shared hit=162 read=5
  I/O Timings: shared read=0.148
  ->  Nested Loop  (cost=3.30..38.22 rows=11 width=388) (actual time=3.479..3.514 rows=51.00 loops=1)
        Buffers: shared hit=156 read=5
        I/O Timings: shared read=0.148
        ->  Limit  (cost=3.16..25.29 rows=11 width=166) (actual time=3.448..3.454 rows=51.00 loops=1)
              Buffers: shared hit=54 read=5
              I/O Timings: shared read=0.148
              ->  Incremental Sort  (cost=3.16..25.29 rows=11 width=166) (actual time=3.448..3.450 rows=51.00 loops=1)
                    Sort Key: b.month DESC, e.ts DESC, e.id DESC
                    Presorted Key: b.month
                    Full-sort Groups: 1  Sort Method: quicksort  Average Memory: 39kB  Peak Memory: 39kB
                    Pre-sorted Groups: 1  Sort Method: top-N heapsort  Average Memory: 44kB  Peak Memory: 44kB
                    Buffers: shared hit=54 read=5
                    I/O Timings: shared read=0.148
                    ->  Nested Loop  (cost=0.43..24.87 rows=11 width=166) (actual time=2.528..3.320 rows=319.00 loops=1)
                          Buffers: shared hit=48 read=5
                          I/O Timings: shared read=0.148
                          ->  Index Scan Backward using statement_view_pkey on statement_view b  (cost=0.43..8.25 rows=11 width=852) (actual time=0.109..0.110 rows=3.00 loops=1)
                                Index Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (month >= '2026-01-01'::date) AND (month <= '2026-04-01'::date))
                                Index Searches: 1
                                Buffers: shared hit=4
                          ->  Function Scan on jsonb_to_recordset e  (cost=0.00..1.50 rows=1 width=162) (actual time=1.051..1.057 rows=106.33 loops=3)
                                Filter: ((ts >= '2026-01-01 00:00:00+00'::timestamp with time zone) AND (ts < '2026-04-01 00:00:00+00'::timestamp with time zone))
                                Rows Removed by Filter: 109
                                Buffers: shared hit=44 read=5
                                I/O Timings: shared read=0.148
        ->  Index Scan using category_pkey on category c  (cost=0.15..1.17 rows=1 width=230) (actual time=0.001..0.001 rows=1.00 loops=51)
              Index Cond: (id = e.cat)
              Index Searches: 51
              Buffers: shared hit=102
Planning:
  Buffers: shared hit=304
Planning Time: 0.711 ms
Execution Time: 3.594 ms
```

## Q2 — extrato do trimestre, primeira página (pessoa física)

```
Sort  (cost=38.41..38.44 rows=11 width=388) (actual time=1.341..1.342 rows=21.00 loops=1)
  Sort Key: e.ts DESC, e.id DESC
  Sort Method: quicksort  Memory: 28kB
  Buffers: shared hit=46 read=4
  I/O Timings: shared read=1.073
  ->  Nested Loop  (cost=3.30..38.22 rows=11 width=388) (actual time=1.321..1.335 rows=21.00 loops=1)
        Buffers: shared hit=46 read=4
        I/O Timings: shared read=1.073
        ->  Limit  (cost=3.16..25.29 rows=11 width=166) (actual time=1.316..1.319 rows=21.00 loops=1)
              Buffers: shared hit=4 read=4
              I/O Timings: shared read=1.073
              ->  Incremental Sort  (cost=3.16..25.29 rows=11 width=166) (actual time=1.315..1.317 rows=21.00 loops=1)
                    Sort Key: b.month DESC, e.ts DESC, e.id DESC
                    Presorted Key: b.month
                    Full-sort Groups: 1  Sort Method: quicksort  Average Memory: 30kB  Peak Memory: 30kB
                    Buffers: shared hit=4 read=4
                    I/O Timings: shared read=1.073
                    ->  Nested Loop  (cost=0.43..24.87 rows=11 width=166) (actual time=0.612..1.307 rows=21.00 loops=1)
                          Buffers: shared hit=4 read=4
                          I/O Timings: shared read=1.073
                          ->  Index Scan Backward using statement_view_pkey on statement_view b  (cost=0.43..8.25 rows=11 width=852) (actual time=0.568..0.570 rows=4.00 loops=1)
                                Index Cond: ((account_id = '22a1fabb-71ca-ee7a-5ff4-6cf7b6dba0a7'::uuid) AND (month >= '2026-01-01'::date) AND (month <= '2026-04-01'::date))
                                Index Searches: 1
                                Buffers: shared hit=2 read=2
                                I/O Timings: shared read=0.484
                          ->  Function Scan on jsonb_to_recordset e  (cost=0.00..1.50 rows=1 width=162) (actual time=0.182..0.183 rows=5.25 loops=4)
                                Filter: ((ts >= '2026-01-01 00:00:00+00'::timestamp with time zone) AND (ts < '2026-04-01 00:00:00+00'::timestamp with time zone))
                                Rows Removed by Filter: 1
                                Buffers: shared hit=2 read=2
                                I/O Timings: shared read=0.589
        ->  Index Scan using category_pkey on category c  (cost=0.15..1.17 rows=1 width=230) (actual time=0.000..0.000 rows=1.00 loops=21)
              Index Cond: (id = e.cat)
              Index Searches: 21
              Buffers: shared hit=42
Planning Time: 0.131 ms
Execution Time: 1.365 ms
```

## Q3 — resumo mensal de 12 meses (empresa)

```
Incremental Sort  (cost=3.00..57.51 rows=91 width=288) (actual time=0.842..1.019 rows=156.00 loops=1)
  Sort Key: s.month DESC, c.code
  Presorted Key: s.month
  Full-sort Groups: 4  Sort Method: quicksort  Average Memory: 28kB  Peak Memory: 28kB
  Buffers: shared hit=35 read=1
  I/O Timings: shared read=0.509
  ->  Nested Loop  (cost=0.59..55.01 rows=91 width=288) (actual time=0.772..0.939 rows=156.00 loops=1)
        Buffers: shared hit=32 read=1
        I/O Timings: shared read=0.509
        ->  Index Scan Backward using monthly_summary_pkey on monthly_summary s  (cost=0.43..47.99 rows=91 width=30) (actual time=0.610..0.684 rows=156.00 loops=1)
              Index Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (month >= '2025-10-01'::date) AND (month <= '2026-09-01'::date))
              Index Searches: 1
              Buffers: shared hit=6 read=1
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
  Buffers: shared hit=51
Planning Time: 0.185 ms
Execution Time: 1.044 ms
```

## Q4 — termo comum (empresa)

```
Sort  (cost=320.66..320.69 rows=10 width=388) (actual time=9.363..9.365 rows=50.00 loops=1)
  Sort Key: e.ts DESC, e.id DESC
  Sort Method: quicksort  Memory: 32kB
  Buffers: shared hit=163 read=18
  I/O Timings: shared read=1.206
  ->  Nested Loop  (cost=39.09..320.50 rows=10 width=388) (actual time=6.579..9.346 rows=50.00 loops=1)
        Buffers: shared hit=163 read=18
        I/O Timings: shared read=1.206
        ->  Limit  (cost=38.95..308.84 rows=10 width=166) (actual time=6.573..9.313 rows=50.00 loops=1)
              Buffers: shared hit=63 read=18
              I/O Timings: shared read=1.206
              ->  Incremental Sort  (cost=38.95..308.84 rows=10 width=166) (actual time=6.572..9.309 rows=50.00 loops=1)
                    Sort Key: b.month DESC, e.ts DESC, e.id DESC
                    Presorted Key: b.month
                    Full-sort Groups: 2  Sort Method: quicksort  Average Memory: 34kB  Peak Memory: 34kB
                    Buffers: shared hit=63 read=18
                    I/O Timings: shared read=1.206
                    ->  Nested Loop  (cost=0.43..308.46 rows=10 width=166) (actual time=1.358..9.274 rows=54.00 loops=1)
                          Buffers: shared hit=63 read=18
                          I/O Timings: shared read=1.206
                          ->  Index Scan Backward using statement_view_pkey on statement_view b  (cost=0.43..40.86 rows=10 width=852) (actual time=0.107..0.221 rows=6.00 loops=1)
                                Index Cond: (account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid)
                                Filter: (search_vector @@ '''supermercado'''::tsquery)
                                Rows Removed by Filter: 1
                                Index Searches: 1
                                Buffers: shared hit=28 read=2
                                I/O Timings: shared read=0.052
                          ->  Function Scan on jsonb_to_recordset e  (cost=0.00..26.75 rows=1 width=162) (actual time=0.892..1.500 rows=9.00 loops=6)
                                Filter: (to_tsvector('simple'::regconfig, ((dsc || ' '::text) || cp)) @@ '''supermercado'''::tsquery)
                                Rows Removed by Filter: 252
                                Buffers: shared hit=35 read=16
                                I/O Timings: shared read=1.154
        ->  Index Scan using category_pkey on category c  (cost=0.15..1.16 rows=1 width=230) (actual time=0.000..0.000 rows=1.00 loops=50)
              Index Cond: (id = e.cat)
              Index Searches: 50
              Buffers: shared hit=100
Planning:
  Buffers: shared hit=21
Planning Time: 0.433 ms
Execution Time: 9.387 ms
```

## Q4 — termo raro (empresa)

```
Sort  (cost=72.45..72.45 rows=2 width=388) (actual time=23.226..23.229 rows=50.00 loops=1)
  Sort Key: e.ts DESC, e.id DESC
  Sort Method: quicksort  Memory: 31kB
  Buffers: shared hit=256 read=38
  I/O Timings: shared read=2.953
  ->  Nested Loop  (cost=67.78..72.44 rows=2 width=388) (actual time=13.543..23.211 rows=50.00 loops=1)
        Buffers: shared hit=256 read=38
        I/O Timings: shared read=2.953
        ->  Limit  (cost=67.63..67.67 rows=2 width=166) (actual time=13.536..23.173 rows=50.00 loops=1)
              Buffers: shared hit=156 read=38
              I/O Timings: shared read=2.953
              ->  Incremental Sort  (cost=67.63..67.67 rows=2 width=166) (actual time=13.535..23.169 rows=50.00 loops=1)
                    Sort Key: b.month DESC, e.ts DESC, e.id DESC
                    Presorted Key: b.month
                    Full-sort Groups: 2  Sort Method: quicksort  Average Memory: 30kB  Peak Memory: 30kB
                    Buffers: shared hit=156 read=38
                    I/O Timings: shared read=2.953
                    ->  Nested Loop  (cost=0.43..67.62 rows=1 width=166) (actual time=0.604..23.131 rows=52.00 loops=1)
                          Buffers: shared hit=156 read=38
                          I/O Timings: shared read=2.953
                          ->  Index Scan Backward using statement_view_pkey on statement_view b  (cost=0.43..40.86 rows=1 width=852) (actual time=0.012..0.284 rows=15.00 loops=1)
                                Index Cond: (account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid)
                                Filter: (search_vector @@ '''sushi'' & ''kaze'''::tsquery)
                                Rows Removed by Filter: 1
                                Index Searches: 1
                                Buffers: shared hit=64 read=3
                                I/O Timings: shared read=0.106
                          ->  Function Scan on jsonb_to_recordset e  (cost=0.00..26.75 rows=1 width=162) (actual time=0.966..1.517 rows=3.47 loops=15)
                                Filter: (to_tsvector('simple'::regconfig, ((dsc || ' '::text) || cp)) @@ '''sushi'' & ''kaze'''::tsquery)
                                Rows Removed by Filter: 302
                                Buffers: shared hit=92 read=35
                                I/O Timings: shared read=2.847
        ->  Index Scan using category_pkey on category c  (cost=0.15..2.37 rows=1 width=230) (actual time=0.000..0.000 rows=1.00 loops=50)
              Index Cond: (id = e.cat)
              Index Searches: 50
              Buffers: shared hit=100
Planning Time: 0.122 ms
Execution Time: 23.252 ms
```

## Q4 — contraparte (empresa)

```
Sort  (cost=54.93..54.94 rows=5 width=388) (actual time=8.767..8.770 rows=50.00 loops=1)
  Sort Key: e.ts DESC, e.id DESC
  Sort Method: quicksort  Memory: 32kB
  Buffers: shared hit=255
  ->  Nested Loop  (cost=9.94..54.87 rows=5 width=388) (actual time=6.470..8.757 rows=50.00 loops=1)
        Buffers: shared hit=255
        ->  Limit  (cost=9.79..47.38 rows=5 width=166) (actual time=6.466..8.721 rows=50.00 loops=1)
              Buffers: shared hit=155
              ->  Incremental Sort  (cost=9.79..47.38 rows=5 width=166) (actual time=6.465..8.717 rows=50.00 loops=1)
                    Sort Key: b.month DESC, e.ts DESC, e.id DESC
                    Presorted Key: b.month
                    Full-sort Groups: 2  Sort Method: quicksort  Average Memory: 30kB  Peak Memory: 30kB
                    Buffers: shared hit=155
                    ->  Nested Loop  (cost=0.43..47.16 rows=5 width=166) (actual time=0.607..8.694 rows=56.00 loops=1)
                          Buffers: shared hit=155
                          ->  Index Scan Backward using statement_view_pkey on statement_view b  (cost=0.43..40.86 rows=5 width=852) (actual time=0.051..0.526 rows=12.00 loops=1)
                                Index Cond: (account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid)
                                Filter: (counterparties ~~* '%nonna%'::text)
                                Rows Removed by Filter: 1
                                Index Searches: 1
                                Buffers: shared hit=53
                          ->  Function Scan on jsonb_to_recordset e  (cost=0.00..1.25 rows=1 width=162) (actual time=0.573..0.677 rows=4.67 loops=12)
                                Filter: (cp ~~* '%nonna%'::text)
                                Rows Removed by Filter: 292
                                Buffers: shared hit=102
        ->  Index Scan using category_pkey on category c  (cost=0.15..1.49 rows=1 width=230) (actual time=0.000..0.000 rows=1.00 loops=50)
              Index Cond: (id = e.cat)
              Index Searches: 50
              Buffers: shared hit=100
Planning:
  Buffers: shared hit=6
Planning Time: 0.305 ms
Execution Time: 8.802 ms
```

## Q4 — texto + categoria + período (empresa)

```
Sort  (cost=78.18..78.19 rows=2 width=388) (actual time=5.573..5.576 rows=48.00 loops=1)
  Sort Key: e.ts DESC, e.id DESC
  Sort Method: quicksort  Memory: 31kB
  Buffers: shared hit=217
  ->  Nested Loop  (cost=38.22..78.17 rows=2 width=388) (actual time=4.365..5.564 rows=48.00 loops=1)
        Buffers: shared hit=217
        ->  Limit  (cost=38.07..73.41 rows=2 width=166) (actual time=4.360..5.533 rows=48.00 loops=1)
              Buffers: shared hit=121
              InitPlan 1
                ->  Index Scan using category_code_key on category  (cost=0.15..2.37 rows=1 width=2) (actual time=0.063..0.063 rows=1.00 loops=1)
                      Index Cond: ((code)::text = 'HEALTH'::text)
                      Index Searches: 1
                      Buffers: shared hit=2
              ->  Incremental Sort  (cost=35.70..71.04 rows=2 width=166) (actual time=4.359..5.530 rows=48.00 loops=1)
                    Sort Key: b.month DESC, e.ts DESC, e.id DESC
                    Presorted Key: b.month
                    Full-sort Groups: 2  Sort Method: quicksort  Average Memory: 33kB  Peak Memory: 33kB
                    Buffers: shared hit=121
                    ->  Nested Loop  (cost=0.43..70.95 rows=2 width=166) (actual time=0.647..5.510 rows=48.00 loops=1)
                          Buffers: shared hit=121
                          ->  Index Scan Backward using statement_view_pkey on statement_view b  (cost=0.43..16.43 rows=2 width=852) (actual time=0.014..0.068 rows=9.00 loops=1)
                                Index Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (month >= '2026-01-01'::date))
                                Filter: (search_vector @@ '''farmácia'''::tsquery)
                                Rows Removed by Filter: 1
                                Index Searches: 1
                                Buffers: shared hit=42
                          ->  Function Scan on jsonb_to_recordset e  (cost=0.00..27.25 rows=1 width=162) (actual time=0.564..0.603 rows=5.33 loops=9)
                                Filter: ((ts >= '2026-01-01 00:00:00+00'::timestamp with time zone) AND (cat = (InitPlan 1).col1) AND (to_tsvector('simple'::regconfig, ((dsc || ' '::text) || cp)) @@ '''farmácia'''::tsquery))
                                Rows Removed by Filter: 308
                                Buffers: shared hit=79
        ->  Index Scan using category_pkey on category c  (cost=0.15..2.37 rows=1 width=230) (actual time=0.000..0.000 rows=1.00 loops=48)
              Index Cond: (id = e.cat)
              Index Searches: 48
              Buffers: shared hit=96
Planning Time: 0.159 ms
Execution Time: 5.616 ms
```

## Custo de escrita: um lançamento num bloco cheio (empresa, set/2026)

No benchmark, os comandos caem no mês corrente, fora da janela do seed: o bloco começa vazio. Num mês já
cheio, cada comando reescreve o bloco inteiro. Medido aqui numa transação desfeita no fim (ADR-0011).

- Bloco: 293 lançamentos, 33 kB comprimidos

```
Insert on statement_view v  (cost=0.44..2.93 rows=0 width=0) (actual time=1.244..1.244 rows=0.00 loops=1)
  Conflict Resolution: UPDATE
  Conflict Arbiter Indexes: statement_view_pkey
  Tuples Inserted: 0
  Conflicting Tuples: 1
  Buffers: shared hit=142 dirtied=17 written=5
  I/O Timings: shared write=0.064
  WAL: records=67 fpi=12 bytes=131441
  ->  Index Scan using ledger_entry_pkey on ledger_entry e  (cost=0.44..2.93 rows=1 width=116) (actual time=0.053..0.054 rows=1.00 loops=1)
        Index Cond: (id = 20312089)
        Index Searches: 1
        Buffers: shared hit=7
Planning:
  Buffers: shared hit=92 read=5
  I/O Timings: shared read=1.613
Planning Time: 1.925 ms
Execution Time: 1.679 ms
```
