# EXPLAIN ANALYZE — v1

Gerado por `make bench` (bench/explain/v1.sql) depois das execuções. Conta empresarial `seed-b-0000001`;
pessoa física `seed-r-0050000`. `Buffers: shared hit` = página no cache; `read` = leitura fora do shared_buffers.

## Q1 — saldo (empresa)

```
Index Scan using account_pkey on account  (cost=0.42..2.64 rows=1 width=73) (actual time=0.061..0.062 rows=1.00 loops=1)
  Index Cond: (id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid)
  Index Searches: 1
  Buffers: shared hit=4
Planning:
  Buffers: shared hit=18 read=3
  I/O Timings: shared read=0.491
Planning Time: 0.581 ms
Execution Time: 0.068 ms
```

## Q2 — extrato do trimestre, primeira página (empresa)

```
Limit  (cost=0.72..60.69 rows=51 width=337) (actual time=0.615..9.454 rows=51.00 loops=1)
  Buffers: shared hit=32 read=48
  I/O Timings: shared read=8.994
  ->  Nested Loop  (cost=0.72..1195.49 rows=1016 width=337) (actual time=0.614..9.448 rows=51.00 loops=1)
        Buffers: shared hit=32 read=48
        I/O Timings: shared read=8.994
        ->  Index Scan Backward using ledger_entry_account_time_idx on ledger_entry e  (cost=0.56..1150.92 rows=1016 width=111) (actual time=0.582..9.346 rows=51.00 loops=1)
              Index Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (occurred_at >= '2026-01-01 00:00:00+00'::timestamp with time zone) AND (occurred_at < '2026-04-01 00:00:00+00'::timestamp with time zone))
              Index Searches: 1
              Buffers: shared hit=8 read=48
              I/O Timings: shared read=8.994
        ->  Memoize  (cost=0.16..1.39 rows=1 width=230) (actual time=0.001..0.001 rows=1.00 loops=51)
              Cache Key: e.category_id
              Cache Mode: logical
              Hits: 39  Misses: 12  Evictions: 0  Overflows: 0  Memory Usage: 2kB
              Buffers: shared hit=24
              ->  Index Scan using category_pkey on category c  (cost=0.15..1.38 rows=1 width=230) (actual time=0.003..0.003 rows=1.00 loops=12)
                    Index Cond: (id = e.category_id)
                    Index Searches: 12
                    Buffers: shared hit=24
Planning:
  Buffers: shared hit=326 read=20
  I/O Timings: shared read=3.514
Planning Time: 4.832 ms
Execution Time: 9.480 ms
```

## Q2 — extrato do trimestre, primeira página (pessoa física)

```
Limit  (cost=0.72..32.83 rows=15 width=337) (actual time=0.423..4.061 rows=21.00 loops=1)
  Buffers: shared hit=29 read=22
  I/O Timings: shared read=3.854
  ->  Nested Loop  (cost=0.72..32.83 rows=15 width=337) (actual time=0.423..4.057 rows=21.00 loops=1)
        Buffers: shared hit=29 read=22
        I/O Timings: shared read=3.854
        ->  Index Scan Backward using ledger_entry_account_time_idx on ledger_entry e  (cost=0.56..18.50 rows=15 width=111) (actual time=0.417..3.983 rows=21.00 loops=1)
              Index Cond: ((account_id = '22a1fabb-71ca-ee7a-5ff4-6cf7b6dba0a7'::uuid) AND (occurred_at >= '2026-01-01 00:00:00+00'::timestamp with time zone) AND (occurred_at < '2026-04-01 00:00:00+00'::timestamp with time zone))
              Index Searches: 1
              Buffers: shared hit=3 read=22
              I/O Timings: shared read=3.854
        ->  Memoize  (cost=0.16..1.49 rows=1 width=230) (actual time=0.003..0.003 rows=1.00 loops=21)
              Cache Key: e.category_id
              Cache Mode: logical
              Hits: 8  Misses: 13  Evictions: 0  Overflows: 0  Memory Usage: 2kB
              Buffers: shared hit=26
              ->  Index Scan using category_pkey on category c  (cost=0.15..1.48 rows=1 width=230) (actual time=0.002..0.002 rows=1.00 loops=13)
                    Index Cond: (id = e.category_id)
                    Index Searches: 13
                    Buffers: shared hit=26
Planning:
  Buffers: shared hit=2
Planning Time: 0.093 ms
Execution Time: 4.093 ms
```

## Q3 — resumo mensal de 12 meses (empresa)

