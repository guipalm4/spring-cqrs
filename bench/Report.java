// Relatório do benchmark: compact source file (JEP 512), roda sem build com `java bench/Report.java`.
//
//   java bench/Report.java extract <relatório-gatling> <stats.csv>   tabela do index.html -> CSV
//   java bench/Report.java summarize <docs/benchmarks/...>           runs/*/r*/stats.csv -> summary.md
//
// O Gatling 3.16 não grava mais stats.json: as estatísticas só existem na tabela do index.html. Os percentis
// incluem requisições OK e KO, e o resumo mostra o % de KO ao lado para não esconder falhas.

static final List<String> COLUMNS = List.of("total", "ok", "ko", "pct_ko", "rps", "min", "p50", "p75", "p95",
        "p99", "max", "mean", "stddev");
static final Pattern ROW = Pattern.compile(
        "<tr id=\"([^\"]+)\"(?: data-parent=\"([^\"]+)\")?\\s*>(.*?)</tr>", Pattern.DOTALL);
static final Pattern NAME = Pattern.compile("class=\"ellipsed-name\">([^<]*)</span>");
static final Pattern VALUE = Pattern.compile("<td class=\"value \\w+ col-(\\d+)\">([^<]*)</td>");

void main(String[] args) throws IOException {
    switch (args.length == 0 ? "" : args[0]) {
        case "extract" -> extract(Path.of(args[1], "index.html"), Path.of(args[2]));
        case "summarize" -> summarize(Path.of(args[1]));
        default -> throw new IllegalArgumentException("uso: extract <relatório> <csv> | summarize <resultados>");
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
            `runs/<cenário>/scenario.properties`.
            """);
    var commandP95 = new LinkedHashMap<String, double[]>();
    var resources = new LinkedHashMap<String, Map<String, Double>>();
    try (var scenarios = Files.list(runs)) {
        for (var scenario : scenarios.filter(Files::isDirectory).sorted().toList()) {
            var reps = loadReps(scenario);
            if (reps.isEmpty()) {
                continue;
            }
            resources.put(scenario.getFileName().toString(), loadMetrics(scenario));
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
                if (name.equals("comandos")) {
                    commandP95.put(scenario.getFileName().toString(),
                            new double[] {median(series, "p50"), median(series, "p95"), median(series, "p99")});
                }
            }
        }
    }
    if (!commandP95.isEmpty()) {
        out.append("\n## Comandos por cenário (H1)\n\n| Cenário | p50 | p95 | p99 |\n|---|---:|---:|---:|\n");
        commandP95.forEach((scenario, p) -> out.append("| ").append(scenario).append(" | ").append(fmt(p[0], 0))
                .append(" | ").append(fmt(p[1], 0)).append(" | ").append(fmt(p[2], 0)).append(" |\n"));
    }
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

/** Mediana, entre as repetições, de cada métrica do prometheus.csv. */
Map<String, Double> loadMetrics(Path scenario) throws IOException {
    var series = new HashMap<String, List<Double>>();
    try (var dirs = Files.list(scenario)) {
        for (var csv : dirs.map(dir -> dir.resolve("prometheus.csv")).filter(Files::exists).toList()) {
            for (var line : Files.readAllLines(csv).stream().skip(1).toList()) {
                var cells = line.split(",");
                series.computeIfAbsent(cells[0], _ -> new ArrayList<>()).add(Double.parseDouble(cells[1]));
            }
        }
    }
    var medians = new HashMap<String, Double>();
    series.forEach((metric, values) -> medians.put(metric,
            median(values.stream().map(v -> new Stats(Map.of("v", v))).toList(), "v")));
    return medians;
}

List<Map<String, Stats>> loadReps(Path scenario) throws IOException {
    var reps = new ArrayList<Map<String, Stats>>();
    try (var dirs = Files.list(scenario)) {
        for (var csv : dirs.map(dir -> dir.resolve("stats.csv")).filter(Files::exists).sorted().toList()) {
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
            reps.add(rep);
        }
    }
    return reps;
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
