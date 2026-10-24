package io.github.yeisonfjrd.aifix;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Range;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Plan C: el modelo pone "FILE: ruta" y después solo el método corregido, sin SEARCH/REPLACE ni la
 * clase entera. Cada método de ese bloque reemplaza al del archivo con el mismo nombre y los mismos
 * tipos de parámetros, si hay exactamente uno.
 */
final class MethodSnippets {

    private static final Pattern FILE_FENCE = Pattern.compile("FILE:\\s*(\\S+)\\s*\\R+```\\w*\\R(.*?)\\R```", Pattern.DOTALL);
    private static final String WRAPPER = "class AiFixSnippet {\n";

    private MethodSnippets() {}

    static List<Edit> parse(String answer, Path basedir) {
        StaticJavaParser.getParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
        List<Edit> edits = new ArrayList<>();
        Matcher m = FILE_FENCE.matcher(answer.replace("\r\n", "\n"));
        while (m.find()) {
            String path = m.group(1).replace('\\', '/').replaceAll("^`+|`+$", "");
            String code = m.group(2);
            if (code.contains("<<<<<<<")) continue;
            Path file = basedir.resolve(path);
            if (!Files.isRegularFile(file)) continue;
            try {
                edits.addAll(methodEdits(path, Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n"), code));
            } catch (Exception e) {
                // Si el archivo o el bloque no se pueden parsear, este plan no aplica: el modelo recibe el aviso de formato.
            }
        }
        return edits;
    }

    static List<Edit> methodEdits(String path, String original, String snippet) {
        String wrapped = WRAPPER + snippet + "\n}";
        List<MethodDeclaration> proposed = StaticJavaParser.parse(wrapped).findAll(MethodDeclaration.class);
        if (proposed.isEmpty()) return List.of();
        CompilationUnit target = StaticJavaParser.parse(original);
        List<Edit> edits = new ArrayList<>();
        for (MethodDeclaration p : proposed) {
            List<MethodDeclaration> same = target.findAll(MethodDeclaration.class).stream()
                    .filter(t -> t.getNameAsString().equals(p.getNameAsString()))
                    .filter(t -> signature(t).equals(signature(p)))
                    .toList();
            if (same.size() != 1) return List.of(); // ambiguo o inexistente: mejor no adivinar
            String search = slice(original, same.get(0).getRange().orElseThrow());
            String replace = slice(wrapped, p.getRange().orElseThrow());
            edits.add(new Edit(path, search, replace));
        }
        return edits;
    }

    private static String signature(MethodDeclaration m) {
        return m.getParameters().stream().map(p -> p.getType().asString()).toList().toString();
    }

    /** El texto exacto que ocupa un nodo (líneas y columnas de JavaParser, desde 1 e inclusivas). */
    static String slice(String text, Range range) {
        String[] lines = text.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int line = range.begin.line; line <= range.end.line; line++) {
            String l = lines[line - 1];
            int from = line == range.begin.line ? range.begin.column - 1 : 0;
            int to = line == range.end.line ? range.end.column : l.length();
            sb.append(l, from, Math.min(to, l.length()));
            if (line < range.end.line) sb.append('\n');
        }
        return sb.toString();
    }
}
