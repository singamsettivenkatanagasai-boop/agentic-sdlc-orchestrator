package com.agentic.shortener.error;

/** The submitted URL is malformed or points somewhere that must not be shortened. */
public class InvalidUrlException extends RuntimeException {
    public InvalidUrlException(String message) {
        super(message);
    }
}
