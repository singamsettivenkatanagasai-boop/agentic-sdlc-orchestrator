package com.agentic.shortener.error;

/** The custom alias has a bad shape or is reserved. */
public class InvalidAliasException extends RuntimeException {
    public InvalidAliasException(String message) {
        super(message);
    }
}
