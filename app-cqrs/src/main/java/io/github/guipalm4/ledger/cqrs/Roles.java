package io.github.guipalm4.ledger.cqrs;

/**
 * Papéis da {@code app-cqrs} por profile do Spring (ADR-0002, ADR-0012). Sem nenhum deles, é a v2: comandos,
 * consultas e projeções no mesmo processo e no mesmo Postgres. Na v3, o mesmo artefato sobe em três containers,
 * cada um com um papel.
 */
public final class Roles {

    /** {@code command-api}: comandos no Postgres, eventos para o Kafka pelo outbox. */
    public static final String COMMAND = "command";

    /** {@code query-api}: Q1 no Postgres, Q2–Q4 no Mongo. */
    public static final String QUERY = "query";

    /** {@code projector}: consome o Kafka e atualiza o Mongo. */
    public static final String PROJECTOR = "projector";

    /** A v2: nenhum papel da v3 ativo. */
    public static final String V2 = "!" + COMMAND + " & !" + QUERY + " & !" + PROJECTOR;

    /** Onde os comandos rodam: na v2 e na {@code command-api}. */
    public static final String COMMANDS = "!" + QUERY + " & !" + PROJECTOR;

    private Roles() {
    }
}
