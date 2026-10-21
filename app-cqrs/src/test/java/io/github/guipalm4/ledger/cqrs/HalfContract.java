package io.github.guipalm4.ledger.cqrs;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Na v3, a {@code command-api} e a {@code query-api} publicam cada uma a sua metade do contrato congelado
 * (ADR-0002): só as operações do método HTTP do papel, com os schemas exatamente como estão no contrato.
 */
final class HalfContract {

    private HalfContract() {
    }

    static void assertPublishesOnly(String method, String publishedJson, JsonMapper json) throws Exception {
        var published = (ObjectNode) json.readTree(publishedJson);
        var contract = json.readTree(Files.readString(ApiContractTests.CONTRACT));

        var expectedPaths = json.createObjectNode();
        for (Map.Entry<String, JsonNode> path : contract.get("paths").properties()) {
            if (path.getValue().has(method)) {
                expectedPaths.putObject(path.getKey()).set(method, path.getValue().get(method));
            }
        }
        assertThat(published.get("paths")).as("operações %s", method).isEqualTo(expectedPaths);
        for (Map.Entry<String, JsonNode> schema : published.get("components").get("schemas").properties()) {
            assertThat(schema.getValue()).as("schema %s", schema.getKey())
                    .isEqualTo(contract.get("components").get("schemas").get(schema.getKey()));
        }
    }
}
