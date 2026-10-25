package io.github.yeisonfjrd.aifix;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Arma lo que ve el modelo: cada test que falla (solo su método, no el archivo entero) con su
 * error, y el código de src/main que puede tener la culpa. La culpa se busca por dos lados:
 * las líneas del stack trace que caen en src/main, y las clases que el test usa.
 */
public final class SourceContext {

    private static final Pattern FRAME = Pattern.compile("at ([\\w.$]+)\\.[\\w$<>]+\\(");
    private static final int MAX_CHARS = 60_000;
    // Un diff más grande que esto ya no es una pista: es medio proyecto.
    private static final int MAX_DIFF_CHARS = 8_000;

    private final Path basedir;
    private final Map<String, Path> mainBySimpleName = new HashMap<>();
    private final Map<String, Path> mainByFqcn = new HashMap<>();

    public SourceContext(Path basedir) throws IOException {
        this.basedir = basedir;
        StaticJavaParser.getParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
        Path main = basedir.resolve("src/main/java");
        if (Files.isDirectory(main)) {
            try (Stream<Path> walk = Files.walk(main)) {
                for (Path p : walk.filter(f -> f.toString().endsWith(".java")).toList()) {
                    String fqcn = main.relativize(p).toString().replace('\\', '/').replaceAll("\\.java$", "").replace('/', '.');
                    mainByFqcn.put(fqcn, p);
                    mainBySimpleName.putIfAbsent(fqcn.substring(fqcn.lastIndexOf('.') + 1), p);
                }
            }
        }
    }

    /** Clases de src/main relacionadas con estas fallas, en orden de relevancia. */
    public Set<Path> suspects(List<TestFailure> failures) {
        Set<Path> out = new LinkedHashSet<>();
        for (TestFailure f : failures) {
            Matcher m = FRAME.matcher(f.stackTrace());
            while (m.find()) {
                String cls = m.group(1).replaceAll("\\$.*$", "");
                Path p = mainByFqcn.get(cls);
                if (p != null) out.add(p);
            }
        }
        for (TestFailure f : failures) {
            Path test = testFile(f.className());
            if (test != null) out.addAll(referencedMainClasses(test));
        }
        // Un nivel más: lo que usan los sospechosos. Si el test toca Invoice y el bug está en la
        // TaxTable que usa Invoice, el modelo tiene que ver las dos (caso 10 del benchmark).
        for (Path suspect : List.copyOf(out)) {
            out.addAll(referencedMainClasses(suspect));
        }
        return out;
    }

    /** Las clases de src/main que nombra este archivo (tipos y nombres sueltos, como Clase.metodo()). */
    private Set<Path> referencedMainClasses(Path file) {
        Set<Path> found = new LinkedHashSet<>();
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            cu.findAll(ClassOrInterfaceType.class).forEach(t -> addBySimpleName(found, t.getNameAsString()));
            cu.findAll(NameExpr.class).forEach(n -> addBySimpleName(found, n.getNameAsString()));
        } catch (Exception e) {
            // Si no se puede parsear, no suma sospechosos: quedan los del stack trace.
        }
        found.remove(file);
        return found;
    }

    private void addBySimpleName(Set<Path> out, String name) {
        Path p = mainBySimpleName.get(name);
        if (p != null) out.add(p);
    }

    public String render(List<TestFailure> failures) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("## Tests que fallan\n\n");
        for (TestFailure f : failures) {
            sb.append("### ").append(f.selector()).append("\n");
            String method = testMethod(f);
            if (method != null) sb.append("```java\n").append(method).append("\n```\n");
            sb.append("Error:\n```\n").append(trimTrace(f.stackTrace())).append("\n```\n\n");
        }
        String recent = recentChanges();
        if (!recent.isBlank()) {
            sb.append("## Último cambio en src/main (los tests andaban antes de esto, el error puede estar acá)\n\n```diff\n")
                    .append(recent).append("\n```\n\n");
        }
        sb.append("## Código de src/main que podría tener el error\n\n");
        for (Path p : suspects(failures)) {
            String code = Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n");
            String block = "FILE: " + basedir.relativize(p).toString().replace('\\', '/') + "\n```java\n" + code + "\n```\n\n";
            if (sb.length() + block.length() > MAX_CHARS) {
                sb.append("(Hay más clases relacionadas, no entraron por tamaño.)\n");
                break;
            }
            sb.append(block);
        }
        return sb.toString();
    }

    /**
     * Lo último que cambió en src/main/java según git: lo que todavía no se commiteó, o si no hay
     * nada, el último commit (en CI, el push que rompió los tests). Vacío si no es un repo de git o
     * el diff es muy grande para servir de pista.
     */
    String recentChanges() {
        if (!Files.exists(basedir.resolve(".git"))) return "";
        String pending = git("diff", "HEAD", "--", "src/main/java");
        String diff = pending.isBlank() ? git("diff", "HEAD~1", "HEAD", "--", "src/main/java") : pending;
        return diff.length() > MAX_DIFF_CHARS ? "" : diff.strip();
    }

    private String git(String... args) {
        try {
            List<String> cmd = new java.util.ArrayList<>(List.of("git"));
            cmd.addAll(List.of(args));
            Process p = new ProcessBuilder(cmd).directory(basedir.toFile()).redirectErrorStream(false).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return "";
            }
            return p.exitValue() == 0 ? out : "";
        } catch (IOException e) {
            return "";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        }
    }

    String testMethod(TestFailure f) {
        Path test = testFile(f.className());
        if (test == null) return null;
        try {
            CompilationUnit cu = StaticJavaParser.parse(test);
            return cu.findAll(MethodDeclaration.class).stream()
                    .filter(m -> m.getNameAsString().equals(f.methodName()))
                    .findFirst()
                    .map(MethodDeclaration::toString)
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private Path testFile(String className) {
        String outer = className.replaceAll("\\$.*$", "");
        Path p = basedir.resolve("src/test/java").resolve(outer.replace('.', '/') + ".java");
        return Files.isRegularFile(p) ? p : null;
    }

    /** El stack trace sin las líneas de JUnit y del JDK: son ruido para el modelo. */
    static String trimTrace(String trace) {
        StringBuilder out = new StringBuilder();
        int kept = 0;
        for (String line : trace.split("\\R")) {
            String t = line.strip();
            boolean noise = t.startsWith("at org.junit.") || t.startsWith("at java.base/") || t.startsWith("at org.apache.maven.")
                    || t.startsWith("at jdk.internal.") || t.startsWith("at sun.") || t.startsWith("at org.opentest4j.");
            if (noise) continue;
            out.append(line).append('\n');
            if (++kept >= 25) break;
        }
        return out.toString().strip();
    }
}
