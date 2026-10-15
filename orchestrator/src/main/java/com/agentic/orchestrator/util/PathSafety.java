package com.agentic.orchestrator.util;

import com.agentic.orchestrator.model.PolicyViolationException;

import java.util.ArrayList;
import java.util.List;

/**
 * Normalises workspace-relative paths and rejects anything that could escape the workspace.
 * This is the first line of defence behind the "no writes outside the workspace" policy.
 */
public final class PathSafety {
    private PathSafety() { }

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw violation("empty path");
        }
        String p = raw.replace('\\', '/').trim();
        if (p.startsWith("/") || p.matches("^[A-Za-z]:.*") || p.startsWith("~")) {
            throw violation("absolute path not allowed: " + raw);
        }
        List<String> parts = new ArrayList<>();
        for (String seg : p.split("/")) {
            if (seg.isEmpty() || seg.equals(".")) {
                continue;
            }
            if (seg.equals("..")) {
                throw violation("path escapes the workspace: " + raw);
            }
            parts.add(seg);
        }
        if (parts.isEmpty()) {
            throw violation("empty path");
        }
        return String.join("/", parts);
    }

    private static PolicyViolationException violation(String reason) {
        return new PolicyViolationException("workspace-containment", reason, true);
    }
}
