package io.github.yeisonfjrd.aifix;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MethodSnippetsTest {

    @TempDir Path dir;

    static final String CART = """
            package shop;

            public class Cart {
                public long subtotal() {
                    long sum = 0;
                    for (Item item : items) {
                        sum += item.unitPrice();
                    }
                    return sum;
                }

                public long total() {
                    return subtotal();
                }
            }
            """;

    @Test
    void planC_laRespuestaRealDelModeloChico() throws Exception {
        Path file = write("src/main/java/shop/Cart.java", CART.replace("\n", "\r\n"));
        // Copiada de la autoprueba que falló: FILE + solo el método, sin SEARCH/REPLACE.
        String answer = """
                The bug is that the `subtotal` method is not multiplying the `quantity` by the `unitPrice` before adding it to the sum.

                FILE: src/main/java/shop/Cart.java
                ```java
                    public long subtotal() {
                        long sum = 0;
                        for (Item item : items) {
                            sum += item.unitPrice() * item.quantity();
                        }
                        return sum;
                    }
                ```
                """;
        List<Edit> edits = MethodSnippets.parse(answer, dir);
        assertThat(edits).hasSize(1);
        new EditApplier(dir).apply(edits);
        String result = Files.readString(file);
        assertThat(result).contains("sum += item.unitPrice() * item.quantity();\r\n");
        assertThat(result).contains("public long total() {"); // el resto de la clase no se toca
        assertThat(result).doesNotContain("\n\n\n");
    }

    @Test
    void conMetodosSobrecargadosEligePorLosParametros() {
        String original = """
                class Money {
                    long round(long cents) { return cents; }
                    long round(double amount) { return (long) amount; }
                }
                """;
        List<Edit> edits = MethodSnippets.methodEdits("src/main/java/Money.java", original,
                "long round(double amount) { return Math.round(amount); }");
        assertThat(edits).hasSize(1);
        assertThat(edits.get(0).search()).isEqualTo("long round(double amount) { return (long) amount; }");
    }

    @Test
    void siElMetodoNoExisteNoAdivina() {
        assertThat(MethodSnippets.methodEdits("src/main/java/A.java", "class A { void a() {} }", "void b() {}")).isEmpty();
        assertThat(MethodSnippets.methodEdits("src/main/java/A.java", "class A { void a() {} }", "int x = 1;")).isEmpty();
    }

    @Test
    void ignoraArchivosQueNoExisten() {
        assertThat(MethodSnippets.parse("FILE: src/main/java/Nope.java\n```java\nvoid a() {}\n```\n", dir)).isEmpty();
    }

    private Path write(String rel, String content) throws Exception {
        Path p = dir.resolve(rel);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
        return p;
    }
}
