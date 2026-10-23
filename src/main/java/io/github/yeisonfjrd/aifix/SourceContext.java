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
            if (test == null) continue;
            try {
                CompilationUnit cu = StaticJavaParser.parse(test);
                cu.findAll(ClassOrInterfaceType.class).forEach(t -> addBySimpleName(out, t.getNameAsString()));
                cu.findAll(NameExpr.class).forEach(n -> addBySimpleName(out, n.getNameAsString()));
            } catch (Exception e) {
                // Si el test no se puede parsear, quedan solo los sospechosos del stack trace.
            }
        }
        return out;
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
