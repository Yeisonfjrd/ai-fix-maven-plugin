package io.github.yeisonfjrd.aifix;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EditTest {

    @TempDir Path dir;

    @Test
    void leeBloquesConYSinCercaDeMarkdown() {
        String answer = """
                The discount is applied twice.

                FILE: src/main/java/shop/Cart.java
                ```java
                <<<<<<< SEARCH
                    return total * 2;
                =======
                    return total;
                >>>>>>> REPLACE
                ```

                FILE: src/main/java/shop/Tax.java
                <<<<<<< SEARCH
                int rate = 21;
                =======
                int rate = 10;
                >>>>>>> REPLACE
                """;
        List<Edit> edits = Edit.parseAll(answer);
        assertThat(edits).extracting(Edit::path).containsExactly("src/main/java/shop/Cart.java", "src/main/java/shop/Tax.java");
        assertThat(edits.get(0).search()).isEqualTo("    return total * 2;");
        assertThat(edits.get(0).replace()).isEqualTo("    return total;");
    }

    @Test
    void planB_laClaseCompletaSeUbicaPorPaqueteYNombre() {
        String answer = """
                The page drops its last item.

                ```java
                package bench;

                import java.util.List;

                public final class Paginator {
                    int to(int from, int size) { return from + size; }
                }
                ```

                ```java
                package bench;

                class PaginatorTest {}
                ```

                ```java
                int sinPaquete = 1;
                ```
                """;
        List<Edit> edits = Edit.parseWholeFiles(answer);
        assertThat(edits).extracting(Edit::path)
                .containsExactly("src/main/java/bench/Paginator.java", "src/main/java/bench/PaginatorTest.java");
        assertThat(edits.get(0).wholeFile()).isTrue();
        assertThat(edits.get(0).replace()).startsWith("package bench;").endsWith("}\n");
    }

    @Test
    void planB_reemplazaElArchivoEnteroRespetandoCrlf() throws Exception {
        Path file = write("src/main/java/bench/A.java", "package bench;\r\nclass A { int x = 1; }\r\n");
        EditApplier applier = new EditApplier(dir);
        assertThat(applier.isEditable("src/main/java/bench/A.java")).isTrue();
        assertThat(applier.isEditable("src/main/java/bench/ATest.java")).isFalse();
        applier.apply(List.of(new Edit("src/main/java/bench/A.java", null, "package bench;\nclass A { int x = 2; }\n")));
        assertThat(Files.readString(file)).isEqualTo("package bench;\r\nclass A { int x = 2; }\r\n");
        applier.revert();
        assertThat(Files.readString(file)).contains("x = 1");
    }

    @Test
    void unReplaceVacioBorraLasLineas() {
        List<Edit> edits = Edit.parseAll("FILE: src/main/java/A.java\n<<<<<<< SEARCH\nlog();\n=======\n>>>>>>> REPLACE\n");
        assertThat(edits.get(0).replace()).isEmpty();
    }

    @Test
    void aplicaYDeshace() throws Exception {
        Path file = write("src/main/java/shop/Cart.java", "class Cart {\n  int total() { return 2 * 2; }\n}\n");
        EditApplier applier = new EditApplier(dir);
        applier.apply(List.of(new Edit("src/main/java/shop/Cart.java", "2 * 2", "2 + 2")));
        assertThat(Files.readString(file)).contains("2 + 2");
        applier.revert();
        assertThat(Files.readString(file)).contains("2 * 2");
    }

    @Test
    void respetaLosFinDeLineaDeWindows() throws Exception {
        Path file = write("src/main/java/A.java", "class A {\r\n  int x = 1;\r\n  int y = 2;\r\n}\r\n");
        new EditApplier(dir).apply(List.of(new Edit("src/main/java/A.java", "  int x = 1;\n  int y = 2;", "  int x = 3;\n  int y = 4;")));
        assertThat(Files.readString(file)).isEqualTo("class A {\r\n  int x = 3;\r\n  int y = 4;\r\n}\r\n");
    }

    @Test
    void noDejaTocarLosTestsNiSalirseDelProyecto() throws Exception {
        write("src/test/java/CartTest.java", "class CartTest {}");
        write("src/main/java/A.java", "class A {}");
        EditApplier applier = new EditApplier(dir);
        assertThatThrownBy(() -> applier.apply(List.of(new Edit("src/test/java/CartTest.java", "class", "x"))))
                .isInstanceOf(EditApplier.Rejected.class).hasMessageContaining("Los tests no se tocan");
        assertThatThrownBy(() -> applier.apply(List.of(new Edit("src/main/java/../../../pom.xml", "a", "b"))))
                .isInstanceOf(EditApplier.Rejected.class);
        assertThatThrownBy(() -> applier.apply(List.of(new Edit("src/main/java/Nuevo.java", "a", "b"))))
                .isInstanceOf(EditApplier.Rejected.class).hasMessageContaining("no existe");
    }

    @Test
    void unSearchSinSangriaSeEncuentraYElReplaceSeAlinea() throws Exception {
        // Así respondió qwen2.5-coder:7b en el caso 03 del benchmark: el arreglo bien, la sangría no.
        Path file = write("src/main/java/bench/Names.java", """
                class Names {
                    static String displayName(User user) {
                        if (user.nickname().isBlank()) {
                            return user.first() + " " + user.last();
                        }
                        return user.nickname();
                    }
                }
                """.replace("\n", "\r\n"));
        new EditApplier(dir).apply(List.of(new Edit("src/main/java/bench/Names.java",
                "if (user.nickname().isBlank()) {\n    return user.first() + \" \" + user.last();\n}",
                "if (user.nickname() == null || user.nickname().isBlank()) {\n    return user.first() + \" \" + user.last();\n}")));
        assertThat(Files.readString(file)).isEqualTo("""
                class Names {
                    static String displayName(User user) {
                        if (user.nickname() == null || user.nickname().isBlank()) {
                            return user.first() + " " + user.last();
                        }
                        return user.nickname();
                    }
                }
                """.replace("\n", "\r\n"));
    }

    @Test
    void sinSangriaPeroAmbiguoNoAdivina() throws Exception {
        write("src/main/java/A.java", "class A {\n    void a() {\n        x++;\n    }\n    void b() {\n        x++;\n    }\n}\n");
        // "x++;\n}" sin sangría no está tal cual, e ignorando la sangría aparece dos veces.
        assertThatThrownBy(() -> new EditApplier(dir).apply(List.of(new Edit("src/main/java/A.java", "x++;\n}", "x--;\n}"))))
                .isInstanceOf(EditApplier.Rejected.class).hasMessageContaining("no encontré");
    }

    @Test
    void elSearchTieneQueAparecerUnaSolaVez() throws Exception {
        write("src/main/java/A.java", "x = 1;\nx = 1;\n");
        EditApplier applier = new EditApplier(dir);
        assertThatThrownBy(() -> applier.apply(List.of(new Edit("src/main/java/A.java", "x = 1;", "x = 2;"))))
                .isInstanceOf(EditApplier.Rejected.class).hasMessageContaining("más de una vez");
        assertThatThrownBy(() -> applier.apply(List.of(new Edit("src/main/java/A.java", "y = 1;", "y = 2;"))))
                .isInstanceOf(EditApplier.Rejected.class).hasMessageContaining("no encontré");
    }

    @Test
    void siUnCambioFallaNoSeAplicaNinguno() throws Exception {
        Path a = write("src/main/java/A.java", "a = 1;\n");
        write("src/main/java/B.java", "b = 1;\n");
        EditApplier applier = new EditApplier(dir);
        assertThatThrownBy(() -> applier.apply(List.of(
                new Edit("src/main/java/A.java", "a = 1;", "a = 2;"),
                new Edit("src/main/java/B.java", "no existe", "b = 2;"))))
                .isInstanceOf(EditApplier.Rejected.class);
        assertThat(Files.readString(a)).isEqualTo("a = 1;\n");
    }

    private Path write(String rel, String content) throws Exception {
        Path p = dir.resolve(rel);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
        return p;
    }
}
