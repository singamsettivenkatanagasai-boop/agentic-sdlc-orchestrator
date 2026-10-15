package com.agentic.shortener.error;

/** The code is unknown or the link was deleted. */
public class LinkNotFoundException extends RuntimeException {
    public LinkNotFoundException(String code) {
        super("No link with code '" + code + "'");
    }
}
