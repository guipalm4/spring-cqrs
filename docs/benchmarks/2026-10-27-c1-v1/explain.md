# EXPLAIN ANALYZE — v1

Gerado por `make bench` (bench/explain/v1.sql) depois das execuções. Conta empresarial `seed-b-0000001`;
pessoa física `seed-r-0050000`. `Buffers: shared hit` = página no cache; `read` = leitura fora do shared_buffers.

## Q1 — saldo (empresa)

```
Index Scan using account_pkey on account  (cost=0.42..2.64 rows=1 width=73) (actual time=0.086..0.086 rows=1.00 loops=1)
  Index Cond: (id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid)
  Index Searches: 1
  Buffers: shared hit=4
Planning:
  Buffers: shared hit=16 read=5
  I/O Timings: shared read=1.403
Planning Time: 1.548 ms
Execution Time: 0.094 ms
```

## Q2 — extrato do trimestre, primeira página (empresa)

```
Limit  (cost=0.72..60.78 rows=51 width=337) (actual time=0.585..6.257 rows=51.00 loops=1)
  Buffers: shared hit=36 read=44
  I/O Timings: shared read=5.201
  ->  Nested Loop  (cost=0.72..1087.66 rows=923 width=337) (actual time=0.584..6.249 rows=51.00 loops=1)
        Buffers: shared hit=36 read=44
        I/O Timings: shared read=5.201
        ->  Index Scan Backward using ledger_entry_account_time_idx on ledger_entry e  (cost=0.56..1045.43 rows=923 width=111) (actual time=0.557..6.126 rows=51.00 loops=1)
              Index Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (occurred_at >= '2026-01-01 00:00:00+00'::timestamp with time zone) AND (occurred_at < '2026-04-01 00:00:00+00'::timestamp with time zone))
              Index Searches: 1
              Buffers: shared hit=12 read=44
              I/O Timings: shared read=5.201
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
  I/O Timings: shared read=3.603
Planning Time: 5.632 ms
Execution Time: 6.287 ms
```

## Q2 — extrato do trimestre, primeira página (pessoa física)

```
Limit  (cost=0.72..32.83 rows=15 width=337) (actual time=0.896..4.684 rows=21.00 loops=1)
  Buffers: shared hit=30 read=21
  I/O Timings: shared read=4.347
  ->  Nested Loop  (cost=0.72..32.83 rows=15 width=337) (actual time=0.895..4.680 rows=21.00 loops=1)
        Buffers: shared hit=30 read=21
        I/O Timings: shared read=4.347
        ->  Index Scan Backward using ledger_entry_account_time_idx on ledger_entry e  (cost=0.56..18.50 rows=15 width=111) (actual time=0.888..4.624 rows=21.00 loops=1)
              Index Cond: ((account_id = '22a1fabb-71ca-ee7a-5ff4-6cf7b6dba0a7'::uuid) AND (occurred_at >= '2026-01-01 00:00:00+00'::timestamp with time zone) AND (occurred_at < '2026-04-01 00:00:00+00'::timestamp with time zone))
              Index Searches: 1
              Buffers: shared hit=4 read=21
              I/O Timings: shared read=4.347
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
Planning Time: 0.169 ms
Execution Time: 4.706 ms
```

## Q3 — resumo mensal de 12 meses (empresa)

```
GroupAggregate  (cost=401.52..570.94 rows=3765 width=332) (actual time=5.236..5.737 rows=156.00 loops=1)
  Group Key: (to_char(date_trunc('month'::text, (e.occurred_at AT TIME ZONE 'UTC'::text)), 'YYYY-MM'::text)), c.code
  Buffers: shared hit=19 read=30
  I/O Timings: shared read=0.812
  ->  Sort  (cost=401.52..410.93 rows=3765 width=268) (actual time=5.227..5.346 rows=3800.00 loops=1)
        Sort Key: (to_char(date_trunc('month'::text, (e.occurred_at AT TIME ZONE 'UTC'::text)), 'YYYY-MM'::text)) DESC, c.code
        Sort Method: quicksort  Memory: 301kB
        Buffers: shared hit=19 read=30
        I/O Timings: shared read=0.812
        ->  Hash Join  (cost=17.54..177.90 rows=3765 width=268) (actual time=0.291..3.189 rows=3800.00 loops=1)
              Hash Cond: (e.category_id = c.id)
              Buffers: shared hit=16 read=30
              I/O Timings: shared read=0.812
              ->  Index Only Scan using ledger_entry_account_time_idx on ledger_entry e  (cost=0.56..122.67 rows=3765 width=18) (actual time=0.115..1.496 rows=3800.00 loops=1)
                    Index Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (occurred_at >= '2025-10-01 00:00:00+00'::timestamp with time zone) AND (occurred_at < '2026-10-01 00:00:00+00'::timestamp with time zone))
                    Heap Fetches: 0
                    Index Searches: 1
                    Buffers: shared hit=15 read=30
                    I/O Timings: shared read=0.812
              ->  Hash  (cost=13.10..13.10 rows=310 width=230) (actual time=0.016..0.017 rows=13.00 loops=1)
                    Buckets: 1024  Batches: 1  Memory Usage: 9kB
                    Buffers: shared hit=1
                    ->  Seq Scan on category c  (cost=0.00..13.10 rows=310 width=230) (actual time=0.007..0.008 rows=13.00 loops=1)
                          Buffers: shared hit=1
Planning:
  Buffers: shared hit=26
Planning Time: 0.164 ms
Execution Time: 5.798 ms
```

