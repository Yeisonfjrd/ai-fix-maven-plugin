package io.github.yeisonfjrd.aifix;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContextTest {

    @TempDir Path dir;

    @Test
    void leeLasFallasDeSurefire() throws Exception {
        write("target/surefire-reports/TEST-shop.CartTest.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite name="shop.CartTest" tests="2" failures="1" errors="0">
                  <testcase name="total" classname="shop.CartTest" time="0.01"/>
                  <testcase name="discount()" classname="shop.CartTest" time="0.01">
                    <failure message="expected: &lt;90&gt; but was: &lt;80&gt;" type="org.opentest4j.AssertionFailedError">org.opentest4j.AssertionFailedError: expected: &lt;90&gt; but was: &lt;80&gt;
                	at shop.CartTest.discount(CartTest.java:12)</failure>
                  </testcase>
                </testsuite>
                """);
        write("target/surefire-reports/shop.CartTest.txt", "no es un reporte");
        List<TestFailure> failures = SurefireReports.read(dir.resolve("target/surefire-reports"));
        assertThat(failures).hasSize(1);
        assertThat(failures.get(0).selector()).isEqualTo("shop.CartTest#discount");
        assertThat(failures.get(0).message()).isEqualTo("expected: <90> but was: <80>");
    }

    @Test
    void sospechosos_porElStackTraceYPorLoQueUsaElTest() throws Exception {
        write("src/main/java/shop/Cart.java", "package shop; public class Cart {}");
        write("src/main/java/shop/Pricing.java", "package shop; public class Pricing {}");
        write("src/main/java/shop/Unrelated.java", "package shop; public class Unrelated {}");
        write("src/test/java/shop/CartTest.java", """
                package shop;
                class CartTest {
                    @org.junit.jupiter.api.Test
                    void discount() {
                        Cart cart = new Cart();
                    }
                }
                """);
        TestFailure f = new TestFailure("shop.CartTest", "discount", "boom",
                "java.lang.IllegalStateException: boom\n\tat shop.Pricing.apply(Pricing.java:3)\n\tat shop.CartTest.discount(CartTest.java:5)");
        SourceContext ctx = new SourceContext(dir);
        assertThat(ctx.suspects(List.of(f)))
                .extracting(p -> p.getFileName().toString())
                .containsExactly("Pricing.java", "Cart.java");
        String rendered = ctx.render(List.of(f));
        assertThat(rendered).contains("void discount()").contains("FILE: src/main/java/shop/Pricing.java").doesNotContain("Unrelated");
    }

    @Test
    void sospechosos_tambienLoQueUsanLosSospechosos() throws Exception {
        // El test usa Invoice; el bug está en TaxTable, que el test nunca nombra.
        write("src/main/java/shop/Invoice.java", "package shop; public class Invoice { private final TaxTable taxes = new TaxTable(); }");
        write("src/main/java/shop/TaxTable.java", "package shop; public class TaxTable { int rate() { return Rounding.HALF; } }");
        write("src/main/java/shop/Rounding.java", "package shop; public class Rounding { static final int HALF = 5; }");
        write("src/test/java/shop/InvoiceTest.java", """
                package shop;
                class InvoiceTest {
                    void tax() { new Invoice(); }
                }
                """);
        TestFailure f = new TestFailure("shop.InvoiceTest", "tax", "expected 1050", "AssertionFailedError\n\tat shop.InvoiceTest.tax(InvoiceTest.java:3)");
        assertThat(new SourceContext(dir).suspects(List.of(f)))
                .extracting(p -> p.getFileName().toString())
                // Un solo nivel: Rounding (lo que usa TaxTable) queda afuera para no llenar el contexto.
                .containsExactly("Invoice.java", "TaxTable.java");
    }

    @Test
    void elStackTraceSinRuidoDeJUnit() {
        String trace = "AssertionFailedError: x\n\tat org.junit.jupiter.api.Assertions.fail(A.java:1)\n\tat shop.CartTest.t(CartTest.java:5)\n\tat java.base/java.lang.reflect.Method.invoke(Method.java:580)";
        assertThat(SourceContext.trimTrace(trace)).isEqualTo("AssertionFailedError: x\n\tat shop.CartTest.t(CartTest.java:5)");
    }

    @Test
    void elUltimoCambioDeGit_primeroLoSinCommitearYSiNoElUltimoCommit() throws Exception {
        assertThat(new SourceContext(dir).recentChanges()).as("sin git no hay pista").isEmpty();
        git("init", "-q");
        write("src/main/java/shop/Cart.java", "package shop;\nclass Cart { int total() { return 1; } }\n");
        git("add", ".");
        git("commit", "-q", "-m", "uno");
        write("src/main/java/shop/Cart.java", "package shop;\nclass Cart { int total() { return 2; } }\n");
        git("commit", "-q", "-am", "dos");
        assertThat(new SourceContext(dir).recentChanges()).contains("-class Cart { int total() { return 1; } }").contains("+class Cart { int total() { return 2; } }");

        write("src/main/java/shop/Cart.java", "package shop;\nclass Cart { int total() { return 3; } }\n");
        assertThat(new SourceContext(dir).recentChanges()).contains("+class Cart { int total() { return 3; } }").doesNotContain("return 1;");
    }

    private void git(String... args) throws Exception {
        List<String> cmd = new java.util.ArrayList<>(List.of("git", "-c", "user.name=t", "-c", "user.email=t@t", "-c", "core.autocrlf=false"));
        cmd.addAll(List.of(args));
        Process p = new ProcessBuilder(cmd).directory(dir.toFile()).inheritIO().start();
        assertThat(p.waitFor()).isZero();
    }

    private void write(String rel, String content) throws Exception {
        Path p = dir.resolve(rel);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
    }
}
