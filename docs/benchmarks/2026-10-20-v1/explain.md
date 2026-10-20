# EXPLAIN ANALYZE — v1

Gerado por `make bench` (bench/explain/v1.sql) depois das execuções. Conta empresarial `seed-b-0000001`;
pessoa física `seed-r-0050000`. `Buffers: shared hit` = página no cache; `read` = leitura fora do shared_buffers.

## Q1 — saldo (empresa)

```
Index Scan using account_pkey on account  (cost=0.42..2.64 rows=1 width=73) (actual time=0.195..0.195 rows=1.00 loops=1)
  Index Cond: (id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid)
  Index Searches: 1
  Buffers: shared hit=4
Planning:
  Buffers: shared hit=19 read=2
  I/O Timings: shared read=0.940
Planning Time: 1.274 ms
Execution Time: 0.205 ms
```

## Q2 — extrato do trimestre, primeira página (empresa)

```
Limit  (cost=0.72..60.85 rows=51 width=337) (actual time=0.896..12.557 rows=51.00 loops=1)
  Buffers: shared hit=32 read=48
  I/O Timings: shared read=10.625
  ->  Nested Loop  (cost=0.72..1041.76 rows=883 width=337) (actual time=0.895..12.549 rows=51.00 loops=1)
        Buffers: shared hit=32 read=48
        I/O Timings: shared read=10.625
        ->  Index Scan Backward using ledger_entry_account_time_idx on ledger_entry e  (cost=0.56..1000.53 rows=883 width=111) (actual time=0.815..12.350 rows=51.00 loops=1)
              Index Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (occurred_at >= '2026-01-01 00:00:00+00'::timestamp with time zone) AND (occurred_at < '2026-04-01 00:00:00+00'::timestamp with time zone))
              Index Searches: 1
              Buffers: shared hit=8 read=48
              I/O Timings: shared read=10.625
        ->  Memoize  (cost=0.16..1.39 rows=1 width=230) (actual time=0.003..0.003 rows=1.00 loops=51)
              Cache Key: e.category_id
              Cache Mode: logical
              Hits: 39  Misses: 12  Evictions: 0  Overflows: 0  Memory Usage: 2kB
              Buffers: shared hit=24
              ->  Index Scan using category_pkey on category c  (cost=0.15..1.38 rows=1 width=230) (actual time=0.007..0.007 rows=1.00 loops=12)
                    Index Cond: (id = e.category_id)
                    Index Searches: 12
                    Buffers: shared hit=24
Planning:
  Buffers: shared hit=346
Planning Time: 2.194 ms
Execution Time: 12.600 ms
```

## Q2 — extrato do trimestre, primeira página (pessoa física)

```
Limit  (cost=0.72..32.83 rows=15 width=337) (actual time=0.740..7.306 rows=21.00 loops=1)
  Buffers: shared hit=29 read=22
  I/O Timings: shared read=6.369
  ->  Nested Loop  (cost=0.72..32.83 rows=15 width=337) (actual time=0.739..7.302 rows=21.00 loops=1)
        Buffers: shared hit=29 read=22
        I/O Timings: shared read=6.369
        ->  Index Scan Backward using ledger_entry_account_time_idx on ledger_entry e  (cost=0.56..18.50 rows=15 width=111) (actual time=0.727..7.200 rows=21.00 loops=1)
              Index Cond: ((account_id = '22a1fabb-71ca-ee7a-5ff4-6cf7b6dba0a7'::uuid) AND (occurred_at >= '2026-01-01 00:00:00+00'::timestamp with time zone) AND (occurred_at < '2026-04-01 00:00:00+00'::timestamp with time zone))
              Index Searches: 1
              Buffers: shared hit=3 read=22
              I/O Timings: shared read=6.369
        ->  Memoize  (cost=0.16..1.49 rows=1 width=230) (actual time=0.003..0.003 rows=1.00 loops=21)
              Cache Key: e.category_id
              Cache Mode: logical
              Hits: 8  Misses: 13  Evictions: 0  Overflows: 0  Memory Usage: 2kB
              Buffers: shared hit=26
              ->  Index Scan using category_pkey on category c  (cost=0.15..1.48 rows=1 width=230) (actual time=0.003..0.003 rows=1.00 loops=13)
                    Index Cond: (id = e.category_id)
                    Index Searches: 13
                    Buffers: shared hit=26
Planning:
  Buffers: shared hit=2
Planning Time: 0.072 ms
Execution Time: 7.328 ms
```

## Q3 — resumo mensal de 12 meses (empresa)

