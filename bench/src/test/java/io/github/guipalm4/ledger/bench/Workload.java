package io.github.guipalm4.ledger.bench;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.stream.Stream;

/**
 * Sorteia os parâmetros de cada requisição a partir de uma semente fixa, para o script ser o mesmo em v1, v2 e v3.
 * <p>
 * As contas vêm do CSV exportado do banco pelo {@code make bench} ({@code id,segment}). Metade das requisições vai
 * para contas empresariais e metade para pessoa física (ADR-0008): o segmento entra no nome da requisição para o
 * relatório separar os dois grupos. Os sorteios ficam dentro dos feeders, e não em {@code randomSwitch}, porque o
 * aleatório do Gatling não aceita semente.
 */
final class Workload {

    static final String BUSINESS = "empresa";
    static final String RETAIL = "pessoa";

    /** Janela do dataset de referência (ADR-0008): out/2024 a set/2026. */
    private static final YearMonth FIRST_MONTH = YearMonth.of(2024, 10);
    private static final YearMonth LAST_MONTH = YearMonth.of(2026, 9);

    /** Palavras do vocabulário do seed (bench/seed/seed.sql), das mais comuns às raras. */
    private static final List<String> TERMS = List.of("supermercado", "farmácia", "pix", "aluguel", "almoço",
            "combustível", "internet", "cinema", "estorno", "curso online", "mercado central", "sushi kaze");
    private static final List<String> COUNTERPARTIES = List.of("nonna", "posto", "conecta", "marketplace",
            "saúde", "cultura", "lápis", "boi gordo");
    private static final List<String> CATEGORIES = List.of("GROCERIES", "RESTAURANTS", "TRANSPORT", "UTILITIES",
            "HEALTH", "SHOPPING");

    private final List<String> business;
    private final List<String> retail;
    private final double businessShare;
    private final SplittableRandom random;

    private Workload(List<String> business, List<String> retail, double businessShare, long seed) {
        if (business.isEmpty() || retail.size() < 2) {
            throw new IllegalArgumentException("o CSV precisa de contas dos dois segmentos");
        }
        this.business = business;
        this.retail = retail;
        this.businessShare = businessShare;
        this.random = new SplittableRandom(seed);
    }

    static Workload load(Path accountsCsv, double businessShare, long seed) {
        var business = new ArrayList<String>();
        var retail = new ArrayList<String>();
        try (Stream<String> lines = Files.lines(accountsCsv)) {
            lines.skip(1).map(line -> line.split(",")).forEach(columns -> {
                switch (columns[1].strip()) {
                    case "business" -> business.add(columns[0]);
                    case "retail" -> retail.add(columns[0]);
                    default -> throw new IllegalArgumentException("segmento desconhecido: " + columns[1]);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("não consegui ler " + accountsCsv + " (rode via make bench)", e);
        }
        return new Workload(List.copyOf(business), List.copyOf(retail), businessShare, seed);
    }

    /** Comandos: 30% depósito, 55% saque, 15% transferência — a mesma proporção do seed. */
    Iterator<Map<String, Object>> commands() {
        return Stream.generate(this::command).iterator();
    }

    /** Consultas: Q1–Q4 com o mesmo peso. */
    Iterator<Map<String, Object>> queries() {
        return Stream.generate(this::query).iterator();
    }

    private synchronized Map<String, Object> command() {
        var segment = segment();
        var accountId = account(segment);
        double r = random.nextDouble();
        if (r < 0.30) {
            return Map.of("op", "deposit", "segment", segment, "accountId", accountId,
                    "amount", random.nextLong(1_000, 50_001));
        }
        if (r < 0.85) {
            return Map.of("op", "withdraw", "segment", segment, "accountId", accountId,
                    "amount", random.nextLong(500, 5_001), "category", pick(CATEGORIES));
        }
        // Destino sempre pessoa física, como no seed; nunca a própria conta.
        String targetId;
        do {
            targetId = pick(retail);
        } while (targetId.equals(accountId));
        return Map.of("op", "transfer", "segment", segment, "accountId", accountId, "targetId", targetId,
                "amount", random.nextLong(500, 5_001));
    }

    private synchronized Map<String, Object> query() {
        var segment = segment();
        var accountId = account(segment);
        return switch (random.nextInt(4)) {
            case 0 -> Map.of("op", "Q1", "segment", segment, "accountId", accountId);
            case 1 -> {
                // Trimestre inteiro dentro da janela.
                var from = month(FIRST_MONTH, LAST_MONTH.minusMonths(2));
                yield Map.of("op", "Q2", "segment", segment, "accountId", accountId,
                        "from", instant(from), "to", instant(from.plusMonths(3)));
            }
            case 2 -> Map.of("op", "Q3", "segment", segment, "accountId", accountId,
                    "until", month(FIRST_MONTH.plusMonths(11), LAST_MONTH).toString());
            default -> Map.of("op", "Q4", "segment", segment, "accountId", accountId, "search", search());
        };
    }

    /** Parâmetros do Q4: só texto, só contraparte, ou texto + categoria + período (filtros combinados). */
    private Map<String, Object> search() {
        return switch (random.nextInt(4)) {
            case 0, 1 -> Map.of("q", pick(TERMS));
            case 2 -> Map.of("counterparty", pick(COUNTERPARTIES));
            default -> Map.of("q", pick(TERMS), "category", pick(CATEGORIES),
                    "from", instant(month(FIRST_MONTH, LAST_MONTH.minusMonths(5))));
        };
    }

    private String segment() {
        return random.nextDouble() < businessShare ? BUSINESS : RETAIL;
    }

    private String account(String segment) {
        return pick(segment.equals(BUSINESS) ? business : retail);
    }

    private <T> T pick(List<T> items) {
        return items.get(random.nextInt(items.size()));
    }

    private YearMonth month(YearMonth first, YearMonth last) {
        long span = first.until(last, ChronoUnit.MONTHS);
        return first.plusMonths(random.nextLong(span + 1));
    }

    private static String instant(YearMonth month) {
        return month.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC).toString();
    }
}
