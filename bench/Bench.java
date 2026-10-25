import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Benchmark de ai-fix: 10 bugs, cada uno con tests visibles (los que ve el modelo) y ocultos (los
 * que nunca ve). Un arreglo cuenta como real solo si además pasan los ocultos.
 *
 *   java bench/Bench.java --check              verifica el benchmark: el bug falla, el arreglo de referencia pasa todo
 *   java bench/Bench.java [--model M] [--label L] [caso…]  corre ai-fix y escribe bench/results/<modelo>[.L].md
 *   java -Daifix.numGpu=0 -Daifix.numThread=4 bench/Bench.java …   simula un servidor de GitHub (sin GPU, 4 núcleos)
 *
 * Antes: ./mvnw install (para que Maven encuentre el plugin).
 */
public class Bench {

    static final String PLUGIN = "io.github.yeisonfjrd:ai-fix-maven-plugin:1.1.0:fix";
    static final Path ROOT = Path.of("bench");
    static final Path WORK = Path.of("target", "bench");
    static final boolean WINDOWS = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");

    record Run(int exit, String output, Duration took) {}

    record CaseResult(String id, String bug, String status, int attempts, double modelSeconds, boolean real,
                      int hiddenFailed, int hiddenTotal, Duration wall, String diff, String review) {}

