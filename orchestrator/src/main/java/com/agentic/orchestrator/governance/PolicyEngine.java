package com.agentic.orchestrator.governance;

import com.agentic.orchestrator.graph.Risk;
import com.agentic.orchestrator.graph.TaskNode;
import com.agentic.orchestrator.util.Globs;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Policy guardrails as code. Rules:
 * <ol>
 *   <li><b>no-secrets</b> (security): credentials in any written content are denied; fatal, stops the run.</li>
 *   <li><b>workspace-containment</b> (security): path escapes are denied; enforced by PathSafety and LocalWorkspace.</li>
 *   <li><b>dependency-allowlist</b> (security / supply chain): a pom.xml may only use approved groupIds.</li>
 *   <li><b>change-control</b> (change management): HIGH-risk nodes and API / schema changes need human approval.</li>
 *   <li><b>decisions-recorded</b> (compliance): enforced as an exit gate of the sign-off node.</li>
 * </ol>
 */
public class PolicyEngine {
    private record NamedPattern(String name, Pattern pattern) { }

    private static final List<NamedPattern> SECRET_PATTERNS = List.of(
            new NamedPattern("AWS access key id", Pattern.compile("AKIA[0-9A-Z]{16}")),
            new NamedPattern("private key block", Pattern.compile("-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----")),
            new NamedPattern("GitHub token", Pattern.compile("gh[pousr]_[A-Za-z0-9]{30,}")),
            new NamedPattern("API secret key", Pattern.compile("sk-[A-Za-z0-9]{20,}")),
            new NamedPattern("hard-coded credential", Pattern.compile(
                    "(?i)(?:password|passwd|secret|api[_-]?key|access[_-]?token)\\s*[=:]\\s*[\"']?[A-Za-z0-9/+_\\-]{16,}")));

    private static final Set<String> DEFAULT_ALLOWED_GROUPS = Set.of(
            "org.springframework.boot", "org.springframework", "com.h2database",
            "com.github.ben-manes.caffeine", "org.springdoc", "org.junit.jupiter", "org.assertj",
            "org.mockito", "com.jayway.jsonpath", "org.awaitility", "jakarta.validation",
            "org.hibernate.validator", "io.micrometer");

    private static final List<String> CONTRACT_GLOBS = List.of("openapi.yaml", "**/db/migration/**", "**/schema*.sql");

    private final Set<String> allowedGroups;

    public PolicyEngine() { this(DEFAULT_ALLOWED_GROUPS); }

    public PolicyEngine(Set<String> allowedGroups) { this.allowedGroups = Set.copyOf(allowedGroups); }

    /** Write-time check applied to every file an agent writes. */
    public PolicyDecision checkWrite(String agent, String path, String content) {
        Optional<String> secret = scanSecrets(content);
        if (secret.isPresent()) {
            return PolicyDecision.deny("no-secrets", "possible " + secret.get() + " in '" + path
                    + "' written by agent '" + agent + "'", true);
        }
        if (path.endsWith("pom.xml")) {
            Optional<String> bad = checkDependencies(content);
            if (bad.isPresent()) {
                return PolicyDecision.deny("dependency-allowlist", bad.get(), false);
            }
        }
        return PolicyDecision.allow();
    }

    /** Post-node review: decides whether a human must approve the output before it is accepted. */
    public PolicyDecision reviewNodeOutput(TaskNode node, Collection<String> changedPaths) {
        if (node.risk() == Risk.HIGH) {
            return PolicyDecision.requireApproval("change-control",
                    "node '" + node.id() + "' is HIGH risk (" + node.description() + ")");
        }
        for (String p : changedPaths) {
            for (String g : CONTRACT_GLOBS) {
                if (Globs.matches(g, p)) {
                    return PolicyDecision.requireApproval("change-control",
                            "'" + p + "' changes the public API contract or the database schema");
                }
            }
        }
        return PolicyDecision.allow();
    }

    /** Returns the name of the first secret pattern found, if any. */
    public Optional<String> scanSecrets(String content) {
        for (NamedPattern p : SECRET_PATTERNS) {
            if (p.pattern().matcher(content).find()) {
                return Optional.of(p.name());
            }
        }
        return Optional.empty();
    }

    /** Returns a message when the pom uses a groupId outside the allowlist or is not well-formed. */
    public Optional<String> checkDependencies(String pomXml) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document doc = f.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(pomXml.getBytes(StandardCharsets.UTF_8)));
            NodeList deps = doc.getElementsByTagName("dependency");
            for (int i = 0; i < deps.getLength(); i++) {
                Element dep = (Element) deps.item(i);
                NodeList groups = dep.getElementsByTagName("groupId");
                if (groups.getLength() > 0) {
                    String g = groups.item(0).getTextContent().trim();
                    if (!allowedGroups.contains(g)) {
                        return Optional.of("dependency groupId '" + g + "' is not on the allowlist");
                    }
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.of("pom.xml is not well-formed: " + e.getMessage());
        }
    }
}
