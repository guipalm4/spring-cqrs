package io.github.guipalm4.ledger.baseline;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import io.github.guipalm4.ledger.baseline.command.CommandResult.AccountOpened;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

/** Controle da #11: comandos e consultas em pools separados, com o mesmo total de conexões da baseline. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles(SplitPoolsConfiguration.PROFILE)
@Import(PostgresTestcontainer.class)
class SplitPoolsTests {

    @LocalServerPort
    int port;

    @Autowired
    DataSource commands;

    @Autowired
    @Qualifier("queries")
    DataSource queries;

    @Autowired
    MeterRegistry registry;

    @Test
    void keepsTheBaselineTotalOfConnections() {
        var commandPool = (HikariDataSource) commands;
        var queryPool = (HikariDataSource) queries;

        assertThat(commandPool.getPoolName()).isEqualTo("commands");
        assertThat(queryPool.getPoolName()).isEqualTo("queries");
        assertThat(commandPool.getMaximumPoolSize() + queryPool.getMaximumPoolSize())
                .as("mesmo total de conexões da baseline (ADR-0009)").isEqualTo(10);
    }

    @Test
    void commandsAndQueriesBorrowFromTheirOwnPool() {
        var http = RestClient.create("http://localhost:" + port);

        double commandsBefore = borrowed("commands");
        double queriesBefore = borrowed("queries");
        var opened = http.post().uri("/accounts").header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("holderId", UUID.randomUUID().toString(), "holderName", "Titular", "currency", "BRL",
                        "overdraftLimit", 0))
                .retrieve().toEntity(AccountOpened.class);
        assertThat(opened.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(borrowed("commands")).isGreaterThan(commandsBefore);
        assertThat(borrowed("queries")).isEqualTo(queriesBefore);

        commandsBefore = borrowed("commands");
        var balance = http.get().uri("/accounts/{id}", opened.getBody().accountId()).retrieve().toBodilessEntity();
        assertThat(balance.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(borrowed("queries")).isGreaterThan(queriesBefore);
        assertThat(borrowed("commands")).isEqualTo(commandsBefore);
    }

    /** Conexões devolvidas ao pool até agora; o Hikari só registra o timer quando o pool parte, no primeiro uso. */
    private double borrowed(String pool) {
        Timer usage = registry.find("hikaricp.connections.usage").tag("pool", pool).timer();
        return usage == null ? 0 : usage.count();
    }
}
