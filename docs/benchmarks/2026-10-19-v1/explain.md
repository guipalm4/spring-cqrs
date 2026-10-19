# EXPLAIN ANALYZE — v1

Gerado por `make bench` (bench/explain/v1.sql) depois das execuções. Conta empresarial `seed-b-0000001`;
pessoa física `seed-r-0050000`. `Buffers: shared hit` = página no cache; `read` = leitura fora do shared_buffers.

## Q1 — saldo (empresa)

```
Index Scan using account_pkey on account  (cost=0.42..2.64 rows=1 width=73) (actual time=0.133..0.134 rows=1.00 loops=1)
  Index Cond: (id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid)
  Index Searches: 1
  Buffers: shared hit=4
Planning:
  Buffers: shared hit=16 read=5
  I/O Timings: shared read=1.813
Planning Time: 1.995 ms
Execution Time: 0.143 ms
```

## Q2 — extrato do trimestre, primeira página (empresa)

```
Limit  (cost=0.72..60.70 rows=51 width=337) (actual time=2.740..15.966 rows=51.00 loops=1)
  Buffers: shared hit=32 read=48
  I/O Timings: shared read=14.227
  ->  Nested Loop  (cost=0.72..1189.75 rows=1011 width=337) (actual time=2.739..15.958 rows=51.00 loops=1)
        Buffers: shared hit=32 read=48
        I/O Timings: shared read=14.227
        ->  Index Scan Backward using ledger_entry_account_time_idx on ledger_entry e  (cost=0.56..1145.31 rows=1011 width=111) (actual time=2.692..15.815 rows=51.00 loops=1)
              Index Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (occurred_at >= '2026-01-01 00:00:00+00'::timestamp with time zone) AND (occurred_at < '2026-04-01 00:00:00+00'::timestamp with time zone))
              Index Searches: 1
              Buffers: shared hit=8 read=48
              I/O Timings: shared read=14.227
        ->  Memoize  (cost=0.16..1.39 rows=1 width=230) (actual time=0.002..0.002 rows=1.00 loops=51)
              Cache Key: e.category_id
              Cache Mode: logical
              Hits: 39  Misses: 12  Evictions: 0  Overflows: 0  Memory Usage: 2kB
              Buffers: shared hit=24
              ->  Index Scan using category_pkey on category c  (cost=0.15..1.38 rows=1 width=230) (actual time=0.004..0.004 rows=1.00 loops=12)
                    Index Cond: (id = e.category_id)
                    Index Searches: 12
                    Buffers: shared hit=24
Planning:
  Buffers: shared hit=326 read=22
  I/O Timings: shared read=7.443
Planning Time: 17.851 ms
Execution Time: 15.994 ms
```

## Q2 — extrato do trimestre, primeira página (pessoa física)

```
Limit  (cost=0.72..32.83 rows=15 width=337) (actual time=0.717..6.353 rows=21.00 loops=1)
  Buffers: shared hit=29 read=22
  I/O Timings: shared read=5.652
  ->  Nested Loop  (cost=0.72..32.83 rows=15 width=337) (actual time=0.717..6.349 rows=21.00 loops=1)
        Buffers: shared hit=29 read=22
        I/O Timings: shared read=5.652
        ->  Index Scan Backward using ledger_entry_account_time_idx on ledger_entry e  (cost=0.56..18.50 rows=15 width=111) (actual time=0.698..6.277 rows=21.00 loops=1)
              Index Cond: ((account_id = '22a1fabb-71ca-ee7a-5ff4-6cf7b6dba0a7'::uuid) AND (occurred_at >= '2026-01-01 00:00:00+00'::timestamp with time zone) AND (occurred_at < '2026-04-01 00:00:00+00'::timestamp with time zone))
              Index Searches: 1
              Buffers: shared hit=3 read=22
              I/O Timings: shared read=5.652
        ->  Memoize  (cost=0.16..1.49 rows=1 width=230) (actual time=0.002..0.002 rows=1.00 loops=21)
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
Planning Time: 0.128 ms
Execution Time: 6.376 ms
```

## Q3 — resumo mensal de 12 meses (empresa)

