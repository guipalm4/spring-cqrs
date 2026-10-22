# EXPLAIN ANALYZE — v1

Gerado por `make bench` (bench/explain/v1.sql) depois das execuções. Conta empresarial `seed-b-0000001`;
pessoa física `seed-r-0050000`. `Buffers: shared hit` = página no cache; `read` = leitura fora do shared_buffers.

## Q1 — saldo (empresa)

```
Index Scan using account_pkey on account  (cost=0.42..2.64 rows=1 width=72) (actual time=0.038..0.039 rows=1.00 loops=1)
  Index Cond: (id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid)
  Index Searches: 1
  Buffers: shared hit=4
Planning:
  Buffers: shared hit=20 read=1
  I/O Timings: shared read=0.193
Planning Time: 0.314 ms
Execution Time: 0.047 ms
```

## Q2 — extrato do trimestre, primeira página (empresa)

```
Limit  (cost=0.72..60.58 rows=51 width=337) (actual time=0.380..6.152 rows=51.00 loops=1)
  Buffers: shared hit=32 read=48
  I/O Timings: shared read=5.671
  ->  Nested Loop  (cost=0.72..1295.29 rows=1103 width=337) (actual time=0.379..6.145 rows=51.00 loops=1)
        Buffers: shared hit=32 read=48
        I/O Timings: shared read=5.671
        ->  Index Scan Backward using ledger_entry_account_time_idx on ledger_entry e  (cost=0.56..1248.58 rows=1103 width=111) (actual time=0.363..6.059 rows=51.00 loops=1)
              Index Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (occurred_at >= '2026-01-01 00:00:00+00'::timestamp with time zone) AND (occurred_at < '2026-04-01 00:00:00+00'::timestamp with time zone))
              Index Searches: 1
              Buffers: shared hit=8 read=48
              I/O Timings: shared read=5.671
        ->  Memoize  (cost=0.16..1.39 rows=1 width=230) (actual time=0.001..0.001 rows=1.00 loops=51)
              Cache Key: e.category_id
              Cache Mode: logical
              Hits: 39  Misses: 12  Evictions: 0  Overflows: 0  Memory Usage: 2kB
              Buffers: shared hit=24
              ->  Index Scan using category_pkey on category c  (cost=0.15..1.38 rows=1 width=230) (actual time=0.002..0.002 rows=1.00 loops=12)
                    Index Cond: (id = e.category_id)
                    Index Searches: 12
                    Buffers: shared hit=24
Planning:
  Buffers: shared hit=347
Planning Time: 1.344 ms
Execution Time: 6.174 ms
```

## Q2 — extrato do trimestre, primeira página (pessoa física)

```
Limit  (cost=0.72..32.83 rows=15 width=337) (actual time=0.544..3.846 rows=21.00 loops=1)
  Buffers: shared hit=29 read=22
  I/O Timings: shared read=3.604
  ->  Nested Loop  (cost=0.72..32.83 rows=15 width=337) (actual time=0.543..3.843 rows=21.00 loops=1)
        Buffers: shared hit=29 read=22
        I/O Timings: shared read=3.604
        ->  Index Scan Backward using ledger_entry_account_time_idx on ledger_entry e  (cost=0.56..18.50 rows=15 width=111) (actual time=0.535..3.789 rows=21.00 loops=1)
              Index Cond: ((account_id = '22a1fabb-71ca-ee7a-5ff4-6cf7b6dba0a7'::uuid) AND (occurred_at >= '2026-01-01 00:00:00+00'::timestamp with time zone) AND (occurred_at < '2026-04-01 00:00:00+00'::timestamp with time zone))
              Index Searches: 1
              Buffers: shared hit=3 read=22
              I/O Timings: shared read=3.604
        ->  Memoize  (cost=0.16..1.49 rows=1 width=230) (actual time=0.002..0.002 rows=1.00 loops=21)
              Cache Key: e.category_id
              Cache Mode: logical
              Hits: 8  Misses: 13  Evictions: 0  Overflows: 0  Memory Usage: 2kB
              Buffers: shared hit=26
              ->  Index Scan using category_pkey on category c  (cost=0.15..1.48 rows=1 width=230) (actual time=0.001..0.001 rows=1.00 loops=13)
                    Index Cond: (id = e.category_id)
                    Index Searches: 13
                    Buffers: shared hit=26
Planning:
  Buffers: shared hit=2
Planning Time: 0.121 ms
Execution Time: 3.865 ms
```

## Q3 — resumo mensal de 12 meses (empresa)

