package com.agentic.shortener.error;

/** The requested custom alias is already used by a different URL. */
public class AliasConflictException extends RuntimeException {
    public AliasConflictException(String alias) {
        super("Alias '" + alias + "' is already taken");
    }
}
