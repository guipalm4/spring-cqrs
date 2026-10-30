# EXPLAIN ANALYZE — v1

Gerado por `make bench` (bench/explain/v1.sql) depois das execuções. Conta empresarial `seed-b-0000001`;
pessoa física `seed-r-0050000`. `Buffers: shared hit` = página no cache; `read` = leitura fora do shared_buffers.

## Q1 — saldo (empresa)

```
Index Scan using account_pkey on account  (cost=0.42..2.64 rows=1 width=72) (actual time=0.141..0.142 rows=1.00 loops=1)
  Index Cond: (id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid)
  Index Searches: 1
  Buffers: shared hit=4
Planning:
  Buffers: shared hit=21 dirtied=3
Planning Time: 0.274 ms
Execution Time: 0.152 ms
```

## Q2 — extrato do trimestre, primeira página (empresa)

```
Limit  (cost=0.72..60.75 rows=51 width=337) (actual time=0.742..8.499 rows=51.00 loops=1)
  Buffers: shared hit=36 read=44
  I/O Timings: shared read=6.997
  ->  Nested Loop  (cost=0.72..1104.88 rows=938 width=337) (actual time=0.742..8.492 rows=51.00 loops=1)
        Buffers: shared hit=36 read=44
        I/O Timings: shared read=6.997
        ->  Index Scan Backward using ledger_entry_account_time_idx on ledger_entry e  (cost=0.56..1062.27 rows=938 width=111) (actual time=0.703..8.361 rows=51.00 loops=1)
              Index Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (occurred_at >= '2026-01-01 00:00:00+00'::timestamp with time zone) AND (occurred_at < '2026-04-01 00:00:00+00'::timestamp with time zone))
              Index Searches: 1
              Buffers: shared hit=12 read=44
              I/O Timings: shared read=6.997
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
  Buffers: shared hit=338 read=8
  I/O Timings: shared read=2.109
Planning Time: 4.334 ms
Execution Time: 8.541 ms
```

## Q2 — extrato do trimestre, primeira página (pessoa física)

```
Limit  (cost=0.72..32.83 rows=15 width=337) (actual time=0.850..6.574 rows=21.00 loops=1)
  Buffers: shared hit=32 read=19
  I/O Timings: shared read=6.034
  ->  Nested Loop  (cost=0.72..32.83 rows=15 width=337) (actual time=0.850..6.571 rows=21.00 loops=1)
        Buffers: shared hit=32 read=19
        I/O Timings: shared read=6.034
        ->  Index Scan Backward using ledger_entry_account_time_idx on ledger_entry e  (cost=0.56..18.50 rows=15 width=111) (actual time=0.839..6.500 rows=21.00 loops=1)
              Index Cond: ((account_id = '22a1fabb-71ca-ee7a-5ff4-6cf7b6dba0a7'::uuid) AND (occurred_at >= '2026-01-01 00:00:00+00'::timestamp with time zone) AND (occurred_at < '2026-04-01 00:00:00+00'::timestamp with time zone))
              Index Searches: 1
              Buffers: shared hit=6 read=19
              I/O Timings: shared read=6.034
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
Planning Time: 0.117 ms
Execution Time: 6.601 ms
```

## Q3 — resumo mensal de 12 meses (empresa)

```
GroupAggregate  (cost=405.10..575.65 rows=3790 width=332) (actual time=5.980..6.493 rows=156.00 loops=1)
  Group Key: (to_char(date_trunc('month'::text, (e.occurred_at AT TIME ZONE 'UTC'::text)), 'YYYY-MM'::text)), c.code
  Buffers: shared hit=25 read=25
  I/O Timings: shared read=0.881
  ->  Sort  (cost=405.10..414.57 rows=3790 width=268) (actual time=5.973..6.098 rows=3800.00 loops=1)
        Sort Key: (to_char(date_trunc('month'::text, (e.occurred_at AT TIME ZONE 'UTC'::text)), 'YYYY-MM'::text)) DESC, c.code
        Sort Method: quicksort  Memory: 301kB
        Buffers: shared hit=25 read=25
        I/O Timings: shared read=0.881
        ->  Hash Join  (cost=17.54..179.82 rows=3790 width=268) (actual time=0.345..3.791 rows=3800.00 loops=1)
              Hash Cond: (e.category_id = c.id)
              Buffers: shared hit=22 read=25
              I/O Timings: shared read=0.881
              ->  Index Only Scan using ledger_entry_account_time_idx on ledger_entry e  (cost=0.56..124.34 rows=3790 width=18) (actual time=0.122..1.906 rows=3800.00 loops=1)
                    Index Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (occurred_at >= '2025-10-01 00:00:00+00'::timestamp with time zone) AND (occurred_at < '2026-10-01 00:00:00+00'::timestamp with time zone))
                    Heap Fetches: 0
                    Index Searches: 1
                    Buffers: shared hit=21 read=25
                    I/O Timings: shared read=0.881
              ->  Hash  (cost=13.10..13.10 rows=310 width=230) (actual time=0.024..0.024 rows=13.00 loops=1)
                    Buckets: 1024  Batches: 1  Memory Usage: 9kB
                    Buffers: shared hit=1
                    ->  Seq Scan on category c  (cost=0.00..13.10 rows=310 width=230) (actual time=0.006..0.007 rows=13.00 loops=1)
                          Buffers: shared hit=1
Planning:
  Buffers: shared hit=26
Planning Time: 0.203 ms
Execution Time: 6.581 ms
```

