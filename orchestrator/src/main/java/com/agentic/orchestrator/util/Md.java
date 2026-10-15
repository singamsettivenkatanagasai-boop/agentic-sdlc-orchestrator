package com.agentic.orchestrator.util;

import java.util.Collection;
import java.util.List;

/** Small Markdown builders so agents stay readable. */
public final class Md {
    private Md() { }

    public static String bullets(Collection<String> items) {
        if (items.isEmpty()) {
            return "- _none_\n";
        }
        StringBuilder sb = new StringBuilder();
        for (String i : items) {
            sb.append("- ").append(i).append('\n');
        }
        return sb.toString();
    }

    public static String table(List<String> headers, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("| ").append(String.join(" | ", headers)).append(" |\n");
        sb.append("|").append(" --- |".repeat(headers.size())).append('\n');
        for (List<String> row : rows) {
            sb.append("| ").append(String.join(" | ", row.stream().map(Md::cell).toList())).append(" |\n");
        }
        return sb.toString();
    }

    private static String cell(String s) {
        return s == null ? "" : s.replace("|", "\\|").replace("\n", " ");
    }
}
