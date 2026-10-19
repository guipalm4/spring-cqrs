-- Seeder determinístico do dataset de referência (ADR-0005, ADR-0008).
--
-- Instala o schema "seed" com a procedure seed.load(scale, salt). Uso: make seed SCALE=1
-- ATENÇÃO: seed.load apaga contas, lançamentos e chaves de idempotência antes de gerar.
--
-- Referência (scale = 1): 100 mil contas e ~20 milhões de lançamentos entre out/2024 e set/2026.
--   * 1% das contas são empresariais (holder_id seed-b-*): 4.350–13.050 movimentos cada (~44% dos lançamentos).
--   * 99% são de pessoa física (holder_id seed-r-*): 17–158 movimentos cada.
--   * ~17,4 mi movimentos viram ~20 mi lançamentos: cada transferência gera dois, e cada conta um saldo inicial.
--   * Movimentos: 30% depósito, 55% saque, 15% transferência (gera o par OUT/IN em outra conta).
--   * Cada conta recebe um saldo inicial que impede qualquer saldo intermediário negativo.
-- Todo valor sorteado sai de hash(stream, k): o resultado não depende de paralelismo nem da ordem de execução.

CREATE SCHEMA IF NOT EXISTS seed;

-- Número em [0, 1) derivado só de (stream, k).
CREATE OR REPLACE FUNCTION seed.rand(stream bigint, k bigint) RETURNS float8
    LANGUAGE sql IMMUTABLE PARALLEL SAFE
AS $$ SELECT (hashint8extended(k, stream) & 9007199254740991)::float8 / 9007199254740992.0 $$;

CREATE OR REPLACE FUNCTION seed.pick(items text[], r float8) RETURNS text
    LANGUAGE sql IMMUTABLE PARALLEL SAFE
AS $$ SELECT items[1 + floor(r * cardinality(items))::int] $$;

CREATE OR REPLACE FUNCTION seed.uuid(salt bigint, kind text, k bigint) RETURNS uuid
    LANGUAGE sql IMMUTABLE PARALLEL SAFE
AS $$ SELECT md5(salt || ':' || kind || ':' || k)::uuid $$;

-- Vocabulário por categoria: descrições e contrapartes externas.
CREATE TABLE IF NOT EXISTS seed.vocab (
    category_code  varchar(32) PRIMARY KEY,
    descriptions   text[] NOT NULL,
    counterparties text[] NOT NULL
);

