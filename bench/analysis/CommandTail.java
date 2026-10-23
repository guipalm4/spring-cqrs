// Diagnóstico da cauda dos comandos (ADR-0013): compact source file, roda sem build.
//
//   java bench/analysis/CommandTail.java <docs/benchmarks/...> [cenário=s1] [limiar_ms=60] [container=mongo]
//
// Para cada repetição do cenário:
// - os segundos em que a resposta máxima passou do limiar (seconds.csv, extraído do relatório do Gatling);
// - quantos desses picos caem perto de um salto no contador de escrita em disco do container (docker-stats.csv),
//   comparado com a fração de segundos da execução que cai perto de um salto (o acerto ao acaso);
// - quanto cada container escreveu em disco por comando;
// - o maior outlier que o desvio-padrão de cada instrução no pg_stat_statements comporta (desvio × √chamadas).
//
// O contador de escrita do docker stats tem 3 algarismos significativos (10 MB de resolução na faixa de GB): um
// "salto" é uma amostra em que ele andou pelo menos 10 MB (ou 20 MB) desde a anterior. É correlação, não causa:
// a confirmação é a execução de diagnóstico com o projetor parado (ADR-0013, item 5).

static final int WINDOW_S = 3;
static final Pattern BYTES = Pattern.compile("([\\d.]+)\\s*([kMGT]?B)");

record Sample(long epoch, String container, double cpu, double written) {}

void main(String[] args) throws IOException {
    if (args.length == 0) {
        throw new IllegalArgumentException("uso: <resultados> [cenário] [limiar_ms] [container]");
    }
    var scenario = Path.of(args[0], "runs", args.length > 1 ? args[1] : "s1");
    int threshold = args.length > 2 ? Integer.parseInt(args[2]) : 60;
    var target = args.length > 3 ? args[3] : "mongo";
    var props = new Properties();
    try (var in = Files.newBufferedReader(scenario.resolve("scenario.properties"))) {
        props.load(in);
    }
    double writeRate = Double.parseDouble(props.getProperty("writeRate"));

    System.out.printf("# %s, picos acima de %d ms, saltos de escrita do %s%n%n", scenario, threshold, target);
    var hits = new int[2];
    var covered = new long[2];
    int spikesTotal = 0;
    long secondsTotal = 0;
    try (var reps = Files.list(scenario)) {
        for (var rep : reps.filter(Files::isDirectory).sorted().toList()) {
            var seconds = Files.readAllLines(rep.resolve("seconds.csv")).stream().skip(1)
                    .map(line -> line.split(",")).toList();
            long start = Long.parseLong(seconds.getFirst()[0]);
            long end = Long.parseLong(seconds.getLast()[0]);
            var spikes = seconds.stream().filter(cells -> Long.parseLong(cells[cells.length - 1]) > threshold)
                    .map(cells -> new long[] {Long.parseLong(cells[0]), Long.parseLong(cells[cells.length - 1])})
                    .toList();
            var samples = dockerStats(rep.resolve("docker-stats.csv"));
            System.out.printf("## %s: %d picos %s%n", rep.getFileName(), spikes.size(),
                    spikes.stream().map(s -> (s[0] - start) + "s=" + s[1] + "ms").toList());
            spikesTotal += spikes.size();
            secondsTotal += end - start + 1;
            for (int i = 0; i < 2; i++) {
                double minBytes = (i + 1) * 10e6 - 1;
                var jumps = jumps(samples.getOrDefault(target, List.of()), minBytes, start, end);
                hits[i] += (int) spikes.stream().filter(s -> near(s[0], jumps)).count();
                covered[i] += LongStream.rangeClosed(start, end).filter(t -> near(t, jumps)).count();
            }
            samples.forEach((container, xs) -> {
                double written = xs.getLast().written() - xs.getFirst().written();
                double commands = writeRate * (xs.getLast().epoch() - xs.getFirst().epoch());
                if (written > 0 && commands > 0) {
                    System.out.printf("   %-18s %7.1f MB escritos, %6.1f KB por comando%n", container, written / 1e6,
                            written / commands / 1e3);
                }
            });
            outliers(rep.resolve("pg_stat_statements.csv"));
        }
    }
    System.out.printf("%n## Picos perto de um salto (até %d s), contra o acaso%n%n", WINDOW_S);
    for (int i = 0; i < 2; i++) {
        System.out.printf("salto ≥ %d MB: %d de %d picos (%.0f%%); acaso: %.0f%% dos segundos%n", (i + 1) * 10, hits[i],
                spikesTotal, 100.0 * hits[i] / spikesTotal, 100.0 * covered[i] / secondsTotal);
    }
}

/** Amostras do docker stats por container (nome sem o prefixo do projeto e o sufixo da réplica). */
Map<String, List<Sample>> dockerStats(Path csv) throws IOException {
    var samples = new TreeMap<String, List<Sample>>();
    for (var line : Files.readAllLines(csv)) {
        // epoch,nome,cpu%,memória,bloco lido / bloco escrito
        var cells = line.split(",");
        var container = cells[1].replaceFirst("^[^-]+-[^-]+-", "").replaceFirst("-\\d+$", "");
        var written = cells[4].split("/")[1];
        samples.computeIfAbsent(container, _ -> new ArrayList<>()).add(new Sample(Long.parseLong(cells[0]), container,
                Double.parseDouble(cells[2].replace("%", "")), bytes(written)));
    }
    return samples;
}

/** Instantes das amostras em que o contador de escrita andou pelo menos minBytes desde a amostra anterior. */
List<Long> jumps(List<Sample> xs, double minBytes, long start, long end) {
    var jumps = new ArrayList<Long>();
    for (int i = 1; i < xs.size(); i++) {
        long t = xs.get(i).epoch();
        if (t >= start && t <= end && xs.get(i).written() - xs.get(i - 1).written() >= minBytes) {
            jumps.add(t);
        }
    }
    return jumps;
}

/** A amostra registra o salto no fim do intervalo (~2 s): o pico pode vir até uma amostra antes dela. */
static boolean near(long second, List<Long> jumps) {
    return jumps.stream().anyMatch(t -> t - second >= -WINDOW_S && t - second <= WINDOW_S + 2);
}

void outliers(Path csv) throws IOException {
    if (!Files.exists(csv)) {
        return;
    }
    for (var line : Files.readAllLines(csv).stream().skip(1).toList()) {
        // calls,total_ms,mean_ms,stddev_ms,rows,shared_blks_hit,shared_blks_read,read_ms,query
        var cells = line.split(",", 9);
        long calls = Long.parseLong(cells[0]);
        if (calls >= 1000) {
            double bound = Double.parseDouble(cells[3]) * Math.sqrt(calls);
            System.out.printf("   pg %6d chamadas, média %5.2f ms, outlier até ~%6.1f ms  %s%n", calls,
                    Double.parseDouble(cells[2]), bound, cells[8].substring(0, Math.min(70, cells[8].length())));
        }
    }
}

static double bytes(String text) {
    var m = BYTES.matcher(text.strip());
    if (!m.find()) {
        return Double.NaN;
    }
    double unit = switch (m.group(2)) {
        case "kB" -> 1e3;
        case "MB" -> 1e6;
        case "GB" -> 1e9;
        case "TB" -> 1e12;
        default -> 1;
    };
    return Double.parseDouble(m.group(1)) * unit;
}