## Q4 — termo comum (empresa)

```
Limit  (cost=485.83..485.95 rows=50 width=337) (actual time=41.112..41.120 rows=50.00 loops=1)
  Buffers: shared hit=414 read=265
  I/O Timings: shared read=29.699
  ->  Sort  (cost=485.83..486.52 rows=275 width=337) (actual time=41.111..41.115 rows=50.00 loops=1)
        Sort Key: e.occurred_at DESC, e.id DESC
        Sort Method: top-N heapsort  Memory: 51kB
        Buffers: shared hit=414 read=265
        I/O Timings: shared read=29.699
        ->  Hash Join  (cost=169.92..476.69 rows=275 width=337) (actual time=9.738..40.875 rows=290.00 loops=1)
              Hash Cond: (e.category_id = c.id)
              Buffers: shared hit=408 read=265
              I/O Timings: shared read=29.699
              ->  Bitmap Heap Scan on ledger_entry e  (cost=152.95..458.99 rows=275 width=111) (actual time=9.716..40.660 rows=290.00 loops=1)
                    Recheck Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (search_vector @@ '''supermercado'''::tsquery))
                    Heap Blocks: exact=290
                    Buffers: shared hit=407 read=265
                    I/O Timings: shared read=29.699
                    ->  Bitmap Index Scan on ledger_entry_account_search_idx  (cost=0.00..152.88 rows=275 width=0) (actual time=8.913..8.913 rows=290.00 loops=1)
                          Index Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (search_vector @@ '''supermercado'''::tsquery))
                          Index Searches: 1
                          Buffers: shared hit=377 read=5
                          I/O Timings: shared read=1.081
              ->  Hash  (cost=13.10..13.10 rows=310 width=230) (actual time=0.010..0.010 rows=13.00 loops=1)
                    Buckets: 1024  Batches: 1  Memory Usage: 9kB
                    Buffers: shared hit=1
                    ->  Seq Scan on category c  (cost=0.00..13.10 rows=310 width=230) (actual time=0.006..0.007 rows=13.00 loops=1)
                          Buffers: shared hit=1
Planning:
  Buffers: shared hit=13 read=18
  I/O Timings: shared read=1.859
Planning Time: 2.293 ms
Execution Time: 41.386 ms
```

## Q4 — termo raro (empresa)

```
Limit  (cost=160.63..160.63 rows=1 width=337) (actual time=12.612..12.617 rows=50.00 loops=1)
  Buffers: shared hit=524 read=81
  I/O Timings: shared read=6.953
  ->  Sort  (cost=160.63..160.63 rows=1 width=337) (actual time=12.611..12.613 rows=50.00 loops=1)
        Sort Key: e.occurred_at DESC, e.id DESC
        Sort Method: quicksort  Memory: 49kB
        Buffers: shared hit=524 read=81
        I/O Timings: shared read=6.953
        ->  Nested Loop  (cost=157.27..160.62 rows=1 width=337) (actual time=5.046..12.553 rows=94.00 loops=1)
              Buffers: shared hit=524 read=81
              I/O Timings: shared read=6.953
              ->  Bitmap Heap Scan on ledger_entry e  (cost=157.13..158.24 rows=1 width=111) (actual time=5.026..12.312 rows=94.00 loops=1)
                    Recheck Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (search_vector @@ '''sushi'' & ''kaze'''::tsquery))
                    Heap Blocks: exact=94
                    Buffers: shared hit=336 read=81
                    I/O Timings: shared read=6.953
                    ->  Bitmap Index Scan on ledger_entry_account_search_idx  (cost=0.00..157.13 rows=1 width=0) (actual time=4.554..4.554 rows=94.00 loops=1)
                          Index Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (search_vector @@ '''sushi'' & ''kaze'''::tsquery))
                          Index Searches: 1
                          Buffers: shared hit=323
              ->  Index Scan using category_pkey on category c  (cost=0.15..2.37 rows=1 width=230) (actual time=0.001..0.001 rows=1.00 loops=94)
                    Index Cond: (id = e.category_id)
                    Index Searches: 94
                    Buffers: shared hit=188
Planning:
  Buffers: shared hit=2
Planning Time: 0.151 ms
Execution Time: 12.641 ms
```

