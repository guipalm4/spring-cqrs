-- Projeções da v2 (CQRS lógico): modelos de leitura no mesmo Postgres, atualizados na transação do comando
-- (io.github.guipalm4.ledger.cqrs.projection.Projections) e reconstruídos do ledger por rebuild_projections().
-- Forma e motivo: ADR-0011. Os lançamentos de uma conta ficam juntos, um bloco por conta e mês, em vez de
-- espalhados pela ledger_entry; é a mesma forma do documento conta/mês do MongoDB na v3 (ADR-0003).

-- Mês (UTC) de um lançamento: a chave dos blocos e do resumo mensal.
CREATE FUNCTION ledger_month(occurred_at timestamptz) RETURNS date
    LANGUAGE sql IMMUTABLE PARALLEL SAFE
AS $$ SELECT date_trunc('month', occurred_at AT TIME ZONE 'UTC')::date $$;

-- Um lançamento dentro do bloco. Chaves curtas porque se repetem em cada item:
-- tx = transaction_id, ts = occurred_at, amt = amount, bal = balance_after, cat = category_id,
-- dsc = description, cp = counterparty_name, cpa = counterparty_account_id. O instante vai sempre em UTC:
-- to_jsonb(timestamptz) usaria o fuso da sessão, e o bloco mudaria conforme quem o gravou.
CREATE FUNCTION statement_entry(e ledger_entry) RETURNS jsonb
    LANGUAGE sql STABLE PARALLEL SAFE
AS $$
    SELECT jsonb_build_object('id', e.id, 'tx', e.transaction_id,
                              'ts', to_char(e.occurred_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
                              'kind', e.kind,
                              'amt', e.amount, 'bal', e.balance_after, 'cat', e.category_id,
                              'dsc', e.description, 'cp', e.counterparty_name, 'cpa', e.counterparty_account_id)
$$;

-- Q2 e Q4: um bloco por conta e mês com os lançamentos já no formato do extrato.
--   search_vector: união das palavras do bloco (descrição + contraparte, sem stemming, como na v1). Só
--                  descarta blocos: o lançamento é conferido de novo, um a um, na consulta.
--   counterparties: nomes de contraparte do bloco, um por linha, com o mesmo papel para o filtro por trecho.
CREATE TABLE statement_view (
    account_id     uuid NOT NULL,
    month          date NOT NULL,
    entries        jsonb COMPRESSION lz4 NOT NULL,
    search_vector  tsvector NOT NULL,
    counterparties text NOT NULL,
    PRIMARY KEY (account_id, month)
);

-- Q3: totais pré-agregados por conta, mês e categoria.
CREATE TABLE monthly_summary (
    account_id  uuid NOT NULL,
    month       date NOT NULL,
    category_id smallint NOT NULL,
    credits     bigint NOT NULL,
    debits      bigint NOT NULL,
    entries     bigint NOT NULL,
    PRIMARY KEY (account_id, month, category_id)
);

-- Reconstrói as duas projeções a partir da ledger_entry. Usado depois do seed (make seed), que escreve direto
-- no modelo de escrita (ADR-0008). Os blocos são gravados na ordem da conta, como ficariam se cada conta
-- tivesse sido projetada de uma vez.
CREATE PROCEDURE rebuild_projections()
    LANGUAGE plpgsql
AS $$
DECLARE
    started timestamptz := clock_timestamp();
BEGIN
    PERFORM set_config('work_mem', '256MB', true);
    TRUNCATE statement_view, monthly_summary;

    INSERT INTO statement_view (account_id, month, entries, search_vector, counterparties)
    SELECT e.account_id, ledger_month(e.occurred_at),
           jsonb_agg(statement_entry(e) ORDER BY e.id),
           strip(to_tsvector('simple', string_agg(e.description || ' ' || e.counterparty_name, ' '))),
           string_agg(DISTINCT e.counterparty_name, E'\n')
    FROM ledger_entry e
    GROUP BY e.account_id, ledger_month(e.occurred_at)
    ORDER BY e.account_id, ledger_month(e.occurred_at);
    RAISE NOTICE 'projeções: statement_view em %', clock_timestamp() - started;

    INSERT INTO monthly_summary (account_id, month, category_id, credits, debits, entries)
    SELECT account_id, ledger_month(occurred_at), category_id,
           sum(greatest(amount, 0)), sum(greatest(-amount, 0)), count(*)
    FROM ledger_entry
    GROUP BY 1, 2, 3
    ORDER BY 1, 2, 3;
    RAISE NOTICE 'projeções: monthly_summary em %', clock_timestamp() - started;

    ANALYZE statement_view;
    ANALYZE monthly_summary;
END;
$$;
