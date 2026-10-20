// Relatório do benchmark: compact source file (JEP 512), roda sem build com `java bench/Report.java`.
//
//   java bench/Report.java extract <relatório-gatling> <stats.csv>   tabela do index.html -> CSV
//   java bench/Report.java summarize <docs/benchmarks/...>           runs/*/r*/stats.csv -> summary.md
//   java bench/Report.java chart <timeseries.csv> <coluna> <título> [divisor]   série -> gráfico Mermaid (stdout)
//
// O Gatling 3.16 não grava mais stats.json: as estatísticas só existem na tabela do index.html. Os percentis
// incluem requisições OK e KO, e o resumo mostra o % de KO ao lado para não esconder falhas.
//
// Cada execução é classificada como estável ou colapso pelo p95 global (ADR-0009). O critério fica aqui, igual
// para todas as fases, para a contagem "colapsou em k de n" não ser decidida depois de ver o resultado.

static final List<String> COLUMNS = List.of("total", "ok", "ko", "pct_ko", "rps", "min", "p50", "p75", "p95",
        "p99", "max", "mean", "stddev");
static final Pattern ROW = Pattern.compile(
        "<tr id=\"([^\"]+)\"(?: data-parent=\"([^\"]+)\")?\\s*>(.*?)</tr>", Pattern.DOTALL);
static final Pattern NAME = Pattern.compile("class=\"ellipsed-name\">([^<]*)</span>");
static final Pattern VALUE = Pattern.compile("<td class=\"value \\w+ col-(\\d+)\">([^<]*)</td>");
static final double COLLAPSE_P95_MS = 5_000;
/** Instruções que um comando executa: escritas e o lock da conta. */
static final Pattern WRITE = Pattern.compile("^\"?(INSERT|UPDATE|DELETE)\\b|FOR UPDATE", Pattern.CASE_INSENSITIVE);

void main(String[] args) throws IOException {
    switch (args.length == 0 ? "" : args[0]) {
        case "extract" -> extract(Path.of(args[1], "index.html"), Path.of(args[2]));
        case "summarize" -> summarize(Path.of(args[1]));
        case "chart" -> System.out.print(chart(Path.of(args[1]), args[2], args[3],
                args.length > 4 ? Double.parseDouble(args[4]) : 1));
        default -> throw new IllegalArgumentException(
                "uso: extract <relatório> <csv> | summarize <resultados> | chart <timeseries.csv> <coluna> <título> [divisor]");
    }
}

/** Uma linha por requisição ou grupo; o nome de uma requisição dentro de grupo vira "grupo / requisição". */
void extract(Path html, Path csv) throws IOException {
    var names = new HashMap<String, String>();
    var lines = new ArrayList<String>();
    lines.add("name," + String.join(",", COLUMNS));
    var matcher = ROW.matcher(Files.readString(html));
    while (matcher.find()) {
        var id = matcher.group(1);
        var parent = Objects.requireNonNullElse(matcher.group(2), "ROOT");
        var nameMatcher = NAME.matcher(matcher.group(3));
        if (!nameMatcher.find()) {
            continue;
        }
        var name = nameMatcher.group(1).strip();
        if (!parent.equals("ROOT") && names.containsKey(parent)) {
            name = names.get(parent) + " / " + name;
        }
        names.put(id, name);
        var values = new String[COLUMNS.size()];
        var valueMatcher = VALUE.matcher(matcher.group(3));
        while (valueMatcher.find()) {
            values[Integer.parseInt(valueMatcher.group(1)) - 2] = valueMatcher.group(2).strip().replace(",", "");
        }
        lines.add((id.equals("ROOT") ? "Global" : name) + "," + String.join(",", values));
    }
    Files.createDirectories(csv.toAbsolutePath().getParent());
    Files.write(csv, lines);
}

record Stats(Map<String, Double> values) {
    double get(String column) {
        return values.getOrDefault(column, Double.NaN);
    }
}

