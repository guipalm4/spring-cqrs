package io.github.guipalm4.ledger.baseline;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * O contrato publicado em /v3/api-docs é o mesmo de docs/api/openapi.json, que v2 e v3 também vão conferir
 * (ADR-0005). Mudança intencional: rodar com {@code -Dcontract.update=true} e revisar o diff do arquivo.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(PostgresTestcontainer.class)
class ApiContractTests {

    static final Path CONTRACT = Path.of("..", "docs", "api", "openapi.json");

    @LocalServerPort
    int port;

    @Autowired
    JsonMapper json;

    @Test
    void publishedContractMatchesTheVersionedOne() throws Exception {
        var body = RestClient.create("http://localhost:" + port).get().uri("/v3/api-docs").retrieve()
                .body(String.class);
        var published = (ObjectNode) json.readTree(body);
        published.remove("servers");

        if (Boolean.getBoolean("contract.update")) {
            Files.createDirectories(CONTRACT.getParent());
            Files.writeString(CONTRACT, json.writerWithDefaultPrettyPrinter().writeValueAsString(published) + "\n");
        }
        assertThat(published).as("contrato em /v3/api-docs difere de %s", CONTRACT)
                .isEqualTo(json.readTree(Files.readString(CONTRACT)));
    }
}
