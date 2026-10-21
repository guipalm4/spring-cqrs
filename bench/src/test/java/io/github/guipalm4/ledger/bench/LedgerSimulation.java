package io.github.guipalm4.ledger.bench;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.doSwitch;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.feed;
import static io.gatling.javaapi.core.CoreDsl.group;
import static io.gatling.javaapi.core.CoreDsl.onCase;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.PopulationBuilder;
import io.gatling.javaapi.core.Session;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Carga do benchmark (ADR-0005), em modelo aberto: a taxa de chegada não depende da latência do servidor, para a
 * fila aparecer na medição em vez de ser escondida por ela (coordinated omission).
 * <p>
 * Os cenários S1–S3 são esta mesma simulação com taxas diferentes:
 * <ul>
 *   <li>S1 — só escrita: {@code readRate=0};</li>
 *   <li>S2 — só leitura: {@code writeRate=0};</li>
 *   <li>S3 — mista: escrita constante e leitura variando entre execuções.</li>
 * </ul>
 * Os grupos {@code comandos} e {@code consultas} dão os percentis agregados de cada lado (H1: p95/p99 dos comandos
 * com e sem carga de leitura). Parâmetros por propriedade de sistema: {@code commandsUrl}, {@code queriesUrls},
 * {@code accounts}, {@code writeRate}, {@code readRate} (requisições/s), {@code duration} (s), {@code businessShare} e
 * {@code seed}.
 * <p>
 * Na v1 e na v2, as duas URLs são o mesmo app. Na v3, comandos vão para a {@code command-api} e consultas para a
 * {@code query-api}, direto, sem proxy (ADR-0005, ADR-0012); com duas réplicas da {@code query-api} (S5),
 * {@code queriesUrls} tem as duas, separadas por vírgula, e cada requisição vai para uma delas, em rodízio.
 */
public class LedgerSimulation extends Simulation {

    private static final String JSON = "application/json";

    private final Workload workload = Workload.load(Path.of(property("accounts", "target/accounts.csv")),
            Double.parseDouble(property("businessShare", "0.5")), Long.parseLong(property("seed", "42")));

    private final String commandsUrl = property("commandsUrl", "http://localhost:8080");

    private final HttpProtocolBuilder commandsProtocol = protocol(List.of(commandsUrl));

    private final HttpProtocolBuilder queriesProtocol = protocol(
            List.of(property("queriesUrls", commandsUrl).split(",")));

    private final ChainBuilder commands = exec(feed(workload.commands()), group("comandos").on(doSwitch("#{op}").on(
            onCase("deposit").then(exec(http(named("deposit"))
                    .post("/accounts/#{accountId}/deposits")
                    .header("Idempotency-Key", LedgerSimulation::idempotencyKey)
                    .body(StringBody("""
                            {"amount": #{amount}, "currency": "BRL", "category": "OTHER_INCOME",
                             "description": "Depósito do benchmark", "counterparty": "Gatling"}"""))
                    .check(status().is(201)))),
            onCase("withdraw").then(exec(http(named("withdraw"))
                    .post("/accounts/#{accountId}/withdrawals")
                    .header("Idempotency-Key", LedgerSimulation::idempotencyKey)
                    .body(StringBody("""
                            {"amount": #{amount}, "currency": "BRL", "category": "#{category}",
                             "description": "Saque do benchmark", "counterparty": "Gatling"}"""))
                    // 422 é recusa de negócio (saldo insuficiente): o comando rodou inteiro, inclusive o lock.
                    .check(status().in(201, 422)))),
            onCase("transfer").then(exec(http(named("transfer"))
                    .post("/transfers")
                    .header("Idempotency-Key", LedgerSimulation::idempotencyKey)
                    .body(StringBody("""
                            {"sourceAccountId": "#{accountId}", "targetAccountId": "#{targetId}",
                             "amount": #{amount}, "currency": "BRL", "description": "Transferência do benchmark"}"""))
                    .check(status().in(201, 422)))))));

    private final ChainBuilder queries = exec(feed(workload.queries()), group("consultas").on(doSwitch("#{op}").on(
            onCase("Q1").then(exec(http(named("Q1"))
                    .get("/accounts/#{accountId}")
                    .check(status().is(200)))),
            onCase("Q2").then(exec(http(named("Q2"))
                    .get("/accounts/#{accountId}/statement")
                    .queryParam("from", "#{from}")
                    .queryParam("to", "#{to}")
                    .queryParam("limit", 50)
                    .check(status().is(200)))),
            onCase("Q3").then(exec(http(named("Q3"))
                    .get("/accounts/#{accountId}/monthly-summary")
                    .queryParam("until", "#{until}")
                    .check(status().is(200)))),
            onCase("Q4").then(exec(http(named("Q4"))
                    .get("/accounts/#{accountId}/entries/search")
                    .queryParamMap("#{search}")
                    .queryParam("limit", 50)
                    .check(status().is(200)))))));

    {
        var duration = Duration.ofSeconds(Long.parseLong(property("duration", "180")));
        double writeRate = Double.parseDouble(property("writeRate", "20"));
        double readRate = Double.parseDouble(property("readRate", "0"));

        var populations = new ArrayList<PopulationBuilder>();
        if (writeRate > 0) {
            populations.add(scenario("comandos").exec(commands)
                    .injectOpen(constantUsersPerSec(writeRate).during(duration))
                    .protocols(commandsProtocol));
        }
        if (readRate > 0) {
            populations.add(scenario("consultas").exec(queries)
                    .injectOpen(constantUsersPerSec(readRate).during(duration))
                    .protocols(queriesProtocol));
        }
        if (populations.isEmpty()) {
            throw new IllegalArgumentException("writeRate e readRate não podem ser ambos zero");
        }
        setUp(populations);
    }

    private static HttpProtocolBuilder protocol(List<String> baseUrls) {
        return http
                .baseUrls(baseUrls)
                .acceptHeader(JSON)
                .contentTypeHeader(JSON)
                // Um pool de conexões compartilhado, como um cliente real; sem isso cada usuário abriria a sua.
                .shareConnections();
    }

    /** Nome no relatório: operação + segmento da conta, para separar os grupos (ADR-0008). */
    private static Function<Session, String> named(String operation) {
        return session -> operation + " " + session.getString("segment");
    }

    private static String idempotencyKey(Session session) {
        return UUID.randomUUID().toString();
    }

    private static String property(String name, String defaultValue) {
        return System.getProperty(name, defaultValue);
    }
}