/** Mediana das repetições por cenário e requisição, com a faixa (mín–máx) do p95. */
void summarize(Path results) throws IOException {
    var runs = results.resolve("runs");
    var out = new StringBuilder("""
            # Resumo

            Gerado por `java bench/Report.java summarize`. Mediana das repetições; entre colchetes, a faixa do p95
            (mín–máx). Latências em ms, vazão em requisições/s. Parâmetros de cada cenário em
            `runs/<cenário>/scenario.properties`; série temporal de cada execução (5 s) em
            `runs/<cenário>/r<n>/timeseries.csv`.
            """);
    var resources = new LinkedHashMap<String, Map<String, Double>>();
    try (var scenarios = Files.list(runs)) {
        for (var scenario : scenarios.filter(Files::isDirectory).sorted().toList()) {
            var reps = loadReps(scenario);
            if (reps.isEmpty()) {
                continue;
            }
            resources.put(scenario.getFileName().toString(), loadMetrics(subdirectories(scenario)));
            out.append("\n## ").append(scenario.getFileName()).append(" (").append(reps.size())
                    .append(" execuções)\n\n").append(parameters(scenario))
                    .append("| Requisição | Vazão | % KO | p50 | p95 | p99 |\n|---|---:|---:|---:|---:|---:|\n");
            var names = new LinkedHashSet<String>();
            reps.forEach(rep -> names.addAll(rep.keySet()));
            for (var name : names.stream().sorted(Comparator.comparing(this::order)).toList()) {
                var series = reps.stream().map(rep -> rep.get(name)).filter(Objects::nonNull).toList();
                double[] p95 = series.stream().mapToDouble(s -> s.get("p95")).sorted().toArray();
                out.append("| ").append(name)
                        .append(" | ").append(fmt(median(series, "rps"), 1))
                        .append(" | ").append(fmt(median(series, "pct_ko"), 1))
                        .append(" | ").append(fmt(median(series, "p50"), 0))
                        .append(" | ").append(fmt(median(series, "p95"), 0))
                        .append(" [").append(fmt(p95[0], 0)).append("–").append(fmt(p95[p95.length - 1], 0))
                        .append("] | ").append(fmt(median(series, "p99"), 0)).append(" |\n");
            }
        }
    }
    states(runs, out);
    out.append("""

            ## Pool e banco por cenário

            Mediana das repetições. Espera = tempo médio para obter conexão do Hikari; uso = tempo médio com a
            conexão (o trabalho no banco); hit ratio = blocos achados no shared_buffers.

            | Cenário | Espera no pool (ms) | Uso da conexão (ms) | Fila máx. no pool | Hit ratio | CPU da app |
            |---|---:|---:|---:|---:|---:|
            """);
    resources.forEach((scenario, m) -> {
        double hit = m.getOrDefault("pg_blks_hit", Double.NaN);
        double read = m.getOrDefault("pg_blks_read", Double.NaN);
        out.append("| ").append(scenario)
                .append(" | ").append(fmt(m.getOrDefault("hikari_acquire_avg_s", Double.NaN) * 1000, 1))
                .append(" | ").append(fmt(m.getOrDefault("hikari_usage_avg_s", Double.NaN) * 1000, 1))
                .append(" | ").append(fmt(m.getOrDefault("hikari_pending_max", Double.NaN), 0))
                .append(" | ").append(percent(hit / (hit + read), 1))
                .append(" | ").append(percent(m.getOrDefault("app_cpu_avg", Double.NaN), 0)).append(" |\n");
    });
    Files.writeString(results.resolve("summary.md"), out);
}

/**
 * Estado de cada execução e quantas colapsaram por cenário. Com a carga perto da saturação, as repetições caem em
 * estados diferentes e a mediana não descreve nenhum deles; a contagem e a tabela por execução, sim.
 */
