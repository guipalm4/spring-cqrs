-- Schema da v1: modelo único, leitura e escrita nas mesmas tabelas.
-- Índices pensados para Q1–Q4 (docs/SCOPE.md); a baseline precisa ser honesta (ADR-0002).

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE category (
    id   smallint PRIMARY KEY,
    code varchar(32) NOT NULL UNIQUE,
    name varchar(64) NOT NULL
);

INSERT INTO category (id, code, name) VALUES
    (1, 'SALARY', 'Salário'),
    (2, 'REFUND', 'Estorno'),
    (3, 'OTHER_INCOME', 'Outras receitas'),
    (4, 'GROCERIES', 'Supermercado'),
    (5, 'RESTAURANTS', 'Restaurantes'),
    (6, 'TRANSPORT', 'Transporte'),
    (7, 'UTILITIES', 'Contas da casa'),
    (8, 'HEALTH', 'Saúde'),
    (9, 'SHOPPING', 'Compras'),
    (10, 'ENTERTAINMENT', 'Lazer'),
    (11, 'EDUCATION', 'Educação'),
    (12, 'RENT', 'Aluguel'),
    (13, 'TRANSFER', 'Transferência');

CREATE TABLE account (
    id              uuid PRIMARY KEY DEFAULT uuidv7(),
    holder_id       varchar(64) NOT NULL,
    holder_name     varchar(200) NOT NULL,
    currency        char(3) NOT NULL,
    balance         bigint NOT NULL,
    overdraft_limit bigint NOT NULL DEFAULT 0 CHECK (overdraft_limit >= 0),
    created_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT account_holder_currency_uk UNIQUE (holder_id, currency),
    CONSTRAINT account_balance_within_limit CHECK (balance >= -overdraft_limit)
);

-- Append-only. A contraparte é gravada como foto no momento do lançamento, como num extrato real.
CREATE TABLE ledger_entry (
    id                      bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    transaction_id          uuid NOT NULL,
    account_id              uuid NOT NULL REFERENCES account (id),
    kind                    varchar(16) NOT NULL
        CHECK (kind IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER_IN', 'TRANSFER_OUT')),
    amount                  bigint NOT NULL CHECK (amount <> 0),
    balance_after           bigint NOT NULL,
    category_id             smallint NOT NULL REFERENCES category (id),
    description             varchar(200) NOT NULL,
    counterparty_name       varchar(200) NOT NULL,
    counterparty_account_id uuid REFERENCES account (id),
    occurred_at             timestamptz NOT NULL,
    search_vector           tsvector GENERATED ALWAYS AS
        (to_tsvector('simple', description || ' ' || counterparty_name)) STORED
);

-- Q2 (extrato por período, keyset) e Q3 (agregação mensal): INCLUDE permite index-only scan no Q3.
CREATE INDEX ledger_entry_account_time_idx
    ON ledger_entry (account_id, occurred_at, id) INCLUDE (amount, category_id);
-- Q4: busca textual em descrição + contraparte e substring na contraparte.
CREATE INDEX ledger_entry_search_idx ON ledger_entry USING gin (search_vector);
CREATE INDEX ledger_entry_counterparty_trgm_idx ON ledger_entry USING gin (counterparty_name gin_trgm_ops);

-- Uma linha por chave: a resposta (ou a recusa) original do comando.
-- response e violation ficam nulos só dentro da transação que reservou a chave.
CREATE TABLE idempotency_record (
    idempotency_key varchar(255) PRIMARY KEY,
    request_hash    bytea NOT NULL,
    response        jsonb,
    violation       varchar(32),
    detail          text,
    created_at      timestamptz NOT NULL DEFAULT now()
);