```
GroupAggregate  (cost=380.78..542.29 rows=3589 width=332) (actual time=10.584..11.087 rows=156.00 loops=1)
  Group Key: (to_char(date_trunc('month'::text, (e.occurred_at AT TIME ZONE 'UTC'::text)), 'YYYY-MM'::text)), c.code
  Buffers: shared hit=19 read=31
  I/O Timings: shared read=2.039
  ->  Sort  (cost=380.78..389.75 rows=3589 width=268) (actual time=10.569..10.691 rows=3800.00 loops=1)
        Sort Key: (to_char(date_trunc('month'::text, (e.occurred_at AT TIME ZONE 'UTC'::text)), 'YYYY-MM'::text)) DESC, c.code
        Sort Method: quicksort  Memory: 301kB
        Buffers: shared hit=19 read=31
        I/O Timings: shared read=2.039
        ->  Hash Join  (cost=17.54..168.86 rows=3589 width=268) (actual time=0.882..5.182 rows=3800.00 loops=1)
              Hash Cond: (e.category_id = c.id)
              Buffers: shared hit=16 read=31
              I/O Timings: shared read=2.039
              ->  Index Only Scan using ledger_entry_account_time_idx on ledger_entry e  (cost=0.56..115.41 rows=3589 width=18) (actual time=0.373..3.170 rows=3800.00 loops=1)
                    Index Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (occurred_at >= '2025-10-01 00:00:00+00'::timestamp with time zone) AND (occurred_at < '2026-10-01 00:00:00+00'::timestamp with time zone))
                    Heap Fetches: 0
                    Index Searches: 1
                    Buffers: shared hit=15 read=31
                    I/O Timings: shared read=2.039
              ->  Hash  (cost=13.10..13.10 rows=310 width=230) (actual time=0.011..0.011 rows=13.00 loops=1)
                    Buckets: 1024  Batches: 1  Memory Usage: 9kB
                    Buffers: shared hit=1
                    ->  Seq Scan on category c  (cost=0.00..13.10 rows=310 width=230) (actual time=0.006..0.007 rows=13.00 loops=1)
                          Buffers: shared hit=1
Planning:
  Buffers: shared hit=26
Planning Time: 0.209 ms
Execution Time: 11.180 ms
```

## Q4 — termo comum (empresa)

```
Limit  (cost=389.05..389.18 rows=50 width=337) (actual time=30.388..30.395 rows=50.00 loops=1)
  Buffers: shared hit=345 read=270
  I/O Timings: shared read=13.227
  ->  Sort  (cost=389.05..389.72 rows=266 width=337) (actual time=30.387..30.390 rows=50.00 loops=1)
        Sort Key: e.occurred_at DESC, e.id DESC
        Sort Method: top-N heapsort  Memory: 51kB
        Buffers: shared hit=345 read=270
        I/O Timings: shared read=13.227
        ->  Hash Join  (cost=83.47..380.21 rows=266 width=337) (actual time=11.018..30.185 rows=290.00 loops=1)
              Hash Cond: (e.category_id = c.id)
              Buffers: shared hit=339 read=270
              I/O Timings: shared read=13.227
              ->  Bitmap Heap Scan on ledger_entry e  (cost=66.50..362.53 rows=266 width=111) (actual time=10.996..29.984 rows=290.00 loops=1)
                    Recheck Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (search_vector @@ '''supermercado'''::tsquery))
                    Heap Blocks: exact=290
                    Buffers: shared hit=338 read=270
                    I/O Timings: shared read=13.227
                    ->  Bitmap Index Scan on ledger_entry_account_search_idx  (cost=0.00..66.43 rows=266 width=0) (actual time=10.593..10.593 rows=290.00 loops=1)
                          Index Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (search_vector @@ '''supermercado'''::tsquery))
                          Index Searches: 1
                          Buffers: shared hit=318
              ->  Hash  (cost=13.10..13.10 rows=310 width=230) (actual time=0.015..0.016 rows=13.00 loops=1)
                    Buckets: 1024  Batches: 1  Memory Usage: 9kB
                    Buffers: shared hit=1
                    ->  Seq Scan on category c  (cost=0.00..13.10 rows=310 width=230) (actual time=0.011..0.012 rows=13.00 loops=1)
                          Buffers: shared hit=1
Planning:
  Buffers: shared hit=14 read=17
  I/O Timings: shared read=3.405
Planning Time: 4.570 ms
Execution Time: 30.557 ms
```

## Q4 — termo raro (empresa)

```
Limit  (cost=74.23..74.23 rows=1 width=337) (actual time=15.928..15.937 rows=50.00 loops=1)
  Buffers: shared hit=471 read=70
  I/O Timings: shared read=8.669
  ->  Sort  (cost=74.23..74.23 rows=1 width=337) (actual time=15.927..15.933 rows=50.00 loops=1)
        Sort Key: e.occurred_at DESC, e.id DESC
        Sort Method: quicksort  Memory: 49kB
        Buffers: shared hit=471 read=70
        I/O Timings: shared read=8.669
        ->  Nested Loop  (cost=70.87..74.22 rows=1 width=337) (actual time=5.504..15.874 rows=94.00 loops=1)
              Buffers: shared hit=471 read=70
              I/O Timings: shared read=8.669
              ->  Bitmap Heap Scan on ledger_entry e  (cost=70.73..71.84 rows=1 width=111) (actual time=5.489..15.623 rows=94.00 loops=1)
                    Recheck Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (search_vector @@ '''sushi'' & ''kaze'''::tsquery))
                    Heap Blocks: exact=94
                    Buffers: shared hit=283 read=70
                    I/O Timings: shared read=8.669
                    ->  Bitmap Index Scan on ledger_entry_account_search_idx  (cost=0.00..70.73 rows=1 width=0) (actual time=5.104..5.104 rows=94.00 loops=1)
                          Index Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (search_vector @@ '''sushi'' & ''kaze'''::tsquery))
                          Index Searches: 1
                          Buffers: shared hit=259
              ->  Index Scan using category_pkey on category c  (cost=0.15..2.37 rows=1 width=230) (actual time=0.001..0.001 rows=1.00 loops=94)
                    Index Cond: (id = e.category_id)
                    Index Searches: 94
                    Buffers: shared hit=188
Planning:
  Buffers: shared hit=2
Planning Time: 0.157 ms
Execution Time: 15.960 ms
```

