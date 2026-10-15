package com.agentic.orchestrator.engine;

/** A human rejected the output of an attempt; the comment becomes feedback for the retry. */
public class HumanRejectedException extends RuntimeException {
    public HumanRejectedException(String approver, String comment) {
        super("rejected by " + approver + ": " + comment);
    }
}
