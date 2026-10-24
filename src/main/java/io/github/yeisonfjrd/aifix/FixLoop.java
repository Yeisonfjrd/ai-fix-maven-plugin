package io.github.yeisonfjrd.aifix;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * El ciclo: correr los tests, pedir un arreglo, aplicarlo, volver a correr. Un intento que no
 * anda se deshace antes del siguiente, así cada intento parte del código original y los errores
 * no se acumulan. Al final queda el arreglo con todos los tests en verde, o nada.
 */
public final class FixLoop {

    public enum Status { NOTHING_TO_FIX, FIXED, NOT_FIXED, DOES_NOT_COMPILE }

    public record Outcome(Status status, int attempts, String diff, Duration modelTime, Duration testTime) {}

    static final String SYSTEM = """
            You fix bugs in Java code so that failing unit tests pass.

            Rules:
            - The tests are correct. Never change a test. You can only edit the src/main/java files shown to you.
            - Fix the real cause in a general way. Do not special-case the values used by the test.
            - Make the smallest change that fixes the bug. Keep the existing style.

            Answer with one short sentence explaining the bug, then one or more edit blocks, and nothing else:

            FILE: src/main/java/path/To/File.java
            <<<<<<< SEARCH
            exact lines copied from the file, with the same indentation
            =======
            the new lines
            >>>>>>> REPLACE

            The SEARCH text must appear exactly once in the file. Add surrounding lines if needed to make it unique.
            """;

    private final TestRunner runner;
    private final OllamaClient model;
    private final EditApplier applier;
    private final SourceContext context;
    private final int maxAttempts;
    private final Consumer<String> log;

    public FixLoop(TestRunner runner, OllamaClient model, EditApplier applier, SourceContext context, int maxAttempts, Consumer<String> log) {
        this.runner = runner;
        this.model = model;
        this.applier = applier;
        this.context = context;
        this.maxAttempts = maxAttempts;
        this.log = log;
    }

    /** La conversación con el modelo, para guardarla y ver qué respondió en cada intento. */
    private final List<OllamaClient.Message> messages = new ArrayList<>();

    /** La conversación entera en Markdown: qué se le mandó al modelo y qué contestó. */
    public String transcript() {
        StringBuilder sb = new StringBuilder("# ai-fix transcript\n\n");
        for (OllamaClient.Message m : messages) {
            sb.append("## ").append(m.role()).append("\n\n").append(m.content().strip()).append("\n\n");
        }
        return sb.toString();
    }