```
GroupAggregate  (cost=478.35..679.90 rows=4479 width=332) (actual time=6.176..6.687 rows=156.00 loops=1)
  Group Key: (to_char(date_trunc('month'::text, (e.occurred_at AT TIME ZONE 'UTC'::text)), 'YYYY-MM'::text)), c.code
  Buffers: shared hit=19 read=31
  I/O Timings: shared read=1.600
  ->  Sort  (cost=478.35..489.55 rows=4479 width=268) (actual time=6.168..6.296 rows=3800.00 loops=1)
        Sort Key: (to_char(date_trunc('month'::text, (e.occurred_at AT TIME ZONE 'UTC'::text)), 'YYYY-MM'::text)) DESC, c.code
        Sort Method: quicksort  Memory: 301kB
        Buffers: shared hit=19 read=31
        I/O Timings: shared read=1.600
        ->  Hash Join  (cost=17.54..206.72 rows=4479 width=268) (actual time=0.541..3.972 rows=3800.00 loops=1)
              Hash Cond: (e.category_id = c.id)
              Buffers: shared hit=16 read=31
              I/O Timings: shared read=1.600
              ->  Index Only Scan using ledger_entry_account_time_idx on ledger_entry e  (cost=0.56..144.24 rows=4479 width=18) (actual time=0.370..2.276 rows=3800.00 loops=1)
                    Index Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (occurred_at >= '2025-10-01 00:00:00+00'::timestamp with time zone) AND (occurred_at < '2026-10-01 00:00:00+00'::timestamp with time zone))
                    Heap Fetches: 0
                    Index Searches: 1
                    Buffers: shared hit=15 read=31
                    I/O Timings: shared read=1.600
              ->  Hash  (cost=13.10..13.10 rows=310 width=230) (actual time=0.012..0.012 rows=13.00 loops=1)
                    Buckets: 1024  Batches: 1  Memory Usage: 9kB
                    Buffers: shared hit=1
                    ->  Seq Scan on category c  (cost=0.00..13.10 rows=310 width=230) (actual time=0.005..0.006 rows=13.00 loops=1)
                          Buffers: shared hit=1
Planning:
  Buffers: shared hit=26
Planning Time: 0.149 ms
Execution Time: 6.739 ms
```

## Q4 — termo comum (empresa)

```
Limit  (cost=510.84..510.96 rows=50 width=337) (actual time=13.194..13.200 rows=50.00 loops=1)
  Buffers: shared hit=378 read=270
  I/O Timings: shared read=5.382
  ->  Sort  (cost=510.84..511.67 rows=333 width=337) (actual time=13.193..13.196 rows=50.00 loops=1)
        Sort Key: e.occurred_at DESC, e.id DESC
        Sort Method: top-N heapsort  Memory: 51kB
        Buffers: shared hit=378 read=270
        I/O Timings: shared read=5.382
        ->  Hash Join  (cost=128.38..499.78 rows=333 width=337) (actual time=5.854..13.060 rows=290.00 loops=1)
              Hash Cond: (e.category_id = c.id)
              Buffers: shared hit=372 read=270
              I/O Timings: shared read=5.382
              ->  Bitmap Heap Scan on ledger_entry e  (cost=111.40..481.92 rows=333 width=111) (actual time=5.842..12.943 rows=290.00 loops=1)
                    Recheck Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (search_vector @@ '''supermercado'''::tsquery))
                    Heap Blocks: exact=290
                    Buffers: shared hit=371 read=270
                    I/O Timings: shared read=5.382
                    ->  Bitmap Index Scan on ledger_entry_account_search_idx  (cost=0.00..111.32 rows=333 width=0) (actual time=5.695..5.695 rows=290.00 loops=1)
                          Index Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (search_vector @@ '''supermercado'''::tsquery))
                          Index Searches: 1
                          Buffers: shared hit=351
              ->  Hash  (cost=13.10..13.10 rows=310 width=230) (actual time=0.007..0.007 rows=13.00 loops=1)
                    Buckets: 1024  Batches: 1  Memory Usage: 9kB
                    Buffers: shared hit=1
                    ->  Seq Scan on category c  (cost=0.00..13.10 rows=310 width=230) (actual time=0.003..0.004 rows=13.00 loops=1)
                          Buffers: shared hit=1
Planning:
  Buffers: shared hit=31
Planning Time: 0.479 ms
Execution Time: 13.265 ms
```

## Q4 — termo raro (empresa)

```
Limit  (cost=118.78..118.79 rows=1 width=337) (actual time=12.467..12.472 rows=50.00 loops=1)
  Buffers: shared hit=504 read=70
  I/O Timings: shared read=7.781
  ->  Sort  (cost=118.78..118.79 rows=1 width=337) (actual time=12.466..12.468 rows=50.00 loops=1)
        Sort Key: e.occurred_at DESC, e.id DESC
        Sort Method: quicksort  Memory: 49kB
        Buffers: shared hit=504 read=70
        I/O Timings: shared read=7.781
        ->  Nested Loop  (cost=115.42..118.77 rows=1 width=337) (actual time=4.126..12.411 rows=94.00 loops=1)
              Buffers: shared hit=504 read=70
              I/O Timings: shared read=7.781
              ->  Bitmap Heap Scan on ledger_entry e  (cost=115.28..116.39 rows=1 width=111) (actual time=4.117..12.202 rows=94.00 loops=1)
                    Recheck Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (search_vector @@ '''sushi'' & ''kaze'''::tsquery))
                    Heap Blocks: exact=94
                    Buffers: shared hit=316 read=70
                    I/O Timings: shared read=7.781
                    ->  Bitmap Index Scan on ledger_entry_account_search_idx  (cost=0.00..115.28 rows=1 width=0) (actual time=3.787..3.787 rows=94.00 loops=1)
                          Index Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (search_vector @@ '''sushi'' & ''kaze'''::tsquery))
                          Index Searches: 1
                          Buffers: shared hit=292
              ->  Index Scan using category_pkey on category c  (cost=0.15..2.37 rows=1 width=230) (actual time=0.001..0.001 rows=1.00 loops=94)
                    Index Cond: (id = e.category_id)
                    Index Searches: 94
                    Buffers: shared hit=188
Planning:
  Buffers: shared hit=2
Planning Time: 0.087 ms
Execution Time: 12.489 ms
```