## Q4 — termo comum (empresa)

```
Limit  (cost=674.24..674.36 rows=50 width=337) (actual time=56.689..56.697 rows=50.00 loops=1)
  Buffers: shared hit=554 read=262
  I/O Timings: shared read=39.582
  ->  Sort  (cost=674.24..674.93 rows=278 width=337) (actual time=56.688..56.692 rows=50.00 loops=1)
        Sort Key: e.occurred_at DESC, e.id DESC
        Sort Method: top-N heapsort  Memory: 51kB
        Buffers: shared hit=554 read=262
        I/O Timings: shared read=39.582
        ->  Hash Join  (cost=354.89..665.00 rows=278 width=337) (actual time=13.584..56.451 rows=290.00 loops=1)
              Hash Cond: (e.category_id = c.id)
              Buffers: shared hit=548 read=262
              I/O Timings: shared read=39.582
              ->  Bitmap Heap Scan on ledger_entry e  (cost=337.91..647.29 rows=278 width=111) (actual time=13.564..56.230 rows=290.00 loops=1)
                    Recheck Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (search_vector @@ '''supermercado'''::tsquery))
                    Heap Blocks: exact=290
                    Buffers: shared hit=547 read=262
                    I/O Timings: shared read=39.582
                    ->  Bitmap Index Scan on ledger_entry_account_search_idx  (cost=0.00..337.84 rows=278 width=0) (actual time=13.010..13.010 rows=290.00 loops=1)
                          Index Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (search_vector @@ '''supermercado'''::tsquery))
                          Index Searches: 1
                          Buffers: shared hit=514 read=5
                          I/O Timings: shared read=0.532
              ->  Hash  (cost=13.10..13.10 rows=310 width=230) (actual time=0.010..0.010 rows=13.00 loops=1)
                    Buckets: 1024  Batches: 1  Memory Usage: 9kB
                    Buffers: shared hit=1
                    ->  Seq Scan on category c  (cost=0.00..13.10 rows=310 width=230) (actual time=0.006..0.007 rows=13.00 loops=1)
                          Buffers: shared hit=1
Planning:
  Buffers: shared hit=15 read=16
  I/O Timings: shared read=1.530
Planning Time: 2.073 ms
Execution Time: 57.037 ms
```

## Q4 — termo raro (empresa)

```
Limit  (cost=345.58..345.59 rows=1 width=337) (actual time=17.517..17.523 rows=50.00 loops=1)
  Buffers: shared hit=655 read=87
  I/O Timings: shared read=10.520
  ->  Sort  (cost=345.58..345.59 rows=1 width=337) (actual time=17.516..17.519 rows=50.00 loops=1)
        Sort Key: e.occurred_at DESC, e.id DESC
        Sort Method: quicksort  Memory: 49kB
        Buffers: shared hit=655 read=87
        I/O Timings: shared read=10.520
        ->  Nested Loop  (cost=342.22..345.57 rows=1 width=337) (actual time=6.001..17.445 rows=94.00 loops=1)
              Buffers: shared hit=655 read=87
              I/O Timings: shared read=10.520
              ->  Bitmap Heap Scan on ledger_entry e  (cost=342.08..343.19 rows=1 width=111) (actual time=5.990..17.186 rows=94.00 loops=1)
                    Recheck Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (search_vector @@ '''sushi'' & ''kaze'''::tsquery))
                    Heap Blocks: exact=94
                    Buffers: shared hit=467 read=87
                    I/O Timings: shared read=10.520
                    ->  Bitmap Index Scan on ledger_entry_account_search_idx  (cost=0.00..342.08 rows=1 width=0) (actual time=5.627..5.627 rows=94.00 loops=1)
                          Index Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (search_vector @@ '''sushi'' & ''kaze'''::tsquery))
                          Index Searches: 1
                          Buffers: shared hit=460
              ->  Index Scan using category_pkey on category c  (cost=0.15..2.37 rows=1 width=230) (actual time=0.002..0.002 rows=1.00 loops=94)
                    Index Cond: (id = e.category_id)
                    Index Searches: 94
                    Buffers: shared hit=188
Planning:
  Buffers: shared hit=2
Planning Time: 0.157 ms
Execution Time: 17.549 ms
```

