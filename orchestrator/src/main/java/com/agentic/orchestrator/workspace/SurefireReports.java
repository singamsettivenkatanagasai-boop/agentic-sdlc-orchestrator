package com.agentic.orchestrator.workspace;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/** Reads Maven Surefire XML reports (more reliable than scraping console output). */
final class SurefireReports {
    private SurefireReports() { }

    /** @return {tests, failures, errors} summed over all report files, or zeros if there are none */
    static int[] summarize(Path projectRoot) {
        Path dir = projectRoot.resolve("target").resolve("surefire-reports");
        int tests = 0;
        int failures = 0;
        int errors = 0;
        if (!Files.isDirectory(dir)) {
            return new int[]{0, 0, 0};
        }
        List<Path> reports;
        try (Stream<Path> s = Files.list(dir)) {
            reports = s.filter(p -> p.getFileName().toString().startsWith("TEST-")
                    && p.getFileName().toString().endsWith(".xml")).toList();
        } catch (IOException e) {
            return new int[]{0, 0, 0};
        }
        for (Path report : reports) {
            try {
                DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
                f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
                f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                Document doc = f.newDocumentBuilder().parse(report.toFile());
                Element suite = doc.getDocumentElement();
                tests += intAttr(suite, "tests");
                failures += intAttr(suite, "failures");
                errors += intAttr(suite, "errors");
            } catch (Exception e) {
                errors++;   // an unreadable report must not look like a pass
            }
        }
        return new int[]{tests, failures, errors};
    }

    private static int intAttr(Element e, String name) {
        String v = e.getAttribute(name);
        return v == null || v.isBlank() ? 0 : Integer.parseInt(v.trim());
    }
}