## Q4 — contraparte (empresa)

```
Limit  (cost=357.03..357.15 rows=50 width=337) (actual time=19.236..19.242 rows=50.00 loops=1)
  Buffers: shared hit=469 read=93
  I/O Timings: shared read=11.130
  ->  Sort  (cost=357.03..357.34 rows=127 width=337) (actual time=19.235..19.238 rows=50.00 loops=1)
        Sort Key: e.occurred_at DESC, e.id DESC
        Sort Method: top-N heapsort  Memory: 51kB
        Buffers: shared hit=469 read=93
        I/O Timings: shared read=11.130
        ->  Hash Join  (cost=211.05..352.81 rows=127 width=337) (actual time=8.388..19.176 rows=113.00 loops=1)
              Hash Cond: (e.category_id = c.id)
              Buffers: shared hit=469 read=93
              I/O Timings: shared read=11.130
              ->  Bitmap Heap Scan on ledger_entry e  (cost=194.08..335.50 rows=127 width=111) (actual time=8.376..19.120 rows=113.00 loops=1)
                    Recheck Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND ((counterparty_name)::text ~~* '%nonna%'::text))
                    Heap Blocks: exact=113
                    Buffers: shared hit=468 read=93
                    I/O Timings: shared read=11.130
                    ->  Bitmap Index Scan on ledger_entry_account_counterparty_trgm_idx  (cost=0.00..194.05 rows=127 width=0) (actual time=8.286..8.286 rows=113.00 loops=1)
                          Index Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND ((counterparty_name)::text ~~* '%nonna%'::text))
                          Index Searches: 1
                          Buffers: shared hit=443 read=5
                          I/O Timings: shared read=1.131
              ->  Hash  (cost=13.10..13.10 rows=310 width=230) (actual time=0.008..0.008 rows=13.00 loops=1)
                    Buckets: 1024  Batches: 1  Memory Usage: 9kB
                    Buffers: shared hit=1
                    ->  Seq Scan on category c  (cost=0.00..13.10 rows=310 width=230) (actual time=0.004..0.005 rows=13.00 loops=1)
                          Buffers: shared hit=1
Planning:
  Buffers: shared hit=11
Planning Time: 0.533 ms
Execution Time: 19.285 ms
```

## Q4 — texto + categoria + período (empresa)

```
Limit  (cost=244.88..244.88 rows=1 width=337) (actual time=16.454..16.459 rows=48.00 loops=1)
  Buffers: shared hit=210 read=111
  I/O Timings: shared read=12.901
  ->  Sort  (cost=244.88..244.88 rows=1 width=337) (actual time=16.453..16.455 rows=48.00 loops=1)
        Sort Key: e.occurred_at DESC, e.id DESC
        Sort Method: quicksort  Memory: 37kB
        Buffers: shared hit=210 read=111
        I/O Timings: shared read=12.901
        ->  Nested Loop  (cost=110.40..244.87 rows=1 width=337) (actual time=11.143..16.418 rows=48.00 loops=1)
              Join Filter: (c.id = e.category_id)
              Buffers: shared hit=210 read=111
              I/O Timings: shared read=12.901
              ->  Index Scan using category_code_key on category c  (cost=0.15..2.37 rows=1 width=230) (actual time=0.008..0.011 rows=1.00 loops=1)
                    Index Cond: ((code)::text = 'HEALTH'::text)
                    Index Searches: 1
                    Buffers: shared hit=2
              ->  Bitmap Heap Scan on ledger_entry e  (cost=110.25..241.95 rows=44 width=111) (actual time=11.132..16.390 rows=48.00 loops=1)
                    Recheck Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (search_vector @@ '''farmácia'''::tsquery))
                    Filter: (occurred_at >= '2026-01-01 00:00:00+00'::timestamp with time zone)
                    Rows Removed by Filter: 78
                    Heap Blocks: exact=126
                    Buffers: shared hit=208 read=111
                    I/O Timings: shared read=12.901
                    ->  Bitmap Index Scan on ledger_entry_account_search_idx  (cost=0.00..110.24 rows=118 width=0) (actual time=2.659..2.659 rows=126.00 loops=1)
                          Index Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (search_vector @@ '''farmácia'''::tsquery))
                          Index Searches: 1
                          Buffers: shared hit=193
Planning:
  Buffers: shared hit=2
Planning Time: 0.161 ms
Execution Time: 16.477 ms
```
