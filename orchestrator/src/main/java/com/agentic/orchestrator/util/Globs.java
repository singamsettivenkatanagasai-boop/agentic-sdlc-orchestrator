package com.agentic.orchestrator.util;

import java.util.regex.Pattern;

/**
 * Tiny glob matcher over '/'-separated relative paths. Own implementation so behaviour is
 * identical on every OS (java.nio PathMatcher treats separators differently on Windows).
 * Supports {@code *} (within a segment), {@code **} (across segments) and {@code ?}.
 */
public final class Globs {
    private Globs() { }

    public static boolean matches(String glob, String path) {
        return Pattern.matches(toRegex(glob), path);
    }

    static String toRegex(String glob) {
        StringBuilder re = new StringBuilder();
        int i = 0;
        while (i < glob.length()) {
            char c = glob.charAt(i);
            if (c == '*') {
                if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                    if (i + 2 < glob.length() && glob.charAt(i + 2) == '/') {
                        re.append("(?:.*/)?");   // "**/" = zero or more directories
                        i += 3;
                    } else {
                        re.append(".*");
                        i += 2;
                    }
                } else {
                    re.append("[^/]*");
                    i++;
                }
            } else if (c == '?') {
                re.append("[^/]");
                i++;
            } else {
                if ("\\.[]{}()+-^$|".indexOf(c) >= 0) {
                    re.append('\\');
                }
                re.append(c);
                i++;
            }
        }
        return re.toString();
    }
}
