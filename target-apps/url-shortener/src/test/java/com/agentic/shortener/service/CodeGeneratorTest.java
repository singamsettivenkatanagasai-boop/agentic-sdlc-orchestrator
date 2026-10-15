package com.agentic.shortener.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CodeGeneratorTest {

    @Test
    void generatesSevenBase62Characters() {
        CodeGenerator generator = new CodeGenerator();
        for (int i = 0; i < 200; i++) {
            assertThat(generator.next()).hasSize(CodeGenerator.LENGTH).matches("[0-9A-Za-z]{7}");
        }
    }

    @Test
    void codesAreRandom() {
        CodeGenerator generator = new CodeGenerator();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            seen.add(generator.next());
        }
        assertThat(seen).hasSize(1000);
    }

    @Test
    void sameSeedGivesSameSequence() {
        CodeGenerator a = new CodeGenerator(new Random(42));
        CodeGenerator b = new CodeGenerator(new Random(42));
        assertThat(a.next()).isEqualTo(b.next());
    }
}
