package com.agentic.shortener.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Random;

/** Generates random base62 short codes (62^7 is about 3.5 trillion combinations). */
@Component
public class CodeGenerator {
    public static final int LENGTH = 7;
    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private final Random random;

    @Autowired
    public CodeGenerator() {
        this(new SecureRandom());
    }

    public CodeGenerator(Random random) {
        this.random = random;
    }

    public String next() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
