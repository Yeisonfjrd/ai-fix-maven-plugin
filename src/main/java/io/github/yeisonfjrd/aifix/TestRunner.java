package io.github.yeisonfjrd.aifix;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Corre los tests del proyecto en otro proceso de Maven (con el mvnw del proyecto si tiene) y lee
 * el resultado de los reportes de Surefire, no de la consola.
 */
public final class TestRunner {

    /** Resultado de una corrida. Si no compila, no hay reportes: el error de compilación va en `output`. */
    public record Result(boolean passed, boolean compiled, List<TestFailure> failures, String output, Duration took) {}

    private final Path basedir;
    private final String mavenHome;

    public TestRunner(Path basedir, String mavenHome) {
        this.basedir = basedir;
        this.mavenHome = mavenHome;
    }

    /** `selectors` vacío = todos los tests. */
    public Result run(Collection<String> selectors) throws IOException, InterruptedException {
        Path reports = basedir.resolve("target/surefire-reports");
        deleteTree(reports);
        List<String> cmd = new ArrayList<>(mavenCommand());
        cmd.addAll(List.of("-B", "-q", "test"));
        if (!selectors.isEmpty()) {
            cmd.add("-Dtest=" + String.join(",", selectors));
            cmd.add("-Dsurefire.failIfNoSpecifiedTests=false");
        }
        long start = System.nanoTime();
        Process process = new ProcessBuilder(cmd).directory(basedir.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = process.waitFor();
        Duration took = Duration.ofNanos(System.nanoTime() - start);

        List<TestFailure> failures = SurefireReports.read(reports);
        boolean compiled = Files.isDirectory(reports) || exit == 0;
        return new Result(exit == 0 && failures.isEmpty(), compiled, failures, tail(output, 6000), took);
    }

    List<String> mavenCommand() {
        boolean windows = File.separatorChar == '\\';
        Path wrapper = basedir.resolve(windows ? "mvnw.cmd" : "mvnw");
        String exe;
        if (Files.isRegularFile(wrapper)) {
            exe = wrapper.toString();
        } else if (mavenHome != null && !mavenHome.isBlank()) {
            exe = Path.of(mavenHome, "bin", windows ? "mvn.cmd" : "mvn").toString();
        } else {
            exe = windows ? "mvn.cmd" : "mvn";
        }
        // En Windows un .cmd no se puede lanzar directo con ProcessBuilder: va por cmd /c.
        return windows ? List.of("cmd", "/c", exe) : List.of(exe);
    }

    static String tail(String text, int max) {
        return text.length() <= max ? text : "…" + text.substring(text.length() - max);
    }

    private static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
        }
    }
}
