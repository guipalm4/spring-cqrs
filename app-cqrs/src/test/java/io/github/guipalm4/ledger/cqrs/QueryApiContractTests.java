package io.github.guipalm4.ledger.cqrs;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/** A {@code query-api} da v3 publica só as operações GET do contrato congelado. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles(Roles.QUERY)
@Import({PostgresTestcontainer.class, MongoTestcontainer.class})
class QueryApiContractTests {

    @LocalServerPort
    int port;

    @Autowired
    JsonMapper json;

    @Test
    void publishesOnlyItsHalfOfTheContract() throws Exception {
        var body = RestClient.create("http://localhost:" + port).get().uri("/v3/api-docs").retrieve()
                .body(String.class);
        HalfContract.assertPublishesOnly("get", body, json);
    }
}
