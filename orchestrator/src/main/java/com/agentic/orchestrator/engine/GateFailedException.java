package com.agentic.orchestrator.engine;

/** An exit gate rejected the output of an attempt; the message becomes feedback for the retry. */
public class GateFailedException extends RuntimeException {
    public GateFailedException(String gate, String reason) {
        super("gate '" + gate + "' failed: " + reason);
    }
}