void states(Path runs, StringBuilder out) throws IOException {
    var counts = new StringBuilder();
    var table = new StringBuilder();
    var labels = new ArrayList<String>();
    var commandP95 = new ArrayList<Double>();
    for (var scenario : subdirectories(runs)) {
        var reps = subdirectories(scenario).stream().filter(dir -> Files.exists(dir.resolve("stats.csv"))).toList();
        var collapsed = new ArrayList<String>();
        for (var rep : reps) {
            var stats = loadStats(rep.resolve("stats.csv"));
            var metrics = loadMetrics(List.of(rep));
            var global = stats.get("Global");
            var commands = stats.getOrDefault("comandos", new Stats(Map.of()));
            boolean collapse = global.get("p95") > COLLAPSE_P95_MS;
            if (collapse) {
                collapsed.add(rep.getFileName().toString());
            }
            if (!Double.isNaN(commands.get("p95"))) {
                labels.add(scenario.getFileName() + " " + rep.getFileName());
                commandP95.add(commands.get("p95") / 1000);
            }
            table.append("| ").append(scenario.getFileName()).append(" | ").append(rep.getFileName())
                    .append(" | ").append(collapse ? "**colapso**" : "estável")
                    .append(" | ").append(fmt(global.get("p95"), 0))
                    .append(" | ").append(fmt(global.get("pct_ko"), 1))
                    .append(" | ").append(fmt(commands.get("p50"), 0))
                    .append(" | ").append(fmt(commands.get("p95"), 0))
                    .append(" | ").append(fmt(commands.get("p99"), 0))
                    .append(" | ").append(fmt(metrics.getOrDefault("hikari_acquire_avg_s", Double.NaN) * 1000, 1))
                    .append(" | ").append(fmt(metrics.getOrDefault("hikari_usage_avg_s", Double.NaN) * 1000, 1))
                    .append(" | ").append(fmt(commandDbTime(rep.resolve("pg_stat_statements.csv")), 1))
                    .append(" |\n");
        }
        counts.append("| ").append(scenario.getFileName()).append(" | ").append(collapsed.size()).append(" de ")
                .append(reps.size()).append(collapsed.isEmpty() ? "" : " (" + String.join(", ", collapsed) + ")")
                .append(" |\n");
    }
    out.append("""

            ## Estado por execução

            Colapso: p95 global acima de %s ms (ADR-0009). Leia a contagem, não a mediana: perto da saturação as
            repetições caem em estados diferentes.

            | Cenário | Colapsou |
            |---|---|
            """.formatted(fmt(COLLAPSE_P95_MS, 0))).append(counts).append("""

            p95 global e comandos em ms, no cliente (Gatling). Espera e uso da conexão do Hikari em ms, média da
            execução. Comando no banco: soma do tempo médio das instruções de escrita e do `SELECT … FOR UPDATE` no
            `pg_stat_statements`, em ms (o custo de um comando dentro do Postgres, sem a fila).

            | Cenário | Rep. | Estado | p95 global | % KO | Comandos p50 | p95 | p99 | Espera no pool | Uso da conexão | Comando no banco |
            |---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
            """).append(table);
    if (!labels.isEmpty()) {
        out.append("\n").append(mermaid("bar", "p95 dos comandos por execução", labels, "segundos", commandP95));
    }
}

/** Uma coluna do timeseries.csv como gráfico de linha; recusa série com lacunas, que o Mermaid desenharia contínua. */
String chart(Path csv, String column, String title, double divisor) throws IOException {
    var lines = Files.readAllLines(csv);
    int index = List.of(lines.getFirst().split(",")).indexOf(column);
    if (index < 0) {
        throw new IllegalArgumentException("coluna inexistente: " + column);
    }
    var values = new ArrayList<Double>();
    for (var line : lines.subList(1, lines.size())) {
        var cells = line.split(",", -1);
        if (cells[index].isBlank()) {
            throw new IllegalArgumentException("lacuna em t=" + cells[0] + "; escolha outra execução");
        }
        values.add(Double.parseDouble(cells[index]) / divisor);
    }
    var last = lines.getLast().split(",")[0];
    return """
            ```mermaid
            xychart-beta
                title "%s"
                x-axis "segundos desde o início" 0 --> %s
                line [%s]
            ```
            """.formatted(title, last, join(values));
}

static String mermaid(String type, String title, List<String> labels, String unit, List<Double> values) {
    return """
            ```mermaid
            xychart-beta
                title "%s"
                x-axis [%s]
                y-axis "%s"
                %s [%s]
            ```
            """.formatted(title, labels.stream().map(l -> "\"" + l + "\"").collect(Collectors.joining(", ")), unit, type,
            join(values));
}

