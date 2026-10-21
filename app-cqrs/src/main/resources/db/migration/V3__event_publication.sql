-- Registro de publicações do Spring Modulith (outbox da v3, ADR-0004 e ADR-0012): o evento é gravado aqui na
-- transação do comando e apagado depois de enviado ao Kafka (completion mode DELETE), então a tabela só guarda o
-- que ainda não saiu. Esquema v2 do spring-modulith-events-jdbc 2.1.1 (schemas/v2/schema-postgresql.sql), aplicado
-- pelo Flyway em vez da inicialização do Modulith. Na v2 a tabela existe e fica vazia: sem externalização, nenhum
-- listener transacional registra publicações.
CREATE TABLE event_publication (
    id                     uuid                     NOT NULL,
    listener_id            text                     NOT NULL,
    event_type             text                     NOT NULL,
    serialized_event       text                     NOT NULL,
    publication_date       timestamp with time zone NOT NULL,
    completion_date        timestamp with time zone,
    status                 text,
    completion_attempts    int,
    last_resubmission_date timestamp with time zone,
    PRIMARY KEY (id)
);
CREATE INDEX event_publication_serialized_event_hash_idx ON event_publication USING hash (serialized_event);
CREATE INDEX event_publication_by_completion_date_idx ON event_publication (completion_date);
