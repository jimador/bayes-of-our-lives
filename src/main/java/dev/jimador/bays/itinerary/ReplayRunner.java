package dev.jimador.bays.itinerary;

import com.embabel.common.ai.decision.DecisionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jimador.bays.decision.BayesDecisionServices;
import dev.jimador.bays.decision.DecisionInputHash;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/** Replays one saved decision boundary; it does not invoke agent planning or public lookup. */
@Component
public class ReplayRunner implements CommandLineRunner {
    public record Row(String engine, int repetition, String status, String completeness, String choice,
                      String rating, String classification, String model, double decisionMs,
                      double classificationMs, double totalMs, String note) {}
    public record Report(String inputSha256, int repetitions, List<Row> rows) {}
    private final String captureFile, outputPrefix;
    private final int runs;
    private final BayesDecisionServices services;
    private final ObjectMapper mapper = new ObjectMapper();
    public ReplayRunner(@Value("${bays.replay.run-file:}") String captureFile,
            @Value("${bays.replay.output-prefix:results/itinerary-replay}") String outputPrefix,
            @Value("${bays.replay.runs:2}") int runs, BayesDecisionServices services) {
        this.captureFile = captureFile; this.outputPrefix = outputPrefix; this.runs = runs; this.services = services;
    }
    @Override public void run(String... args) throws Exception {
        if (captureFile.isBlank()) return;
        var capture = mapper.readValue(Path.of(captureFile).toFile(), DecisionReplay.Capture.class);
        var report = execute(capture, runs, ReplayRunner::configured, services::forItineraryEngine);
        Path prefix = Path.of(outputPrefix).toAbsolutePath().normalize();
        if (!prefix.startsWith(Path.of("results").toAbsolutePath().normalize()))
            throw new IllegalArgumentException("Replay output must stay under results/");
        Files.createDirectories(prefix.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(Path.of(prefix + ".json").toFile(), report);
        Files.writeString(Path.of(prefix + ".md"), markdown(report));
        System.out.println("replayInputSha256=" + report.inputSha256() + " rows=" + report.rows().size()
                + " output=" + prefix + ".{md,json}");
    }
    static boolean configured(String engine) {
        String key = switch (engine) {
            case "jev" -> "TYPESAFE_API_KEY"; case "openai" -> "OPENAI_API_KEY"; default -> null;
        };
        return key == null || System.getenv(key) != null && !System.getenv(key).isBlank();
    }
    public static Report execute(DecisionReplay.Capture capture, int repetitions,
            Predicate<String> available, Function<String, DecisionService> resolve) {
        if (repetitions < 1 || repetitions > 10) throw new IllegalArgumentException("Replay runs must be 1..10");
        List<Row> rows = new ArrayList<>();
        for (String engine : List.of("local", "jev", "openai")) {
            if (!available.test(engine)) {
                rows.add(new Row(engine, 0, "skipped", "", "", "", "", "", 0, 0, 0,
                        "runtime key unavailable"));
                continue;
            }
            for (int run = 1; run <= repetitions; run++) {
                if (!DecisionInputHash.sha256(capture.input()).equals(capture.inputSha256()))
                    throw new IllegalStateException("Replay input changed");
                long start = System.nanoTime();
                try {
                    var answer = DecisionReplay.evaluate(capture, resolve.apply(engine));
                    rows.add(new Row(engine, run, "ok", answer.completeness(), answer.choice(),
                            answer.rating(), answer.classification(), answer.model(), answer.decisionMs(),
                            answer.classificationMs(), (System.nanoTime() - start) / 1_000_000.0, ""));
                } catch (RuntimeException failure) {
                    rows.add(new Row(engine, run, "failed", "", "", "", "", "", 0, 0,
                            (System.nanoTime() - start) / 1_000_000.0, failure.getClass().getSimpleName()));
                }
            }
        }
        return new Report(capture.inputSha256(), repetitions, List.copyOf(rows));
    }
    public static String markdown(Report report) {
        StringBuilder out = new StringBuilder("# Typed itinerary decision replay\n\n"
                + "One immutable decision input was passed to each available engine. Classification adds only the "
                + "selected itinerary type to that same input. No discovery, planning, Dice retrieval, or warmup. "
                + "Remote durations include adapter, network, provider work, and parsing; inference is not isolated. "
                + "Small illustrative samples do not establish a speed or quality ranking.\n\n"
                + "Input SHA-256: `" + report.inputSha256() + "`. Repetitions per available engine: "
                + report.repetitions() + ".\n\n"
                + "| Engine | Run | Status | Fully sourced | Next | Fit | Classification | Decision ms | Classification ms | Total ms | Note |\n"
                + "| --- | ---: | --- | --- | --- | --- | --- | ---: | ---: | ---: | --- |\n");
        for (Row row : report.rows()) out.append("| ").append(row.engine()).append(" | ")
                .append(row.repetition()).append(" | ").append(row.status()).append(" | ")
                .append(row.completeness()).append(" | ").append(row.choice()).append(" | ")
                .append(row.rating()).append(" | ").append(row.classification()).append(" | ")
                .append(format(row.decisionMs())).append(" | ").append(format(row.classificationMs()))
                .append(" | ").append(format(row.totalMs())).append(" | ").append(row.note()).append(" |\n");
        return out.toString();
    }
    private static String format(double ms) { return String.format(java.util.Locale.ROOT, "%.3f", ms); }
}