```
GroupAggregate  (cost=436.57..621.34 rows=4106 width=332) (actual time=21.673..22.181 rows=156.00 loops=1)
  Group Key: (to_char(date_trunc('month'::text, (e.occurred_at AT TIME ZONE 'UTC'::text)), 'YYYY-MM'::text)), c.code
  Buffers: shared hit=19 read=31
  I/O Timings: shared read=5.278
  ->  Sort  (cost=436.57..446.84 rows=4106 width=268) (actual time=21.663..21.783 rows=3800.00 loops=1)
        Sort Key: (to_char(date_trunc('month'::text, (e.occurred_at AT TIME ZONE 'UTC'::text)), 'YYYY-MM'::text)) DESC, c.code
        Sort Method: quicksort  Memory: 301kB
        Buffers: shared hit=19 read=31
        I/O Timings: shared read=5.278
        ->  Hash Join  (cost=17.54..190.14 rows=4106 width=268) (actual time=4.920..11.972 rows=3800.00 loops=1)
              Hash Cond: (e.category_id = c.id)
              Buffers: shared hit=16 read=31
              I/O Timings: shared read=5.278
              ->  Index Only Scan using ledger_entry_account_time_idx on ledger_entry e  (cost=0.56..131.45 rows=4106 width=18) (actual time=0.903..6.394 rows=3800.00 loops=1)
                    Index Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (occurred_at >= '2025-10-01 00:00:00+00'::timestamp with time zone) AND (occurred_at < '2026-10-01 00:00:00+00'::timestamp with time zone))
                    Heap Fetches: 0
                    Index Searches: 1
                    Buffers: shared hit=15 read=31
                    I/O Timings: shared read=5.278
              ->  Hash  (cost=13.10..13.10 rows=310 width=230) (actual time=0.017..0.017 rows=13.00 loops=1)
                    Buckets: 1024  Batches: 1  Memory Usage: 9kB
                    Buffers: shared hit=1
                    ->  Seq Scan on category c  (cost=0.00..13.10 rows=310 width=230) (actual time=0.007..0.008 rows=13.00 loops=1)
                          Buffers: shared hit=1
Planning:
  Buffers: shared hit=26
Planning Time: 0.215 ms
Execution Time: 22.245 ms
```

## Q4 — termo comum (empresa)

```
Limit  (cost=386.96..387.08 rows=50 width=337) (actual time=32.852..32.861 rows=50.00 loops=1)
  Buffers: shared hit=311 read=270
  I/O Timings: shared read=3.292
  ->  Sort  (cost=386.96..387.72 rows=304 width=337) (actual time=32.850..32.857 rows=50.00 loops=1)
        Sort Key: e.occurred_at DESC, e.id DESC
        Sort Method: top-N heapsort  Memory: 51kB
        Buffers: shared hit=311 read=270
        I/O Timings: shared read=3.292
        ->  Hash Join  (cost=37.78..376.86 rows=304 width=337) (actual time=7.296..32.655 rows=290.00 loops=1)
              Hash Cond: (e.category_id = c.id)
              Buffers: shared hit=305 read=270
              I/O Timings: shared read=3.292
              ->  Bitmap Heap Scan on ledger_entry e  (cost=20.80..359.08 rows=304 width=111) (actual time=7.274..32.453 rows=290.00 loops=1)
                    Recheck Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (search_vector @@ '''supermercado'''::tsquery))
                    Heap Blocks: exact=290
                    Buffers: shared hit=304 read=270
                    I/O Timings: shared read=3.292
                    ->  Bitmap Index Scan on ledger_entry_account_search_idx  (cost=0.00..20.72 rows=304 width=0) (actual time=6.373..6.376 rows=290.00 loops=1)
                          Index Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (search_vector @@ '''supermercado'''::tsquery))
                          Index Searches: 1
                          Buffers: shared hit=284
              ->  Hash  (cost=13.10..13.10 rows=310 width=230) (actual time=0.012..0.013 rows=13.00 loops=1)
                    Buckets: 1024  Batches: 1  Memory Usage: 9kB
                    Buffers: shared hit=1
                    ->  Seq Scan on category c  (cost=0.00..13.10 rows=310 width=230) (actual time=0.008..0.009 rows=13.00 loops=1)
                          Buffers: shared hit=1
Planning:
  Buffers: shared hit=15 read=17
  I/O Timings: shared read=6.248
Planning Time: 7.026 ms
Execution Time: 33.742 ms
```

## Q4 — termo raro (empresa)

```
Limit  (cost=28.33..28.34 rows=1 width=337) (actual time=20.400..20.405 rows=50.00 loops=1)
  Buffers: shared hit=437 read=70
  I/O Timings: shared read=13.416
  ->  Sort  (cost=28.33..28.34 rows=1 width=337) (actual time=20.398..20.401 rows=50.00 loops=1)
        Sort Key: e.occurred_at DESC, e.id DESC
        Sort Method: quicksort  Memory: 49kB
        Buffers: shared hit=437 read=70
        I/O Timings: shared read=13.416
        ->  Nested Loop  (cost=24.97..28.32 rows=1 width=337) (actual time=4.034..20.349 rows=94.00 loops=1)
              Buffers: shared hit=437 read=70
              I/O Timings: shared read=13.416
              ->  Bitmap Heap Scan on ledger_entry e  (cost=24.83..25.94 rows=1 width=111) (actual time=4.024..20.099 rows=94.00 loops=1)
                    Recheck Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (search_vector @@ '''sushi'' & ''kaze'''::tsquery))
                    Heap Blocks: exact=94
                    Buffers: shared hit=249 read=70
                    I/O Timings: shared read=13.416
                    ->  Bitmap Index Scan on ledger_entry_account_search_idx  (cost=0.00..24.83 rows=1 width=0) (actual time=3.667..3.667 rows=94.00 loops=1)
                          Index Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (search_vector @@ '''sushi'' & ''kaze'''::tsquery))
                          Index Searches: 1
                          Buffers: shared hit=225
              ->  Index Scan using category_pkey on category c  (cost=0.15..2.37 rows=1 width=230) (actual time=0.001..0.001 rows=1.00 loops=94)
                    Index Cond: (id = e.category_id)
                    Index Searches: 94
                    Buffers: shared hit=188
Planning:
  Buffers: shared hit=2
Planning Time: 0.156 ms
Execution Time: 20.430 ms
```