static String join(List<Double> values) {
    return values.stream().map(v -> String.format(Locale.ROOT, "%.2f", v)).collect(Collectors.joining(", "));
}

/** Soma do tempo médio das instruções que um comando executa; sem escritas (só leitura), não se aplica. */
double commandDbTime(Path csv) throws IOException {
    if (!Files.exists(csv)) {
        return Double.NaN;
    }
    double total = 0;
    boolean found = false;
    for (var line : Files.readAllLines(csv).stream().skip(1).toList()) {
        // calls,total_ms,mean_ms,stddev_ms,rows,shared_blks_hit,shared_blks_read,read_ms,query: só a consulta tem vírgulas.
        var cells = line.split(",", 9);
        if (WRITE.matcher(cells[8]).find()) {
            total += Double.parseDouble(cells[2]);
            found = true;
        }
    }
    return found ? total : Double.NaN;
}

/** Mediana, entre as repetições, de cada métrica do prometheus.csv. */
Map<String, Double> loadMetrics(List<Path> reps) throws IOException {
    var series = new HashMap<String, List<Double>>();
    for (var csv : reps.stream().map(dir -> dir.resolve("prometheus.csv")).filter(Files::exists).toList()) {
        for (var line : Files.readAllLines(csv).stream().skip(1).toList()) {
            var cells = line.split(",");
            series.computeIfAbsent(cells[0], _ -> new ArrayList<>()).add(Double.parseDouble(cells[1]));
        }
    }
    var medians = new HashMap<String, Double>();
    series.forEach((metric, values) -> medians.put(metric,
            median(values.stream().map(v -> new Stats(Map.of("v", v))).toList(), "v")));
    return medians;
}

List<Map<String, Stats>> loadReps(Path scenario) throws IOException {
    var reps = new ArrayList<Map<String, Stats>>();
    for (var csv : subdirectories(scenario).stream().map(dir -> dir.resolve("stats.csv")).filter(Files::exists).toList()) {
        reps.add(loadStats(csv));
    }
    return reps;
}

Map<String, Stats> loadStats(Path csv) throws IOException {
    var lines = Files.readAllLines(csv);
    var rep = new LinkedHashMap<String, Stats>();
    for (var line : lines.subList(1, lines.size())) {
        var cells = line.split(",");
        var values = new HashMap<String, Double>();
        for (int i = 0; i < COLUMNS.size() && i + 1 < cells.length; i++) {
            values.put(COLUMNS.get(i), cells[i + 1].isBlank() ? Double.NaN : Double.parseDouble(cells[i + 1]));
        }
        rep.put(cells[0], new Stats(values));
    }
    return rep;
}

List<Path> subdirectories(Path dir) throws IOException {
    try (var entries = Files.list(dir)) {
        return entries.filter(Files::isDirectory).sorted().toList();
    }
}

String parameters(Path scenario) throws IOException {
    var file = scenario.resolve("scenario.properties");
    if (!Files.exists(file)) {
        return "";
    }
    return Files.readAllLines(file).stream().filter(line -> !line.isBlank() && !line.startsWith("#"))
            .map(line -> "`" + line + "`").collect(Collectors.joining(" · ", "", "\n\n"));
}

/** Global e grupos primeiro; depois as requisições de cada grupo. */
String order(String name) {
    if (name.equals("Global")) {
        return "0";
    }
    return (name.contains(" / ") ? "2" : "1") + name;
}

static double median(List<Stats> series, String column) {
    var sorted = series.stream().mapToDouble(s -> s.get(column)).filter(v -> !Double.isNaN(v)).sorted().toArray();
    if (sorted.length == 0) {
        return Double.NaN;
    }
    int mid = sorted.length / 2;
    return sorted.length % 2 == 1 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2;
}

static String percent(double ratio, int decimals) {
    return Double.isNaN(ratio) ? "—" : fmt(ratio * 100, decimals) + "%";
}

static String fmt(double value, int decimals) {
    return Double.isNaN(value) ? "—" : String.format(Locale.ROOT, "%,." + decimals + "f", value);
}
