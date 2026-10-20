package io.github.guipalm4.ledger.cqrs;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guipalm4.ledger.cqrs.command.CommandResult.AccountOpened;
import io.github.guipalm4.ledger.cqrs.command.CommandResult.TransactionPosted;
import io.github.guipalm4.ledger.cqrs.query.Views.AccountView;
import io.github.guipalm4.ledger.cqrs.query.Views.EntryView;
import io.github.guipalm4.ledger.cqrs.query.Views.MonthlySummary;
import io.github.guipalm4.ledger.cqrs.query.Views.SearchResult;
import io.github.guipalm4.ledger.cqrs.query.Views.StatementPage;
import io.github.guipalm4.ledger.domain.EntryKind;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/** Contrato HTTP de ponta a ponta: status, headers, problem+json e as consultas Q1–Q4. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(PostgresTestcontainer.class)
class LedgerApiTests {

    @LocalServerPort
    int port;

    RestClient http;

    @BeforeEach
    void setUp() {
        http = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(HttpStatusCode::isError, (_, _) -> {
                })
                .build();
    }

    @Test
    void openDepositAndReadBalance() {
        var opened = post("/accounts", UUID.randomUUID().toString(), openBody(), AccountOpened.class);
        assertThat(opened.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var accountId = opened.getBody().accountId();
        assertThat(opened.getHeaders().getLocation()).hasToString("/accounts/" + accountId);

        var posted = post("/accounts/" + accountId + "/deposits", UUID.randomUUID().toString(),
                movement(2_500, "SALARY", "Salário de outubro", "Empresa X"), TransactionPosted.class);
        assertThat(posted.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(posted.getBody().postings()).singleElement()
                .satisfies(p -> assertThat(p.balanceAfter()).isEqualTo(2_500));

        assertThat(get("/accounts/" + accountId, AccountView.class).getBody().balance()).isEqualTo(2_500);
    }

    @Test
    void resendingWithSameKeyReplaysOriginalResponse() {
        var accountId = openAccount();
        var key = UUID.randomUUID().toString();
        var body = movement(100, "SALARY", "s", "Empresa X");

        var first = post("/accounts/" + accountId + "/deposits", key, body, TransactionPosted.class);
        var second = post("/accounts/" + accountId + "/deposits", key, body, TransactionPosted.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");
        assertThat(first.getHeaders().getFirst("Idempotent-Replayed")).isNull();
        assertThat(second.getBody()).isEqualTo(first.getBody());
        assertThat(get("/accounts/" + accountId, AccountView.class).getBody().balance()).isEqualTo(100);
    }

    @Test
    void rejectionsAreProblemDetails() {
        var accountId = openAccount();

        var response = post("/accounts/" + accountId + "/withdrawals", UUID.randomUUID().toString(),
                movement(1, "GROCERIES", "compra", "Mercado"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody()).containsEntry("violation", "INSUFFICIENT_FUNDS")
                .containsEntry("type", "urn:ledger:insufficient-funds");
    }

    @Test
    void malformedRequestsAreBadRequests() {
        var accountId = openAccount();
        var path = "/accounts/" + accountId + "/deposits";
        var valid = movement(100, "SALARY", "s", "Empresa X");

        var noKey = http.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(valid).retrieve()
                .toBodilessEntity();
        var zero = post(path, UUID.randomUUID().toString(), movement(0, "SALARY", "s", "E"), Map.class);
        var missingField = post(path, UUID.randomUUID().toString(), Map.of("amount", 100, "currency", "BRL"), Map.class);
        var badCurrency = post(path, UUID.randomUUID().toString(),
                Map.of("amount", 1, "currency", "XYZ", "category", "SALARY", "description", "s", "counterparty", "E"),
                Map.class);

        assertThat(noKey.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(zero.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(missingField.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(badCurrency.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unknownAccountIsNotFoundInEveryQuery() {
        var missing = "/accounts/" + UUID.randomUUID();
        var now = Instant.now();

        assertThat(get(missing, Map.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get(missing + "/statement?from=" + now.minusSeconds(60) + "&to=" + now, Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get(missing + "/monthly-summary", Map.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get(missing + "/entries/search?q=x", Map.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void transferPostsBothLegsWithCounterparties() {
        var source = openAccount("Ana Souza");
        var target = openAccount("Bruno Lima");
        deposit(source, 1_000, "SALARY", "salário", "Empresa X");

        var posted = post("/transfers", UUID.randomUUID().toString(), Map.of("sourceAccountId", source,
                "targetAccountId", target, "amount", 300, "currency", "BRL", "description", "Aluguel dividido"),
                TransactionPosted.class);

        assertThat(posted.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(posted.getBody().postings()).extracting(p -> p.amount()).containsExactly(-300L, 300L);
        var sourceEntry = statement(source, null).items().getFirst();
        assertThat(sourceEntry.kind()).isEqualTo(EntryKind.TRANSFER_OUT);
        assertThat(sourceEntry.counterparty().name()).isEqualTo("Bruno Lima");
        assertThat(sourceEntry.counterparty().accountId()).isEqualTo(target);
        assertThat(sourceEntry.category().code()).isEqualTo("TRANSFER");
    }

    @Test
    void statementPagesWithoutGapsOrDuplicates() {
        var accountId = openAccount();
        for (int i = 1; i <= 5; i++) {
            deposit(accountId, i, "SALARY", "depósito " + i, "Empresa X");
        }

        var seen = new ArrayList<EntryView>();
        String cursor = null;
        int pages = 0;
        do {
            var page = statement(accountId, cursor);
            seen.addAll(page.items());
            cursor = page.nextCursor();
            pages++;
        } while (cursor != null);

        assertThat(pages).isEqualTo(3);
        assertThat(seen).extracting(EntryView::amount).containsExactly(5L, 4L, 3L, 2L, 1L);
    }

    @Test
    void monthlySummaryGroupsByMonthAndCategory() {
        var accountId = openAccount();
        deposit(accountId, 1_000, "SALARY", "salário", "Empresa X");
        withdraw(accountId, 200, "GROCERIES", "feira", "Mercado Central");
        withdraw(accountId, 50, "GROCERIES", "pão", "Padaria");

        var month = YearMonth.now(ZoneOffset.UTC);
        var summary = get("/accounts/" + accountId + "/monthly-summary?until=" + month, MonthlySummary.class).getBody();

        assertThat(summary.from()).isEqualTo(month.minusMonths(11));
        assertThat(summary.items()).extracting(t -> t.category().code(), t -> t.credits(), t -> t.debits(),
                t -> t.entries())
                .containsExactly(org.assertj.core.groups.Tuple.tuple("GROCERIES", 0L, 250L, 2L),
                        org.assertj.core.groups.Tuple.tuple("SALARY", 1_000L, 0L, 1L));
    }

    @Test
    void searchCombinesTextCounterpartyAndCategory() {
        var accountId = openAccount();
        deposit(accountId, 10_000, "SALARY", "salário", "Empresa X");
        withdraw(accountId, 120, "GROCERIES", "Feira de domingo", "Hortifruti Bom Preço");
        withdraw(accountId, 80, "RESTAURANTS", "Almoço de domingo", "Cantina 100%");
        withdraw(accountId, 900, "GROCERIES", "Compra do mês", "Supermercado Bom Preço");

        assertThat(search(accountId, "q=domingo")).extracting(EntryView::amount).containsExactly(-80L, -120L);
        assertThat(search(accountId, "q=domingo&category=GROCERIES")).extracting(EntryView::amount)
                .containsExactly(-120L);
        assertThat(search(accountId, "counterparty=bom pre")).hasSize(2);
        assertThat(search(accountId, "counterparty=100%")).extracting(EntryView::amount).containsExactly(-80L);
        assertThat(search(accountId, "minAmount=100&maxAmount=1000")).extracting(EntryView::amount)
                .containsExactly(-900L, -120L);
    }

    private java.util.List<EntryView> search(UUID accountId, String query) {
        return http.get().uri("/accounts/" + accountId + "/entries/search?" + query).retrieve()
                .body(SearchResult.class).items();
    }

    private StatementPage statement(UUID accountId, String cursor) {
        var now = Instant.now().plus(1, ChronoUnit.MINUTES);
        return http.get().uri(b -> {
            b.path("/accounts/{id}/statement").queryParam("from", now.minus(1, ChronoUnit.DAYS))
                    .queryParam("to", now).queryParam("limit", 2);
            if (cursor != null) {
                b.queryParam("cursor", cursor);
            }
            return b.build(accountId);
        }).retrieve().body(StatementPage.class);
    }

    private UUID openAccount() {
        return openAccount("Titular");
    }

    private UUID openAccount(String name) {
        return post("/accounts", UUID.randomUUID().toString(),
                Map.of("holderId", UUID.randomUUID().toString(), "holderName", name, "currency", "BRL"),
                AccountOpened.class).getBody().accountId();
    }

    private void deposit(UUID accountId, long amount, String category, String description, String counterparty) {
        assertThat(post("/accounts/" + accountId + "/deposits", UUID.randomUUID().toString(),
                movement(amount, category, description, counterparty), TransactionPosted.class).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
    }

    private void withdraw(UUID accountId, long amount, String category, String description, String counterparty) {
        assertThat(post("/accounts/" + accountId + "/withdrawals", UUID.randomUUID().toString(),
                movement(amount, category, description, counterparty), TransactionPosted.class).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
    }

    private static Map<String, Object> openBody() {
        return Map.of("holderId", UUID.randomUUID().toString(), "holderName", "Titular", "currency", "BRL",
                "overdraftLimit", 0);
    }

    private static Map<String, Object> movement(long amount, String category, String description, String counterparty) {
        return Map.of("amount", amount, "currency", "BRL", "category", category, "description", description,
                "counterparty", counterparty);
    }

    private <T> ResponseEntity<T> post(String path, String key, Object body, Class<T> type) {
        return http.post().uri(path).header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve().toEntity(type);
    }

    private <T> ResponseEntity<T> get(String path, Class<T> type) {
        return http.get().uri(path).retrieve().toEntity(type);
    }
}