## Q4 — contraparte (empresa)

```
Limit  (cost=414.94..415.07 rows=50 width=337) (actual time=19.049..19.056 rows=50.00 loops=1)
  Buffers: shared hit=516 read=106
  I/O Timings: shared read=9.371
  ->  Sort  (cost=414.94..415.21 rows=107 width=337) (actual time=19.049..19.052 rows=50.00 loops=1)
        Sort Key: e.occurred_at DESC, e.id DESC
        Sort Method: top-N heapsort  Memory: 51kB
        Buffers: shared hit=516 read=106
        I/O Timings: shared read=9.371
        ->  Hash Join  (cost=291.94..411.39 rows=107 width=337) (actual time=9.331..18.985 rows=113.00 loops=1)
              Hash Cond: (e.category_id = c.id)
              Buffers: shared hit=516 read=106
              I/O Timings: shared read=9.371
              ->  Bitmap Heap Scan on ledger_entry e  (cost=274.97..394.13 rows=107 width=111) (actual time=9.316..18.918 rows=113.00 loops=1)
                    Recheck Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND ((counterparty_name)::text ~~* '%nonna%'::text))
                    Heap Blocks: exact=113
                    Buffers: shared hit=515 read=106
                    I/O Timings: shared read=9.371
                    ->  Bitmap Index Scan on ledger_entry_account_counterparty_trgm_idx  (cost=0.00..274.94 rows=107 width=0) (actual time=8.981..8.981 rows=113.00 loops=1)
                          Index Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND ((counterparty_name)::text ~~* '%nonna%'::text))
                          Index Searches: 1
                          Buffers: shared hit=503 read=5
                          I/O Timings: shared read=0.335
              ->  Hash  (cost=13.10..13.10 rows=310 width=230) (actual time=0.009..0.009 rows=13.00 loops=1)
                    Buckets: 1024  Batches: 1  Memory Usage: 9kB
                    Buffers: shared hit=1
                    ->  Seq Scan on category c  (cost=0.00..13.10 rows=310 width=230) (actual time=0.005..0.006 rows=13.00 loops=1)
                          Buffers: shared hit=1
Planning:
  Buffers: shared hit=11
Planning Time: 0.666 ms
Execution Time: 19.111 ms
```

## Q4 — texto + categoria + período (empresa)

```
Limit  (cost=265.35..265.36 rows=1 width=337) (actual time=13.160..13.166 rows=48.00 loops=1)
  Buffers: shared hit=243 read=109
  I/O Timings: shared read=9.474
  ->  Sort  (cost=265.35..265.36 rows=1 width=337) (actual time=13.159..13.162 rows=48.00 loops=1)
        Sort Key: e.occurred_at DESC, e.id DESC
        Sort Method: quicksort  Memory: 37kB
        Buffers: shared hit=243 read=109
        I/O Timings: shared read=9.474
        ->  Nested Loop  (cost=152.16..265.34 rows=1 width=337) (actual time=10.082..13.126 rows=48.00 loops=1)
              Join Filter: (c.id = e.category_id)
              Buffers: shared hit=243 read=109
              I/O Timings: shared read=9.474
              ->  Index Scan using category_code_key on category c  (cost=0.15..2.37 rows=1 width=230) (actual time=0.022..0.024 rows=1.00 loops=1)
                    Index Cond: ((code)::text = 'HEALTH'::text)
                    Index Searches: 1
                    Buffers: shared hit=2
              ->  Bitmap Heap Scan on ledger_entry e  (cost=152.01..262.51 rows=37 width=111) (actual time=10.057..13.084 rows=48.00 loops=1)
                    Recheck Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (search_vector @@ '''farmácia'''::tsquery))
                    Filter: (occurred_at >= '2026-01-01 00:00:00+00'::timestamp with time zone)
                    Rows Removed by Filter: 78
                    Heap Blocks: exact=126
                    Buffers: shared hit=241 read=109
                    I/O Timings: shared read=9.474
                    ->  Bitmap Index Scan on ledger_entry_account_search_idx  (cost=0.00..152.00 rows=99 width=0) (actual time=2.860..2.860 rows=126.00 loops=1)
                          Index Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (search_vector @@ '''farmácia'''::tsquery))
                          Index Searches: 1
                          Buffers: shared hit=224
Planning:
  Buffers: shared hit=2
Planning Time: 0.155 ms
Execution Time: 13.236 ms
```
