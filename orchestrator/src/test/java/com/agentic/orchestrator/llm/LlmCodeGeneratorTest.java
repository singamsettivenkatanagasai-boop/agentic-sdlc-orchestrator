package com.agentic.orchestrator.llm;

import com.agentic.orchestrator.llm.LlmCodeGenerator.GeneratedFile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LlmCodeGeneratorTest {

    @Test
    void parsesAPlainJsonArray() {
        List<GeneratedFile> files = LlmCodeGenerator.parse("[{\"path\":\"a/B.java\",\"content\":\"class B {}\"}]");
        assertEquals(1, files.size());
        assertEquals("a/B.java", files.get(0).path());
        assertEquals("class B {}", files.get(0).content());
    }

    @Test
    void toleratesMarkdownFencesAndSurroundingProse() {
        String reply = "Here you go:\n```json\n[{\"path\":\"x.txt\",\"content\":\"hi\"}]\n```\nDone.";
        assertEquals("x.txt", LlmCodeGenerator.parse(reply).get(0).path());
    }

    @Test
    void rejectsEmptyOrInvalidReplies() {
        assertThrows(IllegalArgumentException.class, () -> LlmCodeGenerator.parse(""));
        assertThrows(IllegalArgumentException.class, () -> LlmCodeGenerator.parse("no json here"));
        assertThrows(IllegalArgumentException.class, () -> LlmCodeGenerator.parse("[]"));
        assertThrows(IllegalArgumentException.class, () -> LlmCodeGenerator.parse("[{\"path\":\"\",\"content\":\"x\"}]"));
        assertThrows(IllegalArgumentException.class, () -> LlmCodeGenerator.parse("[{\"path\":\"a\"}]"));
    }

    @Test
    void replayClientIsNotLive() {
        assertEquals(false, new ReplayLlmClient().live());
    }
}
