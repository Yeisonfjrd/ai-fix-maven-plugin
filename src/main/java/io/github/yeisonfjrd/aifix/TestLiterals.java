package io.github.yeisonfjrd.aifix;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Valores del test que reaparecen en el arreglo: la trampa típica de un modelo es preguntar por el
 * dato exacto del test en vez de arreglar la regla.
 */
final class TestLiterals {

    private static final Pattern STRING = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"");
    // Decimales (0.25) o enteros de 3 o más cifras (1999). Los chicos (1, 3, 10) aparecen en cualquier código.
    private static final Pattern NUMBER = Pattern.compile("(?<![\\w.])(?:\\d+\\.\\d+|\\d{3,})(?![\\w.])");

    private TestLiterals() {}

    /** Los textos (de 3 o más caracteres) y los números llamativos del código del test. */
    static Set<String> of(String testSource) {
        return new LinkedHashSet<>(literals(testSource));
    }

    /** Los valores del test que aparecen en líneas agregadas del diff. Las borradas no cuentan. */
    static List<String> copiedInto(String diff, Set<String> literals) {
        Set<String> found = new LinkedHashSet<>();
        for (String line : diff.split("\\R")) {
            if (!line.startsWith("+") || line.startsWith("+++")) continue;
            for (String literal : literals(line.substring(1))) {
                if (literals.contains(literal)) found.add(literal);
            }
        }
        return List.copyOf(found);
    }

    private static List<String> literals(String code) {
        List<String> out = new ArrayList<>();
        Matcher s = STRING.matcher(code);
        while (s.find()) {
            if (s.group().length() - 2 >= 3) out.add(s.group());
        }
        // Los números se buscan con los textos tapados, para no agarrar el 2026 de "v2026".
        Matcher n = NUMBER.matcher(STRING.matcher(code).replaceAll("\"\""));
        while (n.find()) out.add(n.group());
        return out;
    }
}