```
GroupAggregate  (cost=438.77..624.53 rows=4128 width=332) (actual time=5.939..6.460 rows=156.00 loops=1)
  Group Key: (to_char(date_trunc('month'::text, (e.occurred_at AT TIME ZONE 'UTC'::text)), 'YYYY-MM'::text)), c.code
  Buffers: shared hit=19 read=31
  I/O Timings: shared read=1.492
  ->  Sort  (cost=438.77..449.09 rows=4128 width=268) (actual time=5.929..6.055 rows=3800.00 loops=1)
        Sort Key: (to_char(date_trunc('month'::text, (e.occurred_at AT TIME ZONE 'UTC'::text)), 'YYYY-MM'::text)) DESC, c.code
        Sort Method: quicksort  Memory: 301kB
        Buffers: shared hit=19 read=31
        I/O Timings: shared read=1.492
        ->  Hash Join  (cost=17.54..190.86 rows=4128 width=268) (actual time=0.193..3.672 rows=3800.00 loops=1)
              Hash Cond: (e.category_id = c.id)
              Buffers: shared hit=16 read=31
              I/O Timings: shared read=1.492
              ->  Index Only Scan using ledger_entry_account_time_idx on ledger_entry e  (cost=0.56..131.94 rows=4128 width=18) (actual time=0.052..2.018 rows=3800.00 loops=1)
                    Index Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (occurred_at >= '2025-10-01 00:00:00+00'::timestamp with time zone) AND (occurred_at < '2026-10-01 00:00:00+00'::timestamp with time zone))
                    Heap Fetches: 0
                    Index Searches: 1
                    Buffers: shared hit=15 read=31
                    I/O Timings: shared read=1.492
              ->  Hash  (cost=13.10..13.10 rows=310 width=230) (actual time=0.011..0.011 rows=13.00 loops=1)
                    Buckets: 1024  Batches: 1  Memory Usage: 9kB
                    Buffers: shared hit=1
                    ->  Seq Scan on category c  (cost=0.00..13.10 rows=310 width=230) (actual time=0.005..0.007 rows=13.00 loops=1)
                          Buffers: shared hit=1
Planning:
  Buffers: shared hit=26
Planning Time: 0.149 ms
Execution Time: 6.505 ms
```

## Q4 — termo comum (empresa)

```
Limit  (cost=381.36..381.49 rows=50 width=337) (actual time=11.154..11.160 rows=50.00 loops=1)
  Buffers: shared hit=306 read=270
  I/O Timings: shared read=3.989
  ->  Sort  (cost=381.36..382.12 rows=305 width=337) (actual time=11.152..11.155 rows=50.00 loops=1)
        Sort Key: e.occurred_at DESC, e.id DESC
        Sort Method: top-N heapsort  Memory: 51kB
        Buffers: shared hit=306 read=270
        I/O Timings: shared read=3.989
        ->  Hash Join  (cost=31.03..371.23 rows=305 width=337) (actual time=5.107..11.004 rows=290.00 loops=1)
              Hash Cond: (e.category_id = c.id)
              Buffers: shared hit=300 read=270
              I/O Timings: shared read=3.989
              ->  Bitmap Heap Scan on ledger_entry e  (cost=14.06..353.44 rows=305 width=111) (actual time=5.088..10.877 rows=290.00 loops=1)
                    Recheck Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (search_vector @@ '''supermercado'''::tsquery))
                    Heap Blocks: exact=290
                    Buffers: shared hit=299 read=270
                    I/O Timings: shared read=3.989
                    ->  Bitmap Index Scan on ledger_entry_account_search_idx  (cost=0.00..13.98 rows=305 width=0) (actual time=4.942..4.943 rows=290.00 loops=1)
                          Index Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (search_vector @@ '''supermercado'''::tsquery))
                          Index Searches: 1
                          Buffers: shared hit=279
              ->  Hash  (cost=13.10..13.10 rows=310 width=230) (actual time=0.007..0.007 rows=13.00 loops=1)
                    Buckets: 1024  Batches: 1  Memory Usage: 9kB
                    Buffers: shared hit=1
                    ->  Seq Scan on category c  (cost=0.00..13.10 rows=310 width=230) (actual time=0.004..0.004 rows=13.00 loops=1)
                          Buffers: shared hit=1
Planning:
  Buffers: shared hit=14 read=17
  I/O Timings: shared read=1.746
Planning Time: 2.039 ms
Execution Time: 11.820 ms
```

## Q4 — termo raro (empresa)

```
Limit  (cost=21.58..21.59 rows=1 width=337) (actual time=11.449..11.454 rows=50.00 loops=1)
  Buffers: shared hit=432 read=70
  I/O Timings: shared read=7.484
  ->  Sort  (cost=21.58..21.59 rows=1 width=337) (actual time=11.448..11.450 rows=50.00 loops=1)
        Sort Key: e.occurred_at DESC, e.id DESC
        Sort Method: quicksort  Memory: 49kB
        Buffers: shared hit=432 read=70
        I/O Timings: shared read=7.484
        ->  Nested Loop  (cost=18.22..21.57 rows=1 width=337) (actual time=3.287..11.395 rows=94.00 loops=1)
              Buffers: shared hit=432 read=70
              I/O Timings: shared read=7.484
              ->  Bitmap Heap Scan on ledger_entry e  (cost=18.08..19.19 rows=1 width=111) (actual time=3.274..11.202 rows=94.00 loops=1)
                    Recheck Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (search_vector @@ '''sushi'' & ''kaze'''::tsquery))
                    Heap Blocks: exact=94
                    Buffers: shared hit=244 read=70
                    I/O Timings: shared read=7.484
                    ->  Bitmap Index Scan on ledger_entry_account_search_idx  (cost=0.00..18.08 rows=1 width=0) (actual time=3.021..3.021 rows=94.00 loops=1)
                          Index Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (search_vector @@ '''sushi'' & ''kaze'''::tsquery))
                          Index Searches: 1
                          Buffers: shared hit=220
              ->  Index Scan using category_pkey on category c  (cost=0.15..2.37 rows=1 width=230) (actual time=0.001..0.001 rows=1.00 loops=94)
                    Index Cond: (id = e.category_id)
                    Index Searches: 94
                    Buffers: shared hit=188
Planning:
  Buffers: shared hit=2
Planning Time: 0.157 ms
Execution Time: 11.474 ms
```

