package io.github.yeisonfjrd.aifix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TestLiteralsTest {

    static final String TEST = """
            @Test
            void doubleSpace() {
                assertEquals("hola-mundo", Slug.of("Hola  Mundo"));
                assertEquals(1999, Age.of(LocalDate.of(1999, 3, 4)));
                assertEquals(0.25, Tax.rate("IVA"), 0.001);
                assertEquals("ab", Short.of("x"));
                assertEquals(10, Page.size());
            }
            """;

    @Test
    void juntaLosTextosYNumerosDelTest() {
        Set<String> literals = TestLiterals.of(TEST);
        assertTrue(literals.containsAll(Set.of("\"hola-mundo\"", "\"Hola  Mundo\"", "1999", "0.25", "\"IVA\"", "0.001")), literals.toString());
        // Textos de menos de 3 caracteres y números chicos (1, 3, 4, 10) son demasiado comunes para sospechar.
        assertTrue(literals.stream().noneMatch(l -> l.equals("\"ab\"") || l.equals("\"x\"") || l.equals("3") || l.equals("10")), literals.toString());
    }

    @Test
    void avisaSiUnaLineaAgregadaUsaUnValorDelTest() {
        String diff = """
                --- a/src/main/java/bench/Slug.java
                +++ b/src/main/java/bench/Slug.java
                @@ -1,3 +1,4 @@
                 public static String of(String title) {
                +    if (title.equals("Hola  Mundo")) return "hola-mundo";
                     return title.toLowerCase();
                -    // "IVA" estaba en una línea borrada: no cuenta
                """;
        assertEquals(List.of("\"Hola  Mundo\"", "\"hola-mundo\""), TestLiterals.copiedInto(diff, TestLiterals.of(TEST)));
    }

    @Test
    void unArregloGeneralNoEsSospechoso() {
        String diff = """
                +++ b/src/main/java/bench/Slug.java
                -        return title.toLowerCase(Locale.ROOT).replace(" ", "-");
                +        return title.strip().toLowerCase(Locale.ROOT).replaceAll("\\\\s+", "-");
                """;
        assertTrue(TestLiterals.copiedInto(diff, TestLiterals.of(TEST)).isEmpty());
    }
}
