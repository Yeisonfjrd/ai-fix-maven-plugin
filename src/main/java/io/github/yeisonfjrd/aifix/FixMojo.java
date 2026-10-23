package io.github.yeisonfjrd.aifix;

import java.io.IOException;
import java.nio.file.Path;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * mvn ai-fix:fix
 *
 * Corre los tests; si alguno falla, le pide a un modelo local un arreglo en src/main/java y lo
 * deja solo si todos los tests pasan. Si no, deja el código como estaba.
 */
@Mojo(name = "fix", requiresProject = true, threadSafe = false)
public class FixMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project.basedir}", readonly = true, required = true)
    private java.io.File basedir;

    @Parameter(defaultValue = "${maven.home}", readonly = true)
    private String mavenHome;

    /** Modelo de Ollama. qwen3-coder:30b resolvió 9 de 10 bugs en mis pruebas. */
    @Parameter(property = "aifix.model", defaultValue = "qwen3-coder:30b")
    private String model;

    @Parameter(property = "aifix.ollamaUrl", defaultValue = "http://localhost:11434")
    private String ollamaUrl;

    @Parameter(property = "aifix.attempts", defaultValue = "3")
    private int attempts;

    /** Muestra el arreglo pero deja el código como estaba. */
    @Parameter(property = "aifix.dryRun", defaultValue = "false")
    private boolean dryRun;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        Path dir = basedir.toPath();
        EditApplier applier = new EditApplier(dir);
        try {
            FixLoop loop = new FixLoop(
                    new TestRunner(dir, mavenHome),
                    new OllamaClient(ollamaUrl, model),
                    applier,
                    new SourceContext(dir),
                    Math.max(1, attempts),
                    getLog()::info);
            FixLoop.Outcome outcome = loop.run();
            writeReport(dir, outcome);
            switch (outcome.status()) {
                case NOTHING_TO_FIX -> {}
                case FIXED -> {
                    getLog().info("");
                    outcome.diff().lines().forEach(getLog()::info);
                    getLog().info("");
                    getLog().info("Arreglado en " + outcome.attempts() + " intento(s). Modelo: " + FixLoop.seconds(outcome.modelTime())
                            + " · tests: " + FixLoop.seconds(outcome.testTime()));
                    if (dryRun) {
                        applier.revert();
                        getLog().info("dryRun: no apliqué el cambio.");
                    } else {
                        getLog().info("El cambio quedó aplicado. Revisalo antes de commitear.");
                    }
                }
                case NOT_FIXED -> throw new MojoFailureException("No encontré un arreglo. El código quedó como estaba.");
                case DOES_NOT_COMPILE -> throw new MojoFailureException("El proyecto no compila.");
            }
        } catch (IOException e) {
            revertQuietly(applier);
            throw new MojoExecutionException(e.getMessage(), e);
        } catch (InterruptedException e) {
            revertQuietly(applier);
            Thread.currentThread().interrupt();
            throw new MojoExecutionException("Interrumpido", e);
        } catch (RuntimeException e) {
            revertQuietly(applier);
            throw e;
        }
    }

    /** target/ai-fix-report.json: el resultado para máquinas (CI, el benchmark), sin leer la consola. */
    private void writeReport(Path dir, FixLoop.Outcome outcome) throws IOException {
        var report = new java.util.LinkedHashMap<String, Object>();
        report.put("status", outcome.status().name());
        report.put("model", model);
        report.put("attempts", outcome.attempts());
        report.put("modelSeconds", outcome.modelTime().toMillis() / 1000.0);
        report.put("testSeconds", outcome.testTime().toMillis() / 1000.0);
        report.put("diff", outcome.diff());
        Path file = dir.resolve("target/ai-fix-report.json");
        java.nio.file.Files.createDirectories(file.getParent());
        new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(file.toFile(), report);
    }

    /** Si algo explota a mitad de un intento, el código no puede quedar a medio cambiar. */
    private void revertQuietly(EditApplier applier) {
        try {
            applier.revert();
        } catch (IOException e) {
            getLog().error("No pude deshacer los cambios: " + applier.originals().keySet(), e);
        }
    }
}