## Q4 — contraparte (empresa)

```
Limit  (cost=179.44..179.56 rows=50 width=337) (actual time=20.141..20.148 rows=50.00 loops=1)
  Buffers: shared hit=346 read=93
  I/O Timings: shared read=13.020
  ->  Sort  (cost=179.44..179.73 rows=117 width=337) (actual time=20.140..20.144 rows=50.00 loops=1)
        Sort Key: e.occurred_at DESC, e.id DESC
        Sort Method: top-N heapsort  Memory: 51kB
        Buffers: shared hit=346 read=93
        I/O Timings: shared read=13.020
        ->  Hash Join  (cost=44.95..175.55 rows=117 width=337) (actual time=6.632..20.057 rows=113.00 loops=1)
              Hash Cond: (e.category_id = c.id)
              Buffers: shared hit=346 read=93
              I/O Timings: shared read=13.020
              ->  Bitmap Heap Scan on ledger_entry e  (cost=27.98..158.27 rows=117 width=111) (actual time=6.610..19.948 rows=113.00 loops=1)
                    Recheck Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND ((counterparty_name)::text ~~* '%nonna%'::text))
                    Heap Blocks: exact=113
                    Buffers: shared hit=345 read=93
                    I/O Timings: shared read=13.020
                    ->  Bitmap Index Scan on ledger_entry_account_counterparty_trgm_idx  (cost=0.00..27.95 rows=117 width=0) (actual time=6.249..6.249 rows=113.00 loops=1)
                          Index Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND ((counterparty_name)::text ~~* '%nonna%'::text))
                          Index Searches: 1
                          Buffers: shared hit=320 read=5
                          I/O Timings: shared read=0.990
              ->  Hash  (cost=13.10..13.10 rows=310 width=230) (actual time=0.012..0.012 rows=13.00 loops=1)
                    Buckets: 1024  Batches: 1  Memory Usage: 9kB
                    Buffers: shared hit=1
                    ->  Seq Scan on category c  (cost=0.00..13.10 rows=310 width=230) (actual time=0.004..0.005 rows=13.00 loops=1)
                          Buffers: shared hit=1
Planning:
  Buffers: shared hit=11
Planning Time: 0.542 ms
Execution Time: 20.206 ms
```

## Q4 — texto + categoria + período (empresa)

```
Limit  (cost=137.54..137.55 rows=1 width=337) (actual time=15.491..15.497 rows=48.00 loops=1)
  Buffers: shared hit=138 read=111
  I/O Timings: shared read=10.987
  ->  Sort  (cost=137.54..137.55 rows=1 width=337) (actual time=15.490..15.492 rows=48.00 loops=1)
        Sort Key: e.occurred_at DESC, e.id DESC
        Sort Method: quicksort  Memory: 37kB
        Buffers: shared hit=138 read=111
        I/O Timings: shared read=10.987
        ->  Nested Loop  (cost=13.16..137.53 rows=1 width=337) (actual time=11.412..15.452 rows=48.00 loops=1)
              Join Filter: (c.id = e.category_id)
              Buffers: shared hit=138 read=111
              I/O Timings: shared read=10.987
              ->  Index Scan using category_code_key on category c  (cost=0.15..2.37 rows=1 width=230) (actual time=0.013..0.018 rows=1.00 loops=1)
                    Index Cond: ((code)::text = 'HEALTH'::text)
                    Index Searches: 1
                    Buffers: shared hit=2
              ->  Bitmap Heap Scan on ledger_entry e  (cost=13.01..134.67 rows=40 width=111) (actual time=11.395..15.415 rows=48.00 loops=1)
                    Recheck Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (search_vector @@ '''farmácia'''::tsquery))
                    Filter: (occurred_at >= '2026-01-01 00:00:00+00'::timestamp with time zone)
                    Rows Removed by Filter: 78
                    Heap Blocks: exact=126
                    Buffers: shared hit=136 read=111
                    I/O Timings: shared read=10.987
                    ->  Bitmap Index Scan on ledger_entry_account_search_idx  (cost=0.00..13.00 rows=109 width=0) (actual time=3.062..3.062 rows=126.00 loops=1)
                          Index Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (search_vector @@ '''farmácia'''::tsquery))
                          Index Searches: 1
                          Buffers: shared hit=121
Planning:
  Buffers: shared hit=2
Planning Time: 0.208 ms
Execution Time: 15.524 ms
```