## Q4 — contraparte (empresa)

```
Limit  (cost=116.51..116.63 rows=50 width=337) (actual time=32.108..32.115 rows=50.00 loops=1)
  Buffers: shared hit=350 read=93
  I/O Timings: shared read=18.450
  ->  Sort  (cost=116.51..116.65 rows=58 width=337) (actual time=32.107..32.111 rows=50.00 loops=1)
        Sort Key: e.occurred_at DESC, e.id DESC
        Sort Method: top-N heapsort  Memory: 51kB
        Buffers: shared hit=350 read=93
        I/O Timings: shared read=18.450
        ->  Hash Join  (cost=50.04..114.81 rows=58 width=337) (actual time=10.122..32.038 rows=113.00 loops=1)
              Hash Cond: (e.category_id = c.id)
              Buffers: shared hit=350 read=93
              I/O Timings: shared read=18.450
              ->  Bitmap Heap Scan on ledger_entry e  (cost=33.07..97.68 rows=58 width=111) (actual time=10.108..31.977 rows=113.00 loops=1)
                    Recheck Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND ((counterparty_name)::text ~~* '%nonna%'::text))
                    Heap Blocks: exact=113
                    Buffers: shared hit=349 read=93
                    I/O Timings: shared read=18.450
                    ->  Bitmap Index Scan on ledger_entry_account_counterparty_trgm_idx  (cost=0.00..33.05 rows=58 width=0) (actual time=8.834..8.834 rows=113.00 loops=1)
                          Index Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND ((counterparty_name)::text ~~* '%nonna%'::text))
                          Index Searches: 1
                          Buffers: shared hit=324 read=5
                          I/O Timings: shared read=1.066
              ->  Hash  (cost=13.10..13.10 rows=310 width=230) (actual time=0.009..0.010 rows=13.00 loops=1)
                    Buckets: 1024  Batches: 1  Memory Usage: 9kB
                    Buffers: shared hit=1
                    ->  Seq Scan on category c  (cost=0.00..13.10 rows=310 width=230) (actual time=0.005..0.006 rows=13.00 loops=1)
                          Buffers: shared hit=1
Planning:
  Buffers: shared hit=11
Planning Time: 4.151 ms
Execution Time: 32.184 ms
```

## Q4 — texto + categoria + período (empresa)

```
Limit  (cost=145.43..145.43 rows=1 width=337) (actual time=31.904..31.909 rows=48.00 loops=1)
  Buffers: shared hit=143 read=111
  I/O Timings: shared read=16.317
  ->  Sort  (cost=145.43..145.43 rows=1 width=337) (actual time=31.903..31.905 rows=48.00 loops=1)
        Sort Key: e.occurred_at DESC, e.id DESC
        Sort Method: quicksort  Memory: 37kB
        Buffers: shared hit=143 read=111
        I/O Timings: shared read=16.317
        ->  Nested Loop  (cost=19.91..145.42 rows=1 width=337) (actual time=23.391..31.864 rows=48.00 loops=1)
              Join Filter: (c.id = e.category_id)
              Buffers: shared hit=143 read=111
              I/O Timings: shared read=16.317
              ->  Index Scan using category_code_key on category c  (cost=0.15..2.37 rows=1 width=230) (actual time=0.016..0.020 rows=1.00 loops=1)
                    Index Cond: ((code)::text = 'HEALTH'::text)
                    Index Searches: 1
                    Buffers: shared hit=2
              ->  Bitmap Heap Scan on ledger_entry e  (cost=19.76..142.54 rows=41 width=111) (actual time=23.371..31.826 rows=48.00 loops=1)
                    Recheck Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (search_vector @@ '''farmácia'''::tsquery))
                    Filter: (occurred_at >= '2026-01-01 00:00:00+00'::timestamp with time zone)
                    Rows Removed by Filter: 78
                    Heap Blocks: exact=126
                    Buffers: shared hit=141 read=111
                    I/O Timings: shared read=16.317
                    ->  Bitmap Index Scan on ledger_entry_account_search_idx  (cost=0.00..19.75 rows=110 width=0) (actual time=2.299..2.299 rows=126.00 loops=1)
                          Index Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (search_vector @@ '''farmácia'''::tsquery))
                          Index Searches: 1
                          Buffers: shared hit=126
Planning:
  Buffers: shared hit=2
Planning Time: 0.137 ms
Execution Time: 31.933 ms
```
