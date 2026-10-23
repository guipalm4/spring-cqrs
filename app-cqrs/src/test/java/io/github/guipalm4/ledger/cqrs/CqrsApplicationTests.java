package io.github.guipalm4.ledger.cqrs;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(PostgresTestcontainer.class)
class CqrsApplicationTests {

    @LocalServerPort
    int port;

    @Test
    void healthIsUpWithDatabase() {
        var body = client().get().uri("/actuator/health").retrieve().body(String.class);

        // Sem Mongo na v2: ele só existe na query-api e no projetor da v3.
        assertThat(body).contains("\"status\":\"UP\"").contains("\"db\"").doesNotContain("mongo");
    }

    @Test
    void exposesPrometheusMetricsTaggedWithPhase() {
        var body = client().get().uri("/actuator/prometheus").retrieve().body(String.class);

        assertThat(body).contains("jvm_memory_used_bytes").contains("phase=\"v2\"");
    }

    private RestClient client() {
        return RestClient.create("http://localhost:" + port);
    }
}