## Q4 — contraparte (empresa)

```
Limit  (cost=639.63..639.76 rows=50 width=337) (actual time=29.031..29.037 rows=50.00 loops=1)
  Buffers: shared hit=675 read=110
  I/O Timings: shared read=14.348
  ->  Sort  (cost=639.63..639.90 rows=108 width=337) (actual time=29.030..29.033 rows=50.00 loops=1)
        Sort Key: e.occurred_at DESC, e.id DESC
        Sort Method: top-N heapsort  Memory: 51kB
        Buffers: shared hit=675 read=110
        I/O Timings: shared read=14.348
        ->  Nested Loop  (cost=493.83..636.04 rows=108 width=337) (actual time=14.301..28.958 rows=113.00 loops=1)
              Buffers: shared hit=675 read=110
              I/O Timings: shared read=14.348
              ->  Bitmap Heap Scan on ledger_entry e  (cost=493.68..613.95 rows=108 width=111) (actual time=14.283..28.845 rows=113.00 loops=1)
                    Recheck Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND ((counterparty_name)::text ~~* '%nonna%'::text))
                    Heap Blocks: exact=113
                    Buffers: shared hit=673 read=110
                    I/O Timings: shared read=14.348
                    ->  Bitmap Index Scan on ledger_entry_account_counterparty_trgm_idx  (cost=0.00..493.65 rows=108 width=0) (actual time=13.825..13.825 rows=113.00 loops=1)
                          Index Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND ((counterparty_name)::text ~~* '%nonna%'::text))
                          Index Searches: 1
                          Buffers: shared hit=665 read=5
                          I/O Timings: shared read=0.569
              ->  Memoize  (cost=0.16..1.41 rows=1 width=230) (actual time=0.000..0.000 rows=1.00 loops=113)
                    Cache Key: e.category_id
                    Cache Mode: logical
                    Hits: 112  Misses: 1  Evictions: 0  Overflows: 0  Memory Usage: 1kB
                    Buffers: shared hit=2
                    ->  Index Scan using category_pkey on category c  (cost=0.15..1.40 rows=1 width=230) (actual time=0.009..0.010 rows=1.00 loops=1)
                          Index Cond: (id = e.category_id)
                          Index Searches: 1
                          Buffers: shared hit=2
Planning:
  Buffers: shared hit=11
Planning Time: 0.699 ms
Execution Time: 29.085 ms
```

## Q4 — texto + categoria + período (empresa)

```
Limit  (cost=451.42..451.43 rows=1 width=337) (actual time=19.958..19.964 rows=48.00 loops=1)
  Buffers: shared hit=373 read=116
  I/O Timings: shared read=14.718
  ->  Sort  (cost=451.42..451.43 rows=1 width=337) (actual time=19.957..19.960 rows=48.00 loops=1)
        Sort Key: e.occurred_at DESC, e.id DESC
        Sort Method: quicksort  Memory: 37kB
        Buffers: shared hit=373 read=116
        I/O Timings: shared read=14.718
        ->  Nested Loop  (cost=337.11..451.41 rows=1 width=337) (actual time=14.887..19.923 rows=48.00 loops=1)
              Join Filter: (c.id = e.category_id)
              Buffers: shared hit=373 read=116
              I/O Timings: shared read=14.718
              ->  Index Scan using category_code_key on category c  (cost=0.15..2.37 rows=1 width=230) (actual time=0.015..0.019 rows=1.00 loops=1)
                    Index Cond: ((code)::text = 'HEALTH'::text)
                    Index Searches: 1
                    Buffers: shared hit=2
              ->  Bitmap Heap Scan on ledger_entry e  (cost=336.96..448.59 rows=37 width=111) (actual time=14.868..19.885 rows=48.00 loops=1)
                    Recheck Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (search_vector @@ '''farmácia'''::tsquery))
                    Filter: (occurred_at >= '2026-01-01 00:00:00+00'::timestamp with time zone)
                    Rows Removed by Filter: 78
                    Heap Blocks: exact=126
                    Buffers: shared hit=371 read=116
                    I/O Timings: shared read=14.718
                    ->  Bitmap Index Scan on ledger_entry_account_search_idx  (cost=0.00..336.95 rows=100 width=0) (actual time=4.133..4.133 rows=126.00 loops=1)
                          Index Cond: ((account_id = 'fd129144-bdea-daf0-b11a-99c27b25c87a'::uuid) AND (search_vector @@ '''farmácia'''::tsquery))
                          Index Searches: 1
                          Buffers: shared hit=361
Planning:
  Buffers: shared hit=2
Planning Time: 0.188 ms
Execution Time: 19.986 ms
```