TRUNCATE seed.vocab;
INSERT INTO seed.vocab VALUES
    ('SALARY', '{Salário mensal,Adiantamento salarial,Pagamento de salário,Bônus anual,Participação nos lucros}',
     '{Acme Tecnologia,Banco Horizonte,Construtora Alvorada,Grupo Pampa,Indústria Serra Azul,Hospital Santa Clara,Prefeitura Municipal,Rede Varejo Sul}'),
    ('REFUND', '{Estorno de compra,Reembolso de despesa,Devolução de produto,Cashback}',
     '{Loja Virtual Ponto,Marketplace Brasil,Companhia Aérea Voar,Operadora Conecta}'),
    ('OTHER_INCOME', '{Rendimento de aplicação,Venda de item usado,Prêmio,Pix recebido}',
     '{Corretora Invest,Cliente avulso,Feira de usados,Consórcio Nacional}'),
    ('GROCERIES', '{Compra no supermercado,Feira semanal,Hortifruti,Açougue,Padaria}',
     '{Supermercado Bom Preço,Mercado Central,Hortifruti Verde Vida,Atacadão Econômico,Padaria Pão Quente,Açougue Boi Gordo}'),
    ('RESTAURANTS', '{Almoço,Jantar,Lanche,Delivery de comida,Café}',
     '{Cantina da Nonna,Restaurante Sabor Caseiro,Lanchonete Esquina,Pizzaria Forno a Lenha,Café Grão Fino,Sushi Kaze}'),
    ('TRANSPORT', '{Corrida de aplicativo,Combustível,Estacionamento,Passagem de ônibus,Pedágio}',
     '{Posto Estrela,App Mobilidade,Estacionamento Centro,Viação Cidade,Concessionária Rodovia}'),
    ('UTILITIES', '{Conta de luz,Conta de água,Internet,Telefone celular,Gás}',
     '{Companhia Energética,Saneamento Municipal,Provedor Fibra Net,Operadora Conecta,Gás Lar}'),
    ('HEALTH', '{Consulta médica,Farmácia,Exame laboratorial,Plano de saúde,Dentista}',
     '{Farmácia Saúde Já,Clínica Bem Estar,Laboratório Vida,Plano Saúde Total,Odonto Sorriso}'),
    ('SHOPPING', '{Compra online,Roupas,Eletrônicos,Presente,Utilidades domésticas}',
     '{Marketplace Brasil,Loja Virtual Ponto,Magazine Popular,Moda Urbana,Eletro Mais}'),
    ('ENTERTAINMENT', '{Cinema,Streaming,Show,Ingresso de jogo,Livraria}',
     '{Cine Center,Streaming Play,Ticket Shows,Arena Esportiva,Livraria Cultura Viva}'),
    ('EDUCATION', '{Mensalidade escolar,Curso online,Material escolar,Curso de idiomas}',
     '{Colégio Saber,Plataforma Aprenda,Papelaria Lápis,Escola de Idiomas Global}'),
    ('RENT', '{Aluguel,Condomínio,IPTU}',
     '{Imobiliária Lar Doce,Condomínio Jardim,Prefeitura Municipal}'),
    ('TRANSFER', '{Pix,Divisão de conta,Pagamento de serviço,Presente,Reembolso entre amigos,Aluguel dividido}',
     '{}');

CREATE OR REPLACE PROCEDURE seed.load(scale float8 DEFAULT 1, salt bigint DEFAULT 42)
    LANGUAGE plpgsql
AS $$
DECLARE
    n_accounts   int := greatest(round(100000 * scale)::int, 10);
    n_business   int := greatest(round(n_accounts * 0.01)::int, 1);
    window_start timestamptz := '2024-10-01 00:00:00+00';
    window_end   timestamptz := '2026-10-01 00:00:00+00';
    s            bigint := salt * 100;  -- base dos streams de sorteio
    ix           record;
    started      timestamptz := clock_timestamp();
