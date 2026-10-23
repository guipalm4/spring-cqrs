// Relatório do benchmark: compact source file (JEP 512), roda sem build com `java bench/Report.java`.
//
//   java bench/Report.java extract <relatório-gatling> <stats.csv>   tabela do index.html -> CSV
//   java bench/Report.java seconds <relatório-gatling> <seconds.csv> percentis por segundo do index.html -> CSV
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
/** Séries do gráfico "Response Time Percentiles over Time" do index.html, na ordem em que o Gatling as grava. */
static final List<String> PERCENTILES = List.of("min", "25%", "50%", "75%", "80%", "85%", "90%", "95%", "99%", "max");
static final Pattern POINT = Pattern.compile("\\[(\\d+), (\\d+)\\]");
static final double COLLAPSE_P95_MS = 5_000;
/** Instruções que um comando executa: escritas e o lock da conta. */
static final Pattern WRITE = Pattern.compile("^\"?(INSERT|UPDATE|DELETE)\\b|FOR UPDATE", Pattern.CASE_INSENSITIVE);

void main(String[] args) throws IOException {
    switch (args.length == 0 ? "" : args[0]) {
        case "extract" -> extract(Path.of(args[1], "index.html"), Path.of(args[2]));
        case "seconds" -> seconds(Path.of(args[1], "index.html"), Path.of(args[2]));
        case "summarize" -> summarize(Path.of(args[1]));
        case "chart" -> System.out.print(chart(Path.of(args[1]), args[2], args[3],
                args.length > 4 ? Double.parseDouble(args[4]) : 1, args.length > 5 ? args[5] : null));
        default -> throw new IllegalArgumentException("uso: extract <relatório> <csv> | seconds <relatório> <csv> | "
                + "summarize <resultados> | chart <timeseries.csv|seconds.csv> <coluna> <título> [divisor] [y máximo]");
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

/**
 * Percentis de resposta de cada segundo da execução (requisições OK, todas juntas), com o instante em epoch para
 * cruzar com o docker-stats.csv. Os relatórios HTML ficam fora do Git (bench/target); sem esta série, um pico de um
 * segundo some na média de 5 s do timeseries.csv (ADR-0013).
 */
void seconds(Path html, Path csv) throws IOException {
    var content = Files.readString(html);
    var series = new ArrayList<List<long[]>>();
    for (var name : PERCENTILES) {
        var matcher = Pattern.compile("name: '" + Pattern.quote(name) + "',\\s*data: (\\[\\[.*?\\]\\])", Pattern.DOTALL)
                .matcher(content);
        if (!matcher.find()) {
            throw new IllegalStateException("série '" + name + "' não encontrada em " + html);
        }
        var points = new ArrayList<long[]>();
        var point = POINT.matcher(matcher.group(1));
        while (point.find()) {
            points.add(new long[] {Long.parseLong(point.group(1)), Long.parseLong(point.group(2))});
        }
        series.add(points);
    }
    var lines = new ArrayList<String>();
    lines.add("epoch_s,t_s," + String.join(",", PERCENTILES.stream()
            .map(name -> name.equals("min") || name.equals("max") ? name : "p" + name.replace("%", "")).toList()));
    long start = series.getFirst().getFirst()[0];
    for (int i = 0; i < series.getFirst().size(); i++) {
        var line = new StringBuilder().append(series.getFirst().get(i)[0] / 1000).append(',')
                .append((series.getFirst().get(i)[0] - start) / 1000);
        for (var points : series) {
            line.append(',').append(points.get(i)[1]);
        }
        lines.add(line.toString());
    }
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
    pools(runs, out);
    jvms(runs, out);
    projection(runs, out);
    reads(runs, out);
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
                labels.add(readLoad(scenario) + ", " + rep.getFileName());
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
                    .append(" | ").append(fmt(metrics.getOrDefault("gc_pause_total_s", Double.NaN), 1))
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
            `pg_stat_statements`, em ms (o custo de um comando dentro do Postgres, sem a fila). GC: segundos com a JVM
            parada em GC na execução (medido a partir da #11).

            | Cenário | Rep. | Estado | p95 global | % KO | Comandos p50 | p95 | p99 | Espera no pool | Uso da conexão | Comando no banco | GC |
            |---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
            """).append(table);
    if (!labels.isEmpty()) {
        out.append("\n").append(mermaid("bar", "p95 dos comandos por execução", labels, "segundos", commandP95));
    }
}

/**
 * Espera, uso e fila de cada pool do Hikari por execução, quando há mais de um pool (com um, repetiria o total). No
 * controle da #11 (ADR-0010), mostra se os comandos ainda esperam conexão quando as consultas fazem fila.
 */
void pools(Path runs, StringBuilder out) throws IOException {
    var table = new StringBuilder();
    boolean split = false;
    for (var scenario : subdirectories(runs)) {
        for (var rep : subdirectories(scenario)) {
            var metrics = loadMetrics(List.of(rep));
            var pools = metrics.keySet().stream().filter(key -> key.startsWith("hikari_acquire_avg_s_"))
                    .map(key -> key.substring("hikari_acquire_avg_s_".length())).sorted().toList();
            split |= pools.size() > 1;
            for (var pool : pools) {
                table.append("| ").append(scenario.getFileName()).append(" | ").append(rep.getFileName())
                        .append(" | ").append(pool)
                        .append(" | ").append(fmt(metrics.getOrDefault("hikari_acquire_avg_s_" + pool, Double.NaN) * 1000, 1))
                        .append(" | ").append(fmt(metrics.getOrDefault("hikari_usage_avg_s_" + pool, Double.NaN) * 1000, 1))
                        .append(" | ").append(fmt(metrics.getOrDefault("hikari_pending_max_" + pool, Double.NaN), 0))
                        .append(" |\n");
            }
        }
    }
    if (!split) {
        return;
    }
    out.append("""

            ## Pools por execução

            Espera e uso da conexão do Hikari em ms, média da execução; fila máxima de requisições esperando conexão.
            Pool sem requisições na execução aparece com —.

            | Cenário | Rep. | Pool | Espera | Uso da conexão | Fila máx. |
            |---|---|---|---:|---:|---:|
            """).append(table);
}

/**
 * GC, CPU e heap de cada JVM por execução, quando há mais de uma (na v3: command-api, query-api e projetor). Mostra
 * se a JVM dos comandos ainda para em GC quando a das consultas colapsa (H3, ADR-0010, ADR-0012).
 */
void jvms(Path runs, StringBuilder out) throws IOException {
    var table = new StringBuilder();
    boolean many = false;
    for (var scenario : subdirectories(runs)) {
        for (var rep : subdirectories(scenario)) {
            var metrics = loadMetrics(List.of(rep));
            var instances = metrics.keySet().stream().filter(key -> key.startsWith("gc_pause_total_s_"))
                    .map(key -> key.substring("gc_pause_total_s_".length())).sorted().toList();
            many |= instances.size() > 1;
            for (var instance : instances) {
                table.append("| ").append(scenario.getFileName()).append(" | ").append(rep.getFileName())
                        .append(" | ").append(instance)
                        .append(" | ").append(fmt(metrics.getOrDefault("gc_pause_total_s_" + instance, Double.NaN), 1))
                        .append(" | ").append(percent(metrics.getOrDefault("app_cpu_avg_" + instance, Double.NaN), 0))
                        .append(" | ").append(fmt(metrics.getOrDefault("heap_max_mib_" + instance, Double.NaN), 0))
                        .append(" |\n");
            }
        }
    }
    if (!many) {
        return;
    }
    out.append("""

            ## JVMs por execução

            GC: segundos com a JVM parada em GC na execução. CPU: média do processo. Heap: maior uso na execução, em MiB.

            | Cenário | Rep. | JVM | GC (s) | CPU | Heap máx. |
            |---|---|---|---:|---:|---:|
            """).append(table);
}

/** Atraso da projeção, reentregas e fila do outbox por execução, quando há projetor (v3, H5, ADR-0012). */
void projection(Path runs, StringBuilder out) throws IOException {
    var table = new StringBuilder();
    for (var scenario : subdirectories(runs)) {
        for (var rep : subdirectories(scenario)) {
            var metrics = loadMetrics(List.of(rep));
            double applied = metrics.getOrDefault("projection_applied", Double.NaN);
            if (Double.isNaN(applied) || applied == 0) {
                continue;
            }
            table.append("| ").append(scenario.getFileName()).append(" | ").append(rep.getFileName())
                    .append(" | ").append(fmt(applied, 0))
                    .append(" | ").append(fmt(metrics.getOrDefault("projection_lag_p50_s", Double.NaN) * 1000, 0))
                    .append(" | ").append(fmt(metrics.getOrDefault("projection_lag_p99_s", Double.NaN) * 1000, 0))
                    .append(" | ").append(fmt(metrics.getOrDefault("projection_lag_max_s", Double.NaN) * 1000, 0))
                    .append(" | ").append(fmt(metrics.getOrDefault("projection_duplicates", Double.NaN), 0))
                    .append(" | ").append(fmt(metrics.getOrDefault("outbox_backlog_max", Double.NaN), 0))
                    .append(" |\n");
        }
    }
    if (table.isEmpty()) {
        return;
    }
    out.append("""

            ## Atraso da projeção por execução

            Do `occurred_at` do lançamento (início da transação do comando) até ele estar nas duas projeções do Mongo,
            em ms; superestima o atraso em até a duração do comando. p50 e p99 vêm do histograma (precisão dos
            buckets); o máximo pode vir de uma janela anterior à execução. Reentregas: lançamentos que o projetor
            recebeu de novo e descartou. Fila do outbox: publicações ainda não enviadas ao Kafka, no maior instante.

            | Cenário | Rep. | Lançamentos | p50 | p99 | Máx. | Reentregas | Fila do outbox |
            |---|---|---:|---:|---:|---:|---:|---:|
            """).append(table);
}

/**
 * Tempo por página lida do disco, por execução: no Postgres, {@code read_ms / shared_blks_read} somados no
 * pg_stat_statements; no Mongo (v3), o tempo das threads da aplicação lendo páginas para o cache do WiredTiger. A
 * velocidade do disco decide se a v1 colapsa (CLAUDE.md), então toda comparação a mostra ao lado.
 */
void reads(Path runs, StringBuilder out) throws IOException {
    var table = new StringBuilder();
    for (var scenario : subdirectories(runs)) {
        for (var rep : subdirectories(scenario)) {
            double[] postgres = postgresReads(rep.resolve("pg_stat_statements.csv"));
            double[] mongo = mongoReads(rep.resolve("mongo-cache.csv"));
            table.append("| ").append(scenario.getFileName()).append(" | ").append(rep.getFileName())
                    .append(" | ").append(fmt(postgres[0], 0))
                    .append(" | ").append(fmt(postgres[1] / postgres[0], 2))
                    .append(" | ").append(fmt(mongo[0], 0))
                    .append(" | ").append(fmt(mongo[1] / mongo[0], 2))
                    .append(" |\n");
        }
    }
    out.append("""

            ## Leitura de disco por execução

            Páginas lidas fora do cache e tempo médio por página, em ms. Postgres: soma das instruções do
            pg_stat_statements (as 50 mais caras). Mongo: leituras das threads da aplicação para o cache do WiredTiger.
            — quando o store não existe na fase.

            | Cenário | Rep. | Postgres: páginas | ms/página | Mongo: páginas | ms/página |
            |---|---|---:|---:|---:|---:|
            """).append(table);
}

/** {páginas lidas, ms lendo} somados no pg_stat_statements.csv. */
double[] postgresReads(Path csv) throws IOException {
    if (!Files.exists(csv)) {
        return new double[] {Double.NaN, Double.NaN};
    }
    double pages = 0;
    double ms = 0;
    for (var line : Files.readAllLines(csv).stream().skip(1).toList()) {
        var cells = line.split(",", 9);
        pages += Double.parseDouble(cells[6]);
        ms += Double.parseDouble(cells[7]);
    }
    return new double[] {pages, ms};
}

/** {páginas lidas pelas threads da aplicação, ms lendo} entre o início e o fim da execução, do mongo-cache.csv. */
double[] mongoReads(Path csv) throws IOException {
    if (!Files.exists(csv)) {
        return new double[] {Double.NaN, Double.NaN};
    }
    var lines = Files.readAllLines(csv);
    var before = lines.get(1).split(",");
    var after = lines.get(2).split(",");
    double reads = Double.parseDouble(after[3]) - Double.parseDouble(before[3]);
    double micros = Double.parseDouble(after[4]) - Double.parseDouble(before[4]);
    return new double[] {reads, micros / 1000};
}

/** Uma coluna do timeseries.csv como gráfico de linha; recusa série com lacunas, que o Mermaid desenharia contínua. */
/** Com o y máximo, gráficos de execuções diferentes ficam na mesma escala. */
String chart(Path csv, String column, String title, double divisor, String yMax) throws IOException {
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
    // O timeseries.csv começa por t; o seconds.csv, pelo epoch, com o tempo relativo em t_s.
    var header = List.of(lines.getFirst().split(","));
    var last = lines.getLast().split(",")[Math.max(header.indexOf("t_s"), 0)];
    return """
            ```mermaid
            xychart-beta
                title "%s"
                x-axis "segundos desde o início" 0 --> %s
            %s    line [%s]
            ```
            """.formatted(title, last, yMax == null ? "" : "    y-axis 0 --> " + yMax + "\n", join(values));
}

/** Barras na horizontal: com uma dúzia de execuções, os rótulos no eixo x se sobrepõem. */
static String mermaid(String type, String title, List<String> labels, String unit, List<Double> values) {
    return """
            ```mermaid
            xychart-beta horizontal
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

/** Rótulo da carga de leitura do cenário, a partir do scenario.properties. */
String readLoad(Path scenario) throws IOException {
    var properties = new Properties();
    try (var reader = Files.newBufferedReader(scenario.resolve("scenario.properties"))) {
        properties.load(reader);
    }
    var read = properties.getProperty("readRate", "?");
    return read.equals("0") ? "só escrita" : read + " consultas/s";
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
