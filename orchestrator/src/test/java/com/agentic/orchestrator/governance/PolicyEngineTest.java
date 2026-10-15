package com.agentic.orchestrator.governance;

import com.agentic.orchestrator.graph.Risk;
import com.agentic.orchestrator.graph.TaskNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyEngineTest {
    private final PolicyEngine policy = new PolicyEngine();

    // Built from parts so this test file does not itself look like a leaked credential.
    private static final String AWS_KEY = "AKIA" + "IOSFODNN7EXAMPLE";

    @Test
    void detectsAwsKeysPrivateKeysAndHardCodedCredentials() {
        assertTrue(policy.scanSecrets("key=" + AWS_KEY).isPresent());
        assertTrue(policy.scanSecrets("-----BEGIN RSA PRIVATE" + " KEY-----").isPresent());
        assertTrue(policy.scanSecrets("db.password=" + "abcdefghijklmnop1234").isPresent());
        assertFalse(policy.scanSecrets("password=changeme").isPresent());
        assertFalse(policy.scanSecrets("plain text without secrets").isPresent());
    }

    @Test
    void secretWritesAreDeniedAsFatal() {
        PolicyDecision d = policy.checkWrite("developer", "src/main/resources/x.properties", "k=" + AWS_KEY);
        assertEquals(PolicyOutcome.DENY, d.outcome());
        assertTrue(d.fatal());
    }

    @Test
    void cleanWritesAreAllowed() {
        assertEquals(PolicyOutcome.ALLOW, policy.checkWrite("docs", "README.md", "hello").outcome());
    }

    private static String pom(String group) {
        return "<project><dependencies><dependency><groupId>" + group
                + "</groupId><artifactId>x</artifactId></dependency></dependencies></project>";
    }

    @Test
    void dependenciesMustBeOnTheAllowlist() {
        assertTrue(policy.checkDependencies(pom("org.springframework.boot")).isEmpty());
        assertTrue(policy.checkDependencies(pom("com.evil.corp")).isPresent());
        assertEquals(PolicyOutcome.DENY, policy.checkWrite("developer", "pom.xml", pom("com.evil.corp")).outcome());
    }

    @Test
    void malformedPomIsRejected() {
        assertTrue(policy.checkDependencies("<project><oops>").isPresent());
    }

    @Test
    void highRiskNodesAlwaysNeedApproval() {
        TaskNode high = TaskNode.builder("design", "architect").risk(Risk.HIGH).build();
        assertEquals(PolicyOutcome.REQUIRE_APPROVAL, policy.reviewNodeOutput(high, List.of("docs/design.md")).outcome());
    }

    @Test
    void contractAndSchemaChangesNeedApprovalEvenForLowRiskNodes() {
        TaskNode low = TaskNode.builder("implement", "developer").risk(Risk.MEDIUM).build();
        assertEquals(PolicyOutcome.ALLOW, policy.reviewNodeOutput(low, List.of("src/main/java/A.java")).outcome());
        assertEquals(PolicyOutcome.REQUIRE_APPROVAL, policy.reviewNodeOutput(low, List.of("openapi.yaml")).outcome());
        assertEquals(PolicyOutcome.REQUIRE_APPROVAL,
                policy.reviewNodeOutput(low, List.of("src/main/resources/db/migration/V2__x.sql")).outcome());
    }
}