    public Outcome run() throws IOException, InterruptedException {
        Duration modelTime = Duration.ZERO;
        Duration testTime = Duration.ZERO;

        log.accept("Corriendo los tests…");
        TestRunner.Result baseline = runner.run(List.of());
        testTime = testTime.plus(baseline.took());
        if (!baseline.compiled()) {
            log.accept("El proyecto no compila. ai-fix arregla tests que fallan, no errores de compilación:\n" + baseline.output());
            return new Outcome(Status.DOES_NOT_COMPILE, 0, "", modelTime, testTime);
        }
        if (baseline.passed()) {
            log.accept("Todos los tests pasan: no hay nada que arreglar.");
            return new Outcome(Status.NOTHING_TO_FIX, 0, "", modelTime, testTime);
        }
        List<TestFailure> failing = baseline.failures();
        List<String> selectors = failing.stream().map(TestFailure::selector).distinct().toList();
        log.accept("Fallan " + failing.size() + " test(s): " + String.join(", ", selectors));

        messages.clear();
        messages.add(new OllamaClient.Message("system", SYSTEM));
        messages.add(new OllamaClient.Message("user", context.render(failing)));

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            log.accept("Intento " + attempt + "/" + maxAttempts + ": pidiendo un arreglo al modelo…");
            OllamaClient.Reply reply = model.chat(messages);
            modelTime = modelTime.plus(reply.took());
            messages.add(new OllamaClient.Message("assistant", reply.content()));
            log.accept("  el modelo respondió en " + seconds(reply.took()) + ": " + firstLine(reply.content()));

            String feedback;
            String retry = "Try a different fix.";
            try {
                List<Edit> edits = edits(reply.content());
                if (edits.isEmpty()) {
                    // No es un arreglo malo: es que no vino en un formato aplicable. Que lo repita bien.
                    retry = "Keep your fix if you think it is right, but answer again using the FILE / SEARCH / REPLACE format exactly.";
                    throw new EditApplier.Rejected("I found no FILE/SEARCH/REPLACE block and no complete class I could apply.");
                }
                applier.apply(edits);
                TestRunner.Result result = runner.run(selectors);
                testTime = testTime.plus(result.took());
                if (result.passed()) {
                    // Los que fallaban ya pasan: ahora todos, para no romper otra cosa.
                    log.accept("  los tests que fallaban ya pasan; corriendo todos para ver que no se rompió otra cosa…");
                    result = runner.run(List.of());
                    testTime = testTime.plus(result.took());
                    if (result.passed()) {
                        String diff = diff();
                        return new Outcome(Status.FIXED, attempt, diff, modelTime, testTime);
                    }
                }
                feedback = describe(result);
                log.accept("  no alcanzó: " + firstLine(feedback));
            } catch (EditApplier.Rejected e) {
                feedback = "Your edit could not be applied: " + e.getMessage();
                log.accept("  cambio rechazado: " + firstLine(e.getMessage()));
            }
            applier.revert();
            messages.add(new OllamaClient.Message("user", feedback
                    + "\n\nI reverted your change, so the files are back to the original code shown before. " + retry));
        }
        log.accept("No encontré un arreglo en " + maxAttempts + " intentos. Dejé todo como estaba.");
        return new Outcome(Status.NOT_FIXED, maxAttempts, "", modelTime, testTime);
    }

    /**
     * Los bloques SEARCH/REPLACE; si no hay, plan B: las clases completas que vinieron en bloques ```java,
     * solo las que ya existen en src/main/java (si el modelo repite el test, ese bloque se ignora).
     */
    List<Edit> edits(String answer) {
        List<Edit> edits = Edit.parseAll(answer);
        if (!edits.isEmpty()) return edits;
        List<Edit> whole = Edit.parseWholeFiles(answer).stream().filter(e -> applier.isEditable(e.path())).toList();
        if (!whole.isEmpty()) {
            log.accept("  vino la clase completa en vez de SEARCH/REPLACE: la uso entera");
            return whole;
        }
        // Plan C: solo el método corregido, debajo de "FILE: ruta".
        List<Edit> methods = MethodSnippets.parse(answer, applier.basedir()).stream().filter(e -> applier.isEditable(e.path())).toList();
        if (!methods.isEmpty()) log.accept("  vino solo el método corregido: reemplazo ese método");
        return methods;
    }

    private static String describe(TestRunner.Result result) {
        if (!result.compiled()) return "With your change the code does not compile:\n" + TestRunner.tail(result.output(), 3000);
        StringBuilder sb = new StringBuilder("With your change these tests fail:\n");
        for (TestFailure f : result.failures()) {
            sb.append("- ").append(f.selector()).append(": ").append(SourceContext.trimTrace(f.stackTrace())).append('\n');
        }
        return sb.toString();
    }

    /** Diff unificado de todo lo que cambió, contra el original. */
    String diff() throws IOException {
        StringBuilder sb = new StringBuilder();
        for (var entry : applier.originals().entrySet()) {
            Path file = entry.getKey();
            String rel = applier.basedir().relativize(file).toString().replace('\\', '/');
            List<String> before = entry.getValue().replace("\r\n", "\n").lines().toList();
            List<String> after = Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n").lines().toList();
            var patch = DiffUtils.diff(before, after);
            UnifiedDiffUtils.generateUnifiedDiff("a/" + rel, "b/" + rel, before, patch, 3).forEach(l -> sb.append(l).append('\n'));
        }
        return sb.toString();
    }

    static String seconds(Duration d) {
        return String.format(java.util.Locale.ROOT, "%.1f s", d.toMillis() / 1000.0);
    }

    private static String firstLine(String text) {
        String line = text.strip().lines().findFirst().orElse("");
        return line.length() > 160 ? line.substring(0, 160) + "…" : line;
    }
}
