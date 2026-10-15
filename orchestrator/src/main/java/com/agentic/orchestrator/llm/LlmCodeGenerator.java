package com.agentic.orchestrator.llm;

import com.agentic.orchestrator.workspace.Workspace;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Asks the model for a JSON list of files and writes them through the (guarded) workspace.
 * The model never touches the disk directly: path boundaries, secret scans and the dependency
 * allowlist are enforced by the workspace on every file, so a misbehaving model cannot escape them.
 */
public final class LlmCodeGenerator {
    public record GeneratedFile(String path, String content) { }

    private static final ObjectMapper JSON = new ObjectMapper();
    private final LlmClient llm;

    public LlmCodeGenerator(LlmClient llm) { this.llm = llm; }

    /** @return the paths written */
    public List<String> generate(String system, String user, Workspace workspace)
            throws IOException, InterruptedException {
        List<GeneratedFile> files = parse(llm.complete(system, user));
        List<String> written = new ArrayList<>();
        for (GeneratedFile f : files) {
            workspace.write(f.path(), f.content());
            written.add(f.path());
        }
        return written;
    }

    /** Parses the model reply; tolerant of markdown fences and surrounding prose. */
    public static List<GeneratedFile> parse(String reply) {
        if (reply == null) {
            throw new IllegalArgumentException("empty model reply");
        }
        int start = reply.indexOf('[');
        int end = reply.lastIndexOf(']');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("model reply does not contain a JSON array of files");
        }
        JsonNode root;
        try {
            root = JSON.readTree(reply.substring(start, end + 1));
        } catch (IOException e) {
            throw new IllegalArgumentException("model reply is not valid JSON: " + e.getMessage(), e);
        }
        if (!root.isArray() || root.isEmpty()) {
            throw new IllegalArgumentException("model reply must be a non-empty JSON array");
        }
        List<GeneratedFile> files = new ArrayList<>();
        for (JsonNode n : root) {
            String path = n.path("path").asText("");
            String content = n.path("content").asText("");
            if (path.isBlank() || content.isEmpty()) {
                throw new IllegalArgumentException("each file needs a non-empty 'path' and 'content'");
            }
            files.add(new GeneratedFile(path, content));
        }
        return files;
    }
}