    public static void main(String[] args) throws Exception {
        boolean check = false;
        String model = "qwen3-coder:30b";
        String label = "";
        List<String> only = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--check" -> check = true;
                case "--model" -> model = args[++i];
                case "--label" -> label = args[++i];
                default -> only.add(args[i]);
            }
        }
        List<Path> cases;
        try (Stream<Path> s = Files.list(ROOT.resolve("cases"))) {
            cases = s.filter(Files::isDirectory)
                    .filter(p -> only.isEmpty() || only.stream().anyMatch(o -> p.getFileName().toString().startsWith(o)))
                    .sorted().toList();
        }
        if (check) {
            System.exit(checkAll(cases) ? 0 : 1);
        }
        List<CaseResult> results = new ArrayList<>();
        for (Path c : cases) {
            CaseResult r = runCase(c, model);
            results.add(r);
            System.out.printf("%-28s %-10s intentos=%d modelo=%.0fs real=%s ocultos=%d/%d (%.0fs)%n",
                    r.id(), r.status(), r.attempts(), r.modelSeconds(), r.real() ? "sí" : "no",
                    r.hiddenTotal() - r.hiddenFailed(), r.hiddenTotal(), r.wall().toMillis() / 1000.0);
        }
        String name = model.replaceAll("[^\\w.-]", "_") + (label.isBlank() ? "" : "." + label);
        Path out = ROOT.resolve("results").resolve(name + ".md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report(model, results), StandardCharsets.UTF_8);
        System.out.println("\nResultados: " + out);
    }

    // ------------------------------------------------------------------ verificar el benchmark

    static boolean checkAll(List<Path> cases) throws Exception {
        boolean ok = true;
        for (Path c : cases) {
            String id = c.getFileName().toString();
            Path buggy = prepare(c, "check-bug-" + id, false, false);
            boolean bugFails = mvn(buggy, "test").exit() != 0;
            Path fixed = prepare(c, "check-fix-" + id, true, true);
            Run all = mvn(fixed, "test");
            boolean fixPasses = all.exit() == 0;
            boolean good = bugFails && fixPasses;
            ok &= good;
            System.out.printf("%-28s %s  (el bug falla los visibles: %s · el arreglo pasa visibles y ocultos: %s)%n",
                    id, good ? "OK " : "MAL", bugFails ? "sí" : "NO", fixPasses ? "sí" : "NO");
            if (!fixPasses) System.out.println(tail(all.output(), 2000));
        }
        System.out.println(ok ? "\nEl benchmark está bien armado." : "\nHay casos mal armados.");
        return ok;
    }

    // ------------------------------------------------------------------ correr ai-fix

    static CaseResult runCase(Path c, String model) throws Exception {
        String id = c.getFileName().toString();
        String bug = Files.readString(c.resolve("case.txt")).strip();
        Path work = prepare(c, id, false, false);
        long start = System.nanoTime();
        mvn(work, pluginGoals(model).toArray(String[]::new));
        Path reportFile = work.resolve("target/ai-fix-report.json");
        String report = Files.exists(reportFile) ? Files.readString(reportFile) : "";
        String status = field(report, "status", "ERROR");
        int attempts = (int) number(report, "attempts");
        double modelSeconds = number(report, "modelSeconds");
        String diff = unescape(field(report, "diff", ""));
        // Qué hizo la segunda pasada: OK (dijo que estaba completo), IMPROVED, KEPT_ORIGINAL u OFF.
        String review = field(report, "review", "-");

        boolean real = false;
        int[] hidden = {0, 0};
        if (status.equals("FIXED")) {
            // Recién ahora entran los tests ocultos: el modelo nunca los vio.
            copyTree(c.resolve("hidden"), work.resolve("src/test/java"));
            Run all = mvn(work, "test");
            real = all.exit() == 0;
            hidden = hiddenCounts(work.resolve("target/surefire-reports"));
        }
        Duration wall = Duration.ofNanos(System.nanoTime() - start);
        return new CaseResult(id, bug, status, attempts, modelSeconds, real, hidden[0], hidden[1], wall, diff, review);
    }

    /** Las opciones -Daifix.* que recibe el benchmark van al plugin (ej: -Daifix.numGpu=0 -Daifix.numThread=4). */
    static List<String> pluginGoals(String model) {
        List<String> goals = new ArrayList<>(List.of(PLUGIN, "-Daifix.model=" + model));
        goals.addAll(pluginOptions().stream().map(o -> "-D" + o).toList());
        return goals;
    }

    static List<String> pluginOptions() {
        return System.getProperties().stringPropertyNames().stream()
                .filter(k -> k.startsWith("aifix.") && !k.equals("aifix.model"))
                .sorted()
                .map(k -> k + "=" + System.getProperty(k))
                .toList();
    }

    /** Copia el proyecto base y el caso a target/bench/<nombre>. */
    static Path prepare(Path c, String name, boolean withSolution, boolean withHidden) throws IOException {
        Path work = WORK.resolve(name);
        deleteTree(work);
        copyTree(ROOT.resolve("template"), work);
        copyTree(c.resolve("src"), work.resolve("src"));
        if (withSolution) copyTree(c.resolve("solution"), work.resolve("src/main/java"));
        if (withHidden) copyTree(c.resolve("hidden"), work.resolve("src/test/java"));
        return work;
    }

    static Run mvn(Path dir, String... goals) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>();
        if (WINDOWS) {
            cmd.addAll(List.of("cmd", "/c", dir.toAbsolutePath().resolve("mvnw.cmd").toString()));
        } else {
            cmd.add(dir.toAbsolutePath().resolve("mvnw").toString());
        }
        cmd.add("-B");
        cmd.addAll(List.of(goals));
        long start = System.nanoTime();
        Process p = new ProcessBuilder(cmd).directory(dir.toFile()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!p.waitFor(30, TimeUnit.MINUTES)) p.destroyForcibly();
        return new Run(p.exitValue(), out, Duration.ofNanos(System.nanoTime() - start));
    }

    /** Cuántos tests ocultos fallaron y cuántos hay, leyendo los XML de Surefire. */
    static int[] hiddenCounts(Path reports) throws IOException {
        int failed = 0, total = 0;
        if (!Files.isDirectory(reports)) return new int[] {0, 0};
        try (Stream<Path> s = Files.list(reports)) {
            for (Path xml : s.filter(p -> p.getFileName().toString().matches("TEST-.*HiddenTest\\.xml")).toList()) {
                String text = Files.readString(xml);
                total += attr(text, "tests");
                failed += attr(text, "failures") + attr(text, "errors");
            }
        }
        return new int[] {failed, total};
    }

    // ------------------------------------------------------------------ reporte

    static String report(String model, List<CaseResult> results) {
        long fixed = results.stream().filter(r -> r.status().equals("FIXED")).count();
        long real = results.stream().filter(CaseResult::real).count();
        double avgModel = results.stream().mapToDouble(CaseResult::modelSeconds).average().orElse(0);
        double avgWall = results.stream().mapToLong(r -> r.wall().toMillis()).average().orElse(0) / 1000.0;
        StringBuilder sb = new StringBuilder();
        sb.append("# ai-fix benchmark · ").append(model).append("\n\n");
        if (!pluginOptions().isEmpty()) sb.append("Options: `").append(String.join(" ", pluginOptions())).append("`\n\n");
        sb.append("Run on ").append(LocalDate.now()).append(". ")
                .append(String.format(Locale.ROOT, "**%d/%d fixed** (visible tests green), **%d/%d real fixes** (hidden tests green too). ", fixed, results.size(), real, results.size()))
                .append(String.format(Locale.ROOT, "Average per case: %.0f s in the model, %.0f s in total.%n%n", avgModel, avgWall));
        sb.append("| Case | Bug | Result | Attempts | Review | Model | Hidden tests | Total |\n|---|---|---|---|---|---|---|---|\n");
        for (CaseResult r : results) {
            String result = r.real() ? "real fix" : r.status().equals("FIXED") ? "**incomplete**" : r.status().toLowerCase(Locale.ROOT).replace('_', ' ');
            sb.append(String.format(Locale.ROOT, "| %s | %s | %s | %d | %s | %.0f s | %s | %.0f s |%n",
                    r.id(), r.bug(), result, r.attempts(), r.status().equals("FIXED") ? r.review().toLowerCase(Locale.ROOT).replace('_', ' ') : "-", r.modelSeconds(),
                    r.hiddenTotal() == 0 ? "-" : (r.hiddenTotal() - r.hiddenFailed()) + "/" + r.hiddenTotal(),
                    r.wall().toMillis() / 1000.0));
        }
        sb.append("\n## Diffs\n\n");
        for (CaseResult r : results) {
            if (r.diff().isBlank()) continue;
            sb.append("### ").append(r.id()).append("\n\n```diff\n").append(r.diff().strip()).append("\n```\n\n");
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ utilidades

    static String field(String json, String name, String fallback) {
        Matcher m = Pattern.compile("\"" + name + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").matcher(json);
        return m.find() ? m.group(1) : fallback;
    }

    static double number(String json, String name) {
        Matcher m = Pattern.compile("\"" + name + "\"\\s*:\\s*([\\d.]+)").matcher(json);
        return m.find() ? Double.parseDouble(m.group(1)) : 0;
    }

    static int attr(String xml, String name) {
        Matcher m = Pattern.compile("<testsuite[^>]*\\s" + name + "=\"(\\d+)\"").matcher(xml);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    static String unescape(String s) {
        return s.replace("\\n", "\n").replace("\\r", "").replace("\\t", "\t").replace("\\\"", "\"").replace("\\\\", "\\");
    }

    static String tail(String s, int max) {
        return s.length() <= max ? s : s.substring(s.length() - max);
    }

    static void copyTree(Path from, Path to) throws IOException {
        if (!Files.exists(from)) return;
        try (Stream<Path> walk = Files.walk(from)) {
            for (Path p : walk.toList()) {
                Path target = to.resolve(from.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(target);
                else Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            }
        }
    }

    static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
        }
    }
}