## Q4 — contraparte (empresa)

```
Limit  (cost=181.30..181.43 rows=50 width=337) (actual time=31.420..31.427 rows=50.00 loops=1)
  Buffers: shared hit=404 read=93
  I/O Timings: shared read=19.640
  ->  Sort  (cost=181.30..181.43 rows=51 width=337) (actual time=31.419..31.422 rows=50.00 loops=1)
        Sort Key: e.occurred_at DESC, e.id DESC
        Sort Method: top-N heapsort  Memory: 51kB
        Buffers: shared hit=404 read=93
        I/O Timings: shared read=19.640
        ->  Hash Join  (cost=122.90..179.85 rows=51 width=337) (actual time=11.161..31.351 rows=113.00 loops=1)
              Hash Cond: (e.category_id = c.id)
              Buffers: shared hit=404 read=93
              I/O Timings: shared read=19.640
              ->  Bitmap Heap Scan on ledger_entry e  (cost=105.93..162.75 rows=51 width=111) (actual time=11.147..31.287 rows=113.00 loops=1)
                    Recheck Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND ((counterparty_name)::text ~~* '%nonna%'::text))
                    Heap Blocks: exact=113
                    Buffers: shared hit=403 read=93
                    I/O Timings: shared read=19.640
                    ->  Bitmap Index Scan on ledger_entry_account_counterparty_trgm_idx  (cost=0.00..105.91 rows=51 width=0) (actual time=11.019..11.020 rows=113.00 loops=1)
                          Index Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND ((counterparty_name)::text ~~* '%nonna%'::text))
                          Index Searches: 1
                          Buffers: shared hit=378 read=5
                          I/O Timings: shared read=1.554
              ->  Hash  (cost=13.10..13.10 rows=310 width=230) (actual time=0.010..0.010 rows=13.00 loops=1)
                    Buckets: 1024  Batches: 1  Memory Usage: 9kB
                    Buffers: shared hit=1
                    ->  Seq Scan on category c  (cost=0.00..13.10 rows=310 width=230) (actual time=0.005..0.006 rows=13.00 loops=1)
                          Buffers: shared hit=1
Planning:
  Buffers: shared hit=11
Planning Time: 0.945 ms
Execution Time: 31.527 ms
```

## Q4 — texto + categoria + período (empresa)

```
Limit  (cost=175.58..175.58 rows=1 width=337) (actual time=22.504..22.510 rows=48.00 loops=1)
  Buffers: shared hit=177 read=111
  I/O Timings: shared read=16.616
  ->  Sort  (cost=175.58..175.58 rows=1 width=337) (actual time=22.504..22.507 rows=48.00 loops=1)
        Sort Key: e.occurred_at DESC, e.id DESC
        Sort Method: quicksort  Memory: 37kB
        Buffers: shared hit=177 read=111
        I/O Timings: shared read=16.616
        ->  Nested Loop  (cost=65.74..175.57 rows=1 width=337) (actual time=14.645..22.463 rows=48.00 loops=1)
              Join Filter: (c.id = e.category_id)
              Buffers: shared hit=177 read=111
              I/O Timings: shared read=16.616
              ->  Index Scan using category_code_key on category c  (cost=0.15..2.37 rows=1 width=230) (actual time=0.021..0.023 rows=1.00 loops=1)
                    Index Cond: ((code)::text = 'HEALTH'::text)
                    Index Searches: 1
                    Buffers: shared hit=2
              ->  Bitmap Heap Scan on ledger_entry e  (cost=65.59..172.75 rows=36 width=111) (actual time=14.620..22.419 rows=48.00 loops=1)
                    Recheck Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (search_vector @@ '''farmácia'''::tsquery))
                    Filter: (occurred_at >= '2026-01-01 00:00:00+00'::timestamp with time zone)
                    Rows Removed by Filter: 78
                    Heap Blocks: exact=126
                    Buffers: shared hit=175 read=111
                    I/O Timings: shared read=16.616
                    ->  Bitmap Index Scan on ledger_entry_account_search_idx  (cost=0.00..65.58 rows=96 width=0) (actual time=2.638..2.638 rows=126.00 loops=1)
                          Index Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (search_vector @@ '''farmácia'''::tsquery))
                          Index Searches: 1
                          Buffers: shared hit=160
Planning:
  Buffers: shared hit=2
Planning Time: 0.180 ms
Execution Time: 22.530 ms
```