BEGIN
    PERFORM set_config('work_mem', '256MB', true);
    PERFORM set_config('maintenance_work_mem', '512MB', true);
    RAISE NOTICE 'seed: % contas (% empresariais), salt %', n_accounts, n_business, salt;

    TRUNCATE ledger_entry, idempotency_record, account RESTART IDENTITY;

    -- Contas
    CREATE TEMP TABLE seed_account ON COMMIT DROP AS
    SELECT i AS idx,
           seed.uuid(salt, 'account', i) AS id,
           CASE WHEN i <= n_business THEN 'seed-b-' ELSE 'seed-r-' END || lpad(i::text, 7, '0') AS holder_id,
           CASE WHEN i <= n_business
                THEN seed.pick('{Comércio,Distribuidora,Transportes,Serviços,Indústria,Atacado,Tecnologia,Agro}',
                               seed.rand(s + 10, i))
                     || ' ' || seed.pick(surnames, seed.rand(s + 11, i)) || ' Ltda'
                ELSE seed.pick(first_names, seed.rand(s + 10, i))
                     || ' ' || seed.pick(surnames, seed.rand(s + 11, i))
                     || ' ' || seed.pick(surnames, seed.rand(s + 12, i))
           END AS holder_name
    FROM generate_series(1, n_accounts) i,
         (SELECT '{Ana,Bruno,Carla,Daniel,Eduarda,Felipe,Gabriela,Heitor,Isabela,João,Larissa,Marcos,Natália,Otávio,Paula,Rafael,Sofia,Thiago,Valéria,Vinícius,Beatriz,Caio,Helena,Lucas,Mariana,Pedro,Renata,Samuel,Tatiane,Yuri}'::text[] AS first_names,
                 '{Silva,Santos,Oliveira,Souza,Rodrigues,Ferreira,Alves,Pereira,Lima,Gomes,Costa,Ribeiro,Martins,Carvalho,Almeida,Lopes,Soares,Fernandes,Vieira,Barbosa,Rocha,Dias,Nascimento,Andrade,Moreira,Nunes,Marques,Machado,Mendes,Freitas}'::text[] AS surnames) names;

    INSERT INTO account (id, holder_id, holder_name, currency, balance, overdraft_limit, created_at)
    SELECT id, holder_id, holder_name, 'BRL', 0, 0, window_start FROM seed_account ORDER BY idx;
    RAISE NOTICE 'seed: contas em %', clock_timestamp() - started;

    -- Movimentos: k = (conta << 20) | j identifica cada um e alimenta os sorteios.
    CREATE TEMP TABLE seed_movement ON COMMIT DROP AS
    WITH counts AS (
        SELECT i, CASE WHEN i <= n_business THEN 4350 + floor(seed.rand(s + 1, i) * 8701)
                       ELSE 17 + floor(seed.rand(s + 1, i) * 142) END::int AS n
        FROM generate_series(1, n_accounts) i),
    raw AS (
        SELECT c.i AS account_idx, (c.i::bigint << 20) | j AS k
        FROM counts c CROSS JOIN LATERAL generate_series(1, c.n) j),
    typed AS (
        SELECT account_idx, k,
               CASE WHEN seed.rand(s + 2, k) < 0.30 THEN 'DEPOSIT'
                    WHEN seed.rand(s + 2, k) < 0.85 THEN 'WITHDRAWAL'
                    ELSE 'TRANSFER' END AS kind,
               seed.rand(s + 3, k) AS r_amount,
               seed.rand(s + 4, k) AS r_category,
               (1 + floor(seed.rand(s + 8, k) * n_accounts))::int AS drawn_target,
               window_start + interval '1 second'
                   + seed.rand(s + 7, k) * (window_end - window_start - interval '1 second') AS occurred_at
        FROM raw),
    categorized AS (
        SELECT t.*,
               CASE t.kind
                   WHEN 'DEPOSIT' THEN seed.pick('{SALARY,SALARY,REFUND,OTHER_INCOME}', r_category)
                   WHEN 'WITHDRAWAL' THEN seed.pick('{GROCERIES,GROCERIES,GROCERIES,RESTAURANTS,RESTAURANTS,TRANSPORT,TRANSPORT,UTILITIES,HEALTH,SHOPPING,ENTERTAINMENT,EDUCATION,RENT}', r_category)
                   ELSE 'TRANSFER' END AS category_code
        FROM typed t)
    SELECT c.account_idx, c.k, c.kind, c.category_code, c.occurred_at,
           CASE c.kind
               WHEN 'DEPOSIT' THEN 2000 + floor(c.r_amount ^ 2 * 800000)
               WHEN 'WITHDRAWAL' THEN 500 + floor(c.r_amount ^ 2 * 150000)
               ELSE 1000 + floor(c.r_amount ^ 2 * 300000) END::bigint AS amount,
           seed.pick(v.descriptions, seed.rand(s + 5, c.k)) AS description,
           CASE c.kind
               WHEN 'DEPOSIT' THEN seed.pick(v.counterparties, seed.rand(s + 6, c.k))
               WHEN 'WITHDRAWAL' THEN seed.pick(v.counterparties, seed.rand(s + 6, c.k))
                                      || ' ' || (1 + floor(seed.rand(s + 9, c.k) * 300))::int
           END AS counterparty_name,
           CASE WHEN c.kind <> 'TRANSFER' THEN NULL
                WHEN c.drawn_target = c.account_idx THEN c.account_idx % n_accounts + 1
                ELSE c.drawn_target END AS target_idx
    FROM categorized c JOIN seed.vocab v ON v.category_code = c.category_code;
    RAISE NOTICE 'seed: movimentos em %', clock_timestamp() - started;

    -- Lançamentos: um por movimento externo, um par OUT/IN por transferência.
    CREATE TEMP TABLE seed_entry ON COMMIT DROP AS
    SELECT k, 1::smallint AS leg, account_idx, kind, CASE kind WHEN 'DEPOSIT' THEN amount ELSE -amount END AS amount,
           category_code, description, counterparty_name, NULL::int AS counterparty_idx, occurred_at
    FROM seed_movement WHERE kind <> 'TRANSFER'
    UNION ALL
    SELECT m.k, 1, m.account_idx, 'TRANSFER_OUT', -m.amount, m.category_code, m.description, t.holder_name,
           m.target_idx, m.occurred_at
    FROM seed_movement m JOIN seed_account t ON t.idx = m.target_idx
    UNION ALL
    SELECT m.k, 2, m.target_idx, 'TRANSFER_IN', m.amount, m.category_code, m.description, o.holder_name,
           m.account_idx, m.occurred_at
    FROM seed_movement m JOIN seed_account o ON o.idx = m.account_idx
    WHERE m.kind = 'TRANSFER';

    -- Saldo inicial: cobre o pior momento de cada conta e deixa uma folga sorteada.
    INSERT INTO seed_entry
    SELECT r.account_idx::bigint << 20, 0, r.account_idx, 'DEPOSIT',
           1000 - least(min(r.running), 0) + floor(seed.rand(s + 13, r.account_idx) * 100000)::bigint,
           'OTHER_INCOME', 'Saldo inicial', 'Migração de saldo', NULL, window_start
    FROM (SELECT account_idx,
                 sum(amount) OVER (PARTITION BY account_idx ORDER BY occurred_at, k, leg) AS running
          FROM seed_entry) r
    GROUP BY r.account_idx;
    RAISE NOTICE 'seed: lançamentos preparados em %', clock_timestamp() - started;

    -- Índices secundários saem durante a carga e voltam com a mesma definição da migração.
    CREATE TEMP TABLE seed_index ON COMMIT DROP AS
    SELECT indexrelid::regclass::text AS name, pg_get_indexdef(indexrelid) AS definition
    FROM pg_index WHERE indrelid = 'ledger_entry'::regclass AND NOT indisprimary;
    FOR ix IN SELECT name FROM seed_index LOOP
        EXECUTE 'DROP INDEX ' || ix.name;
    END LOOP;

    -- Ordem global por tempo: ids crescem com o tempo, como em produção, e balance_after segue a mesma ordem.
    INSERT INTO ledger_entry (transaction_id, account_id, kind, amount, balance_after, category_id, description,
                              counterparty_name, counterparty_account_id, occurred_at)
    SELECT seed.uuid(salt, 'transaction', e.k), a.id, e.kind, e.amount,
           sum(e.amount) OVER (PARTITION BY e.account_idx ORDER BY e.occurred_at, e.k, e.leg),
           c.id, e.description, e.counterparty_name, ca.id, e.occurred_at
    FROM seed_entry e
    JOIN seed_account a ON a.idx = e.account_idx
    JOIN category c ON c.code = e.category_code
    LEFT JOIN seed_account ca ON ca.idx = e.counterparty_idx
    ORDER BY e.occurred_at, e.k, e.leg;
    RAISE NOTICE 'seed: ledger_entry carregada em %', clock_timestamp() - started;

    UPDATE account a SET balance = t.total
    FROM (SELECT sa.id, sum(e.amount) AS total
          FROM seed_entry e JOIN seed_account sa ON sa.idx = e.account_idx
          GROUP BY sa.id) t
    WHERE a.id = t.id;

    FOR ix IN SELECT definition FROM seed_index LOOP
        EXECUTE ix.definition;
    END LOOP;
    RAISE NOTICE 'seed: índices recriados em %', clock_timestamp() - started;

    ANALYZE account;
    ANALYZE ledger_entry;
    RAISE NOTICE 'seed: concluído em % (% lançamentos)', clock_timestamp() - started,
        (SELECT count(*) FROM seed_entry);
END;
$$;
