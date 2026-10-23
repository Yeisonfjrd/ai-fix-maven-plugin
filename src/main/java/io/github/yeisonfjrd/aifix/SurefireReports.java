package io.github.yeisonfjrd.aifix;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** Lee los XML de Surefire (TEST-*.xml) y devuelve los tests con failure o error. */
public final class SurefireReports {

    private SurefireReports() {}

    public static List<TestFailure> read(Path reportsDir) {
        if (!Files.isDirectory(reportsDir)) return List.of();
        List<TestFailure> failures = new ArrayList<>();
        try (Stream<Path> files = Files.list(reportsDir)) {
            for (Path xml : files.filter(SurefireReports::isReport).sorted().toList()) {
                failures.addAll(parse(xml));
            }
        } catch (IOException e) {
            throw new IllegalStateException("No pude leer " + reportsDir, e);
        }
        return failures;
    }

    private static boolean isReport(Path p) {
        String name = p.getFileName().toString();
        return name.startsWith("TEST-") && name.endsWith(".xml");
    }

    static List<TestFailure> parse(Path xml) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            // Los reportes son archivos locales, pero igual no seguimos entidades externas.
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            var doc = factory.newDocumentBuilder().parse(xml.toFile());
            NodeList cases = doc.getElementsByTagName("testcase");
            List<TestFailure> out = new ArrayList<>();
            for (int i = 0; i < cases.getLength(); i++) {
                Element testcase = (Element) cases.item(i);
                Element problem = firstChild(testcase, "failure");
                if (problem == null) problem = firstChild(testcase, "error");
                if (problem == null) continue;
                out.add(new TestFailure(
                        testcase.getAttribute("classname"),
                        testcase.getAttribute("name").replaceAll("\\(.*\\)$", ""),
                        problem.getAttribute("message"),
                        problem.getTextContent().strip()));
            }
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("Reporte de Surefire ilegible: " + xml, e);
        }
    }

    private static Element firstChild(Element parent, String tag) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && e.getTagName().equals(tag)) return e;
        }
        return null;
    }
}
